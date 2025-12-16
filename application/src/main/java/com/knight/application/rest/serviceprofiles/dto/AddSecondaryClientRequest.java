package com.knight.application.rest.serviceprofiles.dto;

import java.util.List;

/**
 * DTO for adding a secondary client to a profile.
 */
public record AddSecondaryClientRequest(
    String clientId,
    String accountEnrollmentType,  // AUTOMATIC or MANUAL
    List<String> accountIds        // Required for MANUAL, ignored for AUTOMATIC
) {}
