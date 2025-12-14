package com.knight.domain.serviceprofiles.api.queries;

import com.knight.platform.sharedkernel.ServicingProfileId;

/**
 * Query interface for Servicing Profile Management.
 */
public interface ServicingProfileQueries {

    record ServicingProfileSummary(
        String profileUrn,
        String status,
        int enrolledServices,
        int enrolledAccounts
    ) {}

    ServicingProfileSummary getServicingProfileSummary(ServicingProfileId profileId);
}
