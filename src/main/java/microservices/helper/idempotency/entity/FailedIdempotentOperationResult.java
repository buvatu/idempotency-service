package microservices.helper.idempotency.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

/**
 * Entity representing a failed operation result. Maps to the
 * failed_idempotent_operation_result collection.
 */
@Document(collection = "failed_idempotent_operation_result")
@Data
public class FailedIdempotentOperationResult {

	@Id
	private String id; // In UUIDv4 format

    private String lockID;

	private String errorMessage;

}
