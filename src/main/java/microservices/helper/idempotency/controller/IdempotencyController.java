package microservices.helper.idempotency.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import microservices.helper.idempotency.dto.IdempotencyResponse;
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
	public ResponseEntity<IdempotencyResponse> getStoredExecutionResultOrLockOperation(
			@Valid @RequestBody IdempotentOperationResult idempotentOperation) {
		
		log.info("Received request for idempotent operation: service={}, operation={}", 
				idempotentOperation.getService(), idempotentOperation.getOperation());
		
		IdempotentOperationResult result = idempotencyService.getStoredExecutionResultOrLockOperation(idempotentOperation);
		
		IdempotencyResponse response = mapToResponse(result);
		
		if (ExecutionResult.SUCCESS.getValue().equals(result.getExecutionResult())) {
			return ResponseEntity.ok(response);
		} else if (ExecutionResult.OPERATION_LOCKED_SUCCESSFULLY.getValue().equals(result.getExecutionResult())) {
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
		} else {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
		}
	}

	@PostMapping("/idempotent-operation/result")
	public ResponseEntity<IdempotencyResponse> saveIdempotentOperationResult(
			@Valid @RequestBody IdempotentOperationResult idempotentOperation) {
		
		log.info("Received request to save operation result for lockID: {}", 
				idempotentOperation.getLockID());
		
		idempotencyService.saveIdempotentOperationResult(idempotentOperation);
		
		IdempotencyResponse response = IdempotencyResponse.builder()
				.timestamp(Instant.now())
				.message("Operation result saved successfully")
				.executionResult("SAVED")
				.build();
		
		return ResponseEntity.ok(response);
	}

	private IdempotencyResponse mapToResponse(IdempotentOperationResult result) {
		return IdempotencyResponse.builder()
				.idempotencyID(result.getIdempotencyID())
				.lockID(result.getLockID())
				.executionResult(result.getExecutionResult())
				.idempotentOperationResult(result.getIdempotentOperationResult())
				.timestamp(Instant.now())
				.message(getMessageForResult(result.getExecutionResult()))
				.build();
	}

	private String getMessageForResult(String executionResult) {
		if (ExecutionResult.SUCCESS.getValue().equals(executionResult)) {
			return "Operation result retrieved from cache";
		} else if (ExecutionResult.OPERATION_LOCKED_SUCCESSFULLY.getValue().equals(executionResult)) {
			return "Operation locked successfully, proceed with execution";
		} else {
			return "Operation processing completed";
		}
	}
}
