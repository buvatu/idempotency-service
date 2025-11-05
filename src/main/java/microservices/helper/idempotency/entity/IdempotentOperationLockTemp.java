package microservices.helper.idempotency.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a temporary lock for idempotent operations. Maps to the
 * idempotent_operation_lock_temp collection. Uses TTL index for automatic
 * cleanup.
 */
@Document(collection = "idempotent_operation_lock_temp")
@CompoundIndex(def = "{'service': 1, 'operation': 1, 'idempotencyKey': 1}", unique = true)
@Data
public class IdempotentOperationLockTemp {

	@Id
	private String id;// In UUIDv4 format

	private String service;

	private String operation;

	private String idempotencyKey;

	private Instant lockedAt;

	@Indexed(expireAfter = "0")
	private Instant expiredAt;

}
