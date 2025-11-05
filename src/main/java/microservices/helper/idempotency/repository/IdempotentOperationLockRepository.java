package microservices.helper.idempotency.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import microservices.helper.idempotency.entity.IdempotentOperationLock;

@Repository
public interface IdempotentOperationLockRepository extends MongoRepository<IdempotentOperationLock, String> {
}
