package com.knight.domain.clients.aggregate;

import com.knight.domain.clients.types.ClientStatus;
import com.knight.domain.clients.types.ClientType;
import com.knight.platform.sharedkernel.Address;
import com.knight.platform.sharedkernel.SrfClientId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Client Aggregate Tests")
class ClientTest {

    private static final SrfClientId TEST_CLIENT_ID = new SrfClientId("123456789");
    private static final String TEST_NAME = "John Doe";
    private static final ClientType TEST_CLIENT_TYPE = ClientType.INDIVIDUAL;
    private static final Address TEST_ADDRESS = new Address(
        "123 Main St",
        null,
        "Toronto",
        "ON",
        "M1M1M1",
        "CA"
    );

    @Nested
    @DisplayName("create() factory method")
    class CreateTests {

        @Test
        @DisplayName("should create a new client with valid inputs")
        void shouldCreateClientWithValidInputs() {
            // When
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);

            // Then
            assertThat(client).isNotNull();
            assertThat(client.clientId()).isEqualTo(TEST_CLIENT_ID);
            assertThat(client.name()).isEqualTo(TEST_NAME);
            assertThat(client.clientType()).isEqualTo(TEST_CLIENT_TYPE);
            assertThat(client.address()).isEqualTo(TEST_ADDRESS);
            assertThat(client.status()).isEqualTo(ClientStatus.ACTIVE);
            assertThat(client.createdAt()).isNotNull();
            assertThat(client.updatedAt()).isNotNull();
            assertThat(client.updatedAt()).isEqualTo(client.createdAt());
        }

        @Test
        @DisplayName("should create client with BUSINESS type")
        void shouldCreateBusinessClient() {
            // When
            Client client = Client.create(TEST_CLIENT_ID, "ACME Corp", ClientType.BUSINESS, TEST_ADDRESS);

            // Then
            assertThat(client.clientType()).isEqualTo(ClientType.BUSINESS);
            assertThat(client.name()).isEqualTo("ACME Corp");
        }

        @Test
        @DisplayName("should trim client name")
        void shouldTrimClientName() {
            // When
            Client client = Client.create(TEST_CLIENT_ID, "  John Doe  ", TEST_CLIENT_TYPE, TEST_ADDRESS);

            // Then
            assertThat(client.name()).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("should throw NullPointerException when clientId is null")
        void shouldThrowExceptionWhenClientIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> Client.create(null, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clientId cannot be null");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when name is null")
        void shouldThrowExceptionWhenNameIsNull() {
            // When/Then
            assertThatThrownBy(() -> Client.create(TEST_CLIENT_ID, null, TEST_CLIENT_TYPE, TEST_ADDRESS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name cannot be null or blank");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when name is blank")
        void shouldThrowExceptionWhenNameIsBlank() {
            // When/Then
            assertThatThrownBy(() -> Client.create(TEST_CLIENT_ID, "   ", TEST_CLIENT_TYPE, TEST_ADDRESS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name cannot be null or blank");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when name is empty")
        void shouldThrowExceptionWhenNameIsEmpty() {
            // When/Then
            assertThatThrownBy(() -> Client.create(TEST_CLIENT_ID, "", TEST_CLIENT_TYPE, TEST_ADDRESS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name cannot be null or blank");
        }

        @Test
        @DisplayName("should throw NullPointerException when clientType is null")
        void shouldThrowExceptionWhenClientTypeIsNull() {
            // When/Then
            assertThatThrownBy(() -> Client.create(TEST_CLIENT_ID, TEST_NAME, null, TEST_ADDRESS))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clientType cannot be null");
        }

        @Test
        @DisplayName("should throw NullPointerException when address is null")
        void shouldThrowExceptionWhenAddressIsNull() {
            // When/Then
            assertThatThrownBy(() -> Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("address cannot be null");
        }
    }

    @Nested
    @DisplayName("updateName()")
    class UpdateNameTests {

        @Test
        @DisplayName("should update client name")
        void shouldUpdateName() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            Instant originalUpdatedAt = client.updatedAt();

            // When
            client.updateName("Jane Smith");

            // Then
            assertThat(client.name()).isEqualTo("Jane Smith");
            assertThat(client.updatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        }

        @Test
        @DisplayName("should trim new name")
        void shouldTrimNewName() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);

            // When
            client.updateName("  Jane Smith  ");

            // Then
            assertThat(client.name()).isEqualTo("Jane Smith");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when name is null")
        void shouldThrowExceptionWhenNameIsNull() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);

            // When/Then
            assertThatThrownBy(() -> client.updateName(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name cannot be null or blank");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when name is blank")
        void shouldThrowExceptionWhenNameIsBlank() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);

            // When/Then
            assertThatThrownBy(() -> client.updateName("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name cannot be null or blank");
        }

        @Test
        @DisplayName("should throw IllegalStateException when client is suspended")
        void shouldThrowExceptionWhenSuspended() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            client.suspend();

            // When/Then
            assertThatThrownBy(() -> client.updateName("Jane Smith"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot modify suspended client");
        }
    }

    @Nested
    @DisplayName("updateAddress()")
    class UpdateAddressTests {

        private static final Address NEW_ADDRESS = new Address(
            "456 Oak Ave",
            "Suite 100",
            "Vancouver",
            "BC",
            "V1V1V1",
            "CA"
        );

        @Test
        @DisplayName("should update client address")
        void shouldUpdateAddress() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            Instant originalUpdatedAt = client.updatedAt();

            // When
            client.updateAddress(NEW_ADDRESS);

            // Then
            assertThat(client.address()).isEqualTo(NEW_ADDRESS);
            assertThat(client.updatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        }

        @Test
        @DisplayName("should throw NullPointerException when address is null")
        void shouldThrowExceptionWhenAddressIsNull() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);

            // When/Then
            assertThatThrownBy(() -> client.updateAddress(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("address cannot be null");
        }

        @Test
        @DisplayName("should throw IllegalStateException when client is suspended")
        void shouldThrowExceptionWhenSuspended() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            client.suspend();

            // When/Then
            assertThatThrownBy(() -> client.updateAddress(NEW_ADDRESS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot modify suspended client");
        }
    }

    @Nested
    @DisplayName("updateClientType()")
    class UpdateClientTypeTests {

        @Test
        @DisplayName("should update client type from INDIVIDUAL to BUSINESS")
        void shouldUpdateClientTypeToBusinesss() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, ClientType.INDIVIDUAL, TEST_ADDRESS);
            Instant originalUpdatedAt = client.updatedAt();

            // When
            client.updateClientType(ClientType.BUSINESS);

            // Then
            assertThat(client.clientType()).isEqualTo(ClientType.BUSINESS);
            assertThat(client.updatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        }

        @Test
        @DisplayName("should update client type from BUSINESS to INDIVIDUAL")
        void shouldUpdateClientTypeToIndividual() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, "ACME Corp", ClientType.BUSINESS, TEST_ADDRESS);

            // When
            client.updateClientType(ClientType.INDIVIDUAL);

            // Then
            assertThat(client.clientType()).isEqualTo(ClientType.INDIVIDUAL);
        }

        @Test
        @DisplayName("should throw NullPointerException when clientType is null")
        void shouldThrowExceptionWhenClientTypeIsNull() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);

            // When/Then
            assertThatThrownBy(() -> client.updateClientType(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clientType cannot be null");
        }

        @Test
        @DisplayName("should throw IllegalStateException when client is suspended")
        void shouldThrowExceptionWhenSuspended() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            client.suspend();

            // When/Then
            assertThatThrownBy(() -> client.updateClientType(ClientType.BUSINESS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot modify suspended client");
        }
    }

    @Nested
    @DisplayName("activate()")
    class ActivateTests {

        @Test
        @DisplayName("should activate inactive client")
        void shouldActivateInactiveClient() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            client.deactivate();
            Instant originalUpdatedAt = client.updatedAt();

            // When
            client.activate();

            // Then
            assertThat(client.status()).isEqualTo(ClientStatus.ACTIVE);
            assertThat(client.updatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        }

        @Test
        @DisplayName("should not change status when client is already active")
        void shouldNotChangeStatusWhenAlreadyActive() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            assertThat(client.status()).isEqualTo(ClientStatus.ACTIVE);

            // When
            client.activate();

            // Then
            assertThat(client.status()).isEqualTo(ClientStatus.ACTIVE);
        }

        @Test
        @DisplayName("should throw IllegalStateException when client is suspended")
        void shouldThrowExceptionWhenSuspended() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            client.suspend();

            // When/Then
            assertThatThrownBy(() -> client.activate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot activate a suspended client");
        }
    }

    @Nested
    @DisplayName("deactivate()")
    class DeactivateTests {

        @Test
        @DisplayName("should deactivate active client")
        void shouldDeactivateActiveClient() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            Instant originalUpdatedAt = client.updatedAt();

            // When
            client.deactivate();

            // Then
            assertThat(client.status()).isEqualTo(ClientStatus.INACTIVE);
            assertThat(client.updatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        }

        @Test
        @DisplayName("should not change status when client is already inactive")
        void shouldNotChangeStatusWhenAlreadyInactive() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            client.deactivate();
            assertThat(client.status()).isEqualTo(ClientStatus.INACTIVE);

            // When
            client.deactivate();

            // Then
            assertThat(client.status()).isEqualTo(ClientStatus.INACTIVE);
        }

        @Test
        @DisplayName("should throw IllegalStateException when client is suspended")
        void shouldThrowExceptionWhenSuspended() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            client.suspend();

            // When/Then
            assertThatThrownBy(() -> client.deactivate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot deactivate a suspended client");
        }
    }

    @Nested
    @DisplayName("suspend()")
    class SuspendTests {

        @Test
        @DisplayName("should suspend active client")
        void shouldSuspendActiveClient() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            Instant originalUpdatedAt = client.updatedAt();

            // When
            client.suspend();

            // Then
            assertThat(client.status()).isEqualTo(ClientStatus.SUSPENDED);
            assertThat(client.updatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        }

        @Test
        @DisplayName("should suspend inactive client")
        void shouldSuspendInactiveClient() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            client.deactivate();

            // When
            client.suspend();

            // Then
            assertThat(client.status()).isEqualTo(ClientStatus.SUSPENDED);
        }

        @Test
        @DisplayName("should throw IllegalStateException when client is already suspended")
        void shouldThrowExceptionWhenAlreadySuspended() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            client.suspend();

            // When/Then
            assertThatThrownBy(() -> client.suspend())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Client is already suspended");
        }
    }

    @Nested
    @DisplayName("unsuspend()")
    class UnsuspendTests {

        @Test
        @DisplayName("should unsuspend suspended client")
        void shouldUnsuspendSuspendedClient() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            client.suspend();
            Instant originalUpdatedAt = client.updatedAt();

            // When
            client.unsuspend();

            // Then
            assertThat(client.status()).isEqualTo(ClientStatus.ACTIVE);
            assertThat(client.updatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        }

        @Test
        @DisplayName("should throw IllegalStateException when client is not suspended")
        void shouldThrowExceptionWhenNotSuspended() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);

            // When/Then
            assertThatThrownBy(() -> client.unsuspend())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Client is not suspended");
        }

        @Test
        @DisplayName("should throw IllegalStateException when client is inactive")
        void shouldThrowExceptionWhenInactive() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            client.deactivate();

            // When/Then
            assertThatThrownBy(() -> client.unsuspend())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Client is not suspended");
        }
    }

    @Nested
    @DisplayName("reconstitute()")
    class ReconstituteTests {

        @Test
        @DisplayName("should reconstitute client from persistence")
        void shouldReconstituteClient() {
            // Given
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant updatedAt = Instant.now().minusSeconds(1800);

            // When
            Client client = Client.reconstitute(
                TEST_CLIENT_ID,
                TEST_NAME,
                TEST_CLIENT_TYPE,
                TEST_ADDRESS,
                ClientStatus.INACTIVE,
                createdAt,
                updatedAt
            );

            // Then
            assertThat(client.clientId()).isEqualTo(TEST_CLIENT_ID);
            assertThat(client.name()).isEqualTo(TEST_NAME);
            assertThat(client.clientType()).isEqualTo(TEST_CLIENT_TYPE);
            assertThat(client.address()).isEqualTo(TEST_ADDRESS);
            assertThat(client.status()).isEqualTo(ClientStatus.INACTIVE);
            assertThat(client.createdAt()).isEqualTo(createdAt);
            assertThat(client.updatedAt()).isEqualTo(updatedAt);
        }

        @Test
        @DisplayName("should reconstitute suspended client")
        void shouldReconstituteSuspendedClient() {
            // Given
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant updatedAt = Instant.now().minusSeconds(1800);

            // When
            Client client = Client.reconstitute(
                TEST_CLIENT_ID,
                TEST_NAME,
                TEST_CLIENT_TYPE,
                TEST_ADDRESS,
                ClientStatus.SUSPENDED,
                createdAt,
                updatedAt
            );

            // Then
            assertThat(client.status()).isEqualTo(ClientStatus.SUSPENDED);
        }

        @Test
        @DisplayName("should reconstitute active client")
        void shouldReconstituteActiveClient() {
            // Given
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant updatedAt = Instant.now().minusSeconds(1800);

            // When
            Client client = Client.reconstitute(
                TEST_CLIENT_ID,
                TEST_NAME,
                TEST_CLIENT_TYPE,
                TEST_ADDRESS,
                ClientStatus.ACTIVE,
                createdAt,
                updatedAt
            );

            // Then
            assertThat(client.status()).isEqualTo(ClientStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("State transition validations")
    class StateTransitionTests {

        @Test
        @DisplayName("should allow full lifecycle: create -> deactivate -> activate")
        void shouldAllowDeactivateAndReactivate() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);

            // When/Then
            assertThat(client.status()).isEqualTo(ClientStatus.ACTIVE);

            client.deactivate();
            assertThat(client.status()).isEqualTo(ClientStatus.INACTIVE);

            client.activate();
            assertThat(client.status()).isEqualTo(ClientStatus.ACTIVE);
        }

        @Test
        @DisplayName("should allow full lifecycle: create -> suspend -> unsuspend")
        void shouldAllowSuspendAndUnsuspend() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);

            // When/Then
            assertThat(client.status()).isEqualTo(ClientStatus.ACTIVE);

            client.suspend();
            assertThat(client.status()).isEqualTo(ClientStatus.SUSPENDED);

            client.unsuspend();
            assertThat(client.status()).isEqualTo(ClientStatus.ACTIVE);
        }

        @Test
        @DisplayName("should prevent modifications when suspended")
        void shouldPreventModificationsWhenSuspended() {
            // Given
            Client client = Client.create(TEST_CLIENT_ID, TEST_NAME, TEST_CLIENT_TYPE, TEST_ADDRESS);
            client.suspend();

            // When/Then - all modification methods should fail
            assertThatThrownBy(() -> client.updateName("New Name"))
                .isInstanceOf(IllegalStateException.class);

            assertThatThrownBy(() -> client.updateAddress(TEST_ADDRESS))
                .isInstanceOf(IllegalStateException.class);

            assertThatThrownBy(() -> client.updateClientType(ClientType.BUSINESS))
                .isInstanceOf(IllegalStateException.class);

            assertThatThrownBy(() -> client.activate())
                .isInstanceOf(IllegalStateException.class);

            assertThatThrownBy(() -> client.deactivate())
                .isInstanceOf(IllegalStateException.class);
        }
    }
}
