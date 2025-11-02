// MongoDB Index Creation Script for Idempotent Operation Service
// This script creates all required indexes for the six collections

// Use the idempotent_service database
use('idempotent_service');

print('Creating indexes for Idempotent Operation Service collections...');

// ========== idempotent_operation_config Collection ==========
print('Creating indexes for idempotent_operation_config collection...');

// Unique index on service and operation combination
db.idempotent_operation_config.createIndex(
    { "service": 1, "operation": 1 },
    { 
        unique: true,
        name: "idx_service_operation_unique"
    }
);

print('✓ Created unique index on service+operation for idempotent_operation_config');

// ========== idempotent_operation_lock_temp Collection ==========
print('Creating indexes for idempotent_operation_lock_temp collection...');

// Unique compound index on service, operation, and idempotencyKey combination
db.idempotent_operation_lock_temp.createIndex(
    { "service": 1, "operation": 1, "idempotencyKey": 1 },
    { 
        unique: true,
        name: "idx_service_operation_key_unique"
    }
);

// TTL index on expiredAt field for automatic cleanup
db.idempotent_operation_lock_temp.createIndex(
    { "expiredAt": 1 },
    { 
        expireAfterSeconds: 0,
        name: "idx_expiredAt_ttl"
    }
);

print('✓ Created indexes for idempotent_operation_lock_temp collection');

// ========== stored_idempotent_operation_result Collection ==========
print('Creating indexes for stored_idempotent_operation_result collection...');

// Unique compound index on service, operation, and idempotencyKey combination
db.stored_idempotent_operation_result.createIndex(
    { "service": 1, "operation": 1, "idempotencyKey": 1 },
    { 
        unique: true,
        name: "idx_service_operation_key_unique"
    }
);

print('✓ Created indexes for stored_idempotent_operation_result collection');

// ========== idempotent_operation Collection ==========
print('Creating indexes for idempotent_operation collection...');

// Compound index on service, operation, idempotencyKey for queries
db.idempotent_operation.createIndex(
    { "service": 1, "operation": 1, "idempotencyKey": 1 },
    { name: "idx_service_operation_key" }
);

print('✓ Created indexes for idempotent_operation collection');

// ========== idempotent_operation_lock Collection ==========
print('Creating indexes for idempotent_operation_lock collection...');

// Unique index on idempotencyID as specified in entity
db.idempotent_operation_lock.createIndex(
    { "idempotencyID": 1 },
    { 
        unique: true,
        name: "idx_idempotencyID_unique" 
    }
);

print('✓ Created indexes for idempotent_operation_lock collection');

// ========== failed_idempotent_operation_result Collection ==========
print('Creating indexes for failed_idempotent_operation_result collection...');

// Index on idempotencyID for quick lookups
db.failed_idempotent_operation_result.createIndex(
    { "idempotencyID": 1 },
    { name: "idx_idempotencyID" }
);

print('✓ Created indexes for failed_idempotent_operation_result collection');

print('All indexes created successfully!');

// ========== Verify Indexes ==========
print('\nVerifying created indexes...');

print('idempotent_operation_config indexes:');
db.idempotent_operation_config.getIndexes().forEach(index => print('  - ' + index.name));

print('idempotent_operation_lock_temp indexes:');
db.idempotent_operation_lock_temp.getIndexes().forEach(index => print('  - ' + index.name));

print('stored_idempotent_operation_result indexes:');
db.stored_idempotent_operation_result.getIndexes().forEach(index => print('  - ' + index.name));

print('idempotent_operation indexes:');
db.idempotent_operation.getIndexes().forEach(index => print('  - ' + index.name));

print('idempotent_operation_lock indexes:');
db.idempotent_operation_lock.getIndexes().forEach(index => print('  - ' + index.name));

print('failed_idempotent_operation_result indexes:');
db.failed_idempotent_operation_result.getIndexes().forEach(index => print('  - ' + index.name));

print('\nIndex creation completed successfully!');