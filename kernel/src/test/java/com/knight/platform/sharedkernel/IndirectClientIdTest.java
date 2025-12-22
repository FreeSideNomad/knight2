package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IndirectClientId Tests")
class IndirectClientIdTest {

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("Should create IndirectClientId with SrfClientId")
        void shouldCreateWithSrfClientId() {
            SrfClientId srfClientId = new SrfClientId("123456789");
            IndirectClientId indirectId = IndirectClientId.of(srfClientId, 1);

            assertThat(indirectId.clientId()).isEqualTo(srfClientId);
            assertThat(indirectId.sequence()).isEqualTo(1);
            assertThat(indirectId.urn()).isEqualTo("indirect:srf:123456789:1");
        }

        @Test
        @DisplayName("Should create IndirectClientId with CdrClientId")
        void shouldCreateWithCdrClientId() {
            CdrClientId cdrClientId = new CdrClientId("000123");
            IndirectClientId indirectId = IndirectClientId.of(cdrClientId, 5);

            assertThat(indirectId.clientId()).isEqualTo(cdrClientId);
            assertThat(indirectId.sequence()).isEqualTo(5);
            assertThat(indirectId.urn()).isEqualTo("indirect:cdr:000123:5");
        }

        @Test
        @DisplayName("Should create IndirectClientId with sequence greater than 1")
        void shouldCreateWithHigherSequence() {
            SrfClientId srfClientId = new SrfClientId("987654321");
            IndirectClientId indirectId = IndirectClientId.of(srfClientId, 99);

            assertThat(indirectId.sequence()).isEqualTo(99);
            assertThat(indirectId.urn()).isEqualTo("indirect:srf:987654321:99");
        }

        @Test
        @DisplayName("Should throw exception when clientId is null")
        void shouldThrowWhenClientIdIsNull() {
            assertThatThrownBy(() -> IndirectClientId.of(null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ClientId cannot be null");
        }

        @Test
        @DisplayName("Should throw exception when sequence is zero")
        void shouldThrowWhenSequenceIsZero() {
            SrfClientId srfClientId = new SrfClientId("123456789");

            assertThatThrownBy(() -> IndirectClientId.of(srfClientId, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sequence must be positive");
        }

        @Test
        @DisplayName("Should throw exception when sequence is negative")
        void shouldThrowWhenSequenceIsNegative() {
            SrfClientId srfClientId = new SrfClientId("123456789");

            assertThatThrownBy(() -> IndirectClientId.of(srfClientId, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sequence must be positive");
        }
    }

    @Nested
    @DisplayName("fromUrn() Tests")
    class FromUrnTests {

        @Test
        @DisplayName("Should parse IndirectClientId from URN with SrfClientId")
        void shouldParseFromUrnWithSrfClientId() {
            IndirectClientId indirectId = IndirectClientId.fromUrn("indirect:srf:123456789:1");

            assertThat(indirectId.clientId()).isInstanceOf(SrfClientId.class);
            assertThat(((SrfClientId) indirectId.clientId()).clientNumber()).isEqualTo("123456789");
            assertThat(indirectId.sequence()).isEqualTo(1);
            assertThat(indirectId.urn()).isEqualTo("indirect:srf:123456789:1");
        }

        @Test
        @DisplayName("Should parse IndirectClientId from URN with CdrClientId")
        void shouldParseFromUrnWithCdrClientId() {
            IndirectClientId indirectId = IndirectClientId.fromUrn("indirect:cdr:000123:2");

            assertThat(indirectId.clientId()).isInstanceOf(CdrClientId.class);
            assertThat(((CdrClientId) indirectId.clientId()).clientNumber()).isEqualTo("000123");
            assertThat(indirectId.sequence()).isEqualTo(2);
            assertThat(indirectId.urn()).isEqualTo("indirect:cdr:000123:2");
        }

        @Test
        @DisplayName("Should parse IndirectClientId with higher sequence number")
        void shouldParseWithHigherSequence() {
            IndirectClientId indirectId = IndirectClientId.fromUrn("indirect:srf:987654321:999");

            assertThat(indirectId.sequence()).isEqualTo(999);
        }

        @Test
        @DisplayName("Should throw exception when URN is null")
        void shouldThrowWhenUrnIsNull() {
            assertThatThrownBy(() -> IndirectClientId.fromUrn(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid IndirectClientId URN format");
        }

        @Test
        @DisplayName("Should throw exception when URN has invalid prefix")
        void shouldThrowWhenUrnHasInvalidPrefix() {
            assertThatThrownBy(() -> IndirectClientId.fromUrn("srf:123456789:1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid IndirectClientId URN format");
        }

        @Test
        @DisplayName("Should throw exception when URN is missing sequence")
        void shouldThrowWhenUrnIsMissingSequence() {
            assertThatThrownBy(() -> IndirectClientId.fromUrn("indirect:srf:123456789"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ClientId URN format");
        }

        @Test
        @DisplayName("Should throw exception when sequence is not a number")
        void shouldThrowWhenSequenceIsNotNumber() {
            assertThatThrownBy(() -> IndirectClientId.fromUrn("indirect:srf:123456789:abc"))
                .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("Should throw exception when client URN portion is invalid")
        void shouldThrowWhenClientUrnIsInvalid() {
            assertThatThrownBy(() -> IndirectClientId.fromUrn("indirect:invalid:123:1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ClientId URN format");
        }
    }

    @Nested
    @DisplayName("Equality and HashCode Tests")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal when URNs are the same")
        void shouldBeEqualWhenUrnsAreSame() {
            SrfClientId srfClientId1 = new SrfClientId("123456789");
            SrfClientId srfClientId2 = new SrfClientId("123456789");

            IndirectClientId indirectId1 = IndirectClientId.of(srfClientId1, 1);
            IndirectClientId indirectId2 = IndirectClientId.of(srfClientId2, 1);

            assertThat(indirectId1).isEqualTo(indirectId2);
            assertThat(indirectId1.hashCode()).isEqualTo(indirectId2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when sequences differ")
        void shouldNotBeEqualWhenSequencesDiffer() {
            SrfClientId srfClientId = new SrfClientId("123456789");

            IndirectClientId indirectId1 = IndirectClientId.of(srfClientId, 1);
            IndirectClientId indirectId2 = IndirectClientId.of(srfClientId, 2);

            assertThat(indirectId1).isNotEqualTo(indirectId2);
        }

        @Test
        @DisplayName("Should not be equal when client IDs differ")
        void shouldNotBeEqualWhenClientIdsDiffer() {
            IndirectClientId indirectId1 = IndirectClientId.of(new SrfClientId("123456789"), 1);
            IndirectClientId indirectId2 = IndirectClientId.of(new SrfClientId("987654321"), 1);

            assertThat(indirectId1).isNotEqualTo(indirectId2);
        }

        @Test
        @DisplayName("Should be equal when created via different methods but same URN")
        void shouldBeEqualWhenCreatedDifferently() {
            IndirectClientId indirectId1 = IndirectClientId.of(new SrfClientId("123456789"), 1);
            IndirectClientId indirectId2 = IndirectClientId.fromUrn("indirect:srf:123456789:1");

            assertThat(indirectId1).isEqualTo(indirectId2);
            assertThat(indirectId1.hashCode()).isEqualTo(indirectId2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal to null")
        void shouldNotBeEqualToNull() {
            IndirectClientId indirectId = IndirectClientId.of(new SrfClientId("123456789"), 1);

            assertThat(indirectId).isNotEqualTo(null);
        }

        @Test
        @DisplayName("Should be equal to itself")
        void shouldBeEqualToItself() {
            IndirectClientId indirectId = IndirectClientId.of(new SrfClientId("123456789"), 1);

            assertThat(indirectId).isEqualTo(indirectId);
        }
    }

    @Nested
    @DisplayName("toString() Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should return correct string representation")
        void shouldReturnCorrectStringRepresentation() {
            IndirectClientId indirectId = IndirectClientId.of(new SrfClientId("123456789"), 1);

            assertThat(indirectId.toString()).isEqualTo("IndirectClientId{indirect:srf:123456789:1}");
        }

        @Test
        @DisplayName("Should include URN in toString")
        void shouldIncludeUrnInToString() {
            IndirectClientId indirectId = IndirectClientId.of(new CdrClientId("000123"), 5);

            assertThat(indirectId.toString())
                .contains("indirect:cdr:000123:5")
                .startsWith("IndirectClientId{")
                .endsWith("}");
        }
    }

    @Nested
    @DisplayName("Nested IndirectClientId Tests")
    class NestedIndirectClientIdTests {

        @Test
        @DisplayName("Should create nested IndirectClientId from another IndirectClientId")
        void shouldCreateNestedIndirectClientId() {
            SrfClientId srfClientId = new SrfClientId("123456789");
            IndirectClientId firstLevel = IndirectClientId.of(srfClientId, 1);
            IndirectClientId secondLevel = IndirectClientId.of(firstLevel, 2);

            assertThat(secondLevel.clientId()).isEqualTo(firstLevel);
            assertThat(secondLevel.sequence()).isEqualTo(2);
            assertThat(secondLevel.urn()).isEqualTo("indirect:indirect:srf:123456789:1:2");
        }

        @Test
        @DisplayName("Should parse nested IndirectClientId from URN")
        void shouldParseNestedFromUrn() {
            IndirectClientId indirectId = IndirectClientId.fromUrn("indirect:indirect:srf:123456789:1:2");

            assertThat(indirectId.clientId()).isInstanceOf(IndirectClientId.class);
            assertThat(indirectId.sequence()).isEqualTo(2);

            IndirectClientId nestedClientId = (IndirectClientId) indirectId.clientId();
            assertThat(nestedClientId.sequence()).isEqualTo(1);
            assertThat(nestedClientId.clientId()).isInstanceOf(SrfClientId.class);
        }
    }
}
