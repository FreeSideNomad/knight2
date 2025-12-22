package com.knight.application.rest.indirectclients.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * REST request DTO for adding an OFI account to an indirect client.
 */
public record AddOfiAccountRequest(
    @NotBlank(message = "Bank code is required")
    @Pattern(regexp = "\\d{3}", message = "Bank code must be 3 digits")
    String bankCode,

    @NotBlank(message = "Transit number is required")
    @Pattern(regexp = "\\d{5}", message = "Transit number must be 5 digits")
    String transitNumber,

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "\\d{7,12}", message = "Account number must be 7-12 digits")
    String accountNumber,

    String accountHolderName
) {}
