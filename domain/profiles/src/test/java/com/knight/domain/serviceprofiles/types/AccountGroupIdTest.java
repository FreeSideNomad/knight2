package com.knight.domain.serviceprofiles.types;

import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for AccountGroupId value object.
 */
class AccountGroupIdTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("should create with valid UUID")
        void shouldCreateWithValidUuid() {
            UUID uuid = UUID.randomUUID();
            AccountGroupId id = new AccountGroupId(uuid);

            assertThat(id.value()).isEqualTo(uuid);
        }

        @Test
        @DisplayName("should reject null UUID")
        void shouldRejectNullUuid() {
            assertThatNullPointerException()
                .isThrownBy(() -> new AccountGroupId(null))
                .withMessageContaining("Account group ID cannot be null");
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodsTests {

        @Test
        @DisplayName("should generate unique IDs")
        void shouldGenerateUniqueIds() {
            AccountGroupId id1 = AccountGroupId.generate();
            AccountGroupId id2 = AccountGroupId.generate();

            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("should create from string")
        void shouldCreateFromString() {
            UUID uuid = UUID.randomUUID();
            String uuidString = uuid.toString();

            AccountGroupId id = AccountGroupId.of(uuidString);

            assertThat(id.value()).isEqualTo(uuid);
        }

        @Test
        @DisplayName("should throw for invalid string")
        void shouldThrowForInvalidString() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> AccountGroupId.of("not-a-uuid"));
        }
    }

    @Nested
    @DisplayName("Accessor Methods")
    class AccessorMethodsTests {

        @Test
        @DisplayName("should return id as string")
        void shouldReturnIdAsString() {
            UUID uuid = UUID.randomUUID();
            AccountGroupId id = new AccountGroupId(uuid);

            assertThat(id.id()).isEqualTo(uuid.toString());
        }

        @Test
        @DisplayName("should return toString as UUID string")
        void shouldReturnToStringAsUuidString() {
            UUID uuid = UUID.randomUUID();
            AccountGroupId id = new AccountGroupId(uuid);

            assertThat(id.toString()).isEqualTo(uuid.toString());
        }
    }

    @Nested
    @DisplayName("Equality")
    class EqualityTests {

        @Test
        @DisplayName("should be equal for same UUID")
        void shouldBeEqualForSameUuid() {
            UUID uuid = UUID.randomUUID();
            AccountGroupId id1 = new AccountGroupId(uuid);
            AccountGroupId id2 = new AccountGroupId(uuid);

            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different UUIDs")
        void shouldNotBeEqualForDifferentUuids() {
            AccountGroupId id1 = AccountGroupId.generate();
            AccountGroupId id2 = AccountGroupId.generate();

            assertThat(id1).isNotEqualTo(id2);
        }
    }
}
