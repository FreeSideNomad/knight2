package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for IndirectProfileId.
 * Tests the new UUID-based IndirectProfileId format.
 */
@DisplayName("IndirectProfileId Tests")
class IndirectProfileIdTest {

    @Nested
    @DisplayName("of()")
    class Of {

        @Test
        @DisplayName("should create IndirectProfileId from IndirectClientId")
        void shouldCreateIndirectProfileIdSuccessfully() {
            // Given
            IndirectClientId indirectClientId = IndirectClientId.generate();

            // When
            IndirectProfileId id = IndirectProfileId.of(indirectClientId);

            // Then
            assertThat(id.indirectClientId()).isEqualTo(indirectClientId);
            assertThat(id.urn()).isEqualTo(indirectClientId.urn());
        }

        @Test
        @DisplayName("should throw when indirectClientId is null")
        void shouldThrowWhenIndirectClientIdIsNull() {
            assertThatThrownBy(() -> IndirectProfileId.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IndirectClientId cannot be null");
        }
    }

    @Nested
    @DisplayName("fromUrn()")
    class FromUrn {

        @Test
        @DisplayName("should parse valid URN")
        void shouldParseValidUrn() {
            // Given
            IndirectClientId indirectClientId = IndirectClientId.generate();
            String urn = indirectClientId.urn();

            // When
            IndirectProfileId id = IndirectProfileId.fromUrn(urn);

            // Then
            assertThat(id.indirectClientId().urn()).isEqualTo(urn);
            assertThat(id.urn()).isEqualTo(urn);
        }

        @Test
        @DisplayName("should throw when URN is null")
        void shouldThrowWhenUrnIsNull() {
            assertThatThrownBy(() -> IndirectProfileId.fromUrn(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid IndirectProfileId URN format");
        }

        @Test
        @DisplayName("should throw when URN has wrong prefix")
        void shouldThrowWhenUrnHasWrongPrefix() {
            assertThatThrownBy(() -> IndirectProfileId.fromUrn("online:srf:123456789"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid IndirectProfileId URN format");
        }
    }

    @Nested
    @DisplayName("equality and hashCode")
    class EqualityAndHashCode {

        @Test
        @DisplayName("should be equal when URN is same")
        void shouldBeEqualWhenUrnIsSame() {
            IndirectClientId indirectClientId = IndirectClientId.generate();
            IndirectProfileId id1 = IndirectProfileId.of(indirectClientId);
            IndirectProfileId id2 = IndirectProfileId.of(indirectClientId);

            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when indirectClientId is different")
        void shouldNotBeEqualWhenIndirectClientIdIsDifferent() {
            IndirectClientId indirectClientId1 = IndirectClientId.generate();
            IndirectClientId indirectClientId2 = IndirectClientId.generate();

            IndirectProfileId id1 = IndirectProfileId.of(indirectClientId1);
            IndirectProfileId id2 = IndirectProfileId.of(indirectClientId2);

            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            IndirectProfileId id = IndirectProfileId.of(IndirectClientId.generate());

            assertThat(id).isNotEqualTo(null);
        }

        @Test
        @DisplayName("should not be equal to different type")
        void shouldNotBeEqualToDifferentType() {
            IndirectProfileId id = IndirectProfileId.of(IndirectClientId.generate());

            assertThat(id).isNotEqualTo("some-string");
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringMethod {

        @Test
        @DisplayName("should return readable string")
        void shouldReturnReadableString() {
            IndirectClientId indirectClientId = IndirectClientId.generate();
            IndirectProfileId id = IndirectProfileId.of(indirectClientId);

            assertThat(id.toString()).contains("IndirectProfileId");
            assertThat(id.toString()).contains("ind:");
        }
    }
}
