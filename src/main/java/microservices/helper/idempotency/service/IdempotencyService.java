package microservices.helper.idempotency.service;

import microservices.helper.idempotency.model.IdempotentOperationResult;

public interface IdempotencyService {

    IdempotentOperationResult getStoredExecutionResultOrLockOperation(IdempotentOperationResult input);

    void saveIdempotentOperationResult(IdempotentOperationResult input);

}
