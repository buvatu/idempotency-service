package microservices.helper.idempotency.service;

import microservices.helper.idempotency.model.IdempotentOperationResult;

public interface IdempotencyService {

	public IdempotentOperationResult getStoredExecutionResultOrLockOperation(IdempotentOperationResult input);

	public void saveIdempotentOperationResult(IdempotentOperationResult input);

}
