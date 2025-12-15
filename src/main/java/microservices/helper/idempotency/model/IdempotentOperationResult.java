package microservices.helper.idempotency.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class IdempotentOperationResult {

    private String idempotencyId;// In UUIDv4 format

    @NotBlank(message = "Service name is required")
    private String service;

    @NotBlank(message = "Operation name is required")
    private String operation;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    private String lockId;// In UUIDv4 format
    private String executionResult;
    private String idempotentOperationResult;
    private Instant lockedAt;
    private Instant expiredAt;
}
