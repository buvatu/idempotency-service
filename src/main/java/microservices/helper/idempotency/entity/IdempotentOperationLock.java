package microservices.helper.idempotency.entity;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Document(collection = "idempotent_operation_lock")
@Data
public class IdempotentOperationLock {

	@Id
	private UUID id;

	@Indexed(unique = true)
	private UUID idempotencyID;

	private Instant lockedAt;

	private Instant expiredAt;

	private Instant releasedAt;

}
