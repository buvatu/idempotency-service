package microservices.helper.idempotency.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import microservices.helper.idempotency.enums.ExecutionResult;
import microservices.helper.idempotency.model.IdempotentOperationResult;
import microservices.helper.idempotency.service.IdempotencyService;

import java.time.Instant;

@RestController
@Slf4j
public class IdempotencyController {

	private final IdempotencyService idempotencyService;

	public IdempotencyController(IdempotencyService idempotencyService) {
		this.idempotencyService = idempotencyService;
	}

	@PostMapping("/idempotent-operation")
	public ResponseEntity<IdempotentOperationResult> getStoredExecutionResultOrLockOperation(
			@Valid @RequestBody IdempotentOperationResult idempotentOperation) {
		
		log.info("Received request for idempotent operation: service={}, operation={}", 
				idempotentOperation.getService(), idempotentOperation.getOperation());
		
		IdempotentOperationResult result = idempotencyService.getStoredExecutionResultOrLockOperation(idempotentOperation);

		if (ExecutionResult.SUCCESS.getValue().equals(result.getExecutionResult())) {
			return ResponseEntity.ok(result);
		} else if (ExecutionResult.OPERATION_LOCKED_SUCCESSFULLY.getValue().equals(result.getExecutionResult())) {
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
		} else {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
		}
	}

	@PostMapping("/idempotent-operation/result")
	public ResponseEntity<?> saveIdempotentOperationResult(
			@Valid @RequestBody IdempotentOperationResult idempotentOperation) {
		
		log.info("Received request to save operation result for lockID: {}", 
				idempotentOperation.getLockID());
		
		idempotencyService.saveIdempotentOperationResult(idempotentOperation);

		return ResponseEntity.ok().build();
	}

}
