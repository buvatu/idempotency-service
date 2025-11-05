package microservices.helper.idempotency.repository;

import microservices.helper.idempotency.entity.IdempotentOperationLockBackup;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.stream.Stream;

@Repository
public interface IdempotentOperationLockBackupRepository extends MongoRepository<IdempotentOperationLockBackup, String> {
    Stream<IdempotentOperationLockBackup> findByExpiredAtBefore(Instant now);
}
