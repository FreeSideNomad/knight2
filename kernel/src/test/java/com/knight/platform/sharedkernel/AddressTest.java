package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Address Tests")
class AddressTest {

    @Nested
    @DisplayName("Creation Tests")
    class CreationTests {

        @Test
        @DisplayName("Should create address with all fields")
        void shouldCreateWithAllFields() {
            Address address = new Address(
                "123 Main St",
                "Apt 4B",
                "New York",
                "NY",
                "10001",
                "US"
            );

            assertThat(address.addressLine1()).isEqualTo("123 Main St");
            assertThat(address.addressLine2()).isEqualTo("Apt 4B");
            assertThat(address.city()).isEqualTo("New York");
            assertThat(address.stateProvince()).isEqualTo("NY");
            assertThat(address.zipPostalCode()).isEqualTo("10001");
            assertThat(address.countryCode()).isEqualTo("US");
        }

        @Test
        @DisplayName("Should create address without address line 2")
        void shouldCreateWithoutAddressLine2() {
            Address address = new Address(
                "456 Oak Ave",
                null,
                "Los Angeles",
                "CA",
                "90001",
                "US"
            );

            assertThat(address.addressLine1()).isEqualTo("456 Oak Ave");
            assertThat(address.addressLine2()).isNull();
            assertThat(address.city()).isEqualTo("Los Angeles");
        }

        @Test
        @DisplayName("Should normalize blank address line 2 to null")
        void shouldNormalizeBlankAddressLine2ToNull() {
            Address address = new Address(
                "789 Pine Rd",
                "   ",
                "Chicago",
                "IL",
                "60601",
                "US"
            );

            assertThat(address.addressLine2()).isNull();
        }

        @Test
        @DisplayName("Should normalize empty address line 2 to null")
        void shouldNormalizeEmptyAddressLine2ToNull() {
            Address address = new Address(
                "321 Elm St",
                "",
                "Houston",
                "TX",
                "77001",
                "US"
            );

            assertThat(address.addressLine2()).isNull();
        }

        @Test
        @DisplayName("Should create address using static factory method")
        void shouldCreateUsingStaticFactoryMethod() {
            Address address = Address.of(
                "100 Broadway",
                "Suite 500",
                "New York",
                "NY",
                "10005",
                "US"
            );

            assertThat(address.addressLine1()).isEqualTo("100 Broadway");
            assertThat(address.addressLine2()).isEqualTo("Suite 500");
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should throw exception when address line 1 is null")
        void shouldThrowWhenAddressLine1IsNull() {
            assertThatThrownBy(() -> new Address(
                null,
                null,
                "New York",
                "NY",
                "10001",
                "US"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Address line 1 cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw exception when address line 1 is blank")
        void shouldThrowWhenAddressLine1IsBlank() {
            assertThatThrownBy(() -> new Address(
                "   ",
                null,
                "New York",
                "NY",
                "10001",
                "US"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Address line 1 cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw exception when city is null")
        void shouldThrowWhenCityIsNull() {
            assertThatThrownBy(() -> new Address(
                "123 Main St",
                null,
                null,
                "NY",
                "10001",
                "US"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("City cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw exception when city is blank")
        void shouldThrowWhenCityIsBlank() {
            assertThatThrownBy(() -> new Address(
                "123 Main St",
                null,
                "",
                "NY",
                "10001",
                "US"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("City cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw exception when state/province is null")
        void shouldThrowWhenStateProvinceIsNull() {
            assertThatThrownBy(() -> new Address(
                "123 Main St",
                null,
                "New York",
                null,
                "10001",
                "US"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("State/Province cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw exception when state/province is blank")
        void shouldThrowWhenStateProvinceIsBlank() {
            assertThatThrownBy(() -> new Address(
                "123 Main St",
                null,
                "New York",
                "   ",
                "10001",
                "US"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("State/Province cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw exception when zip/postal code is null")
        void shouldThrowWhenZipPostalCodeIsNull() {
            assertThatThrownBy(() -> new Address(
                "123 Main St",
                null,
                "New York",
                "NY",
                null,
                "US"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Zip/Postal code cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw exception when zip/postal code is blank")
        void shouldThrowWhenZipPostalCodeIsBlank() {
            assertThatThrownBy(() -> new Address(
                "123 Main St",
                null,
                "New York",
                "NY",
                "",
                "US"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Zip/Postal code cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw exception when country code is null")
        void shouldThrowWhenCountryCodeIsNull() {
            assertThatThrownBy(() -> new Address(
                "123 Main St",
                null,
                "New York",
                "NY",
                "10001",
                null
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Country code must be exactly 2 uppercase letters (ISO 3166-1 alpha-2)");
        }

        @Test
        @DisplayName("Should throw exception when country code is not 2 characters")
        void shouldThrowWhenCountryCodeIsNot2Characters() {
            assertThatThrownBy(() -> new Address(
                "123 Main St",
                null,
                "New York",
                "NY",
                "10001",
                "USA"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Country code must be exactly 2 uppercase letters (ISO 3166-1 alpha-2)");
        }

        @Test
        @DisplayName("Should throw exception when country code is lowercase")
        void shouldThrowWhenCountryCodeIsLowercase() {
            assertThatThrownBy(() -> new Address(
                "123 Main St",
                null,
                "New York",
                "NY",
                "10001",
                "us"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Country code must be exactly 2 uppercase letters (ISO 3166-1 alpha-2)");
        }

        @Test
        @DisplayName("Should throw exception when country code contains numbers")
        void shouldThrowWhenCountryCodeContainsNumbers() {
            assertThatThrownBy(() -> new Address(
                "123 Main St",
                null,
                "New York",
                "NY",
                "10001",
                "U1"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Country code must be exactly 2 uppercase letters (ISO 3166-1 alpha-2)");
        }

        @Test
        @DisplayName("Should accept valid 2-letter uppercase country codes")
        void shouldAcceptValid2LetterUppercaseCountryCodes() {
            Address usAddress = new Address("123 Main St", null, "New York", "NY", "10001", "US");
            Address caAddress = new Address("456 King St", null, "Toronto", "ON", "M5H 1A1", "CA");
            Address gbAddress = new Address("10 Downing St", null, "London", "ENG", "SW1A 2AA", "GB");

            assertThat(usAddress.countryCode()).isEqualTo("US");
            assertThat(caAddress.countryCode()).isEqualTo("CA");
            assertThat(gbAddress.countryCode()).isEqualTo("GB");
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenAllFieldsMatch() {
            Address address1 = new Address("123 Main St", "Apt 4B", "New York", "NY", "10001", "US");
            Address address2 = new Address("123 Main St", "Apt 4B", "New York", "NY", "10001", "US");

            assertThat(address1).isEqualTo(address2);
            assertThat(address1.hashCode()).isEqualTo(address2.hashCode());
        }

        @Test
        @DisplayName("Should be equal when both have null address line 2")
        void shouldBeEqualWhenBothHaveNullAddressLine2() {
            Address address1 = new Address("123 Main St", null, "New York", "NY", "10001", "US");
            Address address2 = new Address("123 Main St", null, "New York", "NY", "10001", "US");

            assertThat(address1).isEqualTo(address2);
        }

        @Test
        @DisplayName("Should not be equal when address line 1 differs")
        void shouldNotBeEqualWhenAddressLine1Differs() {
            Address address1 = new Address("123 Main St", null, "New York", "NY", "10001", "US");
            Address address2 = new Address("456 Oak Ave", null, "New York", "NY", "10001", "US");

            assertThat(address1).isNotEqualTo(address2);
        }

        @Test
        @DisplayName("Should not be equal when address line 2 differs")
        void shouldNotBeEqualWhenAddressLine2Differs() {
            Address address1 = new Address("123 Main St", "Apt 4B", "New York", "NY", "10001", "US");
            Address address2 = new Address("123 Main St", "Suite 5", "New York", "NY", "10001", "US");

            assertThat(address1).isNotEqualTo(address2);
        }

        @Test
        @DisplayName("Should not be equal when city differs")
        void shouldNotBeEqualWhenCityDiffers() {
            Address address1 = new Address("123 Main St", null, "New York", "NY", "10001", "US");
            Address address2 = new Address("123 Main St", null, "Boston", "NY", "10001", "US");

            assertThat(address1).isNotEqualTo(address2);
        }

        @Test
        @DisplayName("Should not be equal when state/province differs")
        void shouldNotBeEqualWhenStateProvinceDiffers() {
            Address address1 = new Address("123 Main St", null, "New York", "NY", "10001", "US");
            Address address2 = new Address("123 Main St", null, "New York", "CA", "10001", "US");

            assertThat(address1).isNotEqualTo(address2);
        }

        @Test
        @DisplayName("Should not be equal when zip/postal code differs")
        void shouldNotBeEqualWhenZipPostalCodeDiffers() {
            Address address1 = new Address("123 Main St", null, "New York", "NY", "10001", "US");
            Address address2 = new Address("123 Main St", null, "New York", "NY", "10002", "US");

            assertThat(address1).isNotEqualTo(address2);
        }

        @Test
        @DisplayName("Should not be equal when country code differs")
        void shouldNotBeEqualWhenCountryCodeDiffers() {
            Address address1 = new Address("123 Main St", null, "New York", "NY", "10001", "US");
            Address address2 = new Address("123 Main St", null, "New York", "NY", "10001", "CA");

            assertThat(address1).isNotEqualTo(address2);
        }

        @Test
        @DisplayName("Should not be equal to null")
        void shouldNotBeEqualToNull() {
            Address address = new Address("123 Main St", null, "New York", "NY", "10001", "US");

            assertThat(address).isNotEqualTo(null);
        }

        @Test
        @DisplayName("Should be equal to itself")
        void shouldBeEqualToItself() {
            Address address = new Address("123 Main St", null, "New York", "NY", "10001", "US");

            assertThat(address).isEqualTo(address);
        }
    }

    @Nested
    @DisplayName("toString() and formatted() Tests")
    class StringRepresentationTests {

        @Test
        @DisplayName("Should return formatted address with all lines")
        void shouldReturnFormattedAddressWithAllLines() {
            Address address = new Address(
                "123 Main St",
                "Apt 4B",
                "New York",
                "NY",
                "10001",
                "US"
            );

            String formatted = address.formatted();

            assertThat(formatted)
                .isEqualTo("123 Main St\nApt 4B\nNew York, NY 10001\nUS");
        }

        @Test
        @DisplayName("Should return formatted address without address line 2")
        void shouldReturnFormattedAddressWithoutAddressLine2() {
            Address address = new Address(
                "456 Oak Ave",
                null,
                "Los Angeles",
                "CA",
                "90001",
                "US"
            );

            String formatted = address.formatted();

            assertThat(formatted)
                .isEqualTo("456 Oak Ave\nLos Angeles, CA 90001\nUS");
        }

        @Test
        @DisplayName("Should format Canadian address correctly")
        void shouldFormatCanadianAddressCorrectly() {
            Address address = new Address(
                "100 Queen St",
                "Unit 200",
                "Toronto",
                "ON",
                "M5H 2N2",
                "CA"
            );

            String formatted = address.formatted();

            assertThat(formatted)
                .contains("100 Queen St")
                .contains("Unit 200")
                .contains("Toronto, ON M5H 2N2")
                .contains("CA");
        }

        @Test
        @DisplayName("Should include all address components in formatted output")
        void shouldIncludeAllComponentsInFormatted() {
            Address address = new Address(
                "789 Pine Rd",
                "Building C",
                "Seattle",
                "WA",
                "98101",
                "US"
            );

            String formatted = address.formatted();

            assertThat(formatted).contains("789 Pine Rd");
            assertThat(formatted).contains("Building C");
            assertThat(formatted).contains("Seattle");
            assertThat(formatted).contains("WA");
            assertThat(formatted).contains("98101");
            assertThat(formatted).contains("US");
        }

        @Test
        @DisplayName("Should have proper line breaks in formatted output")
        void shouldHaveProperLineBreaksInFormatted() {
            Address address = new Address(
                "321 Elm St",
                "Floor 3",
                "Boston",
                "MA",
                "02101",
                "US"
            );

            String formatted = address.formatted();
            String[] lines = formatted.split("\n");

            assertThat(lines).hasSize(4);
            assertThat(lines[0]).isEqualTo("321 Elm St");
            assertThat(lines[1]).isEqualTo("Floor 3");
            assertThat(lines[2]).isEqualTo("Boston, MA 02101");
            assertThat(lines[3]).isEqualTo("US");
        }

        @Test
        @DisplayName("Should have 3 lines in formatted output when address line 2 is null")
        void shouldHave3LinesWhenAddressLine2IsNull() {
            Address address = new Address(
                "555 Broadway",
                null,
                "San Francisco",
                "CA",
                "94102",
                "US"
            );

            String formatted = address.formatted();
            String[] lines = formatted.split("\n");

            assertThat(lines).hasSize(3);
            assertThat(lines[0]).isEqualTo("555 Broadway");
            assertThat(lines[1]).isEqualTo("San Francisco, CA 94102");
            assertThat(lines[2]).isEqualTo("US");
        }
    }

    @Nested
    @DisplayName("International Address Tests")
    class InternationalAddressTests {

        @Test
        @DisplayName("Should create UK address")
        void shouldCreateUkAddress() {
            Address address = new Address(
                "10 Downing Street",
                null,
                "London",
                "ENG",
                "SW1A 2AA",
                "GB"
            );

            assertThat(address.countryCode()).isEqualTo("GB");
            assertThat(address.formatted()).contains("London");
        }

        @Test
        @DisplayName("Should create French address")
        void shouldCreateFrenchAddress() {
            Address address = new Address(
                "1 Rue de la Paix",
                null,
                "Paris",
                "IDF",
                "75001",
                "FR"
            );

            assertThat(address.countryCode()).isEqualTo("FR");
            assertThat(address.city()).isEqualTo("Paris");
        }

        @Test
        @DisplayName("Should create German address")
        void shouldCreateGermanAddress() {
            Address address = new Address(
                "Unter den Linden 1",
                null,
                "Berlin",
                "BE",
                "10117",
                "DE"
            );

            assertThat(address.countryCode()).isEqualTo("DE");
            assertThat(address.zipPostalCode()).isEqualTo("10117");
        }

        @Test
        @DisplayName("Should create Australian address")
        void shouldCreateAustralianAddress() {
            Address address = new Address(
                "1 Sydney Harbour Bridge",
                null,
                "Sydney",
                "NSW",
                "2000",
                "AU"
            );

            assertThat(address.countryCode()).isEqualTo("AU");
            assertThat(address.stateProvince()).isEqualTo("NSW");
        }
    }
}
