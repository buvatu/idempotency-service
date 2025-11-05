package microservices.helper.idempotency.service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import microservices.helper.idempotency.entity.*;
import microservices.helper.idempotency.repository.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import microservices.helper.idempotency.enums.ExecutionResult;
import microservices.helper.idempotency.exception.IdempotencyException;
import microservices.helper.idempotency.model.IdempotentOperationResult;
import microservices.helper.idempotency.cache.IdempotentOperationConfigCache;

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
    private final IdempotentOperationLockBackupRepository idempotentOperationLockBackupRepository;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
	public IdempotentOperationResult getStoredExecutionResultOrLockOperation(IdempotentOperationResult input) {
		log.info("Processing idempotent operation for service: {}, operation: {}, key: {}",
				input.getService(), input.getOperation(), input.getIdempotencyKey());

		// STEP 1: ALWAYS create IdempotentOperation record for ALL incoming requests
		// This is your business requirement - track every request
		IdempotentOperation idempotentOperation = createIdempotentOperation(input);

		// STEP 2: Check if a result already exists (fast path)
		Optional<StoredIdempotentOperationResult> existingResult = checkExistingResult(input);
		if (existingResult.isPresent()) {
			log.info("Found existing result, returning cached response");
			return createSuccessResponse(existingResult.get());
		}

		// STEP 3: ATOMIC LOCK ACQUISITION - Only ONE thread can succeed for the same
		// (service, operation, idempotencyKey)
		// This uses MongoDB's unique constraint to ensure atomicity
		return atomicLockAcquisition(input, idempotentOperation);
	}

	private Optional<StoredIdempotentOperationResult> checkExistingResult(IdempotentOperationResult input) {
		return storedIdempotentOperationResultRepository
				.findByServiceAndOperationAndIdempotencyKey(
						input.getService(),
						input.getOperation(),
						input.getIdempotencyKey());
	}

	private IdempotentOperationResult createSuccessResponse(StoredIdempotentOperationResult storedResult) {
		log.info("Found existing result for operation");
		IdempotentOperationResult output = new IdempotentOperationResult();
		output.setExecutionResult(ExecutionResult.SUCCESS.getValue());
		output.setIdempotentOperationResult(storedResult.getIdempotentOperationResult());
		return output;
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
			throw new IdempotencyException("Failed to create idempotent operation", e,
					ExecutionResult.OPERATION_FAILED);
		}
	}

	/**
	 * ATOMIC LOCK ACQUISITION - Guarantees only ONE lock per (service, operation,
	 * idempotencyKey)
	 * Uses MongoDB's unique constraint on a temp lock collection for atomicity
	 */
	private IdempotentOperationResult atomicLockAcquisition(IdempotentOperationResult input, IdempotentOperation idempotentOperation) {
		UUID lockId = UUID.randomUUID();
		Instant now = Instant.now();
		Instant expiredAt = now.plus(idempotentOperationConfigCache.getLockDuration(input.getService(), input.getOperation()));

		try {
			// ATOMIC OPERATION: Try to acquire lock using MongoDB's unique constraint
			// MongoDB's unique constraint on (service, operation, idempotencyKey) ensures
			// only ONE succeeds
			IdempotentOperationLockTemp tempLock = new IdempotentOperationLockTemp();
			tempLock.setId(lockId.toString());
			tempLock.setService(input.getService());
			tempLock.setOperation(input.getOperation());
			tempLock.setIdempotencyKey(input.getIdempotencyKey());
			tempLock.setLockedAt(now);
			tempLock.setExpiredAt(expiredAt);

			// ATOMIC ACTION 1: Insert temporary lock (this will FAIL with DuplicateKeyException if another thread already has the lock)
			idempotentOperationLockTempRepository.insert(tempLock);
            scheduler.schedule(() -> deleteTempLock(lockId.toString()), tempLock.getExpiredAt().toEpochMilli() - Instant.now().toEpochMilli() , TimeUnit.MILLISECONDS);
			log.info("ATOMIC LOCK ACQUIRED for service: {}, operation: {}, key: {}", input.getService(), input.getOperation(), input.getIdempotencyKey());

			// ATOMIC ACTION 2: Store backup lock (non-critical, don't fail if this fails)
            insertLockBackup(lockId.toString(), idempotentOperation.getId(), tempLock.getLockedAt(), tempLock.getExpiredAt());

			// Return success response
			return createLockAcquiredResponse(idempotentOperation, tempLock);

		} catch (DuplicateKeyException e) {
			// Another thread already has the lock for this (service, operation,
			// idempotencyKey)
			log.warn("🔒 LOCK ALREADY EXISTS for service: {}, operation: {}, key: {} - checking for completed result",
					input.getService(), input.getOperation(), input.getIdempotencyKey());

			// Check if the other thread has completed and saved a result
			Optional<StoredIdempotentOperationResult> completedResult = checkExistingResult(input);
			if (completedResult.isPresent()) {
				log.info("Found completed result from other thread");
				return createSuccessResponse(completedResult.get());
			}

			// Lock exists but no result yet - operation is still in progress by another
			// thread
			throw new IdempotencyException("Operation is already locked by another process", e,
					ExecutionResult.OPERATION_ALREADY_LOCKED);

		} catch (Exception e) {
			log.error("Failed to acquire lock for operation", e);
			throw new IdempotencyException("Failed to acquire lock for operation", e, ExecutionResult.OPERATION_FAILED);
		}
	}

	private IdempotentOperationResult createLockAcquiredResponse(IdempotentOperation idempotentOperation, IdempotentOperationLockTemp tempLock) {
		log.info("Successfully acquired lock for operation");
		IdempotentOperationResult output = new IdempotentOperationResult();
		output.setIdempotencyID(idempotentOperation.getId());
		output.setLockID(tempLock.getId());
		output.setLockedAt(tempLock.getLockedAt());
		output.setExpiredAt(tempLock.getExpiredAt());
		output.setExecutionResult(ExecutionResult.OPERATION_LOCKED_SUCCESSFULLY.getValue());
		return output;
	}

	@Override
	@Transactional
	public void saveIdempotentOperationResult(IdempotentOperationResult input) {
		log.info("Saving idempotent operation result for lockID: {}, result: {}",
				input.getLockID(), input.getExecutionResult());

		validateInput(input);

		// ATOMIC ACTION 1: Save the operation result (success or failure)
		if (isSuccessfulOperation(input)) {
			saveSuccessfulResult(input);
		} else {
			saveFailedResult(input);
		}

		// ATOMIC ACTION 2: Release lock
		releaseLock(input);
		
		// ATOMIC ACTION 3: Delete temporary lock
        deleteTempLock(input.getLockID());
	}

	private void validateInput(IdempotentOperationResult input) {
		if (Objects.isNull(input.getLockID())) {
			throw new IdempotencyException("Lock ID is required", ExecutionResult.OPERATION_FAILED);
		}
		if (Objects.isNull(input.getIdempotencyID())) {
			throw new IdempotencyException("Idempotency ID is required", ExecutionResult.OPERATION_FAILED);
		}
	}

	private boolean isSuccessfulOperation(IdempotentOperationResult input) {
		boolean isSuccess = ExecutionResult.SUCCESS.getValue().equalsIgnoreCase(input.getExecutionResult());
		boolean isNotExpired = input.getExpiredAt() != null && input.getExpiredAt().isAfter(Instant.now());
		boolean allowSaveOnExpired = idempotentOperationConfigCache.isAllowSaveOnExpired(input.getService(), input.getOperation());
		return isSuccess && (isNotExpired || allowSaveOnExpired);
	}

	private void saveSuccessfulResult(IdempotentOperationResult input) {
		// ATOMIC ACTION 1: Insert successful result
		insertSuccessfulResult(input);
		
		// ATOMIC ACTION 2: Delete the failed result (if exists)
		deleteFailedResultById(input.getIdempotencyID());
	}
	
	private void insertSuccessfulResult(IdempotentOperationResult input) {
		try {
			StoredIdempotentOperationResult storedResult = new StoredIdempotentOperationResult();
			storedResult.setId(UUID.randomUUID().toString());
			storedResult.setService(input.getService());
			storedResult.setOperation(input.getOperation());
			storedResult.setIdempotencyKey(input.getIdempotencyKey());
			storedResult.setIdempotentOperationResult(input.getIdempotentOperationResult());

			// Use insert which will fail if a duplicate exists (due to unique constraint)
			storedIdempotentOperationResultRepository.insert(storedResult);
			log.info("✅ Successfully saved operation result atomically");
		} catch (DuplicateKeyException e) {
			// Another thread already saved the result - this is actually OK in concurrent
			// scenarios
			log.warn("Result already exists for this operation - concurrent completion detected");
			// Don't throw exception, this is a valid concurrent scenario
		} catch (Exception e) {
			log.error("❌ Failed to save successful operation result", e);
			throw new IdempotencyException("Failed to save operation result", e, ExecutionResult.OPERATION_FAILED);
		}
	}
	
	private void deleteFailedResultById(String idempotencyID) {
		try {
			failedIdempotentOperationResultRepository.deleteById(idempotencyID);
			log.debug("Deleted failed result for idempotencyID: {}", idempotencyID);
		} catch (Exception e) {
			log.warn("Failed to delete failed result for idempotencyID: {} - {}", idempotencyID, e.getMessage());
			// Don't throw exception as this is a cleanup operation
		}
	}

	private void saveFailedResult(IdempotentOperationResult input) {
		try {
			FailedIdempotentOperationResult failedResult = new FailedIdempotentOperationResult();
			failedResult.setId(input.getIdempotencyID());
			failedResult.setLockID(input.getLockID());

			String errorMessage = Objects.isNull(input.getExecutionResult()) || ExecutionResult.SUCCESS.getValue().equalsIgnoreCase(input.getExecutionResult())
					? "Operation expired"
					: input.getIdempotentOperationResult();
			failedResult.setErrorMessage(errorMessage);

			failedIdempotentOperationResultRepository.insert(failedResult);
			log.info("Saved failed operation result with error: {}", errorMessage);
		} catch (Exception e) {
			log.error("Failed to save failed operation result", e);
			throw new IdempotencyException("Failed to save failed operation result", e, ExecutionResult.OPERATION_FAILED);
		}
	}

	private void releaseLock(IdempotentOperationResult input) {
		// ATOMIC ACTION 1: Insert lock record
		insertLockRecord(input);
		
		// ATOMIC ACTION 2: Delete backup lock
		deleteLockBackupById(input.getLockID());
	}
	
	private void insertLockRecord(IdempotentOperationResult input) {
		try {
			IdempotentOperationLock lock = new IdempotentOperationLock();
			lock.setId(input.getLockID());
			lock.setIdempotencyID(input.getIdempotencyID());
			lock.setLockedAt(input.getLockedAt());
			lock.setExpiredAt(input.getExpiredAt());
			lock.setCreatedAt(Instant.now());
			idempotentOperationLockRepository.insert(lock);
			log.info("Successfully inserted lock");
		} catch (Exception e) {
			log.error("Failed to inserted lock", e);
		}
	}
	
	private void deleteLockBackupById(String lockID) {
		try {
			idempotentOperationLockBackupRepository.deleteById(lockID);
			log.debug("Deleted lock backup for lockID: {}", lockID);
		} catch (Exception e) {
			log.warn("Failed to delete lock backup for lockID: {} - {}", lockID, e.getMessage());
			// Don't throw exception as this is a cleanup operation
		}
	}
	
	private void insertLockBackup(String lockId, String idempotencyID, Instant lockedAt, Instant expiredAt) {
		try {
			IdempotentOperationLockBackup idempotentOperationLockBackup = new IdempotentOperationLockBackup();
			idempotentOperationLockBackup.setId(lockId);
			idempotentOperationLockBackup.setIdempotencyID(idempotencyID);
			idempotentOperationLockBackup.setLockedAt(lockedAt);
			idempotentOperationLockBackup.setExpiredAt(expiredAt);
			idempotentOperationLockBackupRepository.insert(idempotentOperationLockBackup);
			log.debug("Inserted lock backup for lockID: {}", lockId);
		} catch (Exception e) {
			log.warn("Failed to insert lock backup for lockID: {} - {}", lockId, e.getMessage());
			// Don't throw exception as this is a non-critical backup operation
		}
	}

	private void deleteTempLock(String lockID) {
		try {
			idempotentOperationLockTempRepository.deleteById(lockID);
			log.info("Successfully cleaned up temporary lock");
		} catch (Exception e) {
			log.error("Failed to cleanup temporary lock, but operation completed successfully", e);
			// Don't throw exception here as the main operation is complete
		}
	}

    @Scheduled(fixedRateString = "${idempotent.scheduling.expired-lock-removal-rate:3600000}")
    public void removeExpiredLocks() {
        try (Stream<IdempotentOperationLockBackup> expiredLockStream = idempotentOperationLockBackupRepository.findByExpiredAtBefore(Instant.now())) {
            expiredLockStream.forEach(expiredLock -> {
                IdempotentOperationResult input = new IdempotentOperationResult();
                input.setLockID(expiredLock.getId());
                input.setIdempotencyID(expiredLock.getIdempotencyID());
                input.setLockedAt(expiredLock.getLockedAt());
                input.setExpiredAt(expiredLock.getExpiredAt());
                
                // ATOMIC ACTION 1: Release lock
                releaseLock(input);
                
                // ATOMIC ACTION 2: Save the failed result
                saveFailedResult(input);
            });
        } catch (Exception e) {
            // Handle the unchecked exception here
            System.err.println("An error occurred during stream processing: " + e.getMessage());
        }
    }

}
