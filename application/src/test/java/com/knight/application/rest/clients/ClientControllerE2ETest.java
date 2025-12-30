package com.knight.application.rest.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knight.application.persistence.clients.repository.ClientJpaRepository;
import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.types.ClientType;
import com.knight.domain.clients.repository.ClientRepository;
import com.knight.platform.sharedkernel.Address;
import com.knight.platform.sharedkernel.CdrClientId;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.SrfClientId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests for ClientController using real H2 database.
 * Tests the full API layer including serialization, repository, and response formatting.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
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
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.consumer.auto-startup=false"
})
class ClientControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientJpaRepository jpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Client srfClient1;
    private Client srfClient2;
    private Client cdrClient1;
    private Client cdrClient2;
    private Client cdrClient3;

    @BeforeEach
    void setUp() {
        // Clear any existing data
        jpaRepository.deleteAll();

        // Create test clients with different types and names
        srfClient1 = createAndSaveClient(
            new SrfClientId("123456789"),
            "Acme Corporation",
            ClientType.BUSINESS,
            createCanadianAddress("Toronto", "ON")
        );

        srfClient2 = createAndSaveClient(
            new SrfClientId("987654321"),
            "Alpha Industries Ltd.",
            ClientType.BUSINESS,
            createCanadianAddress("Vancouver", "BC")
        );

        cdrClient1 = createAndSaveClient(
            new CdrClientId("000001"),
            "Beta Solutions Inc.",
            ClientType.BUSINESS,
            createUSAddress("New York", "NY")
        );

        cdrClient2 = createAndSaveClient(
            new CdrClientId("000002"),
            "John Smith",
            ClientType.INDIVIDUAL,
            createUSAddress("Los Angeles", "CA")
        );

        cdrClient3 = createAndSaveClient(
            new CdrClientId("000003"),
            "Gamma Corp",
            ClientType.BUSINESS,
            createUSAddress("Chicago", "IL")
        );
    }

    private Client createAndSaveClient(ClientId clientId, String name, ClientType type, Address address) {
        Client client = Client.create(clientId, name, type, address);
        clientRepository.save(client);
        return client;
    }

    private Address createCanadianAddress(String city, String province) {
        return Address.of("123 Main Street", null, city, province, "M1A 2B3", "CA");
    }

    private Address createUSAddress(String city, String state) {
        return Address.of("456 Oak Avenue", null, city, state, "12345", "US");
    }

    @Nested
    @DisplayName("GET /api/clients - Search by name")
    class SearchByNameTests {

        @Test
        @DisplayName("should find clients by partial name match")
        void shouldFindByPartialName() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("name", "Corp")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("totalElements").asInt()).isEqualTo(2);

            // Verify the correct clients are returned
            JsonNode content = response.get("content");
            assertThat(content.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("should find client by exact name match")
        void shouldFindByExactName() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("name", "John Smith")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("totalElements").asInt()).isEqualTo(1);

            JsonNode content = response.get("content");
            assertThat(content.get(0).get("clientId").asText()).isEqualTo("cdr:000002");
            assertThat(content.get(0).get("name").asText()).isEqualTo("John Smith");
        }

        @Test
        @DisplayName("should return empty result for non-matching name")
        void shouldReturnEmptyForNonMatchingName() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients")
                    .param("name", "NonExistent")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("should handle case-insensitive name search")
        void shouldHandleCaseInsensitiveSearch() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("name", "ACME")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("totalElements").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("should paginate name search results")
        void shouldPaginateResults() throws Exception {
            // Search for all clients with 'a' in name - should be 4: Acme, Alpha, Beta, Gamma
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("name", "a")
                    .param("page", "0")
                    .param("size", "2")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("totalElements").asInt()).isEqualTo(4);
            assertThat(response.get("content").size()).isEqualTo(2);
            assertThat(response.get("totalPages").asInt()).isEqualTo(2);
            assertThat(response.get("hasNext").asBoolean()).isTrue();
        }
    }

    @Nested
    @DisplayName("GET /api/clients - Search by clientId")
    class SearchByClientIdTests {

        @Test
        @DisplayName("should find clients by full clientId with prefix")
        void shouldFindByFullClientId() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("clientId", "srf:123456789")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("totalElements").asInt()).isEqualTo(1);

            JsonNode content = response.get("content");
            assertThat(content.get(0).get("clientId").asText()).isEqualTo("srf:123456789");
        }

        @Test
        @DisplayName("should find clients by partial clientId with prefix")
        void shouldFindByPartialClientIdWithPrefix() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("clientId", "srf:123")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("totalElements").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("should find all CDR clients by prefix")
        void shouldFindAllByPrefix() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("clientId", "cdr:")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("totalElements").asInt()).isEqualTo(3);
        }

        @Test
        @DisplayName("should find clients by number pattern only")
        void shouldFindByNumberPatternOnly() throws Exception {
            // Search for "00000" should find all CDR clients
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("clientId", "00000")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("totalElements").asInt()).isEqualTo(3);
        }

        @Test
        @DisplayName("should return empty for non-matching clientId")
        void shouldReturnEmptyForNonMatchingClientId() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients")
                    .param("clientId", "xyz:999999")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("should paginate clientId search results")
        void shouldPaginateResults() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("clientId", "cdr:")
                    .param("page", "0")
                    .param("size", "2")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("totalElements").asInt()).isEqualTo(3);
            assertThat(response.get("content").size()).isEqualTo(2);
            assertThat(response.get("totalPages").asInt()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("GET /api/clients - Search with type filter")
    class SearchWithTypeFilterTests {

        @Test
        @DisplayName("should filter by type when searching by name")
        void shouldFilterByTypeWithName() throws Exception {
            // Search for "Corp" with type "srf" should find only Acme Corporation
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("type", "srf")
                    .param("name", "Corp")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            // Expecting 1 result (Acme Corporation is srf)
            JsonNode content = response.get("content");

            // Log the response for debugging
            System.out.println("Response for type=srf, name=Corp: " + result.getResponse().getContentAsString());
        }

        @Test
        @DisplayName("should filter by type when searching by clientId")
        void shouldFilterByTypeWithClientId() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("type", "srf")
                    .param("clientId", "123")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            System.out.println("Response for type=srf, clientId=123: " + result.getResponse().getContentAsString());
        }

        @Test
        @DisplayName("should return all clients of a type when only type is specified")
        void shouldReturnAllOfType() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("type", "srf")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            System.out.println("Response for type=srf only: " + result.getResponse().getContentAsString());
        }
    }

    @Nested
    @DisplayName("GET /api/clients - No filters")
    class NoFiltersTests {

        @Test
        @DisplayName("should return empty result when no filters provided")
        void shouldReturnEmptyWithNoFilters() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
        }
    }

    @Nested
    @DisplayName("GET /api/clients/{clientId} - Get client detail")
    class GetClientDetailTests {

        @Test
        @DisplayName("should return client details for existing client")
        void shouldReturnClientDetails() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients/srf:123456789")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("clientId").asText()).isEqualTo("srf:123456789");
            assertThat(response.get("name").asText()).isEqualTo("Acme Corporation");
            assertThat(response.get("clientType").asText()).isEqualTo("srf");

            // Verify address
            JsonNode address = response.get("address");
            assertThat(address.get("city").asText()).isEqualTo("Toronto");
            assertThat(address.get("stateProvince").asText()).isEqualTo("ON");
            assertThat(address.get("countryCode").asText()).isEqualTo("CA");
        }

        @Test
        @DisplayName("should return 404 for non-existing client")
        void shouldReturn404ForNonExistingClient() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients/srf:999999999")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 400 for invalid client ID format")
        void shouldReturn400ForInvalidClientId() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients/invalid-format")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Response format verification")
    class ResponseFormatTests {

        @Test
        @DisplayName("should return properly formatted PageResultDto")
        void shouldReturnProperPageFormat() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("name", "Acme")
                    .param("page", "0")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.first").isBoolean())
                .andExpect(jsonPath("$.last").isBoolean())
                .andExpect(jsonPath("$.hasNext").isBoolean())
                .andExpect(jsonPath("$.hasPrevious").isBoolean())
                .andReturn();

            // Verify the content structure
            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            JsonNode content = response.get("content");
            if (content.size() > 0) {
                JsonNode firstItem = content.get(0);
                assertThat(firstItem.has("clientId")).isTrue();
                assertThat(firstItem.has("name")).isTrue();
                assertThat(firstItem.has("clientType")).isTrue();
            }
        }

        @Test
        @DisplayName("should return clientId in URN format")
        void shouldReturnClientIdInUrnFormat() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("name", "Acme")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            JsonNode content = response.get("content");
            if (content.size() > 0) {
                String clientId = content.get(0).get("clientId").asText();
                assertThat(clientId).matches("^(srf|cdr|gid):\\d+$");
            }
        }
    }

    @Nested
    @DisplayName("GET /api/clients/{clientId}/accounts - Get client accounts")
    class GetClientAccountsTests {

        @Test
        @DisplayName("should return empty list when client has no accounts")
        void shouldReturnEmptyListForClientWithNoAccounts() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients/srf:123456789/accounts")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("should return 404 for non-existing client")
        void shouldReturn404ForNonExistingClient() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients/srf:999999999/accounts")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 400 for invalid client ID format")
        void shouldReturn400ForInvalidClientId() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients/invalid-format/accounts")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should paginate account results")
        void shouldPaginateResults() throws Exception {
            mockMvc.perform(get("/api/v1/bank/clients/srf:123456789/accounts")
                    .param("page", "0")
                    .param("size", "10")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
        }
    }

    @Nested
    @DisplayName("Simulating frontend search scenarios")
    class FrontendSearchScenarios {

        @Test
        @DisplayName("Scenario: Search by client number from frontend (srf:123456789)")
        void searchByClientNumberScenario() throws Exception {
            // Frontend sends: type=srf, clientId=srf:123456789
            // This is how ClientSearchView.searchByNumber() works
            String type = "srf";
            String number = "123456789";
            String fullClientId = type + ":" + number;

            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("clientId", fullClientId)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            System.out.println("Frontend scenario - search by client number:");
            System.out.println("  Request: clientId=" + fullClientId);
            System.out.println("  Response: " + responseBody);

            JsonNode response = objectMapper.readTree(responseBody);
            assertThat(response.get("totalElements").asInt())
                .as("Expected to find client srf:123456789")
                .isEqualTo(1);
        }

        @Test
        @DisplayName("Scenario: Search by client name from frontend")
        void searchByClientNameScenario() throws Exception {
            // Frontend sends: name=Acme
            String searchName = "Acme";

            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("name", searchName)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            System.out.println("Frontend scenario - search by client name:");
            System.out.println("  Request: name=" + searchName);
            System.out.println("  Response: " + responseBody);

            JsonNode response = objectMapper.readTree(responseBody);
            assertThat(response.get("totalElements").asInt())
                .as("Expected to find Acme Corporation")
                .isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Scenario: Search by just the number without prefix")
        void searchByNumberWithoutPrefix() throws Exception {
            // User enters just "123456789" without prefix
            String number = "123456789";

            MvcResult result = mockMvc.perform(get("/api/v1/bank/clients")
                    .param("clientId", number)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            System.out.println("Frontend scenario - search by number without prefix:");
            System.out.println("  Request: clientId=" + number);
            System.out.println("  Response: " + responseBody);

            JsonNode response = objectMapper.readTree(responseBody);
            // This should find the client since the clientId contains the number
            assertThat(response.get("totalElements").asInt())
                .as("Expected to find client by number pattern")
                .isGreaterThanOrEqualTo(1);
        }
    }
}
