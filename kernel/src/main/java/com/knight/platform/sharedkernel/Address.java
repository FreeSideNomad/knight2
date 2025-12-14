package com.knight.platform.sharedkernel;

import java.util.Objects;

/**
 * Address value object representing a physical address.
 * Immutable record with validation for required fields.
 */
public record Address(
    String addressLine1,
    String addressLine2,
    String city,
    String stateProvince,
    String zipPostalCode,
    String countryCode
) {

    public Address {
        if (addressLine1 == null || addressLine1.isBlank()) {
            throw new IllegalArgumentException("Address line 1 cannot be null or blank");
        }
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("City cannot be null or blank");
        }
        if (stateProvince == null || stateProvince.isBlank()) {
            throw new IllegalArgumentException("State/Province cannot be null or blank");
        }
        if (zipPostalCode == null || zipPostalCode.isBlank()) {
            throw new IllegalArgumentException("Zip/Postal code cannot be null or blank");
        }
        if (countryCode == null || !countryCode.matches("^[A-Z]{2}$")) {
            throw new IllegalArgumentException(
                "Country code must be exactly 2 uppercase letters (ISO 3166-1 alpha-2)");
        }

        // Normalize addressLine2 - convert empty string to null
        addressLine2 = (addressLine2 == null || addressLine2.isBlank()) ? null : addressLine2;
    }

    /**
     * Static factory method for convenient construction.
     *
     * @param addressLine1 First line of address (required)
     * @param addressLine2 Second line of address (optional)
     * @param city City name (required)
     * @param stateProvince State or province (required)
     * @param zipPostalCode Zip or postal code (required)
     * @param countryCode ISO 3166-1 alpha-2 country code (required, 2 uppercase letters)
     * @return Address instance
     */
    public static Address of(
        String addressLine1,
        String addressLine2,
        String city,
        String stateProvince,
        String zipPostalCode,
        String countryCode
    ) {
        return new Address(addressLine1, addressLine2, city, stateProvince, zipPostalCode, countryCode);
    }

    /**
     * Returns a formatted multi-line address string.
     *
     * @return Formatted address with each component on a separate line
     */
    public String formatted() {
        StringBuilder sb = new StringBuilder();
        sb.append(addressLine1);

        if (addressLine2 != null) {
            sb.append("\n").append(addressLine2);
        }

        sb.append("\n").append(city).append(", ").append(stateProvince).append(" ").append(zipPostalCode);
        sb.append("\n").append(countryCode);

        return sb.toString();
    }
}
