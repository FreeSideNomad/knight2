package com.knight.application.persistence.profiles.mapper;

import com.knight.application.persistence.profiles.entity.AccountEnrollmentEntity;
import com.knight.application.persistence.profiles.entity.ClientEnrollmentEntity;
import com.knight.application.persistence.profiles.entity.ProfileEntity;
import com.knight.application.persistence.profiles.entity.ServiceEnrollmentEntity;
import com.knight.domain.serviceprofiles.aggregate.Profile;
import com.knight.domain.serviceprofiles.types.AccountEnrollmentType;
import com.knight.domain.serviceprofiles.types.ProfileStatus;
import com.knight.domain.serviceprofiles.types.ProfileType;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.EnrollmentId;
import com.knight.platform.sharedkernel.ProfileId;
import org.mapstruct.Mapper;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MapStruct mapper for Profile aggregate.
 */
@Mapper(componentModel = "spring")
public interface ServicingProfileMapper {

    /**
     * Converts Profile domain aggregate to entity.
     */
    default ProfileEntity toEntity(Profile domain) {
        if (domain == null) {
            return null;
        }

        ProfileEntity entity = new ProfileEntity();
        entity.setProfileId(domain.profileId().urn());
        entity.setName(domain.name());
        entity.setProfileType(domain.profileType().name());
        entity.setStatus(domain.status().name());
        entity.setCreatedAt(domain.createdAt());
        entity.setCreatedBy(domain.createdBy());
        entity.setUpdatedAt(domain.updatedAt());

        // Map client enrollments
        List<ClientEnrollmentEntity> clientEnrollments = new ArrayList<>();
        for (Profile.ClientEnrollment ce : domain.clientEnrollments()) {
            ClientEnrollmentEntity ceEntity = new ClientEnrollmentEntity();
            ceEntity.setId(ce.enrollmentId().value());
            ceEntity.setProfile(entity);
            ceEntity.setClientId(ce.clientId().urn());
            ceEntity.setPrimary(ce.isPrimary());
            ceEntity.setAccountEnrollmentType(ce.accountEnrollmentType().name());
            ceEntity.setEnrolledAt(ce.enrolledAt());
            clientEnrollments.add(ceEntity);
        }
        entity.setClientEnrollments(clientEnrollments);

        // Map service enrollments
        List<ServiceEnrollmentEntity> serviceEnrollments = new ArrayList<>();
        for (Profile.ServiceEnrollment se : domain.serviceEnrollments()) {
            ServiceEnrollmentEntity seEntity = new ServiceEnrollmentEntity();
            seEntity.setEnrollmentId(se.enrollmentId().value());
            seEntity.setProfile(entity);
            seEntity.setServiceType(se.serviceType());
            seEntity.setConfiguration(se.configuration());
            seEntity.setStatus(se.status().name());
            seEntity.setEnrolledAt(se.enrolledAt());
            serviceEnrollments.add(seEntity);
        }
        entity.setServiceEnrollments(serviceEnrollments);

        // Map account enrollments
        List<AccountEnrollmentEntity> accountEnrollments = new ArrayList<>();
        for (Profile.AccountEnrollment ae : domain.accountEnrollments()) {
            AccountEnrollmentEntity aeEntity = new AccountEnrollmentEntity();
            aeEntity.setEnrollmentId(ae.enrollmentId().value());
            aeEntity.setProfile(entity);
            aeEntity.setServiceEnrollmentId(ae.serviceEnrollmentId() != null ? ae.serviceEnrollmentId().value() : null);
            aeEntity.setClientId(ae.clientId().urn());
            aeEntity.setAccountId(ae.accountId().urn());
            aeEntity.setStatus(ae.status().name());
            aeEntity.setEnrolledAt(ae.enrolledAt());
            accountEnrollments.add(aeEntity);
        }
        entity.setAccountEnrollments(accountEnrollments);

        return entity;
    }

    /**
     * Updates an existing entity with values from the domain aggregate.
     * This preserves JPA-managed collections to avoid orphan removal issues.
     */
    default void updateEntity(ProfileEntity entity, Profile domain) {
        entity.setName(domain.name());
        entity.setProfileType(domain.profileType().name());
        entity.setStatus(domain.status().name());
        entity.setUpdatedAt(domain.updatedAt());

        // Update client enrollments - sync the collections
        syncClientEnrollments(entity, domain);

        // Update service enrollments - sync the collections
        syncServiceEnrollments(entity, domain);

        // Update account enrollments - sync the collections
        syncAccountEnrollments(entity, domain);
    }

    private void syncClientEnrollments(ProfileEntity entity, Profile domain) {
        // Build map of existing entities by UUID (UUID.equals handles case-insensitivity)
        Map<UUID, ClientEnrollmentEntity> existingMap = new HashMap<>();
        for (ClientEnrollmentEntity ce : entity.getClientEnrollments()) {
            existingMap.put(ce.getId(), ce);
        }

        // Build set of domain UUIDs
        Set<UUID> domainIds = new HashSet<>();
        for (Profile.ClientEnrollment ce : domain.clientEnrollments()) {
            domainIds.add(ce.enrollmentId().value());
        }

        // Remove entities not in domain (UUID.equals is case-insensitive)
        entity.getClientEnrollments().removeIf(ce -> !domainIds.contains(ce.getId()));

        // Add or update entities from domain
        for (Profile.ClientEnrollment ce : domain.clientEnrollments()) {
            UUID id = ce.enrollmentId().value();
            ClientEnrollmentEntity ceEntity = existingMap.get(id);

            if (ceEntity == null) {
                // New enrollment - add it
                ceEntity = new ClientEnrollmentEntity();
                ceEntity.setId(id);
                ceEntity.setProfile(entity);
                entity.getClientEnrollments().add(ceEntity);
            }

            // Update fields
            ceEntity.setClientId(ce.clientId().urn());
            ceEntity.setPrimary(ce.isPrimary());
            ceEntity.setAccountEnrollmentType(ce.accountEnrollmentType().name());
            ceEntity.setEnrolledAt(ce.enrolledAt());
        }
    }

    private void syncServiceEnrollments(ProfileEntity entity, Profile domain) {
        // Build map of existing entities by UUID
        Map<UUID, ServiceEnrollmentEntity> existingMap = new HashMap<>();
        for (ServiceEnrollmentEntity se : entity.getServiceEnrollments()) {
            existingMap.put(se.getEnrollmentId(), se);
        }

        // Build set of domain UUIDs
        Set<UUID> domainIds = new HashSet<>();
        for (Profile.ServiceEnrollment se : domain.serviceEnrollments()) {
            domainIds.add(se.enrollmentId().value());
        }

        entity.getServiceEnrollments().removeIf(se -> !domainIds.contains(se.getEnrollmentId()));

        for (Profile.ServiceEnrollment se : domain.serviceEnrollments()) {
            UUID id = se.enrollmentId().value();
            ServiceEnrollmentEntity seEntity = existingMap.get(id);

            if (seEntity == null) {
                seEntity = new ServiceEnrollmentEntity();
                seEntity.setEnrollmentId(id);
                seEntity.setProfile(entity);
                entity.getServiceEnrollments().add(seEntity);
            }

            seEntity.setServiceType(se.serviceType());
            seEntity.setConfiguration(se.configuration());
            seEntity.setStatus(se.status().name());
            seEntity.setEnrolledAt(se.enrolledAt());
        }
    }

    private void syncAccountEnrollments(ProfileEntity entity, Profile domain) {
        // Build map of existing entities by UUID
        Map<UUID, AccountEnrollmentEntity> existingMap = new HashMap<>();
        for (AccountEnrollmentEntity ae : entity.getAccountEnrollments()) {
            existingMap.put(ae.getEnrollmentId(), ae);
        }

        // Build set of domain UUIDs
        Set<UUID> domainIds = new HashSet<>();
        for (Profile.AccountEnrollment ae : domain.accountEnrollments()) {
            domainIds.add(ae.enrollmentId().value());
        }

        entity.getAccountEnrollments().removeIf(ae -> !domainIds.contains(ae.getEnrollmentId()));

        for (Profile.AccountEnrollment ae : domain.accountEnrollments()) {
            UUID id = ae.enrollmentId().value();
            AccountEnrollmentEntity aeEntity = existingMap.get(id);

            if (aeEntity == null) {
                aeEntity = new AccountEnrollmentEntity();
                aeEntity.setEnrollmentId(id);
                aeEntity.setProfile(entity);
                entity.getAccountEnrollments().add(aeEntity);
            }

            aeEntity.setServiceEnrollmentId(ae.serviceEnrollmentId() != null ? ae.serviceEnrollmentId().value() : null);
            aeEntity.setClientId(ae.clientId().urn());
            aeEntity.setAccountId(ae.accountId().urn());
            aeEntity.setStatus(ae.status().name());
            aeEntity.setEnrolledAt(ae.enrolledAt());
        }
    }

    /**
     * Converts entity to Profile domain aggregate.
     * Uses the Profile.reconstitute() factory method to properly reconstruct the aggregate.
     */
    default Profile toDomain(ProfileEntity entity) {
        if (entity == null) {
            return null;
        }

        try {
            // Parse IDs and profile type
            ProfileId profileId = ProfileId.fromUrn(entity.getProfileId());
            ProfileType profileType = ProfileType.valueOf(entity.getProfileType());
            ProfileStatus status = ProfileStatus.valueOf(entity.getStatus());

            // Reconstruct client enrollments with their original IDs
            List<Profile.ClientEnrollment> clientEnrollments = new ArrayList<>();
            for (ClientEnrollmentEntity ceEntity : entity.getClientEnrollments()) {
                Profile.ClientEnrollment ce = reconstructClientEnrollment(ceEntity);
                clientEnrollments.add(ce);
            }

            // Reconstruct service enrollments with their original IDs
            List<Profile.ServiceEnrollment> serviceEnrollments = new ArrayList<>();
            for (ServiceEnrollmentEntity seEntity : entity.getServiceEnrollments()) {
                Profile.ServiceEnrollment se = reconstructServiceEnrollment(seEntity);
                serviceEnrollments.add(se);
            }

            // Reconstruct account enrollments with their original IDs
            List<Profile.AccountEnrollment> accountEnrollments = new ArrayList<>();
            for (AccountEnrollmentEntity aeEntity : entity.getAccountEnrollments()) {
                Profile.AccountEnrollment ae = reconstructAccountEnrollment(aeEntity);
                accountEnrollments.add(ae);
            }

            // Use reconstitute factory method - no reflection needed for final fields
            return Profile.reconstitute(
                profileId,
                profileType,
                entity.getName(),
                entity.getCreatedBy(),
                status,
                clientEnrollments,
                serviceEnrollments,
                accountEnrollments,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to map ProfileEntity to Profile", e);
        }
    }

    private Profile.ClientEnrollment reconstructClientEnrollment(ClientEnrollmentEntity entity) throws Exception {
        // Use the private reconstruction constructor to avoid final field issues
        Constructor<Profile.ClientEnrollment> constructor = Profile.ClientEnrollment.class.getDeclaredConstructor(
            EnrollmentId.class, ClientId.class, boolean.class, AccountEnrollmentType.class, java.time.Instant.class
        );
        constructor.setAccessible(true);

        return constructor.newInstance(
            EnrollmentId.of(entity.getId()),
            ClientId.of(entity.getClientId()),
            entity.isPrimary(),
            AccountEnrollmentType.valueOf(entity.getAccountEnrollmentType()),
            entity.getEnrolledAt()
        );
    }

    private Profile.ServiceEnrollment reconstructServiceEnrollment(ServiceEnrollmentEntity entity) throws Exception {
        // Use the private reconstruction constructor to avoid final field issues
        Constructor<Profile.ServiceEnrollment> constructor = Profile.ServiceEnrollment.class.getDeclaredConstructor(
            EnrollmentId.class, String.class, String.class, ProfileStatus.class, java.time.Instant.class
        );
        constructor.setAccessible(true);

        return constructor.newInstance(
            EnrollmentId.of(entity.getEnrollmentId()),
            entity.getServiceType(),
            entity.getConfiguration(),
            ProfileStatus.valueOf(entity.getStatus()),
            entity.getEnrolledAt()
        );
    }

    private Profile.AccountEnrollment reconstructAccountEnrollment(AccountEnrollmentEntity entity) throws Exception {
        // serviceEnrollmentId can be null for profile-level enrollments
        EnrollmentId serviceEnrollmentId = entity.getServiceEnrollmentId() != null
            ? EnrollmentId.of(entity.getServiceEnrollmentId())
            : null;

        // Use the private reconstruction constructor to avoid final field issues
        Constructor<Profile.AccountEnrollment> constructor = Profile.AccountEnrollment.class.getDeclaredConstructor(
            EnrollmentId.class, EnrollmentId.class, ClientId.class, ClientAccountId.class, ProfileStatus.class, java.time.Instant.class
        );
        constructor.setAccessible(true);

        return constructor.newInstance(
            EnrollmentId.of(entity.getEnrollmentId()),
            serviceEnrollmentId,
            ClientId.of(entity.getClientId()),
            ClientAccountId.of(entity.getAccountId()),
            ProfileStatus.valueOf(entity.getStatus()),
            entity.getEnrolledAt()
        );
    }

}
