package microservices.helper.idempotency.service;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import microservices.helper.idempotency.entity.*;
import microservices.helper.idempotency.repository.*;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import microservices.helper.idempotency.enums.ExecutionResult;
import microservices.helper.idempotency.exception.IdempotencyException;
import microservices.helper.idempotency.model.IdempotentOperationResult;
import microservices.helper.idempotency.cache.IdempotentOperationConfigCache;
import org.springframework.util.DigestUtils;

@Service
@AllArgsConstructor
@Slf4j
public class IdempotencyServiceImpl implements IdempotencyService {

    private final IdempotentOperationRepository idempotentOperationRepository;
    private final IdempotentOperationLockRepository idempotentOperationLockRepository;
    private final StoredIdempotentOperationResultRepository storedIdempotentOperationResultRepository;
    private final IdempotentOperationLockTempRepository idempotentOperationLockTempRepository;
    private final FailedIdempotentOperationResultRepository failedIdempotentOperationResultRepository;
    private final IdempotentOperationConfigCache idempotentOperationConfigCache;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    public IdempotentOperationResult getStoredExecutionResultOrLockOperation(IdempotentOperationResult input) {
        log.info("Processing idempotent operation for service: {}, operation: {}, key: {}", input.getService(), input.getOperation(), input.getIdempotencyKey());

        // STEP 1: ALWAYS create IdempotentOperation record for ALL incoming requests
        IdempotentOperation idempotentOperation = createIdempotentOperation(input);

        // STEP 2: Check if a result already exists (fast path)
        Optional<StoredIdempotentOperationResult> existingResult = storedIdempotentOperationResultRepository.findById(getHashedKey(input.getService(), input.getOperation(), input.getIdempotencyKey()));
        if (existingResult.isPresent()) {
            log.info("Found existing result, returning cached response");
            return getCachedResult(existingResult.get());
        }

        // STEP 3: ATOMIC LOCK ACQUISITION - Only ONE can succeed for the same(service, operation, idempotencyKey)
        // This uses MongoDB's unique constraint to ensure atomicity
        IdempotentOperationLockTemp tempLock = acquireLock(input, idempotentOperation);

        // Schedule to clean up lock and operation
        scheduler.schedule(() -> {
            if (idempotentOperationLockTempRepository.existsById(tempLock.getId())) { // That means the operation is not completed
                deleteTempLock(tempLock.getId());
                insertLockRecord(tempLock.getId(), tempLock.getIdempotencyId(), tempLock.getLockedAt(), tempLock.getExpiredAt());
                saveFailedResult(tempLock.getIdempotencyId(), tempLock.getId(), ExecutionResult.OPERATION_EXPIRED.getValue());
            }
        }, tempLock.getExpiredAt().toEpochMilli() - Instant.now().toEpochMilli(), TimeUnit.MILLISECONDS);

        return createLockAcquiredResponse(tempLock);
    }

    // Get hashed key as a base64 string
    private String getHashedKey(String service, String operation, String idempotencyKey) {
        return Base64.getEncoder().encodeToString(DigestUtils.md5Digest((service + "-" + operation + "-" + idempotencyKey).getBytes()));
    }

    private IdempotentOperation createIdempotentOperation(IdempotentOperationResult input) {
        IdempotentOperation idempotentOperation = new IdempotentOperation();
        idempotentOperation.setId(UUID.randomUUID().toString());
        idempotentOperation.setService(input.getService());
        idempotentOperation.setOperation(input.getOperation());
        idempotentOperation.setIdempotencyKey(input.getIdempotencyKey());
        idempotentOperation.setCreatedAt(Instant.now());
        try {
            return idempotentOperationRepository.insert(idempotentOperation);
        } catch (Exception e) {
            log.error("Failed to create idempotent operation", e);
            throw new IdempotencyException("Failed to create idempotent operation", e, ExecutionResult.OPERATION_FAILED);
        }
    }

    private IdempotentOperationResult getCachedResult(StoredIdempotentOperationResult storedResult) {
        log.info("Found existing result for operation");
        IdempotentOperationResult output = new IdempotentOperationResult();
        output.setExecutionResult(ExecutionResult.SUCCESS.getValue());
        output.setIdempotentOperationResult(storedResult.getIdempotentOperationResult());
        output.setService(storedResult.getService());
        output.setOperation(storedResult.getOperation());
        output.setIdempotencyKey(storedResult.getIdempotencyKey());
        return output;
    }

    private IdempotentOperationLockTemp acquireLock(IdempotentOperationResult input, IdempotentOperation idempotentOperation) {
        IdempotentOperationLockTemp tempLock = new IdempotentOperationLockTemp();
        tempLock.setId(UUID.randomUUID().toString());
        tempLock.setIdempotencyId(idempotentOperation.getId());
        tempLock.setService(input.getService());
        tempLock.setOperation(input.getOperation());
        tempLock.setIdempotencyKey(input.getIdempotencyKey());
        tempLock.setLockedAt(Instant.now());
        tempLock.setExpiredAt(tempLock.getLockedAt().plus(idempotentOperationConfigCache.getLockDuration(input.getService(), input.getOperation())));
        try {
            idempotentOperationLockTempRepository.insert(tempLock);
            log.info("ATOMIC LOCK ACQUIRED for service: {}, operation: {}, key: {}", input.getService(), input.getOperation(), input.getIdempotencyKey());
            return tempLock;
        } catch (DuplicateKeyException e) {
            // Another thread already has the lock for this (service, operation, idempotencyKey)
            log.warn("LOCK ALREADY EXISTS for service: {}, operation: {}, key: {} - checking for completed result", input.getService(), input.getOperation(), input.getIdempotencyKey());
            // Lock exists but no result yet - operation is still in progress by another thread
            throw new IdempotencyException("Operation is already locked by another process", e, ExecutionResult.OPERATION_ALREADY_LOCKED);
        } catch (Exception e) {
            log.error("Failed to acquire lock for operation", e);
            throw new IdempotencyException("Failed to acquire lock for operation", e, ExecutionResult.OPERATION_FAILED);
        }
    }

    private IdempotentOperationResult createLockAcquiredResponse(IdempotentOperationLockTemp tempLock) {
        log.info("Successfully acquired lock for operation");
        IdempotentOperationResult output = new IdempotentOperationResult();
        output.setIdempotencyId(tempLock.getIdempotencyId());
        output.setService(tempLock.getService());
        output.setOperation(tempLock.getOperation());
        output.setIdempotencyKey(tempLock.getIdempotencyKey());
        output.setLockId(tempLock.getId());
        output.setLockedAt(tempLock.getLockedAt());
        output.setExpiredAt(tempLock.getExpiredAt());
        output.setExecutionResult(ExecutionResult.OPERATION_LOCKED_SUCCESSFULLY.getValue());
        return output;
    }

    @Override
    public void saveIdempotentOperationResult(IdempotentOperationResult input) {
        log.info("Saving idempotent operation result for lockId: {}, result: {}", input.getLockId(), input.getExecutionResult());

        validateInput(input);

        // Check the temp lock is existing or not
        IdempotentOperationLockTemp tempLock = idempotentOperationLockTempRepository.findById(input.getLockId()).orElse(null);
        if (Objects.nonNull(tempLock)) { // Operation is not expired
            deleteTempLock(tempLock.getId());
            insertLockRecord(tempLock.getId(), tempLock.getIdempotencyId(), tempLock.getLockedAt(), tempLock.getExpiredAt());
            saveOperationResult(input);
        }
    }

    private void validateInput(IdempotentOperationResult input) {
        if (Objects.isNull(input.getLockId())) {
            throw new IdempotencyException("Lock Id is required", ExecutionResult.OPERATION_FAILED);
        }
        if (Objects.isNull(input.getIdempotencyId())) {
            throw new IdempotencyException("Idempotency Id is required", ExecutionResult.OPERATION_FAILED);
        }
    }

    private void saveOperationResult(IdempotentOperationResult input) {
        if (ExecutionResult.SUCCESS.getValue().equalsIgnoreCase(input.getExecutionResult())) {
            saveSuccessfulResult(input);
        } else {
            saveFailedResult(input.getIdempotencyId(), input.getLockId(), input.getIdempotentOperationResult());
        }
    }

    private void saveSuccessfulResult(IdempotentOperationResult input) {
        StoredIdempotentOperationResult storedResult = new StoredIdempotentOperationResult();
        storedResult.setId(getHashedKey(input.getService(), input.getOperation(), input.getIdempotencyKey()));
        storedResult.setService(input.getService());
        storedResult.setOperation(input.getOperation());
        storedResult.setIdempotencyKey(input.getIdempotencyKey());
        storedResult.setIdempotentOperationResult(input.getIdempotentOperationResult());
        try {
            storedIdempotentOperationResultRepository.insert(storedResult);
            log.info("Successfully saved operation result atomically");
        } catch (DuplicateKeyException e) {
            log.warn("Result already exists for this operation - concurrent completion detected");
        } catch (Exception e) {
            log.error("Failed to save successful operation result", e);
            throw new IdempotencyException("Failed to save operation result", e, ExecutionResult.OPERATION_FAILED);
        }
    }

    private void saveFailedResult(String idempotencyId, String lockId, String errorMessage) {
        FailedIdempotentOperationResult failedResult = new FailedIdempotentOperationResult();
        failedResult.setId(idempotencyId);
        failedResult.setLockId(lockId);
        failedResult.setErrorMessage(errorMessage);
        try {
            failedIdempotentOperationResultRepository.insert(failedResult);
            log.info("Saved failed operation result with error: {}", errorMessage);
        } catch (Exception e) {
            log.error("Failed to save failed operation result", e);
        }
    }

    private void insertLockRecord(String lockId, String idempotencyId, Instant lockedAt, Instant expiredAt) {
        IdempotentOperationLock lock = new IdempotentOperationLock();
        lock.setId(lockId);
        lock.setIdempotencyId(idempotencyId);
        lock.setLockedAt(lockedAt);
        lock.setExpiredAt(expiredAt);
        lock.setCreatedAt(Instant.now());
        try {
            idempotentOperationLockRepository.insert(lock);
            log.info("Successfully inserted lock");
        } catch (Exception e) {
            log.error("Failed to insert lock", e);
        }
    }

    private void deleteTempLock(String lockID) {
        try {
            idempotentOperationLockTempRepository.deleteById(lockID);
            log.info("Successfully cleaned up temporary lock");
        } catch (Exception e) {
            // Don't throw exception here as the main operation is complete
            log.error("Failed to cleanup temporary lock, but operation completed successfully", e);
        }
    }

    @Scheduled(cron = "${idempotent.scheduling.expired-lock-removal-rate}")
    @SchedulerLock(name = "cleanTempLock", lockAtLeastFor = "PT30S", lockAtMostFor = "PT1M")
    public void cleanLockedOperations() {
        List<IdempotentOperationLockTemp> tempLocks = idempotentOperationLockTempRepository.findByExpiredAtIsAfter(Instant.now());
        for  (IdempotentOperationLockTemp tempLock : tempLocks) {
            deleteTempLock(tempLock.getId());
            saveFailedResult(tempLock.getIdempotencyId(), tempLock.getId(), ExecutionResult.OPERATION_EXPIRED.getValue());
        }
    }

}
