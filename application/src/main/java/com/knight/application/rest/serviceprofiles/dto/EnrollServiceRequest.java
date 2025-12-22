package com.knight.application.rest.serviceprofiles.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request DTO for enrolling a service to a profile.
 * Optionally includes account IDs to link to the service.
 */
public record EnrollServiceRequest(
    @NotBlank String serviceType,
    String configuration,
    List<AccountLink> accountLinks
) {
    /**
     * Links an account to a service.
     */
    public record AccountLink(
        String clientId,
        String accountId
    ) {}
}
