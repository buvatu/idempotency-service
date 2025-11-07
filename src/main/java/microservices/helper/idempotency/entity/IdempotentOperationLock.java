package microservices.helper.idempotency.entity;

import java.time.Instant;

import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Document(collection = "idempotent_operation_lock")
@Data
public class IdempotentOperationLock {

	@Id
	private String id;// In UUIDv4 format

	@Indexed(unique = true)
	private String idempotencyID;// In UUIDv4 format

	private Instant lockedAt;

	private Instant expiredAt;

	private Instant createdAt;

}
