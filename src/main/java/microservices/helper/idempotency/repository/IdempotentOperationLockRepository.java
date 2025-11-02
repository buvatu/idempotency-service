package microservices.helper.idempotency.repository;

import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import microservices.helper.idempotency.entity.IdempotentOperationLock;

@Repository
public interface IdempotentOperationLockRepository extends MongoRepository<IdempotentOperationLock, UUID> {
}
