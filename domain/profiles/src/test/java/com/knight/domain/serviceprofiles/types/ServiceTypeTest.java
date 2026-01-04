package com.knight.domain.serviceprofiles.types;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ServiceType enum.
 */
class ServiceTypeTest {

    @Nested
    @DisplayName("Enum Values")
    class EnumValuesTests {

        @Test
        @DisplayName("should have RECEIVABLES service type")
        void shouldHaveReceivablesServiceType() {
            ServiceType receivables = ServiceType.RECEIVABLES;

            assertThat(receivables).isNotNull();
            assertThat(receivables.name()).isEqualTo("RECEIVABLES");
        }

        @Test
        @DisplayName("should have PAYOR service type")
        void shouldHavePayorServiceType() {
            ServiceType payor = ServiceType.PAYOR;

            assertThat(payor).isNotNull();
            assertThat(payor.name()).isEqualTo("PAYOR");
        }

        @Test
        @DisplayName("should have exactly two service types")
        void shouldHaveExactlyTwoServiceTypes() {
            assertThat(ServiceType.values()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Display Names")
    class DisplayNamesTests {

        @Test
        @DisplayName("should return display name for RECEIVABLES")
        void shouldReturnDisplayNameForReceivables() {
            assertThat(ServiceType.RECEIVABLES.displayName()).isEqualTo("Receivable Service");
        }

        @Test
        @DisplayName("should return display name for PAYOR")
        void shouldReturnDisplayNameForPayor() {
            assertThat(ServiceType.PAYOR.displayName()).isEqualTo("Payor Service");
        }
    }

    @Nested
    @DisplayName("Descriptions")
    class DescriptionsTests {

        @Test
        @DisplayName("should return description for RECEIVABLES")
        void shouldReturnDescriptionForReceivables() {
            assertThat(ServiceType.RECEIVABLES.description()).isEqualTo("Manage receivables and indirect clients");
        }

        @Test
        @DisplayName("should return description for PAYOR")
        void shouldReturnDescriptionForPayor() {
            assertThat(ServiceType.PAYOR.description()).isEqualTo("Process payments from indirect client accounts");
        }
    }

    @Nested
    @DisplayName("Enum Operations")
    class EnumOperationsTests {

        @Test
        @DisplayName("should parse from string")
        void shouldParseFromString() {
            ServiceType receivables = ServiceType.valueOf("RECEIVABLES");
            ServiceType payor = ServiceType.valueOf("PAYOR");

            assertThat(receivables).isEqualTo(ServiceType.RECEIVABLES);
            assertThat(payor).isEqualTo(ServiceType.PAYOR);
        }

        @Test
        @DisplayName("should throw for invalid string")
        void shouldThrowForInvalidString() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> ServiceType.valueOf("INVALID"));
        }
    }
}
