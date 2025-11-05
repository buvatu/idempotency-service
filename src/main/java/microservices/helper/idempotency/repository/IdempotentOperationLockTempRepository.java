package microservices.helper.idempotency.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import microservices.helper.idempotency.entity.IdempotentOperationLockTemp;

@Repository
public interface IdempotentOperationLockTempRepository extends MongoRepository<IdempotentOperationLockTemp, String> {

}
