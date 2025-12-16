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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
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
            ceEntity.setId(ce.enrollmentId().toString());
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
            seEntity.setEnrollmentId(se.enrollmentId().toString());
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
            aeEntity.setEnrollmentId(ae.enrollmentId().toString());
            aeEntity.setProfile(entity);
            aeEntity.setServiceEnrollmentId(ae.serviceEnrollmentId() != null ? ae.serviceEnrollmentId().toString() : null);
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
     * Converts entity to Profile domain aggregate.
     * Uses reflection to reconstruct the aggregate from persisted state.
     */
    default Profile toDomain(ProfileEntity entity) {
        if (entity == null) {
            return null;
        }

        try {
            // Parse IDs and profile type
            ProfileId profileId = ProfileId.fromUrn(entity.getProfileId());
            ProfileType profileType = ProfileType.valueOf(entity.getProfileType());

            // Reconstruct client enrollment requests for creating the profile
            List<Profile.ClientEnrollmentRequest> enrollmentRequests = new ArrayList<>();
            for (ClientEnrollmentEntity ceEntity : entity.getClientEnrollments()) {
                // For reconstruction, we pass empty account lists - we'll set them via reflection
                enrollmentRequests.add(new Profile.ClientEnrollmentRequest(
                    ClientId.of(ceEntity.getClientId()),
                    ceEntity.isPrimary(),
                    AccountEnrollmentType.valueOf(ceEntity.getAccountEnrollmentType()),
                    List.of()  // Empty - accounts will be set separately via reflection
                ));
            }

            // Create profile using factory
            Profile profile = Profile.createWithAccounts(
                profileType,
                entity.getName(),
                enrollmentRequests,
                entity.getCreatedBy()
            );

            // Use reflection to set the persisted values
            setField(profile, "profileId", profileId);
            setField(profile, "status", ProfileStatus.valueOf(entity.getStatus()));
            setField(profile, "createdAt", entity.getCreatedAt());
            setField(profile, "updatedAt", entity.getUpdatedAt());

            // Reconstruct client enrollments with actual enrolledAt times
            List<Profile.ClientEnrollment> clientEnrollments = new ArrayList<>();
            for (ClientEnrollmentEntity ceEntity : entity.getClientEnrollments()) {
                Profile.ClientEnrollment ce = reconstructClientEnrollment(ceEntity);
                clientEnrollments.add(ce);
            }
            setField(profile, "clientEnrollments", clientEnrollments);

            // Reconstruct service enrollments
            List<Profile.ServiceEnrollment> serviceEnrollments = new ArrayList<>();
            for (ServiceEnrollmentEntity seEntity : entity.getServiceEnrollments()) {
                Profile.ServiceEnrollment se = reconstructServiceEnrollment(seEntity);
                serviceEnrollments.add(se);
            }
            setField(profile, "serviceEnrollments", serviceEnrollments);

            // Reconstruct account enrollments
            List<Profile.AccountEnrollment> accountEnrollments = new ArrayList<>();
            for (AccountEnrollmentEntity aeEntity : entity.getAccountEnrollments()) {
                Profile.AccountEnrollment ae = reconstructAccountEnrollment(aeEntity);
                accountEnrollments.add(ae);
            }
            setField(profile, "accountEnrollments", accountEnrollments);

            return profile;

        } catch (Exception e) {
            throw new RuntimeException("Failed to map ProfileEntity to Profile", e);
        }
    }

    private Profile.ClientEnrollment reconstructClientEnrollment(ClientEnrollmentEntity entity) throws Exception {
        Profile.ClientEnrollment ce = new Profile.ClientEnrollment(
            ClientId.of(entity.getClientId()),
            entity.isPrimary(),
            AccountEnrollmentType.valueOf(entity.getAccountEnrollmentType())
        );

        // Override the auto-generated values with persisted values
        setNestedField(ce, "enrollmentId", EnrollmentId.of(entity.getId()));
        setNestedField(ce, "enrolledAt", entity.getEnrolledAt());

        return ce;
    }

    private Profile.ServiceEnrollment reconstructServiceEnrollment(ServiceEnrollmentEntity entity) throws Exception {
        Profile.ServiceEnrollment se = new Profile.ServiceEnrollment(
            entity.getServiceType(), entity.getConfiguration());

        // Override the generated values with persisted ones
        setNestedField(se, "enrollmentId", EnrollmentId.of(entity.getEnrollmentId()));
        setNestedField(se, "status", ProfileStatus.valueOf(entity.getStatus()));
        setNestedField(se, "enrolledAt", entity.getEnrolledAt());

        return se;
    }

    private Profile.AccountEnrollment reconstructAccountEnrollment(AccountEnrollmentEntity entity) throws Exception {
        // serviceEnrollmentId can be null for profile-level enrollments
        EnrollmentId serviceEnrollmentId = entity.getServiceEnrollmentId() != null
            ? EnrollmentId.of(entity.getServiceEnrollmentId())
            : null;

        Profile.AccountEnrollment ae = new Profile.AccountEnrollment(
            serviceEnrollmentId,
            ClientId.of(entity.getClientId()),
            ClientAccountId.of(entity.getAccountId())
        );

        setNestedField(ae, "enrollmentId", EnrollmentId.of(entity.getEnrollmentId()));
        setNestedField(ae, "status", ProfileStatus.valueOf(entity.getStatus()));
        setNestedField(ae, "enrolledAt", entity.getEnrolledAt());

        return ae;
    }

    private void setField(Profile profile, String fieldName, Object value) throws Exception {
        Field field = Profile.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(profile, value);
    }

    private void setNestedField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
