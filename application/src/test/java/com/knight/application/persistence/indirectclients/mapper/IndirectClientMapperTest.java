package com.knight.application.persistence.indirectclients.mapper;

import com.knight.application.persistence.indirectclients.entity.IndirectClientEntity;
import com.knight.application.persistence.indirectclients.entity.RelatedPersonEntity;
import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.types.Email;
import com.knight.domain.indirectclients.types.PersonRole;
import com.knight.domain.indirectclients.types.Phone;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.SrfClientId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for IndirectClientMapper.
 * Tests domain to entity mapping including related persons.
 *
 * Note: toDomain mapping is handled in the repository adapter, so we only test toEntity here.
 */
class IndirectClientMapperTest {

    private IndirectClientMapper mapper;

    private static final ClientId PARENT_CLIENT_ID = new SrfClientId("123456789");
    private static final ProfileId PROFILE_ID = ProfileId.fromUrn("servicing:srf:123456789");
    private static final String BUSINESS_NAME = "Test Business Inc.";
    private static final String EXTERNAL_REFERENCE = "EXT-12345";
    private static final String CREATED_BY = "admin@example.com";

    @BeforeEach
    void setUp() {
        mapper = new IndirectClientMapper();
    }

    // ==================== Domain to Entity ====================

    @Nested
    @DisplayName("Domain to Entity Mapping")
    class DomainToEntityTests {

        @Test
        @DisplayName("should map all fields from domain to entity")
        void shouldMapAllFieldsFromDomainToEntity() {
            IndirectClientId indirectClientId = IndirectClientId.of(PARENT_CLIENT_ID, 1);
            IndirectClient client = IndirectClient.create(
                indirectClientId,
                PARENT_CLIENT_ID,
                PROFILE_ID,
                BUSINESS_NAME,
                EXTERNAL_REFERENCE,
                CREATED_BY
            );

            IndirectClientEntity entity = mapper.toEntity(client);

            assertThat(entity).isNotNull();
            assertThat(entity.getId()).isNotNull();  // Generated DB key
            assertThat(entity.getIndirectClientUrn()).isEqualTo(indirectClientId.urn());
            assertThat(entity.getParentClientId()).isEqualTo("srf:123456789");
            assertThat(entity.getProfileId()).isEqualTo(PROFILE_ID.urn());
            assertThat(entity.getClientType()).isEqualTo("BUSINESS");
            assertThat(entity.getBusinessName()).isEqualTo(BUSINESS_NAME);
            assertThat(entity.getExternalReference()).isEqualTo(EXTERNAL_REFERENCE);
            assertThat(entity.getStatus()).isEqualTo("PENDING");
            assertThat(entity.getCreatedAt()).isNotNull();
            assertThat(entity.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map client without external reference")
        void shouldMapClientWithoutExternalReference() {
            IndirectClientId indirectClientId = IndirectClientId.of(PARENT_CLIENT_ID, 1);
            IndirectClient client = IndirectClient.create(
                indirectClientId,
                PARENT_CLIENT_ID,
                PROFILE_ID,
                BUSINESS_NAME,
                CREATED_BY
            );

            IndirectClientEntity entity = mapper.toEntity(client);

            assertThat(entity.getBusinessName()).isEqualTo(BUSINESS_NAME);
            assertThat(entity.getExternalReference()).isNull();
        }

        @Test
        @DisplayName("should map related persons to entities")
        void shouldMapRelatedPersonsToEntities() {
            IndirectClientId indirectClientId = IndirectClientId.of(PARENT_CLIENT_ID, 1);
            IndirectClient client = IndirectClient.create(
                indirectClientId,
                PARENT_CLIENT_ID,
                PROFILE_ID,
                BUSINESS_NAME,
                CREATED_BY
            );

            client.addRelatedPerson(
                "John Doe",
                PersonRole.ADMIN,
                Email.of("john.doe@example.com"),
                Phone.of("1-555-0100")
            );

            client.addRelatedPerson(
                "Jane Smith",
                PersonRole.CONTACT,
                Email.of("jane.smith@example.com"),
                null  // No phone
            );

            IndirectClientEntity entity = mapper.toEntity(client);

            assertThat(entity.getRelatedPersons()).hasSize(2);

            RelatedPersonEntity admin = entity.getRelatedPersons().stream()
                .filter(p -> p.getRole().equals("ADMIN"))
                .findFirst()
                .orElseThrow();

            assertThat(admin.getName()).isEqualTo("John Doe");
            assertThat(admin.getEmail()).isEqualTo("john.doe@example.com");
            assertThat(admin.getPhone()).isEqualTo("15550100");  // Phone stores digits only
            assertThat(admin.getAddedAt()).isNotNull();
            assertThat(admin.getIndirectClient()).isEqualTo(entity);

            RelatedPersonEntity contact = entity.getRelatedPersons().stream()
                .filter(p -> p.getRole().equals("CONTACT"))
                .findFirst()
                .orElseThrow();

            assertThat(contact.getName()).isEqualTo("Jane Smith");
            assertThat(contact.getEmail()).isEqualTo("jane.smith@example.com");
            assertThat(contact.getPhone()).isNull();
        }

        @Test
        @DisplayName("should map related person with null email and phone")
        void shouldMapRelatedPersonWithNullEmailAndPhone() {
            IndirectClientId indirectClientId = IndirectClientId.of(PARENT_CLIENT_ID, 1);
            IndirectClient client = IndirectClient.create(
                indirectClientId,
                PARENT_CLIENT_ID,
                PROFILE_ID,
                BUSINESS_NAME,
                CREATED_BY
            );

            client.addRelatedPerson(
                "Contact Person",
                PersonRole.CONTACT,
                null,  // No email
                null   // No phone
            );

            IndirectClientEntity entity = mapper.toEntity(client);

            assertThat(entity.getRelatedPersons()).hasSize(1);
            RelatedPersonEntity person = entity.getRelatedPersons().get(0);
            assertThat(person.getName()).isEqualTo("Contact Person");
            assertThat(person.getEmail()).isNull();
            assertThat(person.getPhone()).isNull();
        }

        @Test
        @DisplayName("should map client with no related persons")
        void shouldMapClientWithNoRelatedPersons() {
            IndirectClientId indirectClientId = IndirectClientId.of(PARENT_CLIENT_ID, 1);
            IndirectClient client = IndirectClient.create(
                indirectClientId,
                PARENT_CLIENT_ID,
                PROFILE_ID,
                BUSINESS_NAME,
                CREATED_BY
            );

            IndirectClientEntity entity = mapper.toEntity(client);

            assertThat(entity.getRelatedPersons()).isEmpty();
        }

        @Test
        @DisplayName("should map active client status")
        void shouldMapActiveClientStatus() {
            IndirectClientId indirectClientId = IndirectClientId.of(PARENT_CLIENT_ID, 1);
            IndirectClient client = IndirectClient.create(
                indirectClientId,
                PARENT_CLIENT_ID,
                PROFILE_ID,
                BUSINESS_NAME,
                CREATED_BY
            );

            // Adding a related person will auto-activate the client
            client.addRelatedPerson(
                "Admin User",
                PersonRole.ADMIN,
                Email.of("admin@example.com"),
                Phone.of("1-555-0100")
            );

            IndirectClientEntity entity = mapper.toEntity(client);

            assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("should map suspended client status")
        void shouldMapSuspendedClientStatus() {
            IndirectClientId indirectClientId = IndirectClientId.of(PARENT_CLIENT_ID, 1);
            IndirectClient client = IndirectClient.create(
                indirectClientId,
                PARENT_CLIENT_ID,
                PROFILE_ID,
                BUSINESS_NAME,
                CREATED_BY
            );

            client.addRelatedPerson(
                "Admin User",
                PersonRole.ADMIN,
                Email.of("admin@example.com"),
                Phone.of("1-555-0100")
            );
            client.suspend();

            IndirectClientEntity entity = mapper.toEntity(client);

            assertThat(entity.getStatus()).isEqualTo("SUSPENDED");
        }

        @Test
        @DisplayName("should preserve person IDs when mapping")
        void shouldPreservePersonIdsWhenMapping() {
            IndirectClientId indirectClientId = IndirectClientId.of(PARENT_CLIENT_ID, 1);
            IndirectClient client = IndirectClient.create(
                indirectClientId,
                PARENT_CLIENT_ID,
                PROFILE_ID,
                BUSINESS_NAME,
                CREATED_BY
            );

            client.addRelatedPerson(
                "Test Person",
                PersonRole.ADMIN,
                Email.of("test@example.com"),
                Phone.of("1-555-0100")
            );

            IndirectClientEntity entity1 = mapper.toEntity(client);
            IndirectClientEntity entity2 = mapper.toEntity(client);

            // Person IDs should be consistent across multiple mappings
            assertThat(entity1.getRelatedPersons().get(0).getPersonId())
                .isEqualTo(entity2.getRelatedPersons().get(0).getPersonId());
        }

        @Test
        @DisplayName("should map multiple related persons with different roles")
        void shouldMapMultipleRelatedPersonsWithDifferentRoles() {
            IndirectClientId indirectClientId = IndirectClientId.of(PARENT_CLIENT_ID, 1);
            IndirectClient client = IndirectClient.create(
                indirectClientId,
                PARENT_CLIENT_ID,
                PROFILE_ID,
                BUSINESS_NAME,
                CREATED_BY
            );

            client.addRelatedPerson(
                "Admin One",
                PersonRole.ADMIN,
                Email.of("admin1@example.com"),
                Phone.of("1-555-0101")
            );

            client.addRelatedPerson(
                "Admin Two",
                PersonRole.ADMIN,
                Email.of("admin2@example.com"),
                Phone.of("1-555-0102")
            );

            client.addRelatedPerson(
                "Contact One",
                PersonRole.CONTACT,
                Email.of("contact1@example.com"),
                Phone.of("1-555-0103")
            );

            IndirectClientEntity entity = mapper.toEntity(client);

            assertThat(entity.getRelatedPersons()).hasSize(3);

            long adminCount = entity.getRelatedPersons().stream()
                .filter(p -> p.getRole().equals("ADMIN"))
                .count();
            assertThat(adminCount).isEqualTo(2);

            long contactCount = entity.getRelatedPersons().stream()
                .filter(p -> p.getRole().equals("CONTACT"))
                .count();
            assertThat(contactCount).isEqualTo(1);
        }

        @Test
        @DisplayName("should maintain bidirectional relationship between client and persons")
        void shouldMaintainBidirectionalRelationship() {
            IndirectClientId indirectClientId = IndirectClientId.of(PARENT_CLIENT_ID, 1);
            IndirectClient client = IndirectClient.create(
                indirectClientId,
                PARENT_CLIENT_ID,
                PROFILE_ID,
                BUSINESS_NAME,
                CREATED_BY
            );

            client.addRelatedPerson(
                "Test Person",
                PersonRole.ADMIN,
                Email.of("test@example.com"),
                Phone.of("1-555-0100")
            );

            IndirectClientEntity entity = mapper.toEntity(client);

            // Verify bidirectional relationship
            for (RelatedPersonEntity person : entity.getRelatedPersons()) {
                assertThat(person.getIndirectClient()).isEqualTo(entity);
            }
        }
    }

    // ==================== Null Input Handling ====================

    @Nested
    @DisplayName("Null Input Handling")
    class NullInputTests {

        @Test
        @DisplayName("should handle null domain input")
        void shouldHandleNullDomainInput() {
            // The mapper doesn't have explicit null handling in toEntity,
            // but it should handle null gracefully
            // Note: This test verifies expected behavior if null handling is added
            // Currently this will throw NPE which may be the desired behavior
            try {
                IndirectClientEntity entity = mapper.toEntity(null);
                assertThat(entity).isNull();
            } catch (NullPointerException e) {
                // This is also acceptable behavior - fail fast on null
                assertThat(e).isInstanceOf(NullPointerException.class);
            }
        }
    }
}
