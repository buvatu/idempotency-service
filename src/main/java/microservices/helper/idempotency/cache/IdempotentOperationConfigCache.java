package microservices.helper.idempotency.cache;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;


import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import microservices.helper.idempotency.entity.IdempotentOperationConfig;
import microservices.helper.idempotency.repository.IdempotentOperationConfigRepository;

@Component
@Slf4j
public class IdempotentOperationConfigCache {

	private final List<IdempotentOperationConfig> operationConfigList = new CopyOnWriteArrayList<>();
	private final IdempotentOperationConfigRepository idempotentOperationConfigRepository;

	public IdempotentOperationConfigCache(IdempotentOperationConfigRepository idempotentOperationConfigRepository) {
		this.idempotentOperationConfigRepository = idempotentOperationConfigRepository;
	}

	@PostConstruct
	private void loadCache() {
		operationConfigList.addAll(idempotentOperationConfigRepository.findAll());
	}

	public List<IdempotentOperationConfig> getOperationConfigList() {
		return operationConfigList;
	}

	public boolean isAllowSaveOnExpired(String service, String operation) {
		IdempotentOperationConfig operationConfig = findInCurrentList(service, operation);
		if (operationConfig != null) {
			return operationConfig.isAllowSaveOnExpired();
		}
		operationConfig = findInDB(service, operation);
		if (operationConfig != null) {
			return operationConfig.isAllowSaveOnExpired();
		}
		saveNewOperationConfig(service, operation);
		return true; // Default value
	}

	public Duration getLockDuration(String service, String operation) {
		IdempotentOperationConfig operationConfig = findInCurrentList(service, operation);
		if (operationConfig != null) {
			return operationConfig.getLockDuration();
		}
		operationConfig = findInDB(service, operation);
		if (operationConfig != null) {
			return operationConfig.getLockDuration();
		}
		saveNewOperationConfig(service, operation);
		return Duration.ofMillis(5000); // Default value
	}
	
	private IdempotentOperationConfig findInCurrentList(String service, String operation) {
		for (IdempotentOperationConfig operationConfig : operationConfigList) {
			if (operationConfig.getService().equals(service) && operationConfig.getOperation().equals(operation)) {
				return operationConfig;
			}
		}
		return null;
	}

	private IdempotentOperationConfig findInDB(String service, String operation) {
		Optional<IdempotentOperationConfig> operationConfigOpt = idempotentOperationConfigRepository.findByServiceAndOperation(service, operation);
		if (operationConfigOpt.isPresent()) {
			return operationConfigOpt.get();
		}
		return null;
	}

	private void saveNewOperationConfig(String service, String operation) {
		try {
			IdempotentOperationConfig newOperationConfig = new IdempotentOperationConfig();
			newOperationConfig.setId(UUID.randomUUID());
			newOperationConfig.setService(service);
			newOperationConfig.setOperation(operation);
			newOperationConfig.setLockDuration(Duration.ofMillis(5000));
			newOperationConfig.setAllowSaveOnExpired(true);
			idempotentOperationConfigRepository.save(newOperationConfig);
			operationConfigList.add(newOperationConfig);
		} catch (Exception e) {
			log.error("failed to save idempotent operation config");
		}
	}

}
