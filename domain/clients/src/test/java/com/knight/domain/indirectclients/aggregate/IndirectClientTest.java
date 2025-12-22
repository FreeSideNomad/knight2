package com.knight.domain.indirectclients.aggregate;

import com.knight.domain.indirectclients.types.Email;
import com.knight.domain.indirectclients.types.PersonId;
import com.knight.domain.indirectclients.types.PersonRole;
import com.knight.domain.indirectclients.types.Phone;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.SrfClientId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("IndirectClient Aggregate Tests")
class IndirectClientTest {

    private static final SrfClientId PARENT_CLIENT_ID = new SrfClientId("123456789");
    private static final IndirectClientId TEST_ID = IndirectClientId.of(PARENT_CLIENT_ID, 1);
    private static final ProfileId TEST_PROFILE_ID = ProfileId.of("servicing", PARENT_CLIENT_ID);
    private static final String TEST_BUSINESS_NAME = "ACME Payors Inc";
    private static final String TEST_CREATED_BY = "user@example.com";

    @Nested
    @DisplayName("create() factory methods")
    class CreateTests {

        @Test
        @DisplayName("should create indirect client with business name")
        void shouldCreateIndirectClientWithBusinessName() {
            // When
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );

            // Then
            assertThat(client).isNotNull();
            assertThat(client.id()).isEqualTo(TEST_ID);
            assertThat(client.parentClientId()).isEqualTo(PARENT_CLIENT_ID);
            assertThat(client.profileId()).isEqualTo(TEST_PROFILE_ID);
            assertThat(client.clientType()).isEqualTo(IndirectClient.ClientType.BUSINESS);
            assertThat(client.businessName()).isEqualTo(TEST_BUSINESS_NAME);
            assertThat(client.externalReference()).isNull();
            assertThat(client.status()).isEqualTo(IndirectClient.Status.PENDING);
            assertThat(client.relatedPersons()).isEmpty();
            assertThat(client.createdBy()).isEqualTo(TEST_CREATED_BY);
            assertThat(client.createdAt()).isNotNull();
            assertThat(client.updatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should create indirect client with business name and external reference")
        void shouldCreateIndirectClientWithExternalReference() {
            // When
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                "EXT-REF-123",
                TEST_CREATED_BY
            );

            // Then
            assertThat(client.businessName()).isEqualTo(TEST_BUSINESS_NAME);
            assertThat(client.externalReference()).isEqualTo("EXT-REF-123");
        }

        @Test
        @DisplayName("should throw NullPointerException when id is null")
        void shouldThrowExceptionWhenIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> IndirectClient.create(
                null,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id cannot be null");
        }

        @Test
        @DisplayName("should throw NullPointerException when parentClientId is null")
        void shouldThrowExceptionWhenParentClientIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> IndirectClient.create(
                TEST_ID,
                null,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("parentClientId cannot be null");
        }

        @Test
        @DisplayName("should throw NullPointerException when profileId is null")
        void shouldThrowExceptionWhenProfileIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                null,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("profileId cannot be null");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when business name is null")
        void shouldThrowExceptionWhenBusinessNameIsNull() {
            // When/Then
            assertThatThrownBy(() -> IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                null,
                TEST_CREATED_BY
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Business name cannot be null or blank");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when business name is blank")
        void shouldThrowExceptionWhenBusinessNameIsBlank() {
            // When/Then
            assertThatThrownBy(() -> IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                "   ",
                TEST_CREATED_BY
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Business name cannot be null or blank");
        }

        @Test
        @DisplayName("should throw NullPointerException when createdBy is null")
        void shouldThrowExceptionWhenCreatedByIsNull() {
            // When/Then
            assertThatThrownBy(() -> IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                null
            )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("createdBy cannot be null");
        }
    }

    @Nested
    @DisplayName("addRelatedPerson()")
    class AddRelatedPersonTests {

        @Test
        @DisplayName("should add related person to client")
        void shouldAddRelatedPerson() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            Email email = Email.of("john@example.com");
            Phone phone = Phone.of("416-555-1234");

            // When
            client.addRelatedPerson("John Doe", PersonRole.ADMIN, email, phone);

            // Then
            assertThat(client.relatedPersons()).hasSize(1);
            IndirectClient.RelatedPerson person = client.relatedPersons().get(0);
            assertThat(person.name()).isEqualTo("John Doe");
            assertThat(person.role()).isEqualTo(PersonRole.ADMIN);
            assertThat(person.email()).isEqualTo(email);
            assertThat(person.phone()).isEqualTo(phone);
            assertThat(person.personId()).isNotNull();
            assertThat(person.addedAt()).isNotNull();
        }

        @Test
        @DisplayName("should add related person with null email and phone")
        void shouldAddRelatedPersonWithNullEmailAndPhone() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );

            // When
            client.addRelatedPerson("John Doe", PersonRole.CONTACT, null, null);

            // Then
            assertThat(client.relatedPersons()).hasSize(1);
            IndirectClient.RelatedPerson person = client.relatedPersons().get(0);
            assertThat(person.name()).isEqualTo("John Doe");
            assertThat(person.email()).isNull();
            assertThat(person.phone()).isNull();
        }

        @Test
        @DisplayName("should auto-activate business client when adding first person")
        void shouldAutoActivateWhenAddingFirstPerson() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            assertThat(client.status()).isEqualTo(IndirectClient.Status.PENDING);

            // When
            client.addRelatedPerson("John Doe", PersonRole.ADMIN, null, null);

            // Then
            assertThat(client.status()).isEqualTo(IndirectClient.Status.ACTIVE);
        }

        @Test
        @DisplayName("should add multiple related persons")
        void shouldAddMultipleRelatedPersons() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );

            // When
            client.addRelatedPerson("John Doe", PersonRole.ADMIN, null, null);
            client.addRelatedPerson("Jane Smith", PersonRole.CONTACT, null, null);

            // Then
            assertThat(client.relatedPersons()).hasSize(2);
            assertThat(client.relatedPersons())
                .extracting(IndirectClient.RelatedPerson::name)
                .containsExactly("John Doe", "Jane Smith");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when name is null")
        void shouldThrowExceptionWhenNameIsNull() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );

            // When/Then
            assertThatThrownBy(() -> client.addRelatedPerson(
                null,
                PersonRole.ADMIN,
                null,
                null
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Related person name cannot be null or blank");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when name is blank")
        void shouldThrowExceptionWhenNameIsBlank() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );

            // When/Then
            assertThatThrownBy(() -> client.addRelatedPerson(
                "   ",
                PersonRole.ADMIN,
                null,
                null
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Related person name cannot be null or blank");
        }

        @Test
        @DisplayName("should throw IllegalStateException when client is suspended")
        void shouldThrowExceptionWhenSuspended() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            client.addRelatedPerson("John Doe", PersonRole.ADMIN, null, null);
            client.suspend();

            // When/Then
            assertThatThrownBy(() -> client.addRelatedPerson(
                "Jane Smith",
                PersonRole.CONTACT,
                null,
                null
            )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot add related person to suspended client");
        }
    }

    @Nested
    @DisplayName("updateRelatedPerson()")
    class UpdateRelatedPersonTests {

        @Test
        @DisplayName("should update related person")
        void shouldUpdateRelatedPerson() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            client.addRelatedPerson("John Doe", PersonRole.ADMIN, null, null);
            PersonId personId = client.relatedPersons().get(0).personId();

            Email newEmail = Email.of("john.updated@example.com");
            Phone newPhone = Phone.of("416-555-9999");

            // When
            client.updateRelatedPerson(
                personId,
                "John Updated",
                PersonRole.CONTACT,
                newEmail,
                newPhone
            );

            // Then
            IndirectClient.RelatedPerson person = client.relatedPersons().get(0);
            assertThat(person.name()).isEqualTo("John Updated");
            assertThat(person.role()).isEqualTo(PersonRole.CONTACT);
            assertThat(person.email()).isEqualTo(newEmail);
            assertThat(person.phone()).isEqualTo(newPhone);
            assertThat(person.updatedAt()).isAfter(person.addedAt());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when person not found")
        void shouldThrowExceptionWhenPersonNotFound() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            PersonId nonExistentId = PersonId.generate();

            // When/Then
            assertThatThrownBy(() -> client.updateRelatedPerson(
                nonExistentId,
                "John Doe",
                PersonRole.ADMIN,
                null,
                null
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Related person not found");
        }

        @Test
        @DisplayName("should throw IllegalStateException when client is suspended")
        void shouldThrowExceptionWhenSuspended() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            client.addRelatedPerson("John Doe", PersonRole.ADMIN, null, null);
            PersonId personId = client.relatedPersons().get(0).personId();
            client.suspend();

            // When/Then
            assertThatThrownBy(() -> client.updateRelatedPerson(
                personId,
                "John Updated",
                PersonRole.CONTACT,
                null,
                null
            )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot update related person on suspended client");
        }
    }

    @Nested
    @DisplayName("removeRelatedPerson()")
    class RemoveRelatedPersonTests {

        @Test
        @DisplayName("should remove related person when multiple exist")
        void shouldRemoveRelatedPerson() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            client.addRelatedPerson("John Doe", PersonRole.ADMIN, null, null);
            client.addRelatedPerson("Jane Smith", PersonRole.CONTACT, null, null);
            PersonId personId = client.relatedPersons().get(0).personId();

            // When
            client.removeRelatedPerson(personId);

            // Then
            assertThat(client.relatedPersons()).hasSize(1);
            assertThat(client.relatedPersons().get(0).name()).isEqualTo("Jane Smith");
        }

        @Test
        @DisplayName("should throw IllegalStateException when removing last person from business client")
        void shouldThrowExceptionWhenRemovingLastPerson() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            client.addRelatedPerson("John Doe", PersonRole.ADMIN, null, null);
            PersonId personId = client.relatedPersons().get(0).personId();

            // When/Then
            assertThatThrownBy(() -> client.removeRelatedPerson(personId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Business type client must have at least one related person");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when person not found")
        void shouldThrowExceptionWhenPersonNotFound() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            client.addRelatedPerson("John Doe", PersonRole.ADMIN, null, null);
            client.addRelatedPerson("Jane Smith", PersonRole.CONTACT, null, null);
            PersonId nonExistentId = PersonId.generate();

            // When/Then
            assertThatThrownBy(() -> client.removeRelatedPerson(nonExistentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Related person not found");
        }

        @Test
        @DisplayName("should throw IllegalStateException when client is suspended")
        void shouldThrowExceptionWhenSuspended() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            client.addRelatedPerson("John Doe", PersonRole.ADMIN, null, null);
            client.addRelatedPerson("Jane Smith", PersonRole.CONTACT, null, null);
            PersonId personId = client.relatedPersons().get(0).personId();
            client.suspend();

            // When/Then
            assertThatThrownBy(() -> client.removeRelatedPerson(personId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot remove related person from suspended client");
        }
    }

    @Nested
    @DisplayName("activate()")
    class ActivateTests {

        @Test
        @DisplayName("should activate business client with related persons")
        void shouldActivateBusinessClient() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            client.addRelatedPerson("John Doe", PersonRole.ADMIN, null, null);
            // Client is auto-activated, so we need to go back to pending state via reconstitute
            IndirectClient pendingClient = IndirectClient.reconstitute(
                client.id(),
                client.parentClientId(),
                client.profileId(),
                client.clientType(),
                client.businessName(),
                client.externalReference(),
                IndirectClient.Status.PENDING,
                new ArrayList<>(client.relatedPersons()),
                client.createdAt(),
                client.createdBy(),
                client.updatedAt()
            );

            // When
            pendingClient.activate();

            // Then
            assertThat(pendingClient.status()).isEqualTo(IndirectClient.Status.ACTIVE);
        }

        @Test
        @DisplayName("should not change status when already active")
        void shouldNotChangeStatusWhenAlreadyActive() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            client.addRelatedPerson("John Doe", PersonRole.ADMIN, null, null);
            assertThat(client.status()).isEqualTo(IndirectClient.Status.ACTIVE);

            // When
            client.activate();

            // Then
            assertThat(client.status()).isEqualTo(IndirectClient.Status.ACTIVE);
        }

        @Test
        @DisplayName("should throw IllegalStateException when business client has no related persons")
        void shouldThrowExceptionWhenNoRelatedPersons() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            assertThat(client.relatedPersons()).isEmpty();

            // When/Then
            assertThatThrownBy(() -> client.activate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Business type client must have at least one related person");
        }
    }

    @Nested
    @DisplayName("suspend()")
    class SuspendTests {

        @Test
        @DisplayName("should suspend active client")
        void shouldSuspendActiveClient() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            client.addRelatedPerson("John Doe", PersonRole.ADMIN, null, null);
            assertThat(client.status()).isEqualTo(IndirectClient.Status.ACTIVE);

            // When
            client.suspend();

            // Then
            assertThat(client.status()).isEqualTo(IndirectClient.Status.SUSPENDED);
        }

        @Test
        @DisplayName("should suspend pending client")
        void shouldSuspendPendingClient() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            assertThat(client.status()).isEqualTo(IndirectClient.Status.PENDING);

            // When
            client.suspend();

            // Then
            assertThat(client.status()).isEqualTo(IndirectClient.Status.SUSPENDED);
        }

        @Test
        @DisplayName("should throw IllegalStateException when already suspended")
        void shouldThrowExceptionWhenAlreadySuspended() {
            // Given
            IndirectClient client = IndirectClient.create(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                TEST_BUSINESS_NAME,
                TEST_CREATED_BY
            );
            client.suspend();

            // When/Then
            assertThatThrownBy(() -> client.suspend())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Client is already suspended");
        }
    }

    @Nested
    @DisplayName("reconstitute()")
    class ReconstituteTests {

        @Test
        @DisplayName("should reconstitute indirect client from persistence")
        void shouldReconstituteIndirectClient() {
            // Given
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant updatedAt = Instant.now().minusSeconds(1800);

            PersonId personId = PersonId.generate();
            Instant personAddedAt = Instant.now().minusSeconds(2400);
            IndirectClient.RelatedPerson person = new IndirectClient.RelatedPerson(
                personId,
                "John Doe",
                PersonRole.ADMIN,
                Email.of("john@example.com"),
                Phone.of("416-555-1234"),
                personAddedAt
            );

            List<IndirectClient.RelatedPerson> persons = new ArrayList<>();
            persons.add(person);

            // When
            IndirectClient client = IndirectClient.reconstitute(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                IndirectClient.ClientType.BUSINESS,
                TEST_BUSINESS_NAME,
                "EXT-REF-123",
                IndirectClient.Status.ACTIVE,
                persons,
                createdAt,
                TEST_CREATED_BY,
                updatedAt
            );

            // Then
            assertThat(client.id()).isEqualTo(TEST_ID);
            assertThat(client.parentClientId()).isEqualTo(PARENT_CLIENT_ID);
            assertThat(client.profileId()).isEqualTo(TEST_PROFILE_ID);
            assertThat(client.clientType()).isEqualTo(IndirectClient.ClientType.BUSINESS);
            assertThat(client.businessName()).isEqualTo(TEST_BUSINESS_NAME);
            assertThat(client.externalReference()).isEqualTo("EXT-REF-123");
            assertThat(client.status()).isEqualTo(IndirectClient.Status.ACTIVE);
            assertThat(client.relatedPersons()).hasSize(1);
            assertThat(client.relatedPersons().get(0).personId()).isEqualTo(personId);
            assertThat(client.createdAt()).isEqualTo(createdAt);
            assertThat(client.createdBy()).isEqualTo(TEST_CREATED_BY);
            assertThat(client.updatedAt()).isEqualTo(updatedAt);
        }

        @Test
        @DisplayName("should reconstitute suspended client")
        void shouldReconstituteSuspendedClient() {
            // Given
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant updatedAt = Instant.now().minusSeconds(1800);

            // When
            IndirectClient client = IndirectClient.reconstitute(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                IndirectClient.ClientType.BUSINESS,
                TEST_BUSINESS_NAME,
                null,
                IndirectClient.Status.SUSPENDED,
                new ArrayList<>(),
                createdAt,
                TEST_CREATED_BY,
                updatedAt
            );

            // Then
            assertThat(client.status()).isEqualTo(IndirectClient.Status.SUSPENDED);
        }

        @Test
        @DisplayName("should reconstitute pending client")
        void shouldReconstitutePendingClient() {
            // Given
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant updatedAt = Instant.now().minusSeconds(1800);

            // When
            IndirectClient client = IndirectClient.reconstitute(
                TEST_ID,
                PARENT_CLIENT_ID,
                TEST_PROFILE_ID,
                IndirectClient.ClientType.BUSINESS,
                TEST_BUSINESS_NAME,
                null,
                IndirectClient.Status.PENDING,
                new ArrayList<>(),
                createdAt,
                TEST_CREATED_BY,
                updatedAt
            );

            // Then
            assertThat(client.status()).isEqualTo(IndirectClient.Status.PENDING);
        }
    }

    @Nested
    @DisplayName("RelatedPerson entity")
    class RelatedPersonTests {

        @Test
        @DisplayName("should create related person with all fields")
        void shouldCreateRelatedPersonWithAllFields() {
            // Given
            Email email = Email.of("john@example.com");
            Phone phone = Phone.of("416-555-1234");

            // When
            IndirectClient.RelatedPerson person = new IndirectClient.RelatedPerson(
                "John Doe",
                PersonRole.ADMIN,
                email,
                phone
            );

            // Then
            assertThat(person.personId()).isNotNull();
            assertThat(person.name()).isEqualTo("John Doe");
            assertThat(person.role()).isEqualTo(PersonRole.ADMIN);
            assertThat(person.email()).isEqualTo(email);
            assertThat(person.phone()).isEqualTo(phone);
            assertThat(person.addedAt()).isNotNull();
            assertThat(person.updatedAt()).isEqualTo(person.addedAt());
        }

        @Test
        @DisplayName("should update related person")
        void shouldUpdateRelatedPerson() {
            // Given
            IndirectClient.RelatedPerson person = new IndirectClient.RelatedPerson(
                "John Doe",
                PersonRole.ADMIN,
                null,
                null
            );
            Instant originalUpdatedAt = person.updatedAt();

            Email newEmail = Email.of("john.updated@example.com");
            Phone newPhone = Phone.of("416-555-9999");

            // When
            person.update("John Updated", PersonRole.CONTACT, newEmail, newPhone);

            // Then
            assertThat(person.name()).isEqualTo("John Updated");
            assertThat(person.role()).isEqualTo(PersonRole.CONTACT);
            assertThat(person.email()).isEqualTo(newEmail);
            assertThat(person.phone()).isEqualTo(newPhone);
            assertThat(person.updatedAt()).isAfter(originalUpdatedAt);
        }

        @Test
        @DisplayName("should throw NullPointerException when updating with null name")
        void shouldThrowExceptionWhenUpdatingWithNullName() {
            // Given
            IndirectClient.RelatedPerson person = new IndirectClient.RelatedPerson(
                "John Doe",
                PersonRole.ADMIN,
                null,
                null
            );

            // When/Then
            assertThatThrownBy(() -> person.update(null, PersonRole.CONTACT, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name cannot be null");
        }

        @Test
        @DisplayName("should throw NullPointerException when updating with null role")
        void shouldThrowExceptionWhenUpdatingWithNullRole() {
            // Given
            IndirectClient.RelatedPerson person = new IndirectClient.RelatedPerson(
                "John Doe",
                PersonRole.ADMIN,
                null,
                null
            );

            // When/Then
            assertThatThrownBy(() -> person.update("John Updated", null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("role cannot be null");
        }
    }
}
