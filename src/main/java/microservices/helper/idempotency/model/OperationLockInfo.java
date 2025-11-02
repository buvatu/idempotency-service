package microservices.helper.idempotency.model;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

/**
 * Model class for storing lock information in cache.
 * Used for tracking active locks and timeout handling.
 */
@Data
public class OperationLockInfo {

    /**
     * UUID v4 for the lock id
     */
    private UUID lockID;

    /**
     * UUID v4 for the idempotency operation
     */
    private UUID idempotencyID;
    
    /**
     * Service name that owns this lock
     */
    private String service;
    
    /**
     * Operation name for this lock
     */
    private String operation;
    
    /**
     * Idempotency key for this specific operation attempt
     */
    private String idempotencyKey;
    
    /**
     * Timestamp when the lock was acquired
     */
    private Instant lockedAt;
    
    /**
     * Timestamp when the lock expires (lockedAt + lockDuration)
     */
    private Instant expiredAt;
    
    /**
     * Timestamp when the operation should be completed (lockedAt + executionDurationLimit)
     * Used by timeout scheduler to detect expired operations
     */
    private Instant shouldCompletedAt;
}
