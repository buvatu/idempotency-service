package microservices.helper.idempotency.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import microservices.helper.idempotency.entity.IdempotentOperation;

@Repository
public interface IdempotentOperationRepository extends MongoRepository<IdempotentOperation, String> {
}
