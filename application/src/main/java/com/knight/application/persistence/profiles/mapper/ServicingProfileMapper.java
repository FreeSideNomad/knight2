package com.knight.application.persistence.profiles.mapper;

import com.knight.application.persistence.profiles.entity.AccountEnrollmentEntity;
import com.knight.application.persistence.profiles.entity.ServiceEnrollmentEntity;
import com.knight.application.persistence.profiles.entity.ServicingProfileEntity;
import com.knight.domain.serviceprofiles.aggregate.ServicingProfile;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.ServicingProfileId;
import org.mapstruct.Mapper;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * MapStruct mapper for ServicingProfile aggregate.
 */
@Mapper(componentModel = "spring")
public interface ServicingProfileMapper {

    /**
     * Converts ServicingProfile domain aggregate to entity.
     */
    default ServicingProfileEntity toEntity(ServicingProfile domain) {
        if (domain == null) {
            return null;
        }

        ServicingProfileEntity entity = new ServicingProfileEntity();
        entity.setProfileId(domain.profileId().urn());
        entity.setClientId(domain.clientId().urn());
        entity.setStatus(domain.status().name());
        entity.setCreatedAt(domain.createdAt());
        entity.setCreatedBy(domain.createdBy());
        entity.setUpdatedAt(domain.updatedAt());

        // Map service enrollments
        List<ServiceEnrollmentEntity> serviceEnrollments = new ArrayList<>();
        for (ServicingProfile.ServiceEnrollment se : domain.serviceEnrollments()) {
            ServiceEnrollmentEntity seEntity = new ServiceEnrollmentEntity();
            seEntity.setEnrollmentId(se.enrollmentId());
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
        for (ServicingProfile.AccountEnrollment ae : domain.accountEnrollments()) {
            AccountEnrollmentEntity aeEntity = new AccountEnrollmentEntity();
            aeEntity.setEnrollmentId(ae.enrollmentId());
            aeEntity.setProfile(entity);
            aeEntity.setServiceEnrollmentId(ae.serviceEnrollmentId());
            aeEntity.setAccountId(ae.accountId());
            aeEntity.setStatus(ae.status().name());
            aeEntity.setEnrolledAt(ae.enrolledAt());
            accountEnrollments.add(aeEntity);
        }
        entity.setAccountEnrollments(accountEnrollments);

        return entity;
    }

    /**
     * Converts entity to ServicingProfile domain aggregate.
     * Uses reflection to reconstruct the aggregate from persisted state.
     */
    default ServicingProfile toDomain(ServicingProfileEntity entity) {
        if (entity == null) {
            return null;
        }

        try {
            // Parse IDs
            ClientId clientId = ClientId.of(entity.getClientId());
            ServicingProfileId profileId = ServicingProfileId.fromUrn(entity.getProfileId());

            // Create a new profile using factory (this sets default values)
            ServicingProfile profile = ServicingProfile.create(clientId, entity.getCreatedBy());

            // Use reflection to set the persisted values
            setField(profile, "profileId", profileId);
            setField(profile, "status", ServicingProfile.Status.valueOf(entity.getStatus()));
            setField(profile, "createdAt", entity.getCreatedAt());
            setField(profile, "updatedAt", entity.getUpdatedAt());

            // Reconstruct service enrollments
            List<ServicingProfile.ServiceEnrollment> serviceEnrollments = new ArrayList<>();
            for (ServiceEnrollmentEntity seEntity : entity.getServiceEnrollments()) {
                ServicingProfile.ServiceEnrollment se = reconstructServiceEnrollment(seEntity);
                serviceEnrollments.add(se);
            }
            setField(profile, "serviceEnrollments", serviceEnrollments);

            // Reconstruct account enrollments
            List<ServicingProfile.AccountEnrollment> accountEnrollments = new ArrayList<>();
            for (AccountEnrollmentEntity aeEntity : entity.getAccountEnrollments()) {
                ServicingProfile.AccountEnrollment ae = reconstructAccountEnrollment(aeEntity);
                accountEnrollments.add(ae);
            }
            setField(profile, "accountEnrollments", accountEnrollments);

            return profile;

        } catch (Exception e) {
            throw new RuntimeException("Failed to map ServicingProfileEntity to ServicingProfile", e);
        }
    }

    private ServicingProfile.ServiceEnrollment reconstructServiceEnrollment(ServiceEnrollmentEntity entity) throws Exception {
        // Create instance via reflection (the constructor is public but takes params)
        ServicingProfile.ServiceEnrollment se = new ServicingProfile.ServiceEnrollment(
            entity.getServiceType(), entity.getConfiguration());

        // Override the generated values with persisted ones
        setNestedField(se, "enrollmentId", entity.getEnrollmentId());
        setNestedField(se, "status", ServicingProfile.Status.valueOf(entity.getStatus()));
        setNestedField(se, "enrolledAt", entity.getEnrolledAt());

        return se;
    }

    private ServicingProfile.AccountEnrollment reconstructAccountEnrollment(AccountEnrollmentEntity entity) throws Exception {
        ServicingProfile.AccountEnrollment ae = new ServicingProfile.AccountEnrollment(
            entity.getServiceEnrollmentId(), entity.getAccountId());

        setNestedField(ae, "enrollmentId", entity.getEnrollmentId());
        setNestedField(ae, "status", ServicingProfile.Status.valueOf(entity.getStatus()));
        setNestedField(ae, "enrolledAt", entity.getEnrolledAt());

        return ae;
    }

    private void setField(ServicingProfile profile, String fieldName, Object value) throws Exception {
        Field field = ServicingProfile.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(profile, value);
    }

    private void setNestedField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
