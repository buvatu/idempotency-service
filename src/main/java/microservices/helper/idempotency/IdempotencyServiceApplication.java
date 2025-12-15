package microservices.helper.idempotency;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableSchedulerLock(defaultLockAtLeastFor = "PT20S", defaultLockAtMostFor = "PT25S")
public class IdempotencyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdempotencyServiceApplication.class, args);
    }

}

