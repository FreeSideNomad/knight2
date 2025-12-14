package com.knight.domain.serviceprofiles.service;

import com.knight.domain.serviceprofiles.aggregate.ServicingProfile;
import com.knight.domain.serviceprofiles.api.commands.ServicingProfileCommands;
import com.knight.domain.serviceprofiles.api.events.ServicingProfileCreated;
import com.knight.domain.serviceprofiles.api.queries.ServicingProfileQueries;
import com.knight.domain.serviceprofiles.repository.ServicingProfileRepository;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.ServicingProfileId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Application service for Servicing Profile Management.
 * Orchestrates servicing profile operations with transactions and event publishing.
 */
@Service
public class ServicingProfileApplicationService implements ServicingProfileCommands, ServicingProfileQueries {

    private final ServicingProfileRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public ServicingProfileApplicationService(
        ServicingProfileRepository repository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public ServicingProfileId createServicingProfile(ClientId clientId, String createdBy) {
        ServicingProfile profile = ServicingProfile.create(clientId, createdBy);

        repository.save(profile);

        eventPublisher.publishEvent(new ServicingProfileCreated(
            profile.profileId().urn(),
            clientId.urn(),
            profile.status().name(),
            createdBy,
            Instant.now()
        ));

        return profile.profileId();
    }

    @Override
    @Transactional
    public void enrollService(EnrollServiceCmd cmd) {
        ServicingProfile profile = repository.findById(cmd.profileId())
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + cmd.profileId().urn()));

        profile.enrollService(cmd.serviceType(), cmd.configurationJson());

        repository.save(profile);
    }

    @Override
    @Transactional
    public void enrollAccount(EnrollAccountCmd cmd) {
        ServicingProfile profile = repository.findById(cmd.profileId())
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + cmd.profileId().urn()));

        profile.enrollAccount(cmd.serviceEnrollmentId(), cmd.accountId());

        repository.save(profile);
    }

    @Override
    @Transactional
    public void suspendProfile(SuspendProfileCmd cmd) {
        ServicingProfile profile = repository.findById(cmd.profileId())
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + cmd.profileId().urn()));

        profile.suspend(cmd.reason());

        repository.save(profile);
    }

    @Override
    public ServicingProfileSummary getServicingProfileSummary(ServicingProfileId profileId) {
        ServicingProfile profile = repository.findById(profileId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId.urn()));

        return new ServicingProfileSummary(
            profile.profileId().urn(),
            profile.status().name(),
            profile.serviceEnrollments().size(),
            profile.accountEnrollments().size()
        );
    }
}
