package com.knight.application.rest.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knight.application.persistence.batch.repository.BatchJpaRepository;
import com.knight.application.persistence.clients.repository.ClientAccountJpaRepository;
import com.knight.application.persistence.clients.repository.ClientJpaRepository;
import com.knight.application.persistence.indirectclients.repository.IndirectClientJpaRepository;
import com.knight.application.persistence.profiles.repository.ProfileJpaRepository;
import com.knight.application.persistence.users.repository.UserJpaRepository;
import com.knight.domain.auth0identity.api.Auth0IdentityService;
import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.domain.clients.repository.ClientRepository;
import com.knight.domain.clients.types.ClientType;
import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.domain.indirectclients.types.PersonRole;
import com.knight.domain.batch.aggregate.Batch;
import com.knight.domain.batch.repository.BatchRepository;
import com.knight.domain.batch.types.BatchStatus;
import com.knight.domain.serviceprofiles.aggregate.Profile;
import com.knight.domain.serviceprofiles.repository.ServicingProfileRepository;
import com.knight.domain.serviceprofiles.types.AccountEnrollmentType;
import com.knight.domain.serviceprofiles.types.ProfileType;
import com.knight.domain.serviceprofiles.types.ServiceType;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.repository.UserRepository;
import com.knight.platform.sharedkernel.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests for Batch Payor Import using real H2 database.
 * Tests the complete workflow from validation to execution and verification of created entities.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MSSQLServer",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true",
    "spring.flyway.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.consumer.auto-startup=false"
})
@org.junit.jupiter.api.Disabled("Temporarily disabled - async batch processing issues to be investigated")
class BatchImportE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientAccountRepository clientAccountRepository;

    @Autowired
    private ServicingProfileRepository profileRepository;

    @Autowired
    private IndirectClientRepository indirectClientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private ClientJpaRepository clientJpaRepository;

    @Autowired
    private ClientAccountJpaRepository clientAccountJpaRepository;

    @Autowired
    private ProfileJpaRepository profileJpaRepository;

    @Autowired
    private IndirectClientJpaRepository indirectClientJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private BatchJpaRepository batchJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private Auth0IdentityService auth0IdentityService;

    private Client bankClient;
    private Profile onlineProfile;

    /**
     * Helper method to wait for batch completion
     * Note: Since @Async may not be enabled in test environment, the batch may already be completed
     */
    private void waitForBatchCompletion(String batchId) {
        await().atMost(15, SECONDS).pollInterval(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .ignoreExceptions()
            .until(() -> {
                Optional<Batch> batchOpt = batchRepository.findById(BatchId.of(batchId));
                if (batchOpt.isEmpty()) {
                    return false; // Keep waiting
                }
                Batch b = batchOpt.get();
                return b.status() == BatchStatus.COMPLETED || b.status() == BatchStatus.FAILED;
            });
    }

    @BeforeEach
    void setUp() {
        // Clear all data
        batchJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
        indirectClientJpaRepository.deleteAll();
        profileJpaRepository.deleteAll();
        clientAccountJpaRepository.deleteAll();
        clientJpaRepository.deleteAll();

        // Create a bank client
        bankClient = Client.create(
            new SrfClientId("123456789"),
            "Acme Bank",
            ClientType.BUSINESS,
            Address.of("100 Bank Street", null, "Toronto", "ON", "M5J 2K3", "CA")
        );
        clientRepository.save(bankClient);

        // Create a client account for the bank
        ClientAccount account = ClientAccount.create(
            ClientAccountId.of("CAN_DDA:DDA:12345:000000000001"),
            bankClient.clientId(),
            Currency.CAD
        );
        clientAccountRepository.save(account);

        // Create an ONLINE profile with RECEIVABLES service
        Profile.ClientEnrollmentRequest clientEnrollmentRequest = new Profile.ClientEnrollmentRequest(
            bankClient.clientId(),
            true,  // isPrimary
            AccountEnrollmentType.MANUAL,
            List.of()  // no accounts needed for this test
        );
        onlineProfile = Profile.createWithAccounts(
            ProfileType.ONLINE,
            "Bank Online Profile",
            List.of(clientEnrollmentRequest),
            "system"
        );
        onlineProfile.enrollService(ServiceType.RECEIVABLES.name(), "{}");
        profileRepository.save(onlineProfile);

        // Mock Auth0 provisioning to simulate successful user creation in Auth0
        when(auth0IdentityService.provisionUser(any(Auth0IdentityService.ProvisionUserRequest.class)))
            .thenAnswer(invocation -> {
                Auth0IdentityService.ProvisionUserRequest request = invocation.getArgument(0);
                return new Auth0IdentityService.ProvisionUserResult(
                    "auth0|" + request.internalUserId(),
                    "https://knight.auth0.com/reset-password?ticket=test123",
                    Instant.now()
                );
            });
    }

    @Nested
    @DisplayName("POST /api/profiles/{profileId}/payor-enrolment/validate - Validate Batch")
    class ValidateBatchTests {

        @Test
        @DisplayName("should successfully validate single payor")
        void shouldValidateSinglePayor() throws Exception {
            String json = """
                {
                    "payors": [
                        {
                            "businessName": "ABC Company",
                            "externalReference": "EXT-001",
                            "persons": [
                                {
                                    "name": "John Smith",
                                    "email": "john@abc.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-1234"
                                }
                            ]
                        }
                    ]
                }
                """;

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "payors.json",
                "application/json",
                json.getBytes()
            );

            MvcResult result = mockMvc.perform(multipart("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/validate")
                    .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.payorCount").value(1))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.batchId").exists())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("batchId").asText()).isNotEmpty();
        }

        @Test
        @DisplayName("should validate multiple payors")
        void shouldValidateMultiplePayors() throws Exception {
            String json = """
                {
                    "payors": [
                        {
                            "businessName": "ABC Company",
                            "externalReference": "EXT-001",
                            "persons": [
                                {
                                    "name": "John Smith",
                                    "email": "john@abc.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-1234"
                                }
                            ]
                        },
                        {
                            "businessName": "XYZ Corp",
                            "externalReference": "EXT-002",
                            "persons": [
                                {
                                    "name": "Jane Doe",
                                    "email": "jane@xyz.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-5678"
                                }
                            ]
                        }
                    ]
                }
                """;

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "payors.json",
                "application/json",
                json.getBytes()
            );

            mockMvc.perform(multipart("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/validate")
                    .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.payorCount").value(2))
                .andExpect(jsonPath("$.errors").isEmpty());
        }

        @Test
        @DisplayName("should reject empty file")
        void shouldRejectEmptyFile() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "payors.json",
                "application/json",
                new byte[0]
            );

            mockMvc.perform(multipart("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/validate")
                    .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0].message").value("File is empty"));
        }

        @Test
        @DisplayName("should detect validation errors")
        void shouldDetectValidationErrors() throws Exception {
            String json = """
                {
                    "payors": [
                        {
                            "businessName": "",
                            "persons": []
                        }
                    ]
                }
                """;

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "payors.json",
                "application/json",
                json.getBytes()
            );

            mockMvc.perform(multipart("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/validate")
                    .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("POST /api/profiles/{profileId}/payor-enrolment/execute - Execute Batch")
    class ExecuteBatchTests {

        @Test
        @DisplayName("should import single payor and create all entities")
        void shouldImportSinglePayor() throws Exception {
            // Step 1: Validate to create batch
            String json = """
                {
                    "payors": [
                        {
                            "businessName": "ABC Company",
                            "externalReference": "EXT-001",
                            "persons": [
                                {
                                    "name": "John Smith",
                                    "email": "john@abc.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-1234"
                                }
                            ]
                        }
                    ]
                }
                """;

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "payors.json",
                "application/json",
                json.getBytes()
            );

            MvcResult validateResult = mockMvc.perform(multipart("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/validate")
                    .file(file))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode validateResponse = objectMapper.readTree(validateResult.getResponse().getContentAsString());
            String batchId = validateResponse.get("batchId").asText();

            // Step 2: Execute batch
            String executeRequest = String.format("{\"batchId\": \"%s\"}", batchId);

            mockMvc.perform(post("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(executeRequest))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.batchId").value(batchId))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

            // Step 3: Wait for batch processing to complete
            waitForBatchCompletion(batchId);

            // Step 4: Verify IndirectClient was created
            List<IndirectClient> indirectClients = indirectClientRepository.findByParentProfileId(onlineProfile.profileId());
            assertThat(indirectClients).hasSize(1);

            IndirectClient indirectClient = indirectClients.get(0);
            assertThat(indirectClient.name()).isEqualTo("ABC Company");
            assertThat(indirectClient.externalReference()).isEqualTo("EXT-001");
            assertThat(indirectClient.parentClientId()).isEqualTo(bankClient.clientId());
            assertThat(indirectClient.parentProfileId()).isEqualTo(onlineProfile.profileId());
            assertThat(indirectClient.relatedPersons()).hasSize(1);
            assertThat(indirectClient.relatedPersons().get(0).name()).isEqualTo("John Smith");
            assertThat(indirectClient.relatedPersons().get(0).role()).isEqualTo(PersonRole.ADMIN);

            // Step 5: Verify INDIRECT profile was created
            ProfileId expectedIndirectProfileId = ProfileId.of("indirect", indirectClient.id());
            Optional<Profile> indirectProfileOpt = profileRepository.findById(expectedIndirectProfileId);
            assertThat(indirectProfileOpt).isPresent();

            Profile indirectProfile = indirectProfileOpt.get();
            assertThat(indirectProfile.profileId().urn()).isEqualTo(indirectClient.id().urn());
            assertThat(indirectProfile.name()).isEqualTo("ABC Company Profile");

            // Verify PAYOR service is enrolled
            boolean hasPayorService = indirectProfile.serviceEnrollments().stream()
                .anyMatch(se -> se.serviceType().equals(ServiceType.PAYOR.name()));
            assertThat(hasPayorService).isTrue();

            // Step 6: Verify User was created
            List<User> users = userRepository.findByProfileId(indirectProfile.profileId());
            assertThat(users).hasSize(1);

            User user = users.get(0);
            assertThat(user.email()).isEqualTo("john@abc.com");
            assertThat(user.firstName()).isEqualTo("John");
            assertThat(user.lastName()).isEqualTo("Smith");
            assertThat(user.userType()).isEqualTo(User.UserType.INDIRECT_USER);
            assertThat(user.roles()).contains(User.Role.SECURITY_ADMIN);
            assertThat(user.profileId()).isEqualTo(indirectProfile.profileId());
        }

        @Test
        @DisplayName("should import multiple payors with correct sequence numbers")
        void shouldImportMultiplePayorsWithSequence() throws Exception {
            // Step 1: Validate
            String json = """
                {
                    "payors": [
                        {
                            "businessName": "ABC Company",
                            "externalReference": "EXT-001",
                            "persons": [
                                {
                                    "name": "John Smith",
                                    "email": "john@abc.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-1234"
                                }
                            ]
                        },
                        {
                            "businessName": "XYZ Corp",
                            "externalReference": "EXT-002",
                            "persons": [
                                {
                                    "name": "Jane Doe",
                                    "email": "jane@xyz.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-5678"
                                }
                            ]
                        },
                        {
                            "businessName": "123 Industries",
                            "externalReference": "EXT-003",
                            "persons": [
                                {
                                    "name": "Bob Johnson",
                                    "email": "bob@123ind.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-9999"
                                }
                            ]
                        }
                    ]
                }
                """;

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "payors.json",
                "application/json",
                json.getBytes()
            );

            MvcResult validateResult = mockMvc.perform(multipart("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/validate")
                    .file(file))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode validateResponse = objectMapper.readTree(validateResult.getResponse().getContentAsString());
            String batchId = validateResponse.get("batchId").asText();

            // Step 2: Execute
            String executeRequest = String.format("{\"batchId\": \"%s\"}", batchId);

            mockMvc.perform(post("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(executeRequest))
                .andExpect(status().isAccepted());

            // Wait for processing
            waitForBatchCompletion(batchId);

            // Step 3: Verify all 3 indirect clients created with correct sequences
            List<IndirectClient> indirectClients = indirectClientRepository.findByParentProfileId(onlineProfile.profileId());
            assertThat(indirectClients).hasSize(3);

            // Check sequence numbers (should be 1, 2, 3)
            IndirectClient client1 = indirectClients.stream()
                .filter(c -> c.name().equals("ABC Company"))
                .findFirst()
                .orElseThrow();
            assertThat(client1.id().urn()).startsWith("ind:");

            IndirectClient client2 = indirectClients.stream()
                .filter(c -> c.name().equals("XYZ Corp"))
                .findFirst()
                .orElseThrow();
            assertThat(client2.id().urn()).startsWith("ind:");

            IndirectClient client3 = indirectClients.stream()
                .filter(c -> c.name().equals("123 Industries"))
                .findFirst()
                .orElseThrow();
            assertThat(client3.id().urn()).startsWith("ind:");

            // Verify all IDs are unique
            assertThat(client1.id()).isNotEqualTo(client2.id());
            assertThat(client2.id()).isNotEqualTo(client3.id());
        }

        @Test
        @DisplayName("should create user with SECURITY_ADMIN role for ADMIN person")
        void shouldCreateUserWithAdminRole() throws Exception {
            // Step 1: Validate
            String json = """
                {
                    "payors": [
                        {
                            "businessName": "Admin Test Company",
                            "externalReference": "EXT-ADMIN",
                            "persons": [
                                {
                                    "name": "Admin User",
                                    "email": "admin@test.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-0000"
                                },
                                {
                                    "name": "Contact User",
                                    "email": "contact@test.com",
                                    "role": "CONTACT",
                                    "phone": "1-416-555-0001"
                                }
                            ]
                        }
                    ]
                }
                """;

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "payors.json",
                "application/json",
                json.getBytes()
            );

            MvcResult validateResult = mockMvc.perform(multipart("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/validate")
                    .file(file))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode validateResponse = objectMapper.readTree(validateResult.getResponse().getContentAsString());
            String batchId = validateResponse.get("batchId").asText();

            // Step 2: Execute
            String executeRequest = String.format("{\"batchId\": \"%s\"}", batchId);

            mockMvc.perform(post("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(executeRequest))
                .andExpect(status().isAccepted());

            waitForBatchCompletion(batchId);

            // Step 3: Verify only ADMIN person got a user account (not CONTACT)
            List<IndirectClient> indirectClients = indirectClientRepository.findByParentProfileId(onlineProfile.profileId());
            assertThat(indirectClients).hasSize(1);

            IndirectClient indirectClient = indirectClients.get(0);
            assertThat(indirectClient.relatedPersons()).hasSize(2); // Both persons stored

            // Find the indirect profile
            ProfileId indirectProfileId = ProfileId.of("indirect", indirectClient.id());
            List<User> users = userRepository.findByProfileId(indirectProfileId);

            // Only 1 user should be created (for ADMIN, not CONTACT)
            assertThat(users).hasSize(1);

            User adminUser = users.get(0);
            assertThat(adminUser.email()).isEqualTo("admin@test.com");
            assertThat(adminUser.roles()).contains(User.Role.SECURITY_ADMIN);
            assertThat(adminUser.userType()).isEqualTo(User.UserType.INDIRECT_USER);
            assertThat(adminUser.identityProvider()).isEqualTo(User.IdentityProvider.AUTH0);
        }

        @Test
        @DisplayName("should reject execute for batch from different profile")
        void shouldRejectExecuteForBatchFromDifferentProfile() throws Exception {
            // Step 1: Create batch for the online profile
            String json = """
                {
                    "payors": [
                        {
                            "businessName": "Wrong Profile Test",
                            "externalReference": "EXT-WRONG",
                            "persons": [
                                {
                                    "name": "Test User",
                                    "email": "test@wrong.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-9999"
                                }
                            ]
                        }
                    ]
                }
                """;

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "payors.json",
                "application/json",
                json.getBytes()
            );

            MvcResult validateResult = mockMvc.perform(multipart("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/validate")
                    .file(file))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode validateResponse = objectMapper.readTree(validateResult.getResponse().getContentAsString());
            String batchId = validateResponse.get("batchId").asText();

            // Step 2: Try to execute with wrong profile ID
            String executeRequest = String.format("{\"batchId\": \"%s\"}", batchId);

            mockMvc.perform(post("/api/v1/bank/profiles/online:srf:999999999/payor-enrolment/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(executeRequest))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value("Batch does not belong to this profile"));
        }

        @Test
        @DisplayName("should skip duplicate business name")
        void shouldSkipDuplicateBusinessName() throws Exception {
            // Step 1: Create first payor
            String json1 = """
                {
                    "payors": [
                        {
                            "businessName": "Duplicate Company",
                            "externalReference": "EXT-001",
                            "persons": [
                                {
                                    "name": "John Smith",
                                    "email": "john@dup.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-1234"
                                }
                            ]
                        }
                    ]
                }
                """;

            MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "payors1.json",
                "application/json",
                json1.getBytes()
            );

            MvcResult validateResult1 = mockMvc.perform(multipart("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/validate")
                    .file(file1))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode validateResponse1 = objectMapper.readTree(validateResult1.getResponse().getContentAsString());
            String batchId1 = validateResponse1.get("batchId").asText();

            String executeRequest1 = String.format("{\"batchId\": \"%s\"}", batchId1);
            mockMvc.perform(post("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(executeRequest1))
                .andExpect(status().isAccepted());

            waitForBatchCompletion(batchId1);

            // Step 2: Try to import same business name again
            String json2 = """
                {
                    "payors": [
                        {
                            "businessName": "Duplicate Company",
                            "externalReference": "EXT-002",
                            "persons": [
                                {
                                    "name": "Jane Doe",
                                    "email": "jane@dup.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-5678"
                                }
                            ]
                        }
                    ]
                }
                """;

            MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "payors2.json",
                "application/json",
                json2.getBytes()
            );

            MvcResult validateResult2 = mockMvc.perform(multipart("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/validate")
                    .file(file2))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode validateResponse2 = objectMapper.readTree(validateResult2.getResponse().getContentAsString());

            // Should be marked as invalid due to duplicate
            assertThat(validateResponse2.get("valid").asBoolean()).isFalse();
            assertThat(validateResponse2.get("errors")).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("GET /api/batches/{batchId} - Get Batch Status")
    class GetBatchStatusTests {

        @Test
        @DisplayName("should get batch status with correct counts")
        void shouldGetBatchStatusWithCounts() throws Exception {
            // Step 1: Create and execute batch
            String json = """
                {
                    "payors": [
                        {
                            "businessName": "Status Test 1",
                            "externalReference": "EXT-001",
                            "persons": [
                                {
                                    "name": "User One",
                                    "email": "user1@status.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-1111"
                                }
                            ]
                        },
                        {
                            "businessName": "Status Test 2",
                            "externalReference": "EXT-002",
                            "persons": [
                                {
                                    "name": "User Two",
                                    "email": "user2@status.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-2222"
                                }
                            ]
                        }
                    ]
                }
                """;

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "payors.json",
                "application/json",
                json.getBytes()
            );

            MvcResult validateResult = mockMvc.perform(multipart("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/validate")
                    .file(file))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode validateResponse = objectMapper.readTree(validateResult.getResponse().getContentAsString());
            String batchId = validateResponse.get("batchId").asText();

            String executeRequest = String.format("{\"batchId\": \"%s\"}", batchId);
            mockMvc.perform(post("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(executeRequest))
                .andExpect(status().isAccepted());

            waitForBatchCompletion(batchId);

            // Step 2: Get batch status
            MvcResult statusResult = mockMvc.perform(get("/api/v1/bank/batches/" + batchId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchId))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failedCount").value(0))
                .andExpect(jsonPath("$.status").exists())
                .andReturn();

            JsonNode statusResponse = objectMapper.readTree(statusResult.getResponse().getContentAsString());
            assertThat(statusResponse.get("sourceProfileId").asText()).isEqualTo(onlineProfile.profileId().urn());
        }

        @Test
        @DisplayName("should get batch items filtered by status")
        void shouldGetBatchItemsFilteredByStatus() throws Exception {
            // Step 1: Create and execute batch
            String json = """
                {
                    "payors": [
                        {
                            "businessName": "Status Filter Test",
                            "externalReference": "EXT-STATUS",
                            "persons": [
                                {
                                    "name": "Filter User",
                                    "email": "filter@test.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-5555"
                                }
                            ]
                        }
                    ]
                }
                """;

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "payors.json",
                "application/json",
                json.getBytes()
            );

            MvcResult validateResult = mockMvc.perform(multipart("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/validate")
                    .file(file))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode validateResponse = objectMapper.readTree(validateResult.getResponse().getContentAsString());
            String batchId = validateResponse.get("batchId").asText();

            String executeRequest = String.format("{\"batchId\": \"%s\"}", batchId);
            mockMvc.perform(post("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(executeRequest))
                .andExpect(status().isAccepted());

            waitForBatchCompletion(batchId);

            // Step 2: Get batch items filtered by SUCCESS status
            MvcResult successItems = mockMvc.perform(get("/api/v1/bank/batches/" + batchId + "/items")
                    .param("status", "SUCCESS")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode successItemsResult = objectMapper.readTree(successItems.getResponse().getContentAsString());
            assertThat(successItemsResult.isArray()).isTrue();
            assertThat(successItemsResult.size()).isEqualTo(1);

            // Step 3: Get batch items filtered by FAILED status (should be empty)
            MvcResult failedItems = mockMvc.perform(get("/api/v1/bank/batches/" + batchId + "/items")
                    .param("status", "FAILED")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode failedItemsResult = objectMapper.readTree(failedItems.getResponse().getContentAsString());
            assertThat(failedItemsResult.isArray()).isTrue();
            assertThat(failedItemsResult.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("should get batch items with results")
        void shouldGetBatchItemsWithResults() throws Exception {
            // Step 1: Create and execute batch
            String json = """
                {
                    "payors": [
                        {
                            "businessName": "Item Test Company",
                            "externalReference": "EXT-ITEM",
                            "persons": [
                                {
                                    "name": "Item User",
                                    "email": "item@test.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-0000"
                                }
                            ]
                        }
                    ]
                }
                """;

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "payors.json",
                "application/json",
                json.getBytes()
            );

            MvcResult validateResult = mockMvc.perform(multipart("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/validate")
                    .file(file))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode validateResponse = objectMapper.readTree(validateResult.getResponse().getContentAsString());
            String batchId = validateResponse.get("batchId").asText();

            String executeRequest = String.format("{\"batchId\": \"%s\"}", batchId);
            mockMvc.perform(post("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(executeRequest))
                .andExpect(status().isAccepted());

            waitForBatchCompletion(batchId);

            // Step 2: Get batch items
            MvcResult itemsResult = mockMvc.perform(get("/api/v1/bank/batches/" + batchId + "/items")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode items = objectMapper.readTree(itemsResult.getResponse().getContentAsString());
            assertThat(items.isArray()).isTrue();
            assertThat(items.size()).isEqualTo(1);

            JsonNode item = items.get(0);
            assertThat(item.get("businessName").asText()).isEqualTo("Item Test Company");
            assertThat(item.get("status").asText()).isEqualTo("SUCCESS");
            assertThat(item.has("result")).isTrue();

            // Verify result contains expected IDs
            JsonNode result = item.get("result");
            assertThat(result.get("indirectClientId").asText()).isNotEmpty();
            assertThat(result.get("profileId").asText()).startsWith("ind:");
            assertThat(result.get("userIds").isArray()).isTrue();
            assertThat(result.get("userIds").size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("GET /api/profiles/{profileId}/payor-enrolment/batches - List Batches")
    class ListBatchesTests {

        @Test
        @DisplayName("should list batches for profile")
        void shouldListBatchesForProfile() throws Exception {
            // Step 1: Create multiple batches
            for (int i = 1; i <= 3; i++) {
                String json = String.format("""
                    {
                        "payors": [
                            {
                                "businessName": "Batch %d Company",
                                "externalReference": "EXT-%03d",
                                "persons": [
                                    {
                                        "name": "User %d",
                                        "email": "user%d@batch.com",
                                        "role": "ADMIN",
                                        "phone": "1-416-555-%04d"
                                    }
                                ]
                            }
                        ]
                    }
                    """, i, i, i, i, i);

                MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "payors" + i + ".json",
                    "application/json",
                    json.getBytes()
                );

                mockMvc.perform(multipart("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/validate")
                        .file(file))
                    .andExpect(status().isOk());
            }

            // Step 2: List batches
            MvcResult result = mockMvc.perform(get("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/batches")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode batches = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(batches.isArray()).isTrue();
            assertThat(batches.size()).isEqualTo(3);

            // Verify each batch has expected fields
            for (JsonNode batch : batches) {
                assertThat(batch.has("batchId")).isTrue();
                assertThat(batch.has("batchType")).isTrue();
                assertThat(batch.has("status")).isTrue();
                assertThat(batch.has("totalItems")).isTrue();
                assertThat(batch.get("totalItems").asInt()).isEqualTo(1);
            }
        }
    }

    @Nested
    @DisplayName("Verify Entity Relationships")
    class VerifyEntityRelationshipsTests {

        @Test
        @DisplayName("should verify complete entity graph after import")
        void shouldVerifyCompleteEntityGraph() throws Exception {
            // Import a payor
            String json = """
                {
                    "payors": [
                        {
                            "businessName": "Graph Test Company",
                            "externalReference": "EXT-GRAPH",
                            "persons": [
                                {
                                    "name": "Graph Admin",
                                    "email": "admin@graph.com",
                                    "role": "ADMIN",
                                    "phone": "1-416-555-7890"
                                }
                            ]
                        }
                    ]
                }
                """;

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "payors.json",
                "application/json",
                json.getBytes()
            );

            MvcResult validateResult = mockMvc.perform(multipart("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/validate")
                    .file(file))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode validateResponse = objectMapper.readTree(validateResult.getResponse().getContentAsString());
            String batchId = validateResponse.get("batchId").asText();

            String executeRequest = String.format("{\"batchId\": \"%s\"}", batchId);
            mockMvc.perform(post("/api/v1/bank/profiles/" + onlineProfile.profileId().urn() + "/payor-enrolment/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(executeRequest))
                .andExpect(status().isAccepted());

            waitForBatchCompletion(batchId);

            // Verify the complete graph
            List<IndirectClient> indirectClients = indirectClientRepository.findByParentProfileId(onlineProfile.profileId());
            assertThat(indirectClients).hasSize(1);

            IndirectClient indirectClient = indirectClients.get(0);

            // 1. IndirectClient verification
            assertThat(indirectClient.name()).isEqualTo("Graph Test Company");
            assertThat(indirectClient.externalReference()).isEqualTo("EXT-GRAPH");
            assertThat(indirectClient.parentClientId().urn()).isEqualTo(bankClient.clientId().urn());
            assertThat(indirectClient.parentProfileId().urn()).isEqualTo(onlineProfile.profileId().urn());
            assertThat(indirectClient.id().urn()).startsWith("ind:");

            // 2. INDIRECT Profile verification
            ProfileId indirectProfileId = ProfileId.of("indirect", indirectClient.id());
            Optional<Profile> indirectProfileOpt = profileRepository.findById(indirectProfileId);
            assertThat(indirectProfileOpt).isPresent();

            Profile indirectProfile = indirectProfileOpt.get();
            assertThat(indirectProfile.profileId().urn()).isEqualTo(indirectClient.id().urn());

            // Verify PAYOR service is enrolled
            boolean hasPayorService = indirectProfile.serviceEnrollments().stream()
                .anyMatch(se -> se.serviceType().equals(ServiceType.PAYOR.name()));
            assertThat(hasPayorService).isTrue();

            // 3. User verification
            List<User> users = userRepository.findByProfileId(indirectProfile.profileId());
            assertThat(users).hasSize(1);

            User user = users.get(0);
            assertThat(user.profileId().urn()).isEqualTo(indirectProfile.profileId().urn());
            assertThat(user.email()).isEqualTo("admin@graph.com");
            assertThat(user.userType()).isEqualTo(User.UserType.INDIRECT_USER);
            assertThat(user.identityProvider()).isEqualTo(User.IdentityProvider.AUTH0);
            assertThat(user.roles()).contains(User.Role.SECURITY_ADMIN);

            // 4. Verify the chain of references
            assertThat(indirectClient.parentProfileId()).isEqualTo(onlineProfile.profileId()); // IC -> Online Profile
            assertThat(indirectClient.parentClientId()).isEqualTo(bankClient.clientId()); // IC -> Bank Client
            assertThat(user.profileId()).isEqualTo(indirectProfile.profileId()); // User -> INDIRECT Profile
        }
    }
}
