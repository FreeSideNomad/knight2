package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProfileId Tests")
class ProfileIdTest {

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("Should create ProfileId with SrfClientId and servicing type")
        void shouldCreateWithSrfClientIdAndServicingType() {
            SrfClientId clientId = new SrfClientId("123456789");
            ProfileId profileId = ProfileId.of("servicing", clientId);

            assertThat(profileId.clientId()).isEqualTo(clientId);
            assertThat(profileId.urn()).isEqualTo("servicing:srf:123456789");
        }

        @Test
        @DisplayName("Should create ProfileId with CdrClientId and online type")
        void shouldCreateWithCdrClientIdAndOnlineType() {
            CdrClientId clientId = new CdrClientId("000123");
            ProfileId profileId = ProfileId.of("online", clientId);

            assertThat(profileId.clientId()).isEqualTo(clientId);
            assertThat(profileId.urn()).isEqualTo("online:cdr:000123");
        }

        @Test
        @DisplayName("Should create ProfileId with IndirectClientId")
        void shouldCreateWithIndirectClientId() {
            IndirectClientId indirectId = IndirectClientId.of(new SrfClientId("123456789"), 1);
            ProfileId profileId = ProfileId.of("servicing", indirectId);

            assertThat(profileId.clientId()).isEqualTo(indirectId);
            assertThat(profileId.urn()).isEqualTo("servicing:indirect:srf:123456789:1");
        }

        @Test
        @DisplayName("Should normalize profile type to lowercase")
        void shouldNormalizeProfileTypeToLowercase() {
            SrfClientId clientId = new SrfClientId("123456789");
            ProfileId profileId = ProfileId.of("SERVICING", clientId);

            assertThat(profileId.urn()).isEqualTo("servicing:srf:123456789");
        }

        @Test
        @DisplayName("Should throw exception when profile type is null")
        void shouldThrowWhenProfileTypeIsNull() {
            SrfClientId clientId = new SrfClientId("123456789");

            assertThatThrownBy(() -> ProfileId.of(null, clientId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ProfileType cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw exception when profile type is blank")
        void shouldThrowWhenProfileTypeIsBlank() {
            SrfClientId clientId = new SrfClientId("123456789");

            assertThatThrownBy(() -> ProfileId.of("   ", clientId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ProfileType cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw exception when clientId is null")
        void shouldThrowWhenClientIdIsNull() {
            assertThatThrownBy(() -> ProfileId.of("servicing", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ClientId cannot be null");
        }
    }

    @Nested
    @DisplayName("fromUrn() Tests")
    class FromUrnTests {

        @Test
        @DisplayName("Should parse ProfileId from URN with SrfClientId")
        void shouldParseFromUrnWithSrfClientId() {
            ProfileId profileId = ProfileId.fromUrn("servicing:srf:123456789");

            assertThat(profileId.clientId()).isInstanceOf(SrfClientId.class);
            assertThat(((SrfClientId) profileId.clientId()).clientNumber()).isEqualTo("123456789");
            assertThat(profileId.urn()).isEqualTo("servicing:srf:123456789");
        }

        @Test
        @DisplayName("Should parse ProfileId from URN with CdrClientId")
        void shouldParseFromUrnWithCdrClientId() {
            ProfileId profileId = ProfileId.fromUrn("online:cdr:000123");

            assertThat(profileId.clientId()).isInstanceOf(CdrClientId.class);
            assertThat(((CdrClientId) profileId.clientId()).clientNumber()).isEqualTo("000123");
            assertThat(profileId.urn()).isEqualTo("online:cdr:000123");
        }

        @Test
        @DisplayName("Should parse ProfileId from URN with IndirectClientId")
        void shouldParseFromUrnWithIndirectClientId() {
            ProfileId profileId = ProfileId.fromUrn("servicing:indirect:srf:123456789:1");

            assertThat(profileId.clientId()).isInstanceOf(IndirectClientId.class);
            assertThat(profileId.urn()).isEqualTo("servicing:indirect:srf:123456789:1");
        }

        @Test
        @DisplayName("Should extract profile type from URN")
        void shouldExtractProfileTypeFromUrn() {
            ProfileId servicingProfile = ProfileId.fromUrn("servicing:srf:123456789");
            ProfileId onlineProfile = ProfileId.fromUrn("online:cdr:000123");

            assertThat(servicingProfile.urn()).startsWith("servicing:");
            assertThat(onlineProfile.urn()).startsWith("online:");
        }

        @Test
        @DisplayName("Should throw exception when URN is null")
        void shouldThrowWhenUrnIsNull() {
            assertThatThrownBy(() -> ProfileId.fromUrn(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid ProfileId URN format");
        }

        @Test
        @DisplayName("Should throw exception when URN has no colon")
        void shouldThrowWhenUrnHasNoColon() {
            assertThatThrownBy(() -> ProfileId.fromUrn("servicing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ProfileId URN format");
        }

        @Test
        @DisplayName("Should throw exception when client URN portion is invalid")
        void shouldThrowWhenClientUrnIsInvalid() {
            assertThatThrownBy(() -> ProfileId.fromUrn("servicing:invalid:123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ClientId URN format");
        }
    }

    @Nested
    @DisplayName("Deprecated of(ClientId) Tests")
    class DeprecatedFactoryTests {

        @Test
        @DisplayName("Should create ProfileId with default servicing type")
        void shouldCreateWithDefaultServicingType() {
            SrfClientId clientId = new SrfClientId("123456789");

            @SuppressWarnings("deprecation")
            ProfileId profileId = ProfileId.of(clientId);

            assertThat(profileId.urn()).isEqualTo("servicing:srf:123456789");
        }
    }

    @Nested
    @DisplayName("Equality and HashCode Tests")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal when URNs are the same")
        void shouldBeEqualWhenUrnsAreSame() {
            SrfClientId clientId1 = new SrfClientId("123456789");
            SrfClientId clientId2 = new SrfClientId("123456789");

            ProfileId profileId1 = ProfileId.of("servicing", clientId1);
            ProfileId profileId2 = ProfileId.of("servicing", clientId2);

            assertThat(profileId1).isEqualTo(profileId2);
            assertThat(profileId1.hashCode()).isEqualTo(profileId2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when profile types differ")
        void shouldNotBeEqualWhenProfileTypesDiffer() {
            SrfClientId clientId = new SrfClientId("123456789");

            ProfileId servicingProfile = ProfileId.of("servicing", clientId);
            ProfileId onlineProfile = ProfileId.of("online", clientId);

            assertThat(servicingProfile).isNotEqualTo(onlineProfile);
        }

        @Test
        @DisplayName("Should not be equal when client IDs differ")
        void shouldNotBeEqualWhenClientIdsDiffer() {
            ProfileId profileId1 = ProfileId.of("servicing", new SrfClientId("123456789"));
            ProfileId profileId2 = ProfileId.of("servicing", new SrfClientId("987654321"));

            assertThat(profileId1).isNotEqualTo(profileId2);
        }

        @Test
        @DisplayName("Should be equal when created via different methods but same URN")
        void shouldBeEqualWhenCreatedDifferently() {
            ProfileId profileId1 = ProfileId.of("servicing", new SrfClientId("123456789"));
            ProfileId profileId2 = ProfileId.fromUrn("servicing:srf:123456789");

            assertThat(profileId1).isEqualTo(profileId2);
            assertThat(profileId1.hashCode()).isEqualTo(profileId2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal to null")
        void shouldNotBeEqualToNull() {
            ProfileId profileId = ProfileId.of("servicing", new SrfClientId("123456789"));

            assertThat(profileId).isNotEqualTo(null);
        }

        @Test
        @DisplayName("Should be equal to itself")
        void shouldBeEqualToItself() {
            ProfileId profileId = ProfileId.of("servicing", new SrfClientId("123456789"));

            assertThat(profileId).isEqualTo(profileId);
        }
    }

    @Nested
    @DisplayName("toString() Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should return correct string representation")
        void shouldReturnCorrectStringRepresentation() {
            ProfileId profileId = ProfileId.of("servicing", new SrfClientId("123456789"));

            assertThat(profileId.toString()).isEqualTo("ProfileId{servicing:srf:123456789}");
        }

        @Test
        @DisplayName("Should include URN in toString")
        void shouldIncludeUrnInToString() {
            ProfileId profileId = ProfileId.of("online", new CdrClientId("000123"));

            assertThat(profileId.toString())
                .contains("online:cdr:000123")
                .startsWith("ProfileId{")
                .endsWith("}");
        }
    }

    @Nested
    @DisplayName("Profile Type Extraction Tests")
    class ProfileTypeExtractionTests {

        @Test
        @DisplayName("Should preserve servicing profile type")
        void shouldPreserveServicingType() {
            ProfileId profileId = ProfileId.fromUrn("servicing:srf:123456789");

            assertThat(profileId.urn()).startsWith("servicing:");
        }

        @Test
        @DisplayName("Should preserve online profile type")
        void shouldPreserveOnlineType() {
            ProfileId profileId = ProfileId.fromUrn("online:cdr:000123");

            assertThat(profileId.urn()).startsWith("online:");
        }

        @Test
        @DisplayName("Should preserve custom profile type")
        void shouldPreserveCustomType() {
            ProfileId profileId = ProfileId.of("custom", new SrfClientId("123456789"));

            assertThat(profileId.urn()).startsWith("custom:");
        }

        @Test
        @DisplayName("Should normalize mixed case profile type")
        void shouldNormalizeMixedCase() {
            ProfileId profileId = ProfileId.of("SeRvIcInG", new SrfClientId("123456789"));

            assertThat(profileId.urn()).isEqualTo("servicing:srf:123456789");
        }
    }

    @Nested
    @DisplayName("Complex Scenarios Tests")
    class ComplexScenariosTests {

        @Test
        @DisplayName("Should handle ProfileId with nested IndirectClientId")
        void shouldHandleNestedIndirectClientId() {
            SrfClientId srfClientId = new SrfClientId("123456789");
            IndirectClientId firstLevel = IndirectClientId.of(srfClientId, 1);
            IndirectClientId secondLevel = IndirectClientId.of(firstLevel, 2);
            ProfileId profileId = ProfileId.of("servicing", secondLevel);

            assertThat(profileId.urn()).isEqualTo("servicing:indirect:indirect:srf:123456789:1:2");
        }

        @Test
        @DisplayName("Should parse ProfileId with nested IndirectClientId from URN")
        void shouldParseNestedIndirectClientIdFromUrn() {
            ProfileId profileId = ProfileId.fromUrn("servicing:indirect:indirect:srf:123456789:1:2");

            assertThat(profileId.clientId()).isInstanceOf(IndirectClientId.class);
            IndirectClientId indirectId = (IndirectClientId) profileId.clientId();
            assertThat(indirectId.clientId()).isInstanceOf(IndirectClientId.class);
        }
    }
}
