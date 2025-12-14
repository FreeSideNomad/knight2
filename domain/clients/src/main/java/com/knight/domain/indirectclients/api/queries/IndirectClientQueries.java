package com.knight.domain.indirectclients.api.queries;

import com.knight.platform.sharedkernel.IndirectClientId;

/**
 * Query interface for Indirect Client Management.
 */
public interface IndirectClientQueries {

    record IndirectClientSummary(
        String indirectClientUrn,
        String businessName,
        String status,
        int relatedPersonsCount
    ) {}

    IndirectClientSummary getIndirectClientSummary(IndirectClientId id);
}
