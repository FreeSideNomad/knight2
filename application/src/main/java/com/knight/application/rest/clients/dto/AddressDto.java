package com.knight.application.rest.clients.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * REST DTO for Address information.
 * Used in client detail responses.
 */
public record AddressDto(
    @NotBlank(message = "Address line 1 is required")
    String addressLine1,

    String addressLine2,

    @NotBlank(message = "City is required")
    String city,

    @NotBlank(message = "State/Province is required")
    String stateProvince,

    @NotBlank(message = "Zip/Postal code is required")
    String zipPostalCode,

    @NotBlank(message = "Country code is required")
    @Pattern(regexp = "^[A-Z]{2}$", message = "Country code must be exactly 2 uppercase letters")
    String countryCode
) {
}
