package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ClientId Tests")
class ClientIdTest {

    @Nested
    @DisplayName("SrfClientId Tests")
    class SrfClientIdTests {

        @Test
        @DisplayName("Should create SrfClientId with valid client number")
        void shouldCreateWithValidClientNumber() {
            SrfClientId clientId = new SrfClientId("123456789");

            assertThat(clientId.clientNumber()).isEqualTo("123456789");
            assertThat(clientId.urn()).isEqualTo("srf:123456789");
            assertThat(clientId.system()).isEqualTo("srf");
        }

        @Test
        @DisplayName("Should create SrfClientId from URN")
        void shouldCreateFromUrn() {
            SrfClientId clientId = SrfClientId.of("srf:987654321");

            assertThat(clientId.clientNumber()).isEqualTo("987654321");
            assertThat(clientId.urn()).isEqualTo("srf:987654321");
        }

        @Test
        @DisplayName("Should throw exception when client number is null")
        void shouldThrowWhenClientNumberIsNull() {
            assertThatThrownBy(() -> new SrfClientId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Client number cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw exception when client number is blank")
        void shouldThrowWhenClientNumberIsBlank() {
            assertThatThrownBy(() -> new SrfClientId("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Client number cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw exception when URN is null")
        void shouldThrowWhenUrnIsNull() {
            assertThatThrownBy(() -> SrfClientId.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid SrfClientId URN format");
        }

        @Test
        @DisplayName("Should throw exception when URN has invalid format")
        void shouldThrowWhenUrnHasInvalidFormat() {
            assertThatThrownBy(() -> SrfClientId.of("cdr:123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid SrfClientId URN format");
        }

        @Test
        @DisplayName("Should be equal when client numbers are the same")
        void shouldBeEqualWhenClientNumbersAreSame() {
            SrfClientId clientId1 = new SrfClientId("123456789");
            SrfClientId clientId2 = new SrfClientId("123456789");

            assertThat(clientId1).isEqualTo(clientId2);
            assertThat(clientId1.hashCode()).isEqualTo(clientId2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when client numbers differ")
        void shouldNotBeEqualWhenClientNumbersDiffer() {
            SrfClientId clientId1 = new SrfClientId("123456789");
            SrfClientId clientId2 = new SrfClientId("987654321");

            assertThat(clientId1).isNotEqualTo(clientId2);
        }
    }

    @Nested
    @DisplayName("CdrClientId Tests")
    class CdrClientIdTests {

        @Test
        @DisplayName("Should create CdrClientId with valid client number")
        void shouldCreateWithValidClientNumber() {
            CdrClientId clientId = new CdrClientId("000123");

            assertThat(clientId.clientNumber()).isEqualTo("000123");
            assertThat(clientId.urn()).isEqualTo("cdr:000123");
            assertThat(clientId.system()).isEqualTo("cdr");
        }

        @Test
        @DisplayName("Should create CdrClientId from URN")
        void shouldCreateFromUrn() {
            CdrClientId clientId = CdrClientId.of("cdr:000456");

            assertThat(clientId.clientNumber()).isEqualTo("000456");
            assertThat(clientId.urn()).isEqualTo("cdr:000456");
        }

        @Test
        @DisplayName("Should throw exception when client number is null")
        void shouldThrowWhenClientNumberIsNull() {
            assertThatThrownBy(() -> new CdrClientId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Client number cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw exception when client number is blank")
        void shouldThrowWhenClientNumberIsBlank() {
            assertThatThrownBy(() -> new CdrClientId(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Client number cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw exception when URN is null")
        void shouldThrowWhenUrnIsNull() {
            assertThatThrownBy(() -> CdrClientId.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid GidClientId URN format");
        }

        @Test
        @DisplayName("Should throw exception when URN has invalid format")
        void shouldThrowWhenUrnHasInvalidFormat() {
            assertThatThrownBy(() -> CdrClientId.of("srf:123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid GidClientId URN format");
        }

        @Test
        @DisplayName("Should be equal when client numbers are the same")
        void shouldBeEqualWhenClientNumbersAreSame() {
            CdrClientId clientId1 = new CdrClientId("000123");
            CdrClientId clientId2 = new CdrClientId("000123");

            assertThat(clientId1).isEqualTo(clientId2);
            assertThat(clientId1.hashCode()).isEqualTo(clientId2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when client numbers differ")
        void shouldNotBeEqualWhenClientNumbersDiffer() {
            CdrClientId clientId1 = new CdrClientId("000123");
            CdrClientId clientId2 = new CdrClientId("000456");

            assertThat(clientId1).isNotEqualTo(clientId2);
        }
    }

    @Nested
    @DisplayName("BankClientId Tests")
    class BankClientIdTests {

        @Test
        @DisplayName("Should create SrfClientId from URN using BankClientId.of()")
        void shouldCreateSrfClientIdFromUrn() {
            BankClientId clientId = BankClientId.of("srf:123456789");

            assertThat(clientId).isInstanceOf(SrfClientId.class);
            assertThat(clientId.clientNumber()).isEqualTo("123456789");
            assertThat(clientId.system()).isEqualTo("srf");
        }

        @Test
        @DisplayName("Should create CdrClientId from URN using BankClientId.of()")
        void shouldCreateCdrClientIdFromUrn() {
            BankClientId clientId = BankClientId.of("cdr:000123");

            assertThat(clientId).isInstanceOf(CdrClientId.class);
            assertThat(clientId.clientNumber()).isEqualTo("000123");
            assertThat(clientId.system()).isEqualTo("cdr");
        }

        @Test
        @DisplayName("Should throw exception when URN is null")
        void shouldThrowWhenUrnIsNull() {
            assertThatThrownBy(() -> BankClientId.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BankClientId URN cannot be null");
        }

        @Test
        @DisplayName("Should throw exception when URN has invalid prefix")
        void shouldThrowWhenUrnHasInvalidPrefix() {
            assertThatThrownBy(() -> BankClientId.of("invalid:123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid BankClientId URN format. Expected prefixes: srf: or cdr:");
        }
    }

    @Nested
    @DisplayName("ClientId.of() Factory Method Tests")
    class ClientIdFactoryTests {

        @Test
        @DisplayName("Should create SrfClientId from srf: URN")
        void shouldCreateSrfClientId() {
            ClientId clientId = ClientId.of("srf:123456789");

            assertThat(clientId).isInstanceOf(SrfClientId.class);
            assertThat(((SrfClientId) clientId).clientNumber()).isEqualTo("123456789");
        }

        @Test
        @DisplayName("Should create CdrClientId from cdr: URN")
        void shouldCreateCdrClientId() {
            ClientId clientId = ClientId.of("cdr:000123");

            assertThat(clientId).isInstanceOf(CdrClientId.class);
            assertThat(((CdrClientId) clientId).clientNumber()).isEqualTo("000123");
        }

        @Test
        @DisplayName("Should create IndirectClientId from ind: URN")
        void shouldCreateIndirectClientId() {
            IndirectClientId indirectId = IndirectClientId.generate();
            ClientId clientId = ClientId.of(indirectId.urn());

            assertThat(clientId).isInstanceOf(IndirectClientId.class);
            assertThat(clientId.urn()).isEqualTo(indirectId.urn());
        }

        @Test
        @DisplayName("Should throw exception when URN is null")
        void shouldThrowWhenUrnIsNull() {
            assertThatThrownBy(() -> ClientId.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ClientId URN cannot be null");
        }

        @Test
        @DisplayName("Should throw exception when URN has invalid prefix")
        void shouldThrowWhenUrnHasInvalidPrefix() {
            assertThatThrownBy(() -> ClientId.of("unknown:123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid ClientId URN format. Expected prefixes: srf:, cdr:, or ind:");
        }
    }
}
