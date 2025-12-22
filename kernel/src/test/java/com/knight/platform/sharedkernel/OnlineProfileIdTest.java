package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for OnlineProfileId.
 */
@DisplayName("OnlineProfileId Tests")
class OnlineProfileIdTest {

    private final SrfClientId validSrfClient = new SrfClientId("123456789");

    @Nested
    @DisplayName("of()")
    class Of {

        @Test
        @DisplayName("should create OnlineProfileId successfully")
        void shouldCreateOnlineProfileIdSuccessfully() {
            // When
            OnlineProfileId id = OnlineProfileId.of(validSrfClient, 1);

            // Then
            assertThat(id.clientId()).isEqualTo(validSrfClient);
            assertThat(id.sequence()).isEqualTo(1);
            assertThat(id.urn()).isEqualTo("online:srf:123456789:1");
        }

        @Test
        @DisplayName("should throw when clientId is null")
        void shouldThrowWhenClientIdIsNull() {
            assertThatThrownBy(() -> OnlineProfileId.of(null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ClientId cannot be null");
        }

        @Test
        @DisplayName("should throw when clientId is not SRF")
        void shouldThrowWhenClientIdIsNotSrf() {
            CdrClientId cdrClient = new CdrClientId("000123");

            assertThatThrownBy(() -> OnlineProfileId.of(cdrClient, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OnlineProfileId requires SRF client");
        }

        @Test
        @DisplayName("should throw when sequence is zero")
        void shouldThrowWhenSequenceIsZero() {
            assertThatThrownBy(() -> OnlineProfileId.of(validSrfClient, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sequence must be positive");
        }

        @Test
        @DisplayName("should throw when sequence is negative")
        void shouldThrowWhenSequenceIsNegative() {
            assertThatThrownBy(() -> OnlineProfileId.of(validSrfClient, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sequence must be positive");
        }
    }

    @Nested
    @DisplayName("fromUrn()")
    class FromUrn {

        @Test
        @DisplayName("should parse valid URN")
        void shouldParseValidUrn() {
            // When
            OnlineProfileId id = OnlineProfileId.fromUrn("online:srf:123456789:5");

            // Then
            assertThat(id.clientId().urn()).isEqualTo("srf:123456789");
            assertThat(id.sequence()).isEqualTo(5);
            assertThat(id.urn()).isEqualTo("online:srf:123456789:5");
        }

        @Test
        @DisplayName("should throw when URN is null")
        void shouldThrowWhenUrnIsNull() {
            assertThatThrownBy(() -> OnlineProfileId.fromUrn(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid OnlineProfileId URN format");
        }

        @Test
        @DisplayName("should throw when URN has wrong prefix")
        void shouldThrowWhenUrnHasWrongPrefix() {
            assertThatThrownBy(() -> OnlineProfileId.fromUrn("servicing:srf:123456789:1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid OnlineProfileId URN format");
        }

        @Test
        @DisplayName("should throw when URN is missing sequence")
        void shouldThrowWhenUrnIsMissingSequence() {
            assertThatThrownBy(() -> OnlineProfileId.fromUrn("online:"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("equality and hashCode")
    class EqualityAndHashCode {

        @Test
        @DisplayName("should be equal when URN is same")
        void shouldBeEqualWhenUrnIsSame() {
            OnlineProfileId id1 = OnlineProfileId.of(validSrfClient, 1);
            OnlineProfileId id2 = OnlineProfileId.of(validSrfClient, 1);

            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when sequence is different")
        void shouldNotBeEqualWhenSequenceIsDifferent() {
            OnlineProfileId id1 = OnlineProfileId.of(validSrfClient, 1);
            OnlineProfileId id2 = OnlineProfileId.of(validSrfClient, 2);

            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            OnlineProfileId id = OnlineProfileId.of(validSrfClient, 1);

            assertThat(id).isNotEqualTo(null);
        }

        @Test
        @DisplayName("should not be equal to different type")
        void shouldNotBeEqualToDifferentType() {
            OnlineProfileId id = OnlineProfileId.of(validSrfClient, 1);

            assertThat(id).isNotEqualTo("online:srf:123456789:1");
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringMethod {

        @Test
        @DisplayName("should return readable string")
        void shouldReturnReadableString() {
            OnlineProfileId id = OnlineProfileId.of(validSrfClient, 1);

            assertThat(id.toString()).contains("OnlineProfileId");
            assertThat(id.toString()).contains("online:srf:123456789:1");
        }
    }
}
