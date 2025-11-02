package microservices.helper.idempotency.exception;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
public class ErrorResponse {
    private Instant timestamp;
    private String message;
    private String executionResult;
    private Map<String, String> validationErrors;
}
