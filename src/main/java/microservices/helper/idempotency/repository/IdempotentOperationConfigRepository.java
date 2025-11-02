package microservices.helper.idempotency.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import microservices.helper.idempotency.entity.IdempotentOperationConfig;

@Repository
public interface IdempotentOperationConfigRepository extends MongoRepository<IdempotentOperationConfig, UUID> {

	Optional<IdempotentOperationConfig> findByServiceAndOperation(String service, String operation);

}
