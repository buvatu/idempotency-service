package microservices.helper.idempotency.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an idempotent operation record. Maps to the
 * idempotent_operation collection.
 */
@Document(collection = "idempotent_operation")
@Data
public class IdempotentOperation {

	@Id
	private UUID id;

	private String service;

	private String operation;

	private String idempotencyKey;

	private Instant createdAt;

}
