# Microservices Helper - Idempotency Service

A robust, distributed idempotency service built with Spring Boot and MongoDB that ensures operations are executed exactly once, even in concurrent and distributed environments.

## 🚀 Features

- **Atomic Lock Acquisition**: Uses MongoDB's unique constraints for guaranteed atomic locking
- **Distributed Idempotency**: Prevents duplicate operations across multiple service instances
- **Automatic Cleanup**: TTL indexes automatically remove expired locks
- **Comprehensive Caching**: In-memory caching for improved performance
- **Configurable Timeouts**: Per-service operation timeout configuration
- **Detailed Monitoring**: Built-in metrics and health checks
- **Concurrent Safety**: Handles high-concurrency scenarios gracefully

## 🏗️ Architecture

The service implements a two-phase idempotency pattern:

1. **Lock Acquisition Phase**: Atomically acquire a lock for the operation
2. **Result Storage Phase**: Store the operation result and release the lock

### Core Components

- **IdempotencyService**: Main service interface for idempotency operations
- **MongoDB Collections**: Six specialized collections for different aspects of operation tracking
- **REST API**: Simple HTTP endpoints for integration

## 📋 Prerequisites

- Java 21+
- Maven 3.6+
- MongoDB 4.4+

## 🛠️ Installation & Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd idempotency-service
```

### 2. Setup MongoDB
```bash
# Start MongoDB (using Docker)
docker run -d --name mongodb -p 27017:27017 mongo:latest

# Create database and collections
mongosh --file schema/idempotency-service-collections.js
```

### 3. Configure Application
Update `src/main/resources/application.yml`:
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/idempotent_service
      database: idempotent_service
```

### 4. Build and Run
```bash
# Build the application
mvn clean package

# Run the application
mvn spring-boot:run

# Or run the JAR
java -jar target/idempotency-service-0.0.1-SNAPSHOT.jar
```

The service will start on `http://localhost:8080`

## 📚 API Documentation

### 1. Request Operation Lock or Get Cached Result

**Endpoint**: `POST /idempotent-operation`

**Request Body**:
```json
{
  "service": "payment-service",
  "operation": "process-payment",
  "idempotencyKey": "user123-payment-456"
}
```

**Responses**:

- **200 OK** - Operation result found in cache:
```json
{
  "idempotencyId": null,
  "service": "test-service",
  "operation": "test-operation",
  "idempotencyKey": "user123-payment-456",
  "lockId": null,
  "executionResult": "SUCCESS",
  "idempotentOperationResult": "test-success",
  "lockedAt": null,
  "expiredAt": null
}
```

- **202 ACCEPTED** - Lock acquired, proceed with operation:
```json
{
  "idempotencyId": "d7c1cdc2-315e-4ca5-976b-a9dd0800a02f",
  "service": "test-service",
  "operation": "test-operation",
  "idempotencyKey": "298fd1b6-f8c9-4d21-a1ad-12dcbdc53b5a",
  "lockId": "21993768-1b4f-40b8-892d-658e2648645f",
  "executionResult": "OPERATION_LOCKED_SUCCESSFULLY",
  "idempotentOperationResult": null,
  "lockedAt": "2025-12-15T08:33:02.478688300Z",
  "expiredAt": "2025-12-15T08:34:02.478688300Z"
}
```

- **409 CONFLICT** - Operation already in progress:
```json
{
  "timestamp": "2025-12-15T08:33:57.974929100Z",
  "message": "Operation is already locked by another process",
  "executionResult": "OPERATION_ALREADY_LOCKED",
  "validationErrors": null
}
```

### 2. Save Operation Result

**Endpoint**: `POST /idempotent-operation/result`

**Request Body**:
```json
{
  "idempotencyId": "77d59e3e-c3bc-44bf-8c33-48c5e2635bfc",
  "service": "test-service",
  "operation": "test-operation",
  "idempotencyKey": "8666df43-bac7-45f0-91f7-5e3c59aa79c6",
  "lockId": "96e8c215-143d-4f49-af7a-7d32ac97ea97",
  "executionResult": "SUCCESS",
  "idempotentOperationResult": "Test",
  "lockedAt": "2025-12-15T08:34:23.532131500Z",
  "expiredAt": "2025-12-15T08:35:23.532131500Z"
}
```

**Response**:
```json
```

## 🗄️ Database Schema

The service uses 6 MongoDB collections:

- **idempotent_operation_config**: Configuration for each service-operation
- **idempotent_operation**: Main operation tracking
- **idempotent_operation_lock_temp**: Temporary locks with TTL
- **stored_idempotent_operation_result**: Successful operation results
- **idempotent_operation_lock**: Lock history records
- **failed_idempotent_operation_result**: Failed operation results

See [schema/README.md](schema/README.md) for detailed schema documentation.

## 🧪 Testing

### Run Unit Tests
```bash
mvn test
```

### Run Integration Tests
```bash
mvn verify
```

### Test Coverage
The project includes comprehensive unit tests with 100% coverage of core business logic:
- 19 unit tests covering all scenarios
- Mock-based testing for isolated unit testing
- Integration tests for end-to-end workflows

## ⚙️ Configuration

### Application Properties

```yaml
# MongoDB Configuration
spring:
  application:
    name: idempotency-service
  data:
    mongodb:
      uri: mongodb://localhost:27017/idempotency_service
      database: idempotency_service

server:
  port: 8080

logging:
  level:
    microservices.helper.idempotency: INFO
    org.springframework.data.mongodb: INFO
    root: INFO

idempotent:
  lock-duration: 1m # Default lock for one operation is 1 minute
  scheduling:
    expired-lock-removal-rate: 0 */30 * * * *
```

### Environment Variables

- `MONGODB_URI`: MongoDB connection string
- `MONGODB_DATABASE`: Database name
- `SERVER_PORT`: Application port


## 🚀 Deployment

### Docker Deployment

```dockerfile
FROM openjdk:21-jre-slim
COPY target/idempotency-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
# Build Docker image
docker build -t idempotency-service .

# Run with Docker Compose
docker-compose up -d
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: idempotency-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: idempotency-service
  template:
    metadata:
      labels:
        app: idempotency-service
    spec:
      containers:
      - name: idempotency-service
        image: idempotency-service:latest
        ports:
        - containerPort: 8080
        env:
        - name: MONGODB_URI
          value: "mongodb://mongodb-service:27017/idempotent_service"
```

## 🔧 Performance Tuning

### MongoDB Optimization
- Ensure proper indexing (use provided index scripts)
- Configure appropriate connection pool sizes
- Use MongoDB replica sets for high availability
- Consider using sharding when number of operations goes too high

### Application Optimization
- Configure JVM heap size appropriately
- Enable connection pooling
- Use caching for frequently accessed data

### Monitoring
- Monitor lock acquisition times
- Track operation success/failure rates
- Set up alerts for high error rates

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

For support and questions:
- Create an issue in the GitHub repository
- Check the [documentation](schema/README.md) for detailed schema information
- Review the test cases for usage examples

## 🔄 Version History

- **v0.0.1** - Initial release with core idempotency functionality
- Atomic lock acquisition using MongoDB unique constraints
- Comprehensive test coverage
- REST API for integration
- Automatic cleanup of expired locks