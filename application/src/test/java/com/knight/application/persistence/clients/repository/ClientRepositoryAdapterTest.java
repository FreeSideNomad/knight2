package com.knight.application.persistence.clients.repository;

import com.knight.application.persistence.clients.entity.ClientEntity;
import com.knight.application.persistence.clients.mapper.ClientMapper;
import com.knight.application.persistence.clients.mapper.ClientMapperImpl;
import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.api.PageResult;
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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ClientRepositoryAdapter using H2 in-memory database.
 * Tests all repository methods with comprehensive scenarios.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MSSQLServer",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.flyway.enabled=false"
})
@EntityScan(basePackages = "com.knight.application.persistence.clients.entity")
@EnableJpaRepositories(basePackageClasses = ClientJpaRepository.class)
@Import({ClientRepositoryAdapter.class, ClientMapperImpl.class})
class ClientRepositoryAdapterTest {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientJpaRepository jpaRepository;

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
    @DisplayName("save() tests")
    class SaveTests {

        @Test
        @DisplayName("should save a new client")
        void shouldSaveNewClient() {
            // Given
            ClientId newClientId = new SrfClientId("111222333");
            Client newClient = Client.create(
                newClientId,
                "New Test Company",
                ClientType.BUSINESS,
                createCanadianAddress("Montreal", "QC")
            );

            // When
            clientRepository.save(newClient);

            // Then
            Optional<Client> found = clientRepository.findById(newClientId);
            assertThat(found).isPresent();
            assertThat(found.get().name()).isEqualTo("New Test Company");
            assertThat(found.get().clientType()).isEqualTo(ClientType.BUSINESS);
        }

        @Test
        @DisplayName("should update existing client")
        void shouldUpdateExistingClient() {
            // Given
            Optional<Client> existing = clientRepository.findById(srfClient1.clientId());
            assertThat(existing).isPresent();

            Client client = existing.get();
            client.updateName("Acme Corporation Updated");

            // When
            clientRepository.save(client);

            // Then
            Optional<Client> updated = clientRepository.findById(srfClient1.clientId());
            assertThat(updated).isPresent();
            assertThat(updated.get().name()).isEqualTo("Acme Corporation Updated");
        }
    }

    @Nested
    @DisplayName("findById() tests")
    class FindByIdTests {

        @Test
        @DisplayName("should find existing SRF client by ID")
        void shouldFindSrfClientById() {
            // When
            Optional<Client> found = clientRepository.findById(srfClient1.clientId());

            // Then
            assertThat(found).isPresent();
            assertThat(found.get().clientId().urn()).isEqualTo("srf:123456789");
            assertThat(found.get().name()).isEqualTo("Acme Corporation");
        }

        @Test
        @DisplayName("should find existing CDR client by ID")
        void shouldFindCdrClientById() {
            // When
            Optional<Client> found = clientRepository.findById(cdrClient1.clientId());

            // Then
            assertThat(found).isPresent();
            assertThat(found.get().clientId().urn()).isEqualTo("cdr:000001");
            assertThat(found.get().name()).isEqualTo("Beta Solutions Inc.");
        }

        @Test
        @DisplayName("should return empty for non-existent client")
        void shouldReturnEmptyForNonExistentClient() {
            // Given
            ClientId nonExistentId = new SrfClientId("999999999");

            // When
            Optional<Client> found = clientRepository.findById(nonExistentId);

            // Then
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsById() tests")
    class ExistsByIdTests {

        @Test
        @DisplayName("should return true for existing client")
        void shouldReturnTrueForExistingClient() {
            // When
            boolean exists = clientRepository.existsById(srfClient1.clientId());

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false for non-existent client")
        void shouldReturnFalseForNonExistentClient() {
            // Given
            ClientId nonExistentId = new CdrClientId("999999");

            // When
            boolean exists = clientRepository.existsById(nonExistentId);

            // Then
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("searchByName() with pagination tests")
    class SearchByNamePaginatedTests {

        @Test
        @DisplayName("should return first page of results")
        void shouldReturnFirstPage() {
            // When
            PageResult<Client> result = clientRepository.searchByName("a", 0, 2);

            // Then
            assertThat(result.content()).hasSize(2);
            assertThat(result.page()).isEqualTo(0);
            assertThat(result.size()).isEqualTo(2);
            assertThat(result.totalElements()).isEqualTo(4);
            assertThat(result.totalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return second page of results")
        void shouldReturnSecondPage() {
            // When
            PageResult<Client> result = clientRepository.searchByName("a", 1, 2);

            // Then
            assertThat(result.content()).hasSize(2);
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.totalElements()).isEqualTo(4);
        }

        @Test
        @DisplayName("should return empty page when no results")
        void shouldReturnEmptyPageWhenNoResults() {
            // When
            PageResult<Client> result = clientRepository.searchByName("xyz", 0, 10);

            // Then
            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isEqualTo(0);
            assertThat(result.totalPages()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("searchByClientId() with pagination tests")
    class SearchByClientIdPaginatedTests {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            // When
            PageResult<Client> result = clientRepository.searchByClientId("cdr:", 0, 2);

            // Then
            assertThat(result.content()).hasSize(2);
            assertThat(result.page()).isEqualTo(0);
            assertThat(result.size()).isEqualTo(2);
            assertThat(result.totalElements()).isEqualTo(3);
            assertThat(result.totalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return last page with remaining items")
        void shouldReturnLastPageWithRemainingItems() {
            // When
            PageResult<Client> result = clientRepository.searchByClientId("cdr:", 1, 2);

            // Then
            assertThat(result.content()).hasSize(1);
            assertThat(result.page()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Address persistence tests")
    class AddressPersistenceTests {

        @Test
        @DisplayName("should persist and retrieve Canadian address correctly")
        void shouldPersistCanadianAddress() {
            // When
            Optional<Client> found = clientRepository.findById(srfClient1.clientId());

            // Then
            assertThat(found).isPresent();
            Address address = found.get().address();
            assertThat(address.city()).isEqualTo("Toronto");
            assertThat(address.stateProvince()).isEqualTo("ON");
            assertThat(address.countryCode()).isEqualTo("CA");
            assertThat(address.zipPostalCode()).isEqualTo("M1A 2B3");
        }

        @Test
        @DisplayName("should persist and retrieve US address correctly")
        void shouldPersistUSAddress() {
            // When
            Optional<Client> found = clientRepository.findById(cdrClient1.clientId());

            // Then
            assertThat(found).isPresent();
            Address address = found.get().address();
            assertThat(address.city()).isEqualTo("New York");
            assertThat(address.stateProvince()).isEqualTo("NY");
            assertThat(address.countryCode()).isEqualTo("US");
            assertThat(address.zipPostalCode()).isEqualTo("12345");
        }

        @Test
        @DisplayName("should update address correctly")
        void shouldUpdateAddress() {
            // Given
            Optional<Client> existing = clientRepository.findById(cdrClient2.clientId());
            assertThat(existing).isPresent();
            Client client = existing.get();

            Address newAddress = Address.of("789 New Street", "Suite 100", "Seattle", "WA", "98101", "US");
            client.updateAddress(newAddress);

            // When
            clientRepository.save(client);
            Optional<Client> updated = clientRepository.findById(cdrClient2.clientId());

            // Then
            assertThat(updated).isPresent();
            assertThat(updated.get().address().city()).isEqualTo("Seattle");
            assertThat(updated.get().address().stateProvince()).isEqualTo("WA");
            assertThat(updated.get().address().addressLine2()).isEqualTo("Suite 100");
        }
    }

    @Nested
    @DisplayName("Client type and status persistence tests")
    class ClientTypeStatusTests {

        @Test
        @DisplayName("should persist INDIVIDUAL client type")
        void shouldPersistIndividualClientType() {
            // When
            Optional<Client> found = clientRepository.findById(cdrClient2.clientId());

            // Then
            assertThat(found).isPresent();
            assertThat(found.get().clientType()).isEqualTo(ClientType.INDIVIDUAL);
        }

        @Test
        @DisplayName("should persist BUSINESS client type")
        void shouldPersistBusinessClientType() {
            // When
            Optional<Client> found = clientRepository.findById(cdrClient1.clientId());

            // Then
            assertThat(found).isPresent();
            assertThat(found.get().clientType()).isEqualTo(ClientType.BUSINESS);
        }

        @Test
        @DisplayName("should update client type")
        void shouldUpdateClientType() {
            // Given
            Optional<Client> existing = clientRepository.findById(cdrClient2.clientId());
            assertThat(existing).isPresent();
            Client client = existing.get();
            client.updateClientType(ClientType.BUSINESS);

            // When
            clientRepository.save(client);
            Optional<Client> updated = clientRepository.findById(cdrClient2.clientId());

            // Then
            assertThat(updated).isPresent();
            assertThat(updated.get().clientType()).isEqualTo(ClientType.BUSINESS);
        }
    }
}
