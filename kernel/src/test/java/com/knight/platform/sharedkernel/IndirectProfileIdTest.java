package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for IndirectProfileId.
 */
@DisplayName("IndirectProfileId Tests")
class IndirectProfileIdTest {

    private final SrfClientId validSrfClient = new SrfClientId("123456789");
    private final IndirectClientId validIndirectClient = IndirectClientId.of(validSrfClient, 1);

    @Nested
    @DisplayName("of()")
    class Of {

        @Test
        @DisplayName("should create IndirectProfileId successfully")
        void shouldCreateIndirectProfileIdSuccessfully() {
            // When
            IndirectProfileId id = IndirectProfileId.of(validSrfClient, validIndirectClient);

            // Then
            assertThat(id.clientId()).isEqualTo(validSrfClient);
            assertThat(id.indirectClientId()).isEqualTo(validIndirectClient);
            assertThat(id.urn()).isEqualTo("indirect-profile:indirect:srf:123456789:1");
        }

        @Test
        @DisplayName("should throw when clientId is null")
        void shouldThrowWhenClientIdIsNull() {
            assertThatThrownBy(() -> IndirectProfileId.of(null, validIndirectClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ClientId cannot be null");
        }

        @Test
        @DisplayName("should throw when indirectClientId is null")
        void shouldThrowWhenIndirectClientIdIsNull() {
            assertThatThrownBy(() -> IndirectProfileId.of(validSrfClient, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IndirectClientId cannot be null");
        }

        @Test
        @DisplayName("should throw when clientId is not SRF")
        void shouldThrowWhenClientIdIsNotSrf() {
            CdrClientId cdrClient = new CdrClientId("000123");

            assertThatThrownBy(() -> IndirectProfileId.of(cdrClient, validIndirectClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IndirectProfileId requires SRF client");
        }
    }

    @Nested
    @DisplayName("fromUrn()")
    class FromUrn {

        @Test
        @DisplayName("should parse valid URN")
        void shouldParseValidUrn() {
            // When
            IndirectProfileId id = IndirectProfileId.fromUrn("indirect-profile:indirect:srf:123456789:5");

            // Then
            assertThat(id.clientId().urn()).isEqualTo("srf:123456789");
            assertThat(id.indirectClientId().urn()).isEqualTo("indirect:srf:123456789:5");
            assertThat(id.urn()).isEqualTo("indirect-profile:indirect:srf:123456789:5");
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
            assertThatThrownBy(() -> IndirectProfileId.fromUrn("online:srf:123456789:1"))
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
            IndirectProfileId id1 = IndirectProfileId.of(validSrfClient, validIndirectClient);
            IndirectProfileId id2 = IndirectProfileId.of(validSrfClient, validIndirectClient);

            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when indirectClientId is different")
        void shouldNotBeEqualWhenIndirectClientIdIsDifferent() {
            IndirectClientId otherIndirect = IndirectClientId.of(validSrfClient, 2);

            IndirectProfileId id1 = IndirectProfileId.of(validSrfClient, validIndirectClient);
            IndirectProfileId id2 = IndirectProfileId.of(validSrfClient, otherIndirect);

            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            IndirectProfileId id = IndirectProfileId.of(validSrfClient, validIndirectClient);

            assertThat(id).isNotEqualTo(null);
        }

        @Test
        @DisplayName("should not be equal to different type")
        void shouldNotBeEqualToDifferentType() {
            IndirectProfileId id = IndirectProfileId.of(validSrfClient, validIndirectClient);

            assertThat(id).isNotEqualTo("indirect-profile:indirect:srf:123456789:1");
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringMethod {

        @Test
        @DisplayName("should return readable string")
        void shouldReturnReadableString() {
            IndirectProfileId id = IndirectProfileId.of(validSrfClient, validIndirectClient);

            assertThat(id.toString()).contains("IndirectProfileId");
            assertThat(id.toString()).contains("indirect-profile:");
        }
    }
}
