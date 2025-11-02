package microservices.helper.idempotency.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import microservices.helper.idempotency.entity.StoredIdempotentOperationResult;

@Repository
public interface StoredIdempotentOperationResultRepository extends MongoRepository<StoredIdempotentOperationResult, UUID>{

	Optional<StoredIdempotentOperationResult> findByServiceAndOperationAndIdempotencyKey(String service, String operation, String idempotencyKey);

}
