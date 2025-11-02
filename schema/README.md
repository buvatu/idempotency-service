# MongoDB Schema for Idempotent Operation Service

This folder contains the database schema definition and index creation scripts for the Idempotent Operation Service.

## Files

### mongodb-collections.js
Creates all six required collections with validation schemas:
- `idempotent_operation_config` - Configuration for operations
- `idempotent_operation` - Main operation tracking
- `idempotent_operation_lock_temp` - Temporary locks with TTL
- `stored_idempotent_operation_result` - Successful operation results
- `idempotent_operation_lock` - Lock history records
- `failed_idempotent_operation_result` - Failed operation results

### mongodb-indexes.js
Creates all required indexes for performance optimization:
- Unique constraints for preventing duplicates
- TTL indexes for automatic cleanup
- Compound indexes for efficient queries
- Single field indexes for fast lookups

## Usage

### Using MongoDB Shell
```bash
# Create collections
mongosh --file src/main/resources/schema/mongodb-collections.js

# Create indexes
mongosh --file src/main/resources/schema/mongodb-indexes.js
```

### Using MongoDB Compass
1. Open MongoDB Compass
2. Connect to your MongoDB instance
3. Create database `idempotent_service`
4. Copy and paste the contents of each script into the shell

### Using Docker
```bash
# If using MongoDB in Docker
docker exec -i mongodb-container mongosh < src/main/resources/schema/mongodb-collections.js
docker exec -i mongodb-container mongosh < src/main/resources/schema/mongodb-indexes.js
```

## Collection Schemas

### idempotent_operation_config
Stores configuration for each service-operation combination.
- **Unique Index**: service + operation
- **Fields**: service, operation, lockDuration, executionDurationLimit

### idempotent_operation_lock_temp
Temporary locks with automatic expiration.
- **Unique Index**: service + operation + idempotencyKey
- **TTL Index**: expiredAt (automatic cleanup)
- **Fields**: service, operation, idempotencyKey, expiredAt

### stored_idempotent_operation_result
Successful operation results for idempotency.
- **Unique Index**: service + operation + idempotencyKey
- **Index**: idempotencyID
- **Fields**: service, operation, idempotencyKey, idempotencyID, result

### idempotent_operation
Main operation tracking records.
- **Unique Index**: idempotencyID
- **Index**: service + operation + idempotencyKey
- **Fields**: idempotencyID, service, operation, idempotencyKey

### idempotent_operation_lock
Lock history and status tracking.
- **Indexes**: lockID, idempotencyID, service + operation + idempotencyKey, releasedAt
- **Fields**: service, operation, idempotencyKey, idempotencyID, lockID, status, timestamps

### failed_idempotent_operation_result
Failed operation results and timeout records.
- **Indexes**: idempotencyID, service + operation + idempotencyKey
- **Fields**: service, operation, idempotencyKey, idempotencyID, failureReason, originalResult

## Notes

- All collections include validation schemas to ensure data integrity
- TTL indexes automatically clean up expired temporary locks
- Unique indexes prevent duplicate operations and ensure idempotency
- The application no longer creates indexes at startup - they must be created using these scripts