package microservices.helper.idempotency.exception;

import microservices.helper.idempotency.enums.ExecutionResult;

public class IdempotencyException extends RuntimeException {

    private final ExecutionResult executionResult;

    public IdempotencyException(String message, ExecutionResult executionResult) {
        super(message);
        this.executionResult = executionResult;
    }

    public IdempotencyException(String message, Throwable cause, ExecutionResult executionResult) {
        super(message, cause);
        this.executionResult = executionResult;
    }

    public ExecutionResult getExecutionResult() {
        return executionResult;
    }
}
