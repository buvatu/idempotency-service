package microservices.helper.idempotency.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.Duration;

/**
 * Entity representing operation configuration. Maps to the
 * idempotent_operation_config collection.
 */
@Document(collection = "idempotent_operation_config")
@CompoundIndex(def = "{'service': 1, 'operation': 1}", unique = true)
@Data
public class IdempotentOperationConfig {

    @Id
    private String id;// In UUIDv4 format

    private String service;

    private String operation;

    private Duration lockDuration;

}
