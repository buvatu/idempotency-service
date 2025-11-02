package microservices.helper.idempotency.enums;

public enum ExecutionResult {
    SUCCESS("SUCCESS"),
    OPERATION_LOCKED_SUCCESSFULLY("OPERATION_LOCKED_SUCCESSFULLY"),
    OPERATION_ALREADY_LOCKED("OPERATION_ALREADY_LOCKED"),
    OPERATION_EXPIRED("OPERATION_EXPIRED"),
    OPERATION_FAILED("OPERATION_FAILED");

    private final String value;

    ExecutionResult(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
