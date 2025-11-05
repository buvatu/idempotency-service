package microservices.helper.idempotency.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import microservices.helper.idempotency.entity.FailedIdempotentOperationResult;

@Repository
public interface FailedIdempotentOperationResultRepository extends MongoRepository<FailedIdempotentOperationResult, String> {
}
