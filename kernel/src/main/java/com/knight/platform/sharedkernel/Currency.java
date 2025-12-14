package com.knight.platform.sharedkernel;

import java.util.Set;

/**
 * Currency value object representing an ISO 4217 currency code.
 * Immutable and validated against a set of common ISO 4217 codes.
 */
public record Currency(String code) {

    private static final Set<String> VALID_ISO_CODES = Set.of(
        "CAD", "USD", "EUR", "GBP", "JPY", "AUD", "CHF", "CNY",
        "HKD", "NZD", "SEK", "KRW", "SGD", "NOK", "MXN", "INR",
        "BRL", "ZAR"
    );

    // Common currency constants
    public static final Currency CAD = new Currency("CAD");
    public static final Currency USD = new Currency("USD");
    public static final Currency EUR = new Currency("EUR");
    public static final Currency GBP = new Currency("GBP");

    public Currency {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Currency code cannot be null or blank");
        }

        if (code.length() != 3) {
            throw new IllegalArgumentException(
                "Currency code must be exactly 3 characters, got: " + code.length());
        }

        if (!code.equals(code.toUpperCase())) {
            throw new IllegalArgumentException(
                "Currency code must be uppercase, got: " + code);
        }

        if (!code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                "Currency code must contain only uppercase letters, got: " + code);
        }

        if (!VALID_ISO_CODES.contains(code)) {
            throw new IllegalArgumentException(
                "Invalid ISO 4217 currency code: " + code);
        }
    }

    /**
     * Creates a Currency instance from an ISO 4217 currency code.
     *
     * @param code the 3-letter ISO 4217 currency code
     * @return a Currency instance
     * @throws IllegalArgumentException if the code is invalid
     */
    public static Currency of(String code) {
        return new Currency(code);
    }
}
