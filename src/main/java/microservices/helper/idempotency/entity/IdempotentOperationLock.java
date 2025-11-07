package microservices.helper.idempotency.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Document(collection = "idempotent_operation_lock")
@Data
public class IdempotentOperationLock {

	@Id
	private String id;// In UUIDv4 format

	private String idempotencyID;// In UUIDv4 format

	private Instant lockedAt;

	private Instant expiredAt;

	private Instant createdAt;

}
