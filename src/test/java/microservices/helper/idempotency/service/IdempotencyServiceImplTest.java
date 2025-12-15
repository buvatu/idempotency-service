package microservices.helper.idempotency.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.DigestUtils;

import microservices.helper.idempotency.cache.IdempotentOperationConfigCache;
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
        testOperation.setId(UUID.randomUUID().toString());
        testOperation.setService("test-service");
        testOperation.setOperation("test-operation");
        testOperation.setIdempotencyKey("test-key-123");
        testOperation.setCreatedAt(Instant.now());

        testStoredResult = new StoredIdempotentOperationResult();
        testStoredResult.setId(hashedKey("test-service", "test-operation", "test-key-123"));
        testStoredResult.setService("test-service");
        testStoredResult.setOperation("test-operation");
        testStoredResult.setIdempotencyKey("test-key-123");
        testStoredResult.setIdempotentOperationResult("existing-result");

        testTempLock = new IdempotentOperationLockTemp();
        testTempLock.setId(UUID.randomUUID().toString());
        testTempLock.setIdempotencyId(UUID.randomUUID().toString());
        testTempLock.setService("test-service");
        testTempLock.setOperation("test-operation");
        testTempLock.setIdempotencyKey("test-key-123");
        testTempLock.setLockedAt(Instant.now());
        testTempLock.setExpiredAt(Instant.now().plus(Duration.ofMinutes(5)));
    }

    private String hashedKey(String service, String operation, String idempotencyKey) {
        return Base64.getEncoder().encodeToString(DigestUtils.md5Digest((service + "-" + operation + "-" + idempotencyKey).getBytes()));
    }

    @Test
    void getStoredExecutionResultOrLockOperation_WhenExistingResultFound_ShouldReturnCachedResult() {
        // Arrange
        when(idempotentOperationRepository.insert(any(IdempotentOperation.class))).thenReturn(testOperation);
        when(storedIdempotentOperationResultRepository.findById(hashedKey("test-service", "test-operation", "test-key-123")))
                .thenReturn(Optional.of(testStoredResult));

        // Act
        IdempotentOperationResult result = idempotencyService.getStoredExecutionResultOrLockOperation(testInput);

        // Assert
        assertNotNull(result);
        assertEquals(ExecutionResult.SUCCESS.getValue(), result.getExecutionResult());
        assertEquals("existing-result", result.getIdempotentOperationResult());

        verify(idempotentOperationRepository).insert(any(IdempotentOperation.class));
        verify(storedIdempotentOperationResultRepository).findById(hashedKey("test-service", "test-operation", "test-key-123"));
        verifyNoInteractions(idempotentOperationLockTempRepository);
    }

    @Test
    void getStoredExecutionResultOrLockOperation_WhenNoExistingResult_ShouldAcquireLock() {
        // Arrange
        when(idempotentOperationRepository.insert(any(IdempotentOperation.class))).thenReturn(testOperation);
        when(storedIdempotentOperationResultRepository.findById(hashedKey("test-service", "test-operation", "test-key-123")))
                .thenReturn(Optional.empty());
        when(idempotentOperationConfigCache.getLockDuration("test-service", "test-operation"))
                .thenReturn(Duration.ofMinutes(5));

        // Act
        IdempotentOperationResult result = idempotencyService.getStoredExecutionResultOrLockOperation(testInput);

        // Assert
        assertNotNull(result);
        assertEquals(ExecutionResult.OPERATION_LOCKED_SUCCESSFULLY.getValue(), result.getExecutionResult());
        assertEquals(testOperation.getId(), result.getIdempotencyId());
        assertNotNull(result.getLockId());
        assertNotNull(result.getLockedAt());
        assertNotNull(result.getExpiredAt());

        verify(idempotentOperationLockTempRepository).insert(any(IdempotentOperationLockTemp.class));
    }

    @Test
    void getStoredExecutionResultOrLockOperation_WhenLockAlreadyExists_ShouldThrowException() {
        // Arrange
        when(idempotentOperationRepository.insert(any(IdempotentOperation.class))).thenReturn(testOperation);
        when(storedIdempotentOperationResultRepository.findById(hashedKey("test-service", "test-operation", "test-key-123")))
                .thenReturn(Optional.empty());
        when(idempotentOperationConfigCache.getLockDuration("test-service", "test-operation"))
                .thenReturn(Duration.ofMinutes(5));
        doThrow(new DuplicateKeyException("Lock already exists"))
                .when(idempotentOperationLockTempRepository).insert(any(IdempotentOperationLockTemp.class));

        // Act & Assert
        IdempotencyException exception = assertThrows(IdempotencyException.class,
                () -> idempotencyService.getStoredExecutionResultOrLockOperation(testInput));

        assertEquals(ExecutionResult.OPERATION_ALREADY_LOCKED, exception.getExecutionResult());
        assertTrue(exception.getMessage().contains("Operation is already locked by another process"));
    }

    @Test
    void getStoredExecutionResultOrLockOperation_WhenIdempotentOperationCreationFails_ShouldThrowException() {
        // Arrange
        when(idempotentOperationRepository.insert(any(IdempotentOperation.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        IdempotencyException exception = assertThrows(IdempotencyException.class,
                () -> idempotencyService.getStoredExecutionResultOrLockOperation(testInput));

        assertEquals(ExecutionResult.OPERATION_FAILED, exception.getExecutionResult());
        assertTrue(exception.getMessage().contains("Failed to create idempotent operation"));
    }

    @Test
    void getStoredExecutionResultOrLockOperation_WhenLockAcquisitionFails_ShouldThrowException() {
        // Arrange
        when(idempotentOperationRepository.insert(any(IdempotentOperation.class))).thenReturn(testOperation);
        when(storedIdempotentOperationResultRepository.findById(hashedKey("test-service", "test-operation", "test-key-123")))
                .thenReturn(Optional.empty());
        when(idempotentOperationConfigCache.getLockDuration("test-service", "test-operation"))
                .thenReturn(Duration.ofMinutes(5));
        doThrow(new RuntimeException("Database error"))
                .when(idempotentOperationLockTempRepository).insert(any(IdempotentOperationLockTemp.class));

        // Act & Assert
        IdempotencyException exception = assertThrows(IdempotencyException.class,
                () -> idempotencyService.getStoredExecutionResultOrLockOperation(testInput));

        assertEquals(ExecutionResult.OPERATION_FAILED, exception.getExecutionResult());
        assertTrue(exception.getMessage().contains("Failed to acquire lock for operation"));
    }

    @Test
    void saveIdempotentOperationResult_WhenSuccessfulOperation_ShouldSaveResult() {
        // Arrange
        testInput.setLockId(testTempLock.getId());
        testInput.setIdempotencyId(testTempLock.getIdempotencyId());
        testInput.setExecutionResult(ExecutionResult.SUCCESS.getValue());

        when(idempotentOperationLockTempRepository.findById(testInput.getLockId()))
                .thenReturn(Optional.of(testTempLock));
        when(storedIdempotentOperationResultRepository.insert(any(StoredIdempotentOperationResult.class)))
                .thenReturn(testStoredResult);
        when(idempotentOperationLockRepository.insert(any(IdempotentOperationLock.class)))
                .thenReturn(new IdempotentOperationLock());

        // Act
        assertDoesNotThrow(() -> idempotencyService.saveIdempotentOperationResult(testInput));

        // Assert
        verify(idempotentOperationLockTempRepository).deleteById(testTempLock.getId());
        verify(idempotentOperationLockRepository).insert(any(IdempotentOperationLock.class));
        verify(storedIdempotentOperationResultRepository).insert(any(StoredIdempotentOperationResult.class));
        verifyNoInteractions(failedIdempotentOperationResultRepository);
    }

    @Test
    void saveIdempotentOperationResult_WhenFailedOperation_ShouldSaveFailedResult() {
        // Arrange
        testInput.setLockId(testTempLock.getId());
        testInput.setIdempotencyId(testTempLock.getIdempotencyId());
        testInput.setExecutionResult(ExecutionResult.OPERATION_FAILED.getValue());
        testInput.setIdempotentOperationResult("Operation failed due to validation error");

        when(idempotentOperationLockTempRepository.findById(testInput.getLockId()))
                .thenReturn(Optional.of(testTempLock));
        when(failedIdempotentOperationResultRepository.insert(any(FailedIdempotentOperationResult.class)))
                .thenReturn(new FailedIdempotentOperationResult());
        when(idempotentOperationLockRepository.insert(any(IdempotentOperationLock.class)))
                .thenReturn(new IdempotentOperationLock());

        // Act
        assertDoesNotThrow(() -> idempotencyService.saveIdempotentOperationResult(testInput));

        // Assert
        verify(idempotentOperationLockTempRepository).deleteById(testTempLock.getId());
        verify(idempotentOperationLockRepository).insert(any(IdempotentOperationLock.class));
        verify(failedIdempotentOperationResultRepository).insert(any(FailedIdempotentOperationResult.class));
        verifyNoInteractions(storedIdempotentOperationResultRepository);
    }

    @Test
    void saveIdempotentOperationResult_WhenLockExpired_ShouldNotSaveAnything() {
        // Arrange
        testInput.setLockId(UUID.randomUUID().toString());
        testInput.setIdempotencyId(UUID.randomUUID().toString());
        testInput.setExecutionResult(ExecutionResult.SUCCESS.getValue());

        when(idempotentOperationLockTempRepository.findById(testInput.getLockId()))
                .thenReturn(Optional.empty()); // Lock expired

        // Act
        assertDoesNotThrow(() -> idempotencyService.saveIdempotentOperationResult(testInput));

        // Assert
        verify(idempotentOperationLockTempRepository, never()).deleteById(anyString());
        verifyNoInteractions(idempotentOperationLockRepository);
        verifyNoInteractions(storedIdempotentOperationResultRepository);
        verifyNoInteractions(failedIdempotentOperationResultRepository);
    }

    @Test
    void saveIdempotentOperationResult_WhenLockIdIsNull_ShouldThrowException() {
        // Arrange
        testInput.setLockId(null);
        testInput.setIdempotencyId(UUID.randomUUID().toString());

        // Act & Assert
        IdempotencyException exception = assertThrows(IdempotencyException.class,
                () -> idempotencyService.saveIdempotentOperationResult(testInput));

        assertEquals(ExecutionResult.OPERATION_FAILED, exception.getExecutionResult());
        assertTrue(exception.getMessage().contains("Lock Id is required"));
    }

    @Test
    void saveIdempotentOperationResult_WhenIdempotencyIdIsNull_ShouldThrowException() {
        // Arrange
        testInput.setLockId(UUID.randomUUID().toString());
        testInput.setIdempotencyId(null);

        // Act & Assert
        IdempotencyException exception = assertThrows(IdempotencyException.class,
                () -> idempotencyService.saveIdempotentOperationResult(testInput));

        assertEquals(ExecutionResult.OPERATION_FAILED, exception.getExecutionResult());
        assertTrue(exception.getMessage().contains("Idempotency Id is required"));
    }

    @Test
    void saveIdempotentOperationResult_WhenDuplicateResultExists_ShouldNotThrowException() {
        // Arrange
        testInput.setLockId(testTempLock.getId());
        testInput.setIdempotencyId(testTempLock.getIdempotencyId());
        testInput.setExecutionResult(ExecutionResult.SUCCESS.getValue());

        when(idempotentOperationLockTempRepository.findById(testInput.getLockId()))
                .thenReturn(Optional.of(testTempLock));
        when(storedIdempotentOperationResultRepository.insert(any(StoredIdempotentOperationResult.class)))
                .thenThrow(new DuplicateKeyException("Result already exists"));
        when(idempotentOperationLockRepository.insert(any(IdempotentOperationLock.class)))
                .thenReturn(new IdempotentOperationLock());

        // Act & Assert
        assertDoesNotThrow(() -> idempotencyService.saveIdempotentOperationResult(testInput));

        verify(idempotentOperationLockTempRepository).deleteById(testTempLock.getId());
        verify(idempotentOperationLockRepository).insert(any(IdempotentOperationLock.class));
        verify(storedIdempotentOperationResultRepository).insert(any(StoredIdempotentOperationResult.class));
    }

    @Test
    void saveIdempotentOperationResult_WhenSaveSuccessfulResultFails_ShouldThrowException() {
        // Arrange
        testInput.setLockId(testTempLock.getId());
        testInput.setIdempotencyId(testTempLock.getIdempotencyId());
        testInput.setExecutionResult(ExecutionResult.SUCCESS.getValue());

        when(idempotentOperationLockTempRepository.findById(testInput.getLockId()))
                .thenReturn(Optional.of(testTempLock));
        when(storedIdempotentOperationResultRepository.insert(any(StoredIdempotentOperationResult.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        IdempotencyException exception = assertThrows(IdempotencyException.class,
                () -> idempotencyService.saveIdempotentOperationResult(testInput));

        assertEquals(ExecutionResult.OPERATION_FAILED, exception.getExecutionResult());
        assertTrue(exception.getMessage().contains("Failed to save operation result"));
    }

    @Test
    void saveIdempotentOperationResult_WhenCleanupTempLockFails_ShouldNotThrowException() {
        // Arrange
        testInput.setLockId(testTempLock.getId());
        testInput.setIdempotencyId(testTempLock.getIdempotencyId());
        testInput.setExecutionResult(ExecutionResult.SUCCESS.getValue());

        when(idempotentOperationLockTempRepository.findById(testInput.getLockId()))
                .thenReturn(Optional.of(testTempLock));
        when(storedIdempotentOperationResultRepository.insert(any(StoredIdempotentOperationResult.class)))
                .thenReturn(testStoredResult);
        when(idempotentOperationLockRepository.insert(any(IdempotentOperationLock.class)))
                .thenReturn(new IdempotentOperationLock());
        doThrow(new RuntimeException("Cleanup failed"))
                .when(idempotentOperationLockTempRepository).deleteById(testTempLock.getId());

        // Act & Assert
        assertDoesNotThrow(() -> idempotencyService.saveIdempotentOperationResult(testInput));

        verify(storedIdempotentOperationResultRepository).insert(any(StoredIdempotentOperationResult.class));
        verify(idempotentOperationLockRepository).insert(any(IdempotentOperationLock.class));
        verify(idempotentOperationLockTempRepository).deleteById(testTempLock.getId());
    }

    @Test
    void getStoredExecutionResultOrLockOperation_WhenNoExistingResult_ShouldReturnResponseEchoingInputFields() {
        // Arrange
        when(idempotentOperationRepository.insert(any(IdempotentOperation.class))).thenReturn(testOperation);
        when(storedIdempotentOperationResultRepository.findById(hashedKey("test-service", "test-operation", "test-key-123")))
                .thenReturn(Optional.empty());
        when(idempotentOperationConfigCache.getLockDuration("test-service", "test-operation"))
                .thenReturn(Duration.ofMinutes(5));

        // Act
        IdempotentOperationResult result = idempotencyService.getStoredExecutionResultOrLockOperation(testInput);

        // Assert
        assertEquals("test-service", result.getService());
        assertEquals("test-operation", result.getOperation());
        assertEquals("test-key-123", result.getIdempotencyKey());

        verify(idempotentOperationConfigCache).getLockDuration("test-service", "test-operation");
    }

    @Test
    void saveIdempotentOperationResult_WhenSuccessfulOperation_ShouldPersistHashedId() {
        // Arrange
        testInput.setLockId(testTempLock.getId());
        testInput.setIdempotencyId(testTempLock.getIdempotencyId());
        testInput.setExecutionResult(ExecutionResult.SUCCESS.getValue());
        testInput.setIdempotentOperationResult("payload");

        when(idempotentOperationLockTempRepository.findById(testInput.getLockId()))
                .thenReturn(Optional.of(testTempLock));
        when(idempotentOperationLockRepository.insert(any(IdempotentOperationLock.class)))
                .thenReturn(new IdempotentOperationLock());

        ArgumentCaptor<StoredIdempotentOperationResult> captor =
                ArgumentCaptor.forClass(StoredIdempotentOperationResult.class);

        // Act
        assertDoesNotThrow(() -> idempotencyService.saveIdempotentOperationResult(testInput));

        // Assert
        verify(storedIdempotentOperationResultRepository).insert(captor.capture());
        StoredIdempotentOperationResult saved = captor.getValue();

        assertEquals(hashedKey("test-service", "test-operation", "test-key-123"), saved.getId());
        assertEquals("test-service", saved.getService());
        assertEquals("test-operation", saved.getOperation());
        assertEquals("test-key-123", saved.getIdempotencyKey());
        assertEquals("payload", saved.getIdempotentOperationResult());
    }

    @Test
    void saveIdempotentOperationResult_WhenLockInsertFails_ShouldStillSaveResultAndNotThrow() {
        // Arrange
        testInput.setLockId(testTempLock.getId());
        testInput.setIdempotencyId(testTempLock.getIdempotencyId());
        testInput.setExecutionResult(ExecutionResult.SUCCESS.getValue());

        when(idempotentOperationLockTempRepository.findById(testInput.getLockId()))
                .thenReturn(Optional.of(testTempLock));
        doThrow(new RuntimeException("lock insert failed"))
                .when(idempotentOperationLockRepository).insert(any(IdempotentOperationLock.class));

        // Act & Assert
        assertDoesNotThrow(() -> idempotencyService.saveIdempotentOperationResult(testInput));
        verify(storedIdempotentOperationResultRepository).insert(any(StoredIdempotentOperationResult.class));
    }

    @Test
    void saveIdempotentOperationResult_WhenExecutionResultIsNotSuccess_AndFailedRepoThrows_ShouldNotThrow() {
        // Arrange
        testInput.setLockId(testTempLock.getId());
        testInput.setIdempotencyId(testTempLock.getIdempotencyId());
        testInput.setExecutionResult(ExecutionResult.OPERATION_FAILED.getValue());
        testInput.setIdempotentOperationResult("some error");

        when(idempotentOperationLockTempRepository.findById(testInput.getLockId()))
                .thenReturn(Optional.of(testTempLock));
        when(idempotentOperationLockRepository.insert(any(IdempotentOperationLock.class)))
                .thenReturn(new IdempotentOperationLock());
        doThrow(new RuntimeException("failed result insert blew up"))
                .when(failedIdempotentOperationResultRepository).insert(any(FailedIdempotentOperationResult.class));

        // Act & Assert (saveFailedResult swallows exceptions)
        assertDoesNotThrow(() -> idempotencyService.saveIdempotentOperationResult(testInput));

        verify(failedIdempotentOperationResultRepository).insert(any(FailedIdempotentOperationResult.class));
        verifyNoInteractions(storedIdempotentOperationResultRepository);
    }

    @Test
    void cleanLockedOperations_WhenExpiredTempLocksFound_ShouldDeleteAndSaveExpiredFailure() {
        // Arrange
        IdempotentOperationLockTemp lock1 = new IdempotentOperationLockTemp();
        lock1.setId(UUID.randomUUID().toString());
        lock1.setIdempotencyId(UUID.randomUUID().toString());

        IdempotentOperationLockTemp lock2 = new IdempotentOperationLockTemp();
        lock2.setId(UUID.randomUUID().toString());
        lock2.setIdempotencyId(UUID.randomUUID().toString());

        when(idempotentOperationLockTempRepository.findByExpiredAtIsAfter(any(Instant.class)))
                .thenReturn(List.of(lock1, lock2));

        // Act
        assertDoesNotThrow(() -> idempotencyService.cleanLockedOperations());

        // Assert
        verify(idempotentOperationLockTempRepository).deleteById(lock1.getId());
        verify(idempotentOperationLockTempRepository).deleteById(lock2.getId());
        verify(failedIdempotentOperationResultRepository, times(2)).insert(any(FailedIdempotentOperationResult.class));
    }

    @Test
    void cleanLockedOperations_WhenFailedResultInsertThrows_ShouldStillDeleteLocksAndNotThrow() {
        // Arrange
        IdempotentOperationLockTemp lock = new IdempotentOperationLockTemp();
        lock.setId(UUID.randomUUID().toString());
        lock.setIdempotencyId(UUID.randomUUID().toString());

        when(idempotentOperationLockTempRepository.findByExpiredAtIsAfter(any(Instant.class)))
                .thenReturn(List.of(lock));
        doThrow(new RuntimeException("insert failed"))
                .when(failedIdempotentOperationResultRepository).insert(any(FailedIdempotentOperationResult.class));

        // Act & Assert
        assertDoesNotThrow(() -> idempotencyService.cleanLockedOperations());

        // Assert
        verify(idempotentOperationLockTempRepository).deleteById(lock.getId());
        verify(failedIdempotentOperationResultRepository).insert(any(FailedIdempotentOperationResult.class));
    }
}