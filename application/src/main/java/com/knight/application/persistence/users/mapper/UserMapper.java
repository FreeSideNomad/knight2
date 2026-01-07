package com.knight.application.persistence.users.mapper;

import com.knight.application.persistence.users.entity.UserEntity;
import com.knight.application.persistence.users.entity.UserRoleEntity;
import com.knight.domain.users.aggregate.User;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setUserId(UUID.fromString(user.id().id()));
        entity.setLoginId(user.loginId());
        entity.setEmail(user.email());
        entity.setFirstName(user.firstName());
        entity.setLastName(user.lastName());
        entity.setUserType(user.userType().name());
        entity.setIdentityProvider(user.identityProvider().name());
        entity.setProfileId(user.profileId().urn());
        entity.setIdentityProviderUserId(user.identityProviderUserId());
        entity.setEmailVerified(user.emailVerified());
        entity.setPasswordSet(user.passwordSet());
        entity.setMfaEnrolled(user.mfaEnrolled());
        entity.setLastSyncedAt(user.lastSyncedAt());
        entity.setLastLoggedInAt(user.lastLoggedInAt());
        entity.setStatus(user.status().name());
        entity.setLockType(user.lockType().name());
        entity.setLockedBy(user.lockedBy());
        entity.setLockedAt(user.lockedAt());
        entity.setDeactivationReason(user.deactivationReason());
        entity.setMfaPreference(user.mfaPreference() != null ? user.mfaPreference().name() : null);
        entity.setAllowMfaReenrollment(user.allowMfaReenrollment());
        entity.setMfaReenrollmentRequestedAt(user.mfaReenrollmentRequestedAt());
        entity.setMfaReenrollmentRequestedBy(user.mfaReenrollmentRequestedBy());
        entity.setCreatedAt(user.createdAt());
        entity.setCreatedBy(user.createdBy());
        entity.setUpdatedAt(user.updatedAt());

        // Map roles
        for (User.Role role : user.roles()) {
            UserRoleEntity roleEntity = new UserRoleEntity(
                entity.getUserId(),
                role.name(),
                Instant.now(),
                user.createdBy()
            );
            entity.getRoles().add(roleEntity);
        }

        return entity;
    }

    public User toDomain(UserEntity entity) {
        UserId userId = UserId.of(entity.getUserId().toString());
        ProfileId profileId = ProfileId.fromUrn(entity.getProfileId());

        Set<User.Role> roles = entity.getRoles().stream()
            .map(r -> User.Role.valueOf(r.getRole()))
            .collect(Collectors.toSet());

        User.MfaPreference mfaPreference = null;
        if (entity.getMfaPreference() != null) {
            mfaPreference = User.MfaPreference.valueOf(entity.getMfaPreference());
        }

        return User.reconstitute(
            userId,
            entity.getLoginId(),
            entity.getEmail(),
            entity.getFirstName(),
            entity.getLastName(),
            User.UserType.valueOf(entity.getUserType()),
            User.IdentityProvider.valueOf(entity.getIdentityProvider()),
            profileId,
            roles,
            entity.getIdentityProviderUserId(),
            entity.isEmailVerified(),
            entity.isPasswordSet(),
            entity.isMfaEnrolled(),
            entity.getLastSyncedAt(),
            entity.getLastLoggedInAt(),
            User.Status.valueOf(entity.getStatus()),
            User.LockType.valueOf(entity.getLockType() != null ? entity.getLockType() : "NONE"),
            entity.getLockedBy(),
            entity.getLockedAt(),
            entity.getDeactivationReason(),
            mfaPreference,
            entity.isAllowMfaReenrollment(),
            entity.getMfaReenrollmentRequestedAt(),
            entity.getMfaReenrollmentRequestedBy(),
            entity.getCreatedAt(),
            entity.getCreatedBy(),
            entity.getUpdatedAt()
        );
    }
}
