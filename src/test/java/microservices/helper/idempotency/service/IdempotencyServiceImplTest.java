package microservices.helper.idempotency.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import microservices.helper.idempotency.cache.IdempotentOperationConfigCache;
import microservices.helper.idempotency.cache.LockCache;
import microservices.helper.idempotency.entity.FailedIdempotentOperationResult;
import microservices.helper.idempotency.entity.IdempotentOperation;
import microservices.helper.idempotency.entity.IdempotentOperationLock;
import microservices.helper.idempotency.entity.IdempotentOperationLockTemp;
import microservices.helper.idempotency.entity.StoredIdempotentOperationResult;
import microservices.helper.idempotency.enums.ExecutionResult;
import microservices.helper.idempotency.exception.IdempotencyException;
import microservices.helper.idempotency.model.IdempotentOperationResult;
import microservices.helper.idempotency.repository.FailedIdempotentOperationResultRepository;
import microservices.helper.idempotency.repository.IdempotentOperationLockRepository;
import microservices.helper.idempotency.repository.IdempotentOperationLockTempRepository;
import microservices.helper.idempotency.repository.IdempotentOperationRepository;
import microservices.helper.idempotency.repository.StoredIdempotentOperationResultRepository;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceImplTest {

    @Mock
    private IdempotentOperationRepository idempotentOperationRepository;
    
    @Mock
    private IdempotentOperationLockRepository idempotentOperationLockRepository;
    
    @Mock
    private StoredIdempotentOperationResultRepository storedIdempotentOperationResultRepository;
    
    @Mock
    private IdempotentOperationLockTempRepository idempotentOperationLockTempRepository;
    
    @Mock
    private FailedIdempotentOperationResultRepository failedIdempotentOperationResultRepository;
    
    @Mock
    private IdempotentOperationConfigCache idempotentOperationConfigCache;
    
    @Mock
    private LockCache lockCache;

    @InjectMocks
    private IdempotencyServiceImpl idempotencyService;

    private IdempotentOperationResult testInput;
    private IdempotentOperation testOperation;
    private StoredIdempotentOperationResult testStoredResult;
    private IdempotentOperationLockTemp testTempLock;

    @BeforeEach
    void setUp() {
        testInput = new IdempotentOperationResult();
        testInput.setService("test-service");
        testInput.setOperation("test-operation");
        testInput.setIdempotencyKey("test-key-123");
        testInput.setIdempotentOperationResult("test-result");

        testOperation = new IdempotentOperation();
        testOperation.setId(UUID.randomUUID());
        testOperation.setService("test-service");
        testOperation.setOperation("test-operation");
        testOperation.setIdempotencyKey("test-key-123");
        testOperation.setCreatedAt(Instant.now());

        testStoredResult = new StoredIdempotentOperationResult();
        testStoredResult.setId(UUID.randomUUID());
        testStoredResult.setService("test-service");
        testStoredResult.setOperation("test-operation");
        testStoredResult.setIdempotencyKey("test-key-123");
        testStoredResult.setIdempotentOperationResult("existing-result");

        testTempLock = new IdempotentOperationLockTemp();
        testTempLock.setId(UUID.randomUUID());
        testTempLock.setService("test-service");
        testTempLock.setOperation("test-operation");
        testTempLock.setIdempotencyKey("test-key-123");
        testTempLock.setLockedAt(Instant.now());
        testTempLock.setExpiredAt(Instant.now().plus(Duration.ofMinutes(5)));
    }

    @Test
    void getStoredExecutionResultOrLockOperation_WhenExistingResultFound_ShouldReturnCachedResult() {
        // Arrange
        when(idempotentOperationRepository.insert(any(IdempotentOperation.class))).thenReturn(testOperation);
        when(storedIdempotentOperationResultRepository.findByServiceAndOperationAndIdempotencyKey(
                "test-service", "test-operation", "test-key-123"))
                .thenReturn(Optional.of(testStoredResult));

        // Act
        IdempotentOperationResult result = idempotencyService.getStoredExecutionResultOrLockOperation(testInput);

        // Assert
        assertNotNull(result);
        assertEquals(ExecutionResult.SUCCESS.getValue(), result.getExecutionResult());
        assertEquals("existing-result", result.getIdempotentOperationResult());
        
        verify(idempotentOperationRepository).insert(any(IdempotentOperation.class));
        verify(storedIdempotentOperationResultRepository).findByServiceAndOperationAndIdempotencyKey(
                "test-service", "test-operation", "test-key-123");
        verifyNoInteractions(idempotentOperationLockTempRepository);
    }

    @Test
    void getStoredExecutionResultOrLockOperation_WhenNoExistingResult_ShouldAcquireLock() {
        // Arrange
        when(idempotentOperationRepository.insert(any(IdempotentOperation.class))).thenReturn(testOperation);
        when(storedIdempotentOperationResultRepository.findByServiceAndOperationAndIdempotencyKey(
                "test-service", "test-operation", "test-key-123"))
                .thenReturn(Optional.empty());
        when(idempotentOperationConfigCache.getLockDuration("test-service", "test-operation"))
                .thenReturn(Duration.ofMinutes(5));
        when(idempotentOperationLockTempRepository.insert(any(IdempotentOperationLockTemp.class)))
                .thenReturn(testTempLock);

        // Act
        IdempotentOperationResult result = idempotencyService.getStoredExecutionResultOrLockOperation(testInput);

        // Assert
        assertNotNull(result);
        assertEquals(ExecutionResult.OPERATION_LOCKED_SUCCESSFULLY.getValue(), result.getExecutionResult());
        assertEquals(testOperation.getId(), result.getIdempotencyID());
        assertEquals(testTempLock.getId(), result.getLockID());
        assertNotNull(result.getLockedAt());
        assertNotNull(result.getExpiredAt());
        
        verify(idempotentOperationLockTempRepository).insert(any(IdempotentOperationLockTemp.class));
        verify(lockCache).storeLockInCache(any());
    }    
@Test
    void getStoredExecutionResultOrLockOperation_WhenLockAlreadyExists_ShouldThrowException() {
        // Arrange
        when(idempotentOperationRepository.insert(any(IdempotentOperation.class))).thenReturn(testOperation);
        when(storedIdempotentOperationResultRepository.findByServiceAndOperationAndIdempotencyKey(
                "test-service", "test-operation", "test-key-123"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty()); // Second call during lock conflict handling
        when(idempotentOperationConfigCache.getLockDuration("test-service", "test-operation"))
                .thenReturn(Duration.ofMinutes(5));
        when(idempotentOperationLockTempRepository.insert(any(IdempotentOperationLockTemp.class)))
                .thenThrow(new DuplicateKeyException("Lock already exists"));

        // Act & Assert
        IdempotencyException exception = assertThrows(IdempotencyException.class, () -> 
            idempotencyService.getStoredExecutionResultOrLockOperation(testInput));
        
        assertEquals(ExecutionResult.OPERATION_ALREADY_LOCKED, exception.getExecutionResult());
        assertTrue(exception.getMessage().contains("Operation is already locked by another process"));
    }

    @Test
    void getStoredExecutionResultOrLockOperation_WhenLockExistsButResultCompleted_ShouldReturnResult() {
        // Arrange
        when(idempotentOperationRepository.insert(any(IdempotentOperation.class))).thenReturn(testOperation);
        when(storedIdempotentOperationResultRepository.findByServiceAndOperationAndIdempotencyKey(
                "test-service", "test-operation", "test-key-123"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(testStoredResult)); // Second call finds completed result
        when(idempotentOperationConfigCache.getLockDuration("test-service", "test-operation"))
                .thenReturn(Duration.ofMinutes(5));
        when(idempotentOperationLockTempRepository.insert(any(IdempotentOperationLockTemp.class)))
                .thenThrow(new DuplicateKeyException("Lock already exists"));

        // Act
        IdempotentOperationResult result = idempotencyService.getStoredExecutionResultOrLockOperation(testInput);

        // Assert
        assertNotNull(result);
        assertEquals(ExecutionResult.SUCCESS.getValue(), result.getExecutionResult());
        assertEquals("existing-result", result.getIdempotentOperationResult());
    }

    @Test
    void getStoredExecutionResultOrLockOperation_WhenIdempotentOperationCreationFails_ShouldThrowException() {
        // Arrange
        when(idempotentOperationRepository.insert(any(IdempotentOperation.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        IdempotencyException exception = assertThrows(IdempotencyException.class, () -> 
            idempotencyService.getStoredExecutionResultOrLockOperation(testInput));
        
        assertEquals(ExecutionResult.OPERATION_FAILED, exception.getExecutionResult());
        assertTrue(exception.getMessage().contains("Failed to create idempotent operation"));
    }

    @Test
    void getStoredExecutionResultOrLockOperation_WhenLockAcquisitionFails_ShouldThrowException() {
        // Arrange
        when(idempotentOperationRepository.insert(any(IdempotentOperation.class))).thenReturn(testOperation);
        when(storedIdempotentOperationResultRepository.findByServiceAndOperationAndIdempotencyKey(
                "test-service", "test-operation", "test-key-123"))
                .thenReturn(Optional.empty());
        when(idempotentOperationConfigCache.getLockDuration("test-service", "test-operation"))
                .thenReturn(Duration.ofMinutes(5));
        when(idempotentOperationLockTempRepository.insert(any(IdempotentOperationLockTemp.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        IdempotencyException exception = assertThrows(IdempotencyException.class, () -> 
            idempotencyService.getStoredExecutionResultOrLockOperation(testInput));
        
        assertEquals(ExecutionResult.OPERATION_FAILED, exception.getExecutionResult());
        assertTrue(exception.getMessage().contains("Failed to acquire lock for operation"));
    }

    @Test
    void saveIdempotentOperationResult_WhenSuccessfulOperation_ShouldSaveResult() {
        // Arrange
        testInput.setLockID(UUID.randomUUID());
        testInput.setIdempotencyID(UUID.randomUUID());
        testInput.setExecutionResult(ExecutionResult.SUCCESS.getValue());
        testInput.setExpiredAt(Instant.now().plus(Duration.ofMinutes(5))); // Not expired
        
        when(idempotentOperationConfigCache.isAllowSaveOnExpired("test-service", "test-operation"))
                .thenReturn(false);
        when(storedIdempotentOperationResultRepository.insert(any(StoredIdempotentOperationResult.class)))
                .thenReturn(testStoredResult);
        when(idempotentOperationLockRepository.insert(any(IdempotentOperationLock.class)))
                .thenReturn(new IdempotentOperationLock());

        // Act
        assertDoesNotThrow(() -> idempotencyService.saveIdempotentOperationResult(testInput));

        // Assert
        verify(storedIdempotentOperationResultRepository).insert(any(StoredIdempotentOperationResult.class));
        verify(idempotentOperationLockRepository).insert(any(IdempotentOperationLock.class));
        verify(idempotentOperationLockTempRepository).deleteById(testInput.getLockID());
        verify(lockCache).removeLockFromCache(testInput.getLockID());
        verifyNoInteractions(failedIdempotentOperationResultRepository);
    }

    @Test
    void saveIdempotentOperationResult_WhenFailedOperation_ShouldSaveFailedResult() {
        // Arrange
        testInput.setLockID(UUID.randomUUID());
        testInput.setIdempotencyID(UUID.randomUUID());
        testInput.setExecutionResult(ExecutionResult.OPERATION_FAILED.getValue());
        testInput.setIdempotentOperationResult("Operation failed due to validation error");
        
        when(failedIdempotentOperationResultRepository.insert(any(FailedIdempotentOperationResult.class)))
                .thenReturn(new FailedIdempotentOperationResult());
        when(idempotentOperationLockRepository.insert(any(IdempotentOperationLock.class)))
                .thenReturn(new IdempotentOperationLock());

        // Act
        assertDoesNotThrow(() -> idempotencyService.saveIdempotentOperationResult(testInput));

        // Assert
        verify(failedIdempotentOperationResultRepository).insert(any(FailedIdempotentOperationResult.class));
        verify(idempotentOperationLockRepository).insert(any(IdempotentOperationLock.class));
        verify(idempotentOperationLockTempRepository).deleteById(testInput.getLockID());
        verifyNoInteractions(storedIdempotentOperationResultRepository);
    }

    @Test
    void saveIdempotentOperationResult_WhenExpiredButAllowSaveOnExpired_ShouldSaveResult() {
        // Arrange
        testInput.setLockID(UUID.randomUUID());
        testInput.setIdempotencyID(UUID.randomUUID());
        testInput.setExecutionResult(ExecutionResult.SUCCESS.getValue());
        testInput.setExpiredAt(Instant.now().minus(Duration.ofMinutes(5))); // Expired
        
        when(idempotentOperationConfigCache.isAllowSaveOnExpired("test-service", "test-operation"))
                .thenReturn(true);
        when(storedIdempotentOperationResultRepository.insert(any(StoredIdempotentOperationResult.class)))
                .thenReturn(testStoredResult);
        when(idempotentOperationLockRepository.insert(any(IdempotentOperationLock.class)))
                .thenReturn(new IdempotentOperationLock());

        // Act
        assertDoesNotThrow(() -> idempotencyService.saveIdempotentOperationResult(testInput));

        // Assert
        verify(storedIdempotentOperationResultRepository).insert(any(StoredIdempotentOperationResult.class));
        verifyNoInteractions(failedIdempotentOperationResultRepository);
    }

    @Test
    void saveIdempotentOperationResult_WhenExpiredAndNotAllowSaveOnExpired_ShouldSaveFailedResult() {
        // Arrange
        testInput.setLockID(UUID.randomUUID());
        testInput.setIdempotencyID(UUID.randomUUID());
        testInput.setExecutionResult(ExecutionResult.SUCCESS.getValue());
        testInput.setExpiredAt(Instant.now().minus(Duration.ofMinutes(5))); // Expired
        
        when(idempotentOperationConfigCache.isAllowSaveOnExpired("test-service", "test-operation"))
                .thenReturn(false);
        when(failedIdempotentOperationResultRepository.insert(any(FailedIdempotentOperationResult.class)))
                .thenReturn(new FailedIdempotentOperationResult());
        when(idempotentOperationLockRepository.insert(any(IdempotentOperationLock.class)))
                .thenReturn(new IdempotentOperationLock());

        // Act
        assertDoesNotThrow(() -> idempotencyService.saveIdempotentOperationResult(testInput));

        // Assert
        verify(failedIdempotentOperationResultRepository).insert(any(FailedIdempotentOperationResult.class));
        verifyNoInteractions(storedIdempotentOperationResultRepository);
    }

    @Test
    void saveIdempotentOperationResult_WhenLockIDIsNull_ShouldThrowException() {
        // Arrange
        testInput.setLockID(null);
        testInput.setIdempotencyID(UUID.randomUUID());

        // Act & Assert
        IdempotencyException exception = assertThrows(IdempotencyException.class, () -> 
            idempotencyService.saveIdempotentOperationResult(testInput));
        
        assertEquals(ExecutionResult.OPERATION_FAILED, exception.getExecutionResult());
        assertTrue(exception.getMessage().contains("Lock ID is required"));
    }

    @Test
    void saveIdempotentOperationResult_WhenIdempotencyIDIsNull_ShouldThrowException() {
        // Arrange
        testInput.setLockID(UUID.randomUUID());
        testInput.setIdempotencyID(null);

        // Act & Assert
        IdempotencyException exception = assertThrows(IdempotencyException.class, () -> 
            idempotencyService.saveIdempotentOperationResult(testInput));
        
        assertEquals(ExecutionResult.OPERATION_FAILED, exception.getExecutionResult());
        assertTrue(exception.getMessage().contains("Idempotency ID is required"));
    }

    @Test
    void saveIdempotentOperationResult_WhenDuplicateResultExists_ShouldNotThrowException() {
        // Arrange
        testInput.setLockID(UUID.randomUUID());
        testInput.setIdempotencyID(UUID.randomUUID());
        testInput.setExecutionResult(ExecutionResult.SUCCESS.getValue());
        testInput.setExpiredAt(Instant.now().plus(Duration.ofMinutes(5)));
        
        when(idempotentOperationConfigCache.isAllowSaveOnExpired("test-service", "test-operation"))
                .thenReturn(false);
        when(storedIdempotentOperationResultRepository.insert(any(StoredIdempotentOperationResult.class)))
                .thenThrow(new DuplicateKeyException("Result already exists"));
        when(idempotentOperationLockRepository.insert(any(IdempotentOperationLock.class)))
                .thenReturn(new IdempotentOperationLock());

        // Act & Assert - Should not throw exception for duplicate results
        assertDoesNotThrow(() -> idempotencyService.saveIdempotentOperationResult(testInput));
        
        verify(storedIdempotentOperationResultRepository).insert(any(StoredIdempotentOperationResult.class));
        verify(idempotentOperationLockRepository).insert(any(IdempotentOperationLock.class));
    }

    @Test
    void saveIdempotentOperationResult_WhenSaveSuccessfulResultFails_ShouldThrowException() {
        // Arrange
        testInput.setLockID(UUID.randomUUID());
        testInput.setIdempotencyID(UUID.randomUUID());
        testInput.setExecutionResult(ExecutionResult.SUCCESS.getValue());
        testInput.setExpiredAt(Instant.now().plus(Duration.ofMinutes(5)));
        
        when(idempotentOperationConfigCache.isAllowSaveOnExpired("test-service", "test-operation"))
                .thenReturn(false);
        when(storedIdempotentOperationResultRepository.insert(any(StoredIdempotentOperationResult.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        IdempotencyException exception = assertThrows(IdempotencyException.class, () -> 
            idempotencyService.saveIdempotentOperationResult(testInput));
        
        assertEquals(ExecutionResult.OPERATION_FAILED, exception.getExecutionResult());
        assertTrue(exception.getMessage().contains("Failed to save operation result"));
    }

    @Test
    void saveIdempotentOperationResult_WhenSaveFailedResultFails_ShouldThrowException() {
        // Arrange
        testInput.setLockID(UUID.randomUUID());
        testInput.setIdempotencyID(UUID.randomUUID());
        testInput.setExecutionResult(ExecutionResult.OPERATION_FAILED.getValue());
        
        when(failedIdempotentOperationResultRepository.insert(any(FailedIdempotentOperationResult.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        IdempotencyException exception = assertThrows(IdempotencyException.class, () -> 
            idempotencyService.saveIdempotentOperationResult(testInput));
        
        assertEquals(ExecutionResult.OPERATION_FAILED, exception.getExecutionResult());
        assertTrue(exception.getMessage().contains("Failed to save failed operation result"));
    }

    @Test
    void saveIdempotentOperationResult_WhenReleaseLockFails_ShouldThrowException() {
        // Arrange
        testInput.setLockID(UUID.randomUUID());
        testInput.setIdempotencyID(UUID.randomUUID());
        testInput.setExecutionResult(ExecutionResult.SUCCESS.getValue());
        testInput.setExpiredAt(Instant.now().plus(Duration.ofMinutes(5)));
        
        when(idempotentOperationConfigCache.isAllowSaveOnExpired("test-service", "test-operation"))
                .thenReturn(false);
        when(storedIdempotentOperationResultRepository.insert(any(StoredIdempotentOperationResult.class)))
                .thenReturn(testStoredResult);
        when(idempotentOperationLockRepository.insert(any(IdempotentOperationLock.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        IdempotencyException exception = assertThrows(IdempotencyException.class, () -> 
            idempotencyService.saveIdempotentOperationResult(testInput));
        
        assertEquals(ExecutionResult.OPERATION_FAILED, exception.getExecutionResult());
        assertTrue(exception.getMessage().contains("Failed to release lock"));
    }

    @Test
    void saveIdempotentOperationResult_WhenCleanupTempLockFails_ShouldNotThrowException() {
        // Arrange
        testInput.setLockID(UUID.randomUUID());
        testInput.setIdempotencyID(UUID.randomUUID());
        testInput.setExecutionResult(ExecutionResult.SUCCESS.getValue());
        testInput.setExpiredAt(Instant.now().plus(Duration.ofMinutes(5)));
        
        when(idempotentOperationConfigCache.isAllowSaveOnExpired("test-service", "test-operation"))
                .thenReturn(false);
        when(storedIdempotentOperationResultRepository.insert(any(StoredIdempotentOperationResult.class)))
                .thenReturn(testStoredResult);
        when(idempotentOperationLockRepository.insert(any(IdempotentOperationLock.class)))
                .thenReturn(new IdempotentOperationLock());
        doThrow(new RuntimeException("Cleanup failed")).when(idempotentOperationLockTempRepository)
                .deleteById(testInput.getLockID());

        // Act & Assert - Should not throw exception for cleanup failures
        assertDoesNotThrow(() -> idempotencyService.saveIdempotentOperationResult(testInput));
        
        verify(storedIdempotentOperationResultRepository).insert(any(StoredIdempotentOperationResult.class));
        verify(idempotentOperationLockRepository).insert(any(IdempotentOperationLock.class));
        verify(idempotentOperationLockTempRepository).deleteById(testInput.getLockID());
    }

    @Test
    void saveIdempotentOperationResult_WhenCacheStorageFails_ShouldNotAffectLockAcquisition() {
        // This test verifies that cache storage failures don't affect the main operation
        // during the getStoredExecutionResultOrLockOperation method
        
        // Arrange
        when(idempotentOperationRepository.insert(any(IdempotentOperation.class))).thenReturn(testOperation);
        when(storedIdempotentOperationResultRepository.findByServiceAndOperationAndIdempotencyKey(
                "test-service", "test-operation", "test-key-123"))
                .thenReturn(Optional.empty());
        when(idempotentOperationConfigCache.getLockDuration("test-service", "test-operation"))
                .thenReturn(Duration.ofMinutes(5));
        when(idempotentOperationLockTempRepository.insert(any(IdempotentOperationLockTemp.class)))
                .thenReturn(testTempLock);
        doThrow(new RuntimeException("Cache error")).when(lockCache).storeLockInCache(any());

        // Act - Should not throw exception even if cache storage fails
        IdempotentOperationResult result = idempotencyService.getStoredExecutionResultOrLockOperation(testInput);

        // Assert
        assertNotNull(result);
        assertEquals(ExecutionResult.OPERATION_LOCKED_SUCCESSFULLY.getValue(), result.getExecutionResult());
        verify(lockCache).storeLockInCache(any());
    }
}
