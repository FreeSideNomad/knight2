package com.knight.application.persistence.passkeys.mapper;

import com.knight.application.persistence.passkeys.entity.PasskeyEntity;
import com.knight.domain.users.aggregate.Passkey;
import com.knight.domain.users.types.PasskeyId;
import com.knight.platform.sharedkernel.UserId;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PasskeyMapper {

    public PasskeyEntity toEntity(Passkey passkey) {
        PasskeyEntity entity = new PasskeyEntity();
        entity.setPasskeyId(passkey.id().value());
        entity.setUserId(UUID.fromString(passkey.userId().id()));
        entity.setCredentialId(passkey.credentialId());
        entity.setPublicKey(passkey.publicKey());
        entity.setAaguid(passkey.aaguid());
        entity.setDisplayName(passkey.displayName());
        entity.setSignCount(passkey.signCount());
        entity.setUserVerification(passkey.userVerification());
        entity.setBackupEligible(passkey.backupEligible());
        entity.setBackupState(passkey.backupState());
        entity.setTransports(String.join(",", passkey.transports()));
        entity.setLastUsedAt(passkey.lastUsedAt());
        entity.setCreatedAt(passkey.createdAt());
        entity.setUpdatedAt(passkey.updatedAt());
        return entity;
    }

    public Passkey toDomain(PasskeyEntity entity) {
        PasskeyId passkeyId = new PasskeyId(entity.getPasskeyId());
        UserId userId = UserId.of(entity.getUserId().toString());

        String[] transports = entity.getTransports() != null && !entity.getTransports().isEmpty()
            ? entity.getTransports().split(",")
            : new String[0];

        return Passkey.reconstitute(
            passkeyId,
            userId,
            entity.getCredentialId(),
            entity.getPublicKey(),
            entity.getAaguid(),
            entity.getDisplayName(),
            entity.getSignCount(),
            entity.isUserVerification(),
            entity.isBackupEligible(),
            entity.isBackupState(),
            transports,
            entity.getLastUsedAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
