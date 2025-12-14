package com.knight.domain.serviceprofiles.api.commands;

import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.ServicingProfileId;

/**
 * Command interface for Servicing Profile Management.
 * Defines contract for creating and managing servicing profiles.
 */
public interface ServicingProfileCommands {

    /**
     * Create a new servicing profile for a client
     */
    ServicingProfileId createServicingProfile(ClientId clientId, String createdBy);

    /**
     * Enroll a service to a servicing profile
     */
    record EnrollServiceCmd(
        ServicingProfileId profileId,
        String serviceType,
        String configurationJson
    ) {}

    void enrollService(EnrollServiceCmd cmd);

    /**
     * Enroll an account to a service
     */
    record EnrollAccountCmd(
        ServicingProfileId profileId,
        String serviceEnrollmentId,
        String accountId
    ) {}

    void enrollAccount(EnrollAccountCmd cmd);

    /**
     * Suspend a servicing profile
     */
    record SuspendProfileCmd(
        ServicingProfileId profileId,
        String reason
    ) {}

    void suspendProfile(SuspendProfileCmd cmd);
}
