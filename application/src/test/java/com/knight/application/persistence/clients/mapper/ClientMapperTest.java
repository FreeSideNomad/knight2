package com.knight.application.persistence.clients.mapper;

import com.knight.application.persistence.clients.entity.ClientEntity;
import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.types.ClientStatus;
import com.knight.domain.clients.types.ClientType;
import com.knight.platform.sharedkernel.Address;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.SrfClientId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ClientMapper.
 * Tests domain to entity and entity to domain mapping.
 */
class ClientMapperTest {

    private ClientMapper mapper;

    private static final ClientId CLIENT_ID = new SrfClientId("123456789");
    private static final String CLIENT_NAME = "Test Client Inc.";
    private static final Address CLIENT_ADDRESS = Address.of(
        "123 Main St",
        "Suite 100",
        "New York",
        "NY",
        "10001",
        "US"
    );

    @BeforeEach
    void setUp() {
        mapper = new ClientMapperImpl();
    }

    // ==================== Domain to Entity ====================

    @Nested
    @DisplayName("Domain to Entity Mapping")
    class DomainToEntityTests {

        @Test
        @DisplayName("should map all fields from domain to entity")
        void shouldMapAllFieldsFromDomainToEntity() {
            Client client = Client.create(
                CLIENT_ID,
                CLIENT_NAME,
                ClientType.BUSINESS,
                CLIENT_ADDRESS
            );

            ClientEntity entity = mapper.toEntity(client);

            assertThat(entity).isNotNull();
            assertThat(entity.getClientId()).isEqualTo("srf:123456789");
            assertThat(entity.getName()).isEqualTo(CLIENT_NAME);
            assertThat(entity.getClientType()).isEqualTo("BUSINESS");
            assertThat(entity.getStatus()).isEqualTo("ACTIVE");

            // Address fields
            assertThat(entity.getAddressLine1()).isEqualTo("123 Main St");
            assertThat(entity.getAddressLine2()).isEqualTo("Suite 100");
            assertThat(entity.getCity()).isEqualTo("New York");
            assertThat(entity.getStateProvince()).isEqualTo("NY");
            assertThat(entity.getZipPostalCode()).isEqualTo("10001");
            assertThat(entity.getCountryCode()).isEqualTo("US");

            // Audit fields
            assertThat(entity.getCreatedAt()).isNotNull();
            assertThat(entity.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map individual client type")
        void shouldMapIndividualClientType() {
            Client client = Client.create(
                CLIENT_ID,
                "John Doe",
                ClientType.INDIVIDUAL,
                CLIENT_ADDRESS
            );

            ClientEntity entity = mapper.toEntity(client);

            assertThat(entity.getClientType()).isEqualTo("INDIVIDUAL");
            assertThat(entity.getName()).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("should map suspended client status")
        void shouldMapSuspendedClientStatus() {
            Client client = Client.create(
                CLIENT_ID,
                CLIENT_NAME,
                ClientType.BUSINESS,
                CLIENT_ADDRESS
            );
            client.suspend();

            ClientEntity entity = mapper.toEntity(client);

            assertThat(entity.getStatus()).isEqualTo("SUSPENDED");
        }

        @Test
        @DisplayName("should map inactive client status")
        void shouldMapInactiveClientStatus() {
            Client client = Client.create(
                CLIENT_ID,
                CLIENT_NAME,
                ClientType.BUSINESS,
                CLIENT_ADDRESS
            );
            client.deactivate();

            ClientEntity entity = mapper.toEntity(client);

            assertThat(entity.getStatus()).isEqualTo("INACTIVE");
        }

        @Test
        @DisplayName("should handle null address fields")
        void shouldHandleNullAddressFields() {
            Address minimalAddress = Address.of(
                "123 Main St",
                null,  // addressLine2
                "New York",
                "NY",
                "10001",
                "US"
            );

            Client client = Client.create(
                CLIENT_ID,
                CLIENT_NAME,
                ClientType.BUSINESS,
                minimalAddress
            );

            ClientEntity entity = mapper.toEntity(client);

            assertThat(entity.getAddressLine1()).isEqualTo("123 Main St");
            assertThat(entity.getAddressLine2()).isNull();
            assertThat(entity.getCity()).isEqualTo("New York");
        }

        @Test
        @DisplayName("should handle null domain input")
        void shouldHandleNullDomainInput() {
            ClientEntity entity = mapper.toEntity(null);

            assertThat(entity).isNull();
        }

    }

    // ==================== Entity to Domain ====================

    @Nested
    @DisplayName("Entity to Domain Mapping")
    class EntityToDomainTests {

        @Test
        @DisplayName("should map all fields from entity to domain")
        void shouldMapAllFieldsFromEntityToDomain() {
            ClientEntity entity = createClientEntity();

            Client client = mapper.toDomain(entity);

            assertThat(client).isNotNull();
            assertThat(client.clientId().urn()).isEqualTo("srf:123456789");
            assertThat(client.name()).isEqualTo(CLIENT_NAME);
            assertThat(client.clientType()).isEqualTo(ClientType.BUSINESS);
            assertThat(client.status()).isEqualTo(ClientStatus.ACTIVE);

            // Address
            assertThat(client.address()).isNotNull();
            assertThat(client.address().addressLine1()).isEqualTo("123 Main St");
            assertThat(client.address().addressLine2()).isEqualTo("Suite 100");
            assertThat(client.address().city()).isEqualTo("New York");
            assertThat(client.address().stateProvince()).isEqualTo("NY");
            assertThat(client.address().zipPostalCode()).isEqualTo("10001");
            assertThat(client.address().countryCode()).isEqualTo("US");

            // Audit fields
            assertThat(client.createdAt()).isNotNull();
            assertThat(client.updatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map individual client type from entity")
        void shouldMapIndividualClientTypeFromEntity() {
            ClientEntity entity = createClientEntity();
            entity.setClientType("INDIVIDUAL");
            entity.setName("Jane Smith");

            Client client = mapper.toDomain(entity);

            assertThat(client.clientType()).isEqualTo(ClientType.INDIVIDUAL);
            assertThat(client.name()).isEqualTo("Jane Smith");
        }

        @Test
        @DisplayName("should map all client statuses from entity")
        void shouldMapAllClientStatusesFromEntity() {
            ClientEntity activeEntity = createClientEntity();
            activeEntity.setStatus("ACTIVE");
            assertThat(mapper.toDomain(activeEntity).status()).isEqualTo(ClientStatus.ACTIVE);

            ClientEntity inactiveEntity = createClientEntity();
            inactiveEntity.setStatus("INACTIVE");
            assertThat(mapper.toDomain(inactiveEntity).status()).isEqualTo(ClientStatus.INACTIVE);

            ClientEntity suspendedEntity = createClientEntity();
            suspendedEntity.setStatus("SUSPENDED");
            assertThat(mapper.toDomain(suspendedEntity).status()).isEqualTo(ClientStatus.SUSPENDED);
        }

        @Test
        @DisplayName("should handle case insensitive client type conversion")
        void shouldHandleCaseInsensitiveClientTypeConversion() {
            ClientEntity entity = createClientEntity();
            entity.setClientType("business");  // lowercase

            Client client = mapper.toDomain(entity);

            assertThat(client.clientType()).isEqualTo(ClientType.BUSINESS);
        }

        @Test
        @DisplayName("should handle null address line 2")
        void shouldHandleNullAddressLine2() {
            ClientEntity entity = createClientEntity();
            entity.setAddressLine2(null);

            Client client = mapper.toDomain(entity);

            assertThat(client.address()).isNotNull();
            assertThat(client.address().addressLine1()).isEqualTo("123 Main St");
            assertThat(client.address().addressLine2()).isNull();
        }

        @Test
        @DisplayName("should handle null entity input")
        void shouldHandleNullEntityInput() {
            Client client = mapper.toDomain(null);

            assertThat(client).isNull();
        }

        @Test
        @DisplayName("should return null address when address line 1 is missing")
        void shouldReturnNullAddressWhenAddressLine1IsMissing() {
            ClientEntity entity = createClientEntity();
            entity.setAddressLine1(null);

            // The mapper's entityToAddress method returns null when addressLine1 is null
            // This is expected behavior as address is required for Client domain
            Address address = mapper.entityToAddress(entity);
            assertThat(address).isNull();
        }
    }

    // ==================== Round-trip ====================

    @Nested
    @DisplayName("Round-trip Mapping")
    class RoundTripTests {

        @Test
        @DisplayName("should preserve all data in round-trip conversion")
        void shouldPreserveAllDataInRoundTrip() {
            // Create original domain object
            Client original = Client.create(
                CLIENT_ID,
                CLIENT_NAME,
                ClientType.BUSINESS,
                CLIENT_ADDRESS
            );

            // Round-trip: Domain -> Entity -> Domain
            ClientEntity entity = mapper.toEntity(original);
            Client reconstructed = mapper.toDomain(entity);

            // Verify all fields are preserved
            assertThat(reconstructed.clientId().urn()).isEqualTo(original.clientId().urn());
            assertThat(reconstructed.name()).isEqualTo(original.name());
            assertThat(reconstructed.clientType()).isEqualTo(original.clientType());
            assertThat(reconstructed.status()).isEqualTo(original.status());

            // Address should be equal
            assertThat(reconstructed.address().addressLine1()).isEqualTo(original.address().addressLine1());
            assertThat(reconstructed.address().addressLine2()).isEqualTo(original.address().addressLine2());
            assertThat(reconstructed.address().city()).isEqualTo(original.address().city());
            assertThat(reconstructed.address().stateProvince()).isEqualTo(original.address().stateProvince());
            assertThat(reconstructed.address().zipPostalCode()).isEqualTo(original.address().zipPostalCode());
            assertThat(reconstructed.address().countryCode()).isEqualTo(original.address().countryCode());
        }

        @Test
        @DisplayName("should preserve status changes in round-trip")
        void shouldPreserveStatusChangesInRoundTrip() {
            Client original = Client.create(
                CLIENT_ID,
                CLIENT_NAME,
                ClientType.BUSINESS,
                CLIENT_ADDRESS
            );
            original.deactivate();

            ClientEntity entity = mapper.toEntity(original);
            Client reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.status()).isEqualTo(ClientStatus.INACTIVE);
        }
    }

    // ==================== Named Method Tests ====================

    @Nested
    @DisplayName("Named Method Null Handling")
    class NamedMethodNullTests {

        @Test
        @DisplayName("should return null when clientIdToString receives null")
        void shouldReturnNullWhenClientIdToStringReceivesNull() {
            assertThat(mapper.clientIdToString(null)).isNull();
        }

        @Test
        @DisplayName("should return null when stringToClientId receives null")
        void shouldReturnNullWhenStringToClientIdReceivesNull() {
            assertThat(mapper.stringToClientId(null)).isNull();
        }

        @Test
        @DisplayName("should return null when clientTypeToString receives null")
        void shouldReturnNullWhenClientTypeToStringReceivesNull() {
            assertThat(mapper.clientTypeToString(null)).isNull();
        }

        @Test
        @DisplayName("should return null when stringToClientType receives null")
        void shouldReturnNullWhenStringToClientTypeReceivesNull() {
            assertThat(mapper.stringToClientType(null)).isNull();
        }

        @Test
        @DisplayName("should return null when statusToString receives null")
        void shouldReturnNullWhenStatusToStringReceivesNull() {
            assertThat(mapper.statusToString(null)).isNull();
        }

        @Test
        @DisplayName("should return null when stringToStatus receives null")
        void shouldReturnNullWhenStringToStatusReceivesNull() {
            assertThat(mapper.stringToStatus(null)).isNull();
        }

        @Test
        @DisplayName("should return null when entityToAddress receives null entity")
        void shouldReturnNullWhenEntityToAddressReceivesNull() {
            assertThat(mapper.entityToAddress(null)).isNull();
        }
    }

    // ==================== Helper Methods ====================

    private ClientEntity createClientEntity() {
        ClientEntity entity = new ClientEntity();
        entity.setClientId("srf:123456789");
        entity.setName(CLIENT_NAME);
        entity.setClientType("BUSINESS");
        entity.setStatus("ACTIVE");
        entity.setAddressLine1("123 Main St");
        entity.setAddressLine2("Suite 100");
        entity.setCity("New York");
        entity.setStateProvince("NY");
        entity.setZipPostalCode("10001");
        entity.setCountryCode("US");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }
}
