package com.knight.application.rest.indirectclients.dto;

import java.time.Instant;
import java.util.List;

/**
 * REST DTO for detailed indirect client information.
 * Contains complete indirect client data including related persons and OFI accounts.
 */
public record IndirectClientDetailDto(
    String id,
    String parentClientId,
    String profileId,
    String clientType,
    String businessName,
    String status,
    List<RelatedPersonDto> relatedPersons,
    List<OfiAccountDto> ofiAccounts,
    Instant createdAt,
    Instant updatedAt
) {}
