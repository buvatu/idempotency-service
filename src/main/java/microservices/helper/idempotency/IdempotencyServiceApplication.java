package microservices.helper.idempotency;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IdempotencyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdempotencyServiceApplication.class, args);
    }

}

