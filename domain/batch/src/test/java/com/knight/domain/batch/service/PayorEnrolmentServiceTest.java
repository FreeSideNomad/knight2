package com.knight.domain.batch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knight.domain.batch.aggregate.Batch;
import com.knight.domain.batch.repository.BatchRepository;
import com.knight.domain.batch.types.*;
import com.knight.platform.sharedkernel.BatchId;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.SrfClientId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayorEnrolmentService Tests")
class PayorEnrolmentServiceTest {

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private PayorEnrolmentProcessor processor;

    private PayorEnrolmentService service;
    private ObjectMapper objectMapper;

    @Captor
    private ArgumentCaptor<Batch> batchCaptor;

    private static final ProfileId TEST_PROFILE_ID = ProfileId.of("servicing", new SrfClientId("123456789"));
    private static final String TEST_USER = "test-user@example.com";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new PayorEnrolmentService(batchRepository, processor, objectMapper);
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("validate() should accept valid payor with one admin")
        void validateValidPayor() {
            // Given
            String json = """
                {
                    "payors": [
                        {
                            "businessName": "Acme Corp",
                            "externalReference": "EXT-001",
                            "persons": [
                                {
                                    "name": "John Doe",
                                    "email": "john@acme.com",
                                    "role": "ADMIN",
                                    "phone": "555-1234"
                                }
                            ]
                        }
                    ]
                }
                """;

            when(processor.existsByBusinessName(any(), any())).thenReturn(false);
            when(batchRepository.save(any(Batch.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isTrue();
            assertThat(result.payorCount()).isEqualTo(1);
            assertThat(result.errors()).isEmpty();
            assertThat(result.batchId()).isNotNull();

            verify(batchRepository).save(any(Batch.class));
        }

        @Test
        @DisplayName("validate() should accept valid payors with direct array format")
        void validateValidPayorsArrayFormat() {
            // Given
            String json = """
                [
                    {
                        "businessName": "Acme Corp",
                        "persons": [
                            {
                                "name": "John Doe",
                                "email": "john@acme.com",
                                "role": "ADMIN"
                            }
                        ]
                    },
                    {
                        "businessName": "Beta Inc",
                        "persons": [
                            {
                                "name": "Jane Smith",
                                "email": "jane@beta.com",
                                "role": "ADMIN"
                            }
                        ]
                    }
                ]
                """;

            when(processor.existsByBusinessName(any(), any())).thenReturn(false);
            when(batchRepository.save(any(Batch.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isTrue();
            assertThat(result.payorCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("validate() should reject invalid JSON")
        void validateInvalidJson() {
            // Given
            String invalidJson = "{ invalid json }";

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, invalidJson, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).field()).isEqualTo("json");
            assertThat(result.errors().get(0).message()).contains("Invalid JSON format");

            verifyNoInteractions(batchRepository);
        }

        @Test
        @DisplayName("validate() should reject empty payors array")
        void validateEmptyPayors() {
            // Given
            String json = """
                {
                    "payors": []
                }
                """;

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).field()).isEqualTo("payors");
            assertThat(result.errors().get(0).message()).isEqualTo("No payors found in file");
        }

        @Test
        @DisplayName("validate() should reject too many payors")
        void validateTooManyPayors() {
            // Given - Create a JSON with 501 payors
            StringBuilder jsonBuilder = new StringBuilder("[");
            for (int i = 0; i < 501; i++) {
                if (i > 0) jsonBuilder.append(",");
                jsonBuilder.append(String.format("""
                    {
                        "businessName": "Company %d",
                        "persons": [
                            {
                                "name": "Person %d",
                                "email": "person%d@company.com",
                                "role": "ADMIN"
                            }
                        ]
                    }
                    """, i, i, i));
            }
            jsonBuilder.append("]");

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, jsonBuilder.toString(), TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.payorCount()).isEqualTo(501);
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).message()).contains("Too many payors. Maximum is 500");
        }

        @Test
        @DisplayName("validate() should reject missing business name")
        void validateMissingBusinessName() {
            // Given
            String json = """
                [
                    {
                        "persons": [
                            {
                                "name": "John Doe",
                                "email": "john@example.com",
                                "role": "ADMIN"
                            }
                        ]
                    }
                ]
                """;

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("businessName");
                assertThat(error.message()).isEqualTo("Business name is required");
            });
        }

        @Test
        @DisplayName("validate() should reject business name exceeding 255 characters")
        void validateBusinessNameTooLong() {
            // Given
            String longName = "A".repeat(256);
            String json = String.format("""
                [
                    {
                        "businessName": "%s",
                        "persons": [
                            {
                                "name": "John Doe",
                                "email": "john@example.com",
                                "role": "ADMIN"
                            }
                        ]
                    }
                ]
                """, longName);

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("businessName");
                assertThat(error.message()).isEqualTo("Business name exceeds 255 characters");
            });
        }

        @Test
        @DisplayName("validate() should reject duplicate business name in file")
        void validateDuplicateBusinessNameInFile() {
            // Given
            String json = """
                [
                    {
                        "businessName": "Acme Corp",
                        "persons": [
                            {
                                "name": "John Doe",
                                "email": "john@acme.com",
                                "role": "ADMIN"
                            }
                        ]
                    },
                    {
                        "businessName": "Acme Corp",
                        "persons": [
                            {
                                "name": "Jane Smith",
                                "email": "jane@acme.com",
                                "role": "ADMIN"
                            }
                        ]
                    }
                ]
                """;

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("businessName");
                assertThat(error.message()).isEqualTo("Duplicate business name in file");
            });
        }

        @Test
        @DisplayName("validate() should reject business name already in database")
        void validateBusinessNameExistsInDatabase() {
            // Given
            String json = """
                [
                    {
                        "businessName": "Existing Corp",
                        "persons": [
                            {
                                "name": "John Doe",
                                "email": "john@existing.com",
                                "role": "ADMIN"
                            }
                        ]
                    }
                ]
                """;

            when(processor.existsByBusinessName(TEST_PROFILE_ID, "Existing Corp")).thenReturn(true);

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("businessName");
                assertThat(error.message()).isEqualTo("Business name already exists");
            });
        }

        @Test
        @DisplayName("validate() should reject payor with no persons")
        void validateNoPersons() {
            // Given
            String json = """
                [
                    {
                        "businessName": "Acme Corp",
                        "persons": []
                    }
                ]
                """;

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("persons");
                assertThat(error.message()).isEqualTo("At least one person is required");
            });
        }

        @Test
        @DisplayName("validate() should reject payor without ADMIN person")
        void validateNoAdminPerson() {
            // Given
            String json = """
                [
                    {
                        "businessName": "Acme Corp",
                        "persons": [
                            {
                                "name": "John Doe",
                                "email": "john@acme.com",
                                "role": "CONTACT"
                            }
                        ]
                    }
                ]
                """;

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("persons");
                assertThat(error.message()).isEqualTo("At least one ADMIN person is required");
            });
        }

        @Test
        @DisplayName("validate() should reject person with missing name")
        void validatePersonMissingName() {
            // Given
            String json = """
                [
                    {
                        "businessName": "Acme Corp",
                        "persons": [
                            {
                                "email": "john@acme.com",
                                "role": "ADMIN"
                            }
                        ]
                    }
                ]
                """;

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("persons[0].name");
                assertThat(error.message()).isEqualTo("Name is required");
            });
        }

        @Test
        @DisplayName("validate() should reject person with name exceeding 100 characters")
        void validatePersonNameTooLong() {
            // Given
            String longName = "A".repeat(101);
            String json = String.format("""
                [
                    {
                        "businessName": "Acme Corp",
                        "persons": [
                            {
                                "name": "%s",
                                "email": "john@acme.com",
                                "role": "ADMIN"
                            }
                        ]
                    }
                ]
                """, longName);

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("persons[0].name");
                assertThat(error.message()).isEqualTo("Name exceeds 100 characters");
            });
        }

        @Test
        @DisplayName("validate() should reject person with missing email")
        void validatePersonMissingEmail() {
            // Given
            String json = """
                [
                    {
                        "businessName": "Acme Corp",
                        "persons": [
                            {
                                "name": "John Doe",
                                "role": "ADMIN"
                            }
                        ]
                    }
                ]
                """;

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("persons[0].email");
                assertThat(error.message()).isEqualTo("Email is required");
            });
        }

        @Test
        @DisplayName("validate() should reject person with invalid email format")
        void validatePersonInvalidEmail() {
            // Given
            String json = """
                [
                    {
                        "businessName": "Acme Corp",
                        "persons": [
                            {
                                "name": "John Doe",
                                "email": "not-an-email",
                                "role": "ADMIN"
                            }
                        ]
                    }
                ]
                """;

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("persons[0].email");
                assertThat(error.message()).isEqualTo("Invalid email format");
            });
        }

        @Test
        @DisplayName("validate() should reject duplicate email in file")
        void validateDuplicateEmailInFile() {
            // Given
            String json = """
                [
                    {
                        "businessName": "Acme Corp",
                        "persons": [
                            {
                                "name": "John Doe",
                                "email": "john@acme.com",
                                "role": "ADMIN"
                            }
                        ]
                    },
                    {
                        "businessName": "Beta Inc",
                        "persons": [
                            {
                                "name": "Jane Doe",
                                "email": "john@acme.com",
                                "role": "ADMIN"
                            }
                        ]
                    }
                ]
                """;

            when(processor.existsByBusinessName(any(), any())).thenReturn(false);

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("persons[0].email");
                assertThat(error.message()).isEqualTo("Duplicate email in file");
            });
        }

        @Test
        @DisplayName("validate() should reject person with missing role")
        void validatePersonMissingRole() {
            // Given
            String json = """
                [
                    {
                        "businessName": "Acme Corp",
                        "persons": [
                            {
                                "name": "John Doe",
                                "email": "john@acme.com"
                            }
                        ]
                    }
                ]
                """;

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("persons[0].role");
                assertThat(error.message()).isEqualTo("Role is required");
            });
        }

        @Test
        @DisplayName("validate() should reject person with invalid role")
        void validatePersonInvalidRole() {
            // Given
            String json = """
                [
                    {
                        "businessName": "Acme Corp",
                        "persons": [
                            {
                                "name": "John Doe",
                                "email": "john@acme.com",
                                "role": "INVALID"
                            }
                        ]
                    }
                ]
                """;

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("persons[0].role");
                assertThat(error.message()).isEqualTo("Role must be ADMIN or CONTACT");
            });
        }

        @Test
        @DisplayName("validate() should reject phone exceeding 50 characters")
        void validatePhoneTooLong() {
            // Given
            String longPhone = "1".repeat(51);
            String json = String.format("""
                [
                    {
                        "businessName": "Acme Corp",
                        "persons": [
                            {
                                "name": "John Doe",
                                "email": "john@acme.com",
                                "role": "ADMIN",
                                "phone": "%s"
                            }
                        ]
                    }
                ]
                """, longPhone);

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("persons[0].phone");
                assertThat(error.message()).isEqualTo("Phone exceeds 50 characters");
            });
        }

        @Test
        @DisplayName("validate() should accept payor with multiple persons including ADMIN")
        void validateMultiplePersonsWithAdmin() {
            // Given
            String json = """
                [
                    {
                        "businessName": "Acme Corp",
                        "persons": [
                            {
                                "name": "John Doe",
                                "email": "john@acme.com",
                                "role": "ADMIN"
                            },
                            {
                                "name": "Jane Smith",
                                "email": "jane@acme.com",
                                "role": "CONTACT"
                            }
                        ]
                    }
                ]
                """;

            when(processor.existsByBusinessName(any(), any())).thenReturn(false);
            when(batchRepository.save(any(Batch.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            ValidationResult result = service.validate(TEST_PROFILE_ID, json, TEST_USER);

            // Then
            assertThat(result.valid()).isTrue();
            assertThat(result.payorCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Execution Tests")
    class ExecutionTests {

        @Test
        @DisplayName("execute() should process all items successfully")
        void executeSuccessfully() throws Exception {
            // Given
            BatchId batchId = BatchId.generate();
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("{\"businessName\":\"Acme\",\"persons\":[{\"name\":\"John\",\"email\":\"john@acme.com\",\"role\":\"ADMIN\"}]}");
            batch.addItem("{\"businessName\":\"Beta\",\"persons\":[{\"name\":\"Jane\",\"email\":\"jane@beta.com\",\"role\":\"ADMIN\"}]}");

            // Use reconstitute to set the batch ID
            Batch reconstitutedBatch = Batch.reconstitute(
                    batchId,
                    batch.type(),
                    batch.sourceProfileId(),
                    batch.status(),
                    batch.totalItems(),
                    batch.successCount(),
                    batch.failedCount(),
                    new java.util.ArrayList<>(batch.items()),
                    batch.createdAt(),
                    batch.createdBy(),
                    batch.startedAt(),
                    batch.completedAt()
            );

            when(batchRepository.findById(batchId)).thenReturn(Optional.of(reconstitutedBatch));
            when(batchRepository.save(any(Batch.class))).thenAnswer(inv -> inv.getArgument(0));

            BatchItemResult result1 = new BatchItemResult("client-1", "profile-1", List.of("user-1"));
            BatchItemResult result2 = new BatchItemResult("client-2", "profile-2", List.of("user-2"));

            when(processor.processPayor(eq(TEST_PROFILE_ID), any(), eq(TEST_USER)))
                    .thenReturn(result1, result2);

            // When
            service.execute(batchId);

            // Then
            verify(batchRepository, atLeast(2)).save(batchCaptor.capture());
            Batch finalBatch = batchCaptor.getValue();

            assertThat(finalBatch.status()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(finalBatch.successCount()).isEqualTo(2);
            assertThat(finalBatch.failedCount()).isZero();
            assertThat(finalBatch.completedAt()).isNotNull();
        }

        @Test
        @DisplayName("execute() should handle partial failures")
        void executeWithPartialFailure() throws Exception {
            // Given
            BatchId batchId = BatchId.generate();
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("{\"businessName\":\"Acme\",\"persons\":[{\"name\":\"John\",\"email\":\"john@acme.com\",\"role\":\"ADMIN\"}]}");
            batch.addItem("{\"businessName\":\"Beta\",\"persons\":[{\"name\":\"Jane\",\"email\":\"jane@beta.com\",\"role\":\"ADMIN\"}]}");

            Batch reconstitutedBatch = Batch.reconstitute(
                    batchId,
                    batch.type(),
                    batch.sourceProfileId(),
                    batch.status(),
                    batch.totalItems(),
                    batch.successCount(),
                    batch.failedCount(),
                    new java.util.ArrayList<>(batch.items()),
                    batch.createdAt(),
                    batch.createdBy(),
                    batch.startedAt(),
                    batch.completedAt()
            );

            when(batchRepository.findById(batchId)).thenReturn(Optional.of(reconstitutedBatch));
            when(batchRepository.save(any(Batch.class))).thenAnswer(inv -> inv.getArgument(0));

            BatchItemResult result1 = new BatchItemResult("client-1", "profile-1", List.of("user-1"));

            when(processor.processPayor(eq(TEST_PROFILE_ID), any(), eq(TEST_USER)))
                    .thenReturn(result1)
                    .thenThrow(new RuntimeException("Processing failed"));

            // When
            service.execute(batchId);

            // Then
            verify(batchRepository, atLeast(2)).save(batchCaptor.capture());
            Batch finalBatch = batchCaptor.getValue();

            assertThat(finalBatch.status()).isEqualTo(BatchStatus.COMPLETED_WITH_ERRORS);
            assertThat(finalBatch.successCount()).isEqualTo(1);
            assertThat(finalBatch.failedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("execute() should fail all items when processing fails")
        void executeWithAllFailures() throws Exception {
            // Given
            BatchId batchId = BatchId.generate();
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("{\"businessName\":\"Acme\",\"persons\":[{\"name\":\"John\",\"email\":\"john@acme.com\",\"role\":\"ADMIN\"}]}");

            Batch reconstitutedBatch = Batch.reconstitute(
                    batchId,
                    batch.type(),
                    batch.sourceProfileId(),
                    batch.status(),
                    batch.totalItems(),
                    batch.successCount(),
                    batch.failedCount(),
                    new java.util.ArrayList<>(batch.items()),
                    batch.createdAt(),
                    batch.createdBy(),
                    batch.startedAt(),
                    batch.completedAt()
            );

            when(batchRepository.findById(batchId)).thenReturn(Optional.of(reconstitutedBatch));
            when(batchRepository.save(any(Batch.class))).thenAnswer(inv -> inv.getArgument(0));

            when(processor.processPayor(any(), any(), any()))
                    .thenThrow(new RuntimeException("Processing failed"));

            // When
            service.execute(batchId);

            // Then
            verify(batchRepository, atLeast(2)).save(batchCaptor.capture());
            Batch finalBatch = batchCaptor.getValue();

            assertThat(finalBatch.status()).isEqualTo(BatchStatus.FAILED);
            assertThat(finalBatch.successCount()).isZero();
            assertThat(finalBatch.failedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("execute() should throw exception when batch not found")
        void executeThrowsWhenBatchNotFound() {
            // Given
            BatchId batchId = BatchId.generate();
            when(batchRepository.findById(batchId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.execute(batchId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Batch not found");
        }
    }

    @Nested
    @DisplayName("Query Tests")
    class QueryTests {

        @Test
        @DisplayName("getBatch() should return batch when found")
        void getBatchFound() {
            // Given
            BatchId batchId = BatchId.generate();
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

            // When
            Optional<Batch> result = service.getBatch(batchId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(batch);
        }

        @Test
        @DisplayName("getBatch() should return empty when not found")
        void getBatchNotFound() {
            // Given
            BatchId batchId = BatchId.generate();
            when(batchRepository.findById(batchId)).thenReturn(Optional.empty());

            // When
            Optional<Batch> result = service.getBatch(batchId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("listBatchesByProfile() should return batches for profile")
        void listBatchesByProfile() {
            // Given
            Batch batch1 = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            Batch batch2 = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            when(batchRepository.findBySourceProfileId(TEST_PROFILE_ID))
                    .thenReturn(List.of(batch1, batch2));

            // When
            List<Batch> result = service.listBatchesByProfile(TEST_PROFILE_ID);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(batch1, batch2);
        }
    }
}
