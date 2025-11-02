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
- **Cache Layer**: In-memory caching for performance optimization
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
mongosh --file schema/mongodb-collections.js
mongosh --file schema/mongodb-indexes.js
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
  "idempotencyKey": "user123-payment-456",
  "idempotentOperationResult": "optional-payload"
}
```

**Responses**:

- **200 OK** - Operation result found in cache:
```json
{
  "idempotencyID": "uuid",
  "executionResult": "SUCCESS",
  "idempotentOperationResult": "cached-result",
  "timestamp": "2024-01-01T12:00:00Z",
  "message": "Operation result retrieved from cache"
}
```

- **202 ACCEPTED** - Lock acquired, proceed with operation:
```json
{
  "idempotencyID": "uuid",
  "lockID": "uuid",
  "executionResult": "OPERATION_LOCKED_SUCCESSFULLY",
  "timestamp": "2024-01-01T12:00:00Z",
  "message": "Operation locked successfully, proceed with execution"
}
```

- **409 CONFLICT** - Operation already in progress:
```json
{
  "executionResult": "OPERATION_ALREADY_LOCKED",
  "timestamp": "2024-01-01T12:00:00Z",
  "message": "Operation processing completed"
}
```

### 2. Save Operation Result

**Endpoint**: `POST /idempotent-operation/result`

**Request Body**:
```json
{
  "lockID": "uuid-from-lock-response",
  "idempotencyID": "uuid-from-lock-response",
  "service": "payment-service",
  "operation": "process-payment",
  "idempotencyKey": "user123-payment-456",
  "executionResult": "SUCCESS",
  "idempotentOperationResult": "operation-result-data"
}
```

**Response**:
```json
{
  "timestamp": "2024-01-01T12:00:00Z",
  "message": "Operation result saved successfully",
  "executionResult": "SAVED"
}
```

## 🔄 Usage Flow

### Typical Integration Pattern

```java
// 1. Request lock or get cached result
IdempotentOperationResult request = new IdempotentOperationResult();
request.setService("payment-service");
request.setOperation("process-payment");
request.setIdempotencyKey("user123-payment-456");

ResponseEntity<IdempotencyResponse> response = 
    restTemplate.postForEntity("/idempotent-operation", request, IdempotencyResponse.class);

if (response.getStatusCode() == HttpStatus.OK) {
    // Result already exists, use cached result
    return response.getBody().getIdempotentOperationResult();
    
} else if (response.getStatusCode() == HttpStatus.ACCEPTED) {
    // Lock acquired, execute operation
    String result = executeBusinessLogic();
    
    // Save result
    IdempotentOperationResult saveRequest = new IdempotentOperationResult();
    saveRequest.setLockID(response.getBody().getLockID());
    saveRequest.setIdempotencyID(response.getBody().getIdempotencyID());
    saveRequest.setService("payment-service");
    saveRequest.setOperation("process-payment");
    saveRequest.setIdempotencyKey("user123-payment-456");
    saveRequest.setExecutionResult("SUCCESS");
    saveRequest.setIdempotentOperationResult(result);
    
    restTemplate.postForEntity("/idempotent-operation/result", saveRequest, IdempotencyResponse.class);
    return result;
    
} else {
    // Operation already in progress by another thread/service
    throw new ConcurrentOperationException("Operation already in progress");
}
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
- 18 unit tests covering all scenarios
- Mock-based testing for isolated unit testing
- Integration tests for end-to-end workflows

## 📊 Monitoring & Health Checks

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

### Metrics
```bash
curl http://localhost:8080/actuator/metrics
```

### Application Info
```bash
curl http://localhost:8080/actuator/info
```

## ⚙️ Configuration

### Application Properties

```yaml
# MongoDB Configuration
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/idempotent_service
      database: idempotent_service

# Server Configuration
server:
  port: 8080

# Logging Configuration
logging:
  level:
    microservices.helper.idempotency: INFO

# Idempotency Configuration
idempotent:
  scheduling:
    expired-lock-removal-rate: 3600000  # 1 hour in milliseconds
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