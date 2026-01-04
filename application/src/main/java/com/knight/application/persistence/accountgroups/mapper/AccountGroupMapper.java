package com.knight.application.persistence.accountgroups.mapper;

import com.knight.application.persistence.accountgroups.entity.AccountGroupEntity;
import com.knight.domain.serviceprofiles.aggregate.AccountGroup;
import com.knight.domain.serviceprofiles.types.AccountGroupId;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ProfileId;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AccountGroupMapper {

    public AccountGroupEntity toEntity(AccountGroup group) {
        AccountGroupEntity entity = new AccountGroupEntity();
        entity.setGroupId(group.id().value());
        entity.setProfileId(group.profileId().urn());
        entity.setName(group.name());
        entity.setDescription(group.description());
        entity.setCreatedAt(group.createdAt());
        entity.setCreatedBy(group.createdBy());
        entity.setUpdatedAt(group.updatedAt());

        Set<String> accountIds = group.accounts().stream()
            .map(ClientAccountId::urn)
            .collect(Collectors.toSet());
        entity.setAccountIds(accountIds);

        return entity;
    }

    public AccountGroup toDomain(AccountGroupEntity entity) {
        Set<ClientAccountId> accounts = entity.getAccountIds().stream()
            .map(ClientAccountId::of)
            .collect(Collectors.toSet());

        return AccountGroup.reconstitute(
            AccountGroupId.of(entity.getGroupId().toString()),
            ProfileId.fromUrn(entity.getProfileId()),
            entity.getName(),
            entity.getDescription(),
            accounts,
            entity.getCreatedAt(),
            entity.getCreatedBy(),
            entity.getUpdatedAt()
        );
    }
}
