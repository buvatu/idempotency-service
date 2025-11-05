package microservices.helper.idempotency.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "idempotent_operation_lock_backup")
@Data
public class IdempotentOperationLockBackup {

	@Id
	private String id;// In UUIDv4 format

	@Indexed(unique = true)
	private String idempotencyID;// In UUIDv4 format

	private Instant lockedAt;

	private Instant expiredAt;

}
