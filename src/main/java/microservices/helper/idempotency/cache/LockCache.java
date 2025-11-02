package microservices.helper.idempotency.cache;

import lombok.extern.slf4j.Slf4j;
import microservices.helper.idempotency.entity.IdempotentOperationLock;
import microservices.helper.idempotency.model.OperationLockInfo;
import microservices.helper.idempotency.repository.IdempotentOperationLockRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Cache component for managing active locks.
 * Provides thread-safe storage and retrieval of lock information using
 * ConcurrentHashMap.
 * Handles expired lock detection for timeout handling.
 */
@Component
@Slf4j
public class LockCache {

    private final IdempotentOperationLockRepository idempotentOperationLockRepository;

    /**
     * Thread-safe map for storing active locks
     * Key: lockID (UUID v4)
     * Value: LockInfo containing lock details
     */
    private final Map<UUID, OperationLockInfo> activeLocks = new ConcurrentHashMap<>();

    public LockCache(IdempotentOperationLockRepository idempotentOperationLockRepository) {
        this.idempotentOperationLockRepository = idempotentOperationLockRepository;
    }

    /**
     * Store lock information in cache
     * 
     * @param lockInfo the lock information to store
     */
    public void storeLockInCache(OperationLockInfo lockInfo) {
        if (lockInfo == null || lockInfo.getLockID() == null) {
            log.warn("Attempted to store null lock info or lock info with null lockID");
            return;
        }
        activeLocks.put(lockInfo.getLockID(), lockInfo);
    }

    /**
     * Remove lock from cache by lockID
     * 
     * @param lockID the lock ID to remove
     */
    public void removeLockFromCache(UUID lockID) {
        if (lockID == null) {
            log.warn("Attempted to remove lock with null lockID");
            return;
        }
        activeLocks.remove(lockID);
    }

    @Scheduled(fixedRateString = "${idempotent.scheduling.expired-lock-removal-rate:3600000}")
    public void removeExpiredLocks() {
        Instant now = Instant.now();
        List<OperationLockInfo> expiredLocks = activeLocks.values().stream()
                .filter(lockInfo -> lockInfo.getExpiredAt() != null &&
                        lockInfo.getExpiredAt().isBefore(now))
                .collect(Collectors.toList());

        for (OperationLockInfo expiredLock : expiredLocks) {
            insertLock(expiredLock);
            activeLocks.remove(expiredLock.getLockID());
        }
    }

    private void insertLock(OperationLockInfo expiredLock) {
        IdempotentOperationLock idempotentOperationLock = new IdempotentOperationLock();
        idempotentOperationLock.setId(expiredLock.getLockID());
        idempotentOperationLock.setIdempotencyID(expiredLock.getIdempotencyID());
        idempotentOperationLock.setLockedAt(expiredLock.getLockedAt());
        idempotentOperationLock.setExpiredAt(expiredLock.getExpiredAt());
        idempotentOperationLock.setReleasedAt(Instant.now());
        try {
            idempotentOperationLockRepository.insert(idempotentOperationLock);
        } catch (Exception e) {
            log.error("lock is already inserted");
        }

    }
}
