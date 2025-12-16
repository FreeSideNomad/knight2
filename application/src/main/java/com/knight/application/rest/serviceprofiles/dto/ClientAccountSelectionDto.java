package com.knight.application.rest.serviceprofiles.dto;

import java.util.List;

/**
 * DTO for client account selection in profile creation.
 */
public record ClientAccountSelectionDto(
    String clientId,
    boolean isPrimary,
    String accountEnrollmentType,  // MANUAL or AUTOMATIC
    List<String> accountIds  // For MANUAL: specific accounts; for AUTOMATIC: ignored
) {}
