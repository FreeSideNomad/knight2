package com.knight.application.service;

import com.knight.domain.auth0identity.api.Auth0IdentityService;
import com.knight.domain.batch.service.PayorEnrolmentProcessor;
import com.knight.domain.batch.types.BatchItemResult;
import com.knight.domain.batch.types.PayorEnrolmentRequest;
import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.domain.indirectclients.types.Email;
import com.knight.domain.indirectclients.types.PersonRole;
import com.knight.domain.indirectclients.types.Phone;
import com.knight.domain.serviceprofiles.aggregate.Profile;
import com.knight.domain.serviceprofiles.repository.ServicingProfileRepository;
import com.knight.domain.serviceprofiles.types.ServiceType;
import com.knight.domain.users.api.commands.UserCommands;
import com.knight.domain.users.api.queries.UserQueries;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Implementation of PayorEnrolmentProcessor.
 * Coordinates the creation of indirect clients, indirect profiles with PAYOR service, and users.
 */
@Service
public class PayorEnrolmentProcessorImpl implements PayorEnrolmentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PayorEnrolmentProcessorImpl.class);

    private final IndirectClientRepository indirectClientRepository;
    private final ServicingProfileRepository profileRepository;
    private final UserCommands userCommands;
    private final UserQueries userQueries;
    private final Auth0IdentityService auth0IdentityService;

    public PayorEnrolmentProcessorImpl(
            IndirectClientRepository indirectClientRepository,
            ServicingProfileRepository profileRepository,
            UserCommands userCommands,
            UserQueries userQueries,
            Auth0IdentityService auth0IdentityService) {
        this.indirectClientRepository = indirectClientRepository;
        this.profileRepository = profileRepository;
        this.userCommands = userCommands;
        this.userQueries = userQueries;
        this.auth0IdentityService = auth0IdentityService;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchItemResult processPayor(ProfileId sourceProfileId, PayorEnrolmentRequest request, String createdBy) {
        // 1. Get the source profile to find parent client
        Profile sourceProfile = profileRepository.findById(sourceProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Source profile not found: " + sourceProfileId.urn()));

        ClientId parentClientId = sourceProfile.primaryClientId();

        // 2. Generate UUID-based IndirectClientId
        IndirectClientId indirectClientId = IndirectClientId.generate();

        // 3. Create IndirectClient first (linked to source online profile)
        IndirectClient indirectClient = IndirectClient.create(
                indirectClientId,
                parentClientId,
                sourceProfileId,
                request.businessName(),
                request.externalReference(),
                createdBy
        );

        // 4. Add related persons
        for (PayorEnrolmentRequest.PersonRequest person : request.persons()) {
            indirectClient.addRelatedPerson(
                    person.name(),
                    PersonRole.valueOf(person.role()),
                    person.email() != null ? Email.of(person.email()) : null,
                    person.phone() != null ? Phone.of(person.phone()) : null
            );
        }

        indirectClientRepository.save(indirectClient);

        // 5. Create INDIRECT profile linked to this IndirectClient with PAYOR service
        Profile indirectProfile = Profile.createIndirectProfile(
                indirectClientId,
                request.businessName() + " Profile",
                createdBy
        );
        indirectProfile.enrollService(ServiceType.PAYOR.name(), "{}");
        profileRepository.save(indirectProfile);

        // 6. Create users for ADMIN persons linked to the INDIRECT profile
        List<String> userIds = new ArrayList<>();
        for (PayorEnrolmentRequest.PersonRequest person : request.persons()) {
            if ("ADMIN".equals(person.role())) {
                // Parse name into first/last
                String[] nameParts = person.name().split(" ", 2);
                String firstName = nameParts[0];
                String lastName = nameParts.length > 1 ? nameParts[1] : "";

                // Generate loginId from email (everything before @)
                String loginId = person.email().split("@")[0].replaceAll("[^a-zA-Z0-9_]", "_");
                if (loginId.length() < 3) {
                    loginId = loginId + "_user";
                } else if (loginId.length() > 50) {
                    loginId = loginId.substring(0, 50);
                }

                UserId userId = userCommands.createUser(new UserCommands.CreateUserCmd(
                        loginId,
                        person.email(),
                        firstName,
                        lastName,
                        "INDIRECT_USER",
                        "AUTH0",
                        indirectProfile.profileId(),
                        Set.of("SECURITY_ADMIN"),
                        createdBy
                ));

                userIds.add(userId.id());

                // Provision to Auth0
                try {
                    userCommands.provisionUser(new UserCommands.ProvisionUserCmd(userId));
                    log.info("Successfully provisioned user {} to Auth0", userId.id());
                } catch (Exception e) {
                    // Log but don't fail the entire import - user can be provisioned later
                    log.error("Failed to provision user {} to Auth0: {}", userId.id(), e.getMessage(), e);
                }
            }
        }

        return new BatchItemResult(
                indirectClientId.urn(),
                indirectProfile.profileId().urn(),
                userIds
        );
    }

    @Override
    public boolean existsByBusinessName(ProfileId parentProfileId, String name) {
        return indirectClientRepository.existsByParentProfileIdAndName(parentProfileId, name);
    }

    @Override
    public boolean existsInIdentityProvider(String email) {
        return auth0IdentityService.getUserByEmail(email).isPresent();
    }

    @Override
    public boolean existsByEmail(String email) {
        return userQueries.existsByEmail(email);
    }
}
