package microservices.helper.idempotency.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Document(collection = "stored_idempotent_operation_result")
@CompoundIndex(def = "{'service': 1, 'operation': 1, 'idempotencyKey': 1}", unique = true)
@Data
public class StoredIdempotentOperationResult {

    @Id
    private String id;// In UUIDv4 format

    private String service;

    private String operation;

    private String idempotencyKey;

    private String idempotentOperationResult;

}
