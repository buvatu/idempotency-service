package microservices.helper.idempotency.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
public class IdempotencyResponse {
    private UUID idempotencyID;
    private UUID lockID;
    private String executionResult;
    private String idempotentOperationResult;
    private Instant timestamp;
    private String message;
}
