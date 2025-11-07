// MongoDB Collection Creation Script for Idempotent Operation Service
// This script creates all required collections with validation schemas

// Use the idempotency database
use('idempotency_service');

print('Creating collections for Idempotent Operation Service...');

// ========== idempotent_operation_config Collection ==========
print('Creating idempotent_operation_config collection...');

db.createCollection("idempotent_operation_config", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["service", "operation", "lockDuration", "allowSaveOnExpired"],
            properties: {
                _id: {
                    bsonType: "string",
                    description: "UUID primary key"
                },
                service: {
                    bsonType: "string",
                    description: "Service name - required string"
                },
                operation: {
                    bsonType: "string",
                    description: "Operation name - required string"
                },
                lockDuration: {
                    bsonType: "string",
                    description: "Lock duration as Duration object - required"
                },
                allowSaveOnExpired: {
                    bsonType: "bool",
                    description: "Whether to allow saving on expired operations - required boolean"
                }
            }
        }
    }
});

print('✓ Created idempotent_operation_config collection');

// ========== idempotent_operation Collection ==========
print('Creating idempotent_operation collection...');

db.createCollection("idempotent_operation", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["service", "operation", "idempotencyKey"],
            properties: {
                _id: {
                    bsonType: "string",
                    description: "UUID primary key"
                },
                service: {
                    bsonType: "string",
                    description: "Service name - required string"
                },
                operation: {
                    bsonType: "string",
                    description: "Operation name - required string"
                },
                idempotencyKey: {
                    bsonType: "string",
                    description: "Idempotency key - required string"
                },
                createdAt: {
                    bsonType: "date",
                    description: "Creation timestamp"
                }
            }
        }
    }
});

print('✓ Created idempotent_operation collection');

// ========== idempotent_operation_lock_temp Collection ==========
print('Creating idempotent_operation_lock_temp collection...');

db.createCollection("idempotent_operation_lock_temp", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["service", "operation", "idempotencyKey", "lockedAt", "expiredAt"],
            properties: {
                _id: {
                    bsonType: "string",
                    description: "UUID primary key"
                },
                service: {
                    bsonType: "string",
                    description: "Service name - required string"
                },
                operation: {
                    bsonType: "string",
                    description: "Operation name - required string"
                },
                idempotencyKey: {
                    bsonType: "string",
                    description: "Idempotency key - required string"
                },
                lockedAt: {
                    bsonType: "date",
                    description: "Lock timestamp - required date"
                },
                expiredAt: {
                    bsonType: "date",
                    description: "Lock expiration timestamp - required date"
                }
            }
        }
    }
});

print('✓ Created idempotent_operation_lock_temp collection');

// ========== stored_idempotent_operation_result Collection ==========
print('Creating stored_idempotent_operation_result collection...');

db.createCollection("stored_idempotent_operation_result", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["service", "operation", "idempotencyKey", "idempotentOperationResult"],
            properties: {
                _id: {
                    bsonType: "string",
                    description: "UUID primary key"
                },
                service: {
                    bsonType: "string",
                    description: "Service name - required string"
                },
                operation: {
                    bsonType: "string",
                    description: "Operation name - required string"
                },
                idempotencyKey: {
                    bsonType: "string",
                    description: "Idempotency key - required string"
                },
                idempotentOperationResult: {
                    bsonType: "string",
                    description: "Operation result - required string"
                }
            }
        }
    }
});

print('✓ Created stored_idempotent_operation_result collection');

// ========== idempotent_operation_lock Collection ==========
print('Creating idempotent_operation_lock collection...');

db.createCollection("idempotent_operation_lock", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["idempotencyID"],
            properties: {
                _id: {
                    bsonType: "string",
                    description: "UUID primary key"
                },
                idempotencyID: {
                    bsonType: "string",
                    description: "UUID idempotency ID - required and unique"
                },
                lockedAt: {
                    bsonType: "date",
                    description: "Lock acquisition timestamp"
                },
                expiredAt: {
                    bsonType: "date",
                    description: "Lock expiration timestamp"
                },
                createdAt: {
                    bsonType: "date",
                    description: "Lock release timestamp"
                }
            }
        }
    }
});

print('✓ Created idempotent_operation_lock collection');

// ========== failed_idempotent_operation_result Collection ==========
print('Creating failed_idempotent_operation_result collection...');

db.createCollection("failed_idempotent_operation_result", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["lockID", "errorMessage"],
            properties: {
                _id: {
                    bsonType: "string",
                    description: "UUID primary key"
                },
                lockID: {
                    bsonType: "string",
                    description: "UUID lock ID - required"
                },
                errorMessage: {
                    bsonType: "string",
                    description: "Error message - required string"
                }
            }
        }
    }
});

print('✓ Created failed_idempotent_operation_result collection');

print('All collections created successfully!');

// ========== Create Unique Indexes for Atomic Operations ==========
print('\nCreating unique indexes for atomic lock acquisition...');

// Unique index on temp lock collection to prevent duplicate locks
db.idempotent_operation_lock_temp.createIndex(
    { "service": 1, "operation": 1, "idempotencyKey": 1 }, 
    { 
        unique: true, 
        name: "unique_operation_lock_idx",
        background: true 
    }
);
print('✓ Created unique index on idempotent_operation_lock_temp');

// Unique index on stored results to prevent duplicate results
db.stored_idempotent_operation_result.createIndex(
    { "service": 1, "operation": 1, "idempotencyKey": 1 }, 
    { 
        unique: true, 
        name: "unique_operation_result_idx",
        background: true 
    }
);
print('✓ Created unique index on stored_idempotent_operation_result');

print('All indexes created successfully!');

// ========== Verify Collections ==========
print('\nVerifying created collections...');

db.getCollectionNames().forEach(name => {
    if (name.includes('idempotent')) {
        print('✓ Collection: ' + name);
    }
});

print('\nCollection creation completed successfully!');