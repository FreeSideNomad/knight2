package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for IndirectClientId.
 * Tests the new UUID-based format: ind:{UUID}
 */
@DisplayName("IndirectClientId Tests")
class IndirectClientIdTest {

    @Nested
    @DisplayName("generate() Tests")
    class GenerateTests {

        @Test
        @DisplayName("Should generate a new IndirectClientId with UUID")
        void shouldGenerateWithUuid() {
            IndirectClientId indirectId = IndirectClientId.generate();

            assertThat(indirectId).isNotNull();
            assertThat(indirectId.uuid()).isNotNull();
            assertThat(indirectId.urn()).startsWith("ind:");
        }

        @Test
        @DisplayName("Should generate unique IDs on each call")
        void shouldGenerateUniqueIds() {
            IndirectClientId id1 = IndirectClientId.generate();
            IndirectClientId id2 = IndirectClientId.generate();

            assertThat(id1).isNotEqualTo(id2);
            assertThat(id1.uuid()).isNotEqualTo(id2.uuid());
        }

        @Test
        @DisplayName("Should generate URN in correct format")
        void shouldGenerateUrnInCorrectFormat() {
            IndirectClientId indirectId = IndirectClientId.generate();

            assertThat(indirectId.urn()).matches("ind:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }
    }

    @Nested
    @DisplayName("of(UUID) Tests")
    class OfUuidTests {

        @Test
        @DisplayName("Should create IndirectClientId from UUID")
        void shouldCreateFromUuid() {
            UUID uuid = UUID.randomUUID();
            IndirectClientId indirectId = IndirectClientId.of(uuid);

            assertThat(indirectId.uuid()).isEqualTo(uuid);
            assertThat(indirectId.urn()).isEqualTo("ind:" + uuid);
        }

        @Test
        @DisplayName("Should throw exception when UUID is null")
        void shouldThrowWhenUuidIsNull() {
            assertThatThrownBy(() -> IndirectClientId.of((UUID) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UUID cannot be null");
        }
    }

    @Nested
    @DisplayName("fromUrn() Tests")
    class FromUrnTests {

        @Test
        @DisplayName("Should parse IndirectClientId from URN")
        void shouldParseFromUrn() {
            UUID uuid = UUID.randomUUID();
            String urn = "ind:" + uuid;

            IndirectClientId indirectId = IndirectClientId.fromUrn(urn);

            assertThat(indirectId.uuid()).isEqualTo(uuid);
            assertThat(indirectId.urn()).isEqualTo(urn);
        }

        @Test
        @DisplayName("Should throw exception when URN is null")
        void shouldThrowWhenUrnIsNull() {
            assertThatThrownBy(() -> IndirectClientId.fromUrn(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid IndirectClientId URN format: null");
        }

        @Test
        @DisplayName("Should throw exception when URN has wrong prefix")
        void shouldThrowWhenUrnHasWrongPrefix() {
            assertThatThrownBy(() -> IndirectClientId.fromUrn("srf:123456789"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid IndirectClientId URN format");
        }

        @Test
        @DisplayName("Should throw exception when URN has invalid UUID")
        void shouldThrowWhenUrnHasInvalidUuid() {
            assertThatThrownBy(() -> IndirectClientId.fromUrn("ind:not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should throw exception when URN is missing UUID")
        void shouldThrowWhenUrnIsMissingUuid() {
            assertThatThrownBy(() -> IndirectClientId.fromUrn("ind:"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Equality and HashCode Tests")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal when UUIDs are the same")
        void shouldBeEqualWhenUuidsAreSame() {
            UUID uuid = UUID.randomUUID();
            IndirectClientId indirectId1 = IndirectClientId.of(uuid);
            IndirectClientId indirectId2 = IndirectClientId.of(uuid);

            assertThat(indirectId1).isEqualTo(indirectId2);
            assertThat(indirectId1.hashCode()).isEqualTo(indirectId2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when UUIDs differ")
        void shouldNotBeEqualWhenUuidsDiffer() {
            IndirectClientId indirectId1 = IndirectClientId.generate();
            IndirectClientId indirectId2 = IndirectClientId.generate();

            assertThat(indirectId1).isNotEqualTo(indirectId2);
        }

        @Test
        @DisplayName("Should be equal when created via different methods but same UUID")
        void shouldBeEqualWhenCreatedDifferently() {
            UUID uuid = UUID.randomUUID();
            IndirectClientId indirectId1 = IndirectClientId.of(uuid);
            IndirectClientId indirectId2 = IndirectClientId.fromUrn("ind:" + uuid);

            assertThat(indirectId1).isEqualTo(indirectId2);
            assertThat(indirectId1.hashCode()).isEqualTo(indirectId2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal to null")
        void shouldNotBeEqualToNull() {
            IndirectClientId indirectId = IndirectClientId.generate();

            assertThat(indirectId).isNotEqualTo(null);
        }

        @Test
        @DisplayName("Should be equal to itself")
        void shouldBeEqualToItself() {
            IndirectClientId indirectId = IndirectClientId.generate();

            assertThat(indirectId).isEqualTo(indirectId);
        }

        @Test
        @DisplayName("Should not be equal to object of different type")
        void shouldNotBeEqualToDifferentType() {
            IndirectClientId indirectId = IndirectClientId.generate();

            assertThat(indirectId).isNotEqualTo("some-string");
        }
    }

    @Nested
    @DisplayName("toString() Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should return correct string representation")
        void shouldReturnCorrectStringRepresentation() {
            UUID uuid = UUID.randomUUID();
            IndirectClientId indirectId = IndirectClientId.of(uuid);

            assertThat(indirectId.toString()).isEqualTo("IndirectClientId{ind:" + uuid + "}");
        }

        @Test
        @DisplayName("Should include URN in toString")
        void shouldIncludeUrnInToString() {
            IndirectClientId indirectId = IndirectClientId.generate();

            assertThat(indirectId.toString())
                .contains("ind:")
                .startsWith("IndirectClientId{")
                .endsWith("}");
        }
    }

    @Nested
    @DisplayName("ClientId Implementation Tests")
    class ClientIdTests {

        @Test
        @DisplayName("Should implement ClientId interface")
        void shouldImplementClientId() {
            IndirectClientId indirectId = IndirectClientId.generate();

            assertThat(indirectId).isInstanceOf(ClientId.class);
        }

        @Test
        @DisplayName("Should return correct URN via ClientId interface")
        void shouldReturnCorrectUrnViaInterface() {
            UUID uuid = UUID.randomUUID();
            IndirectClientId indirectId = IndirectClientId.of(uuid);
            ClientId clientId = indirectId;

            assertThat(clientId.urn()).isEqualTo("ind:" + uuid);
        }
    }
}
