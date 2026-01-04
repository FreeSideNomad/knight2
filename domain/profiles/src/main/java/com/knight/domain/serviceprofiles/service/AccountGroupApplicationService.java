package com.knight.domain.serviceprofiles.service;

import com.knight.domain.serviceprofiles.aggregate.AccountGroup;
import com.knight.domain.serviceprofiles.types.AccountGroupId;
import com.knight.domain.serviceprofiles.api.commands.AccountGroupCommands;
import com.knight.domain.serviceprofiles.api.queries.AccountGroupQueries;
import com.knight.domain.serviceprofiles.repository.AccountGroupRepository;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ProfileId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Application service for Account Group management.
 */
@Service
public class AccountGroupApplicationService implements AccountGroupCommands, AccountGroupQueries {

    private final AccountGroupRepository repository;

    public AccountGroupApplicationService(AccountGroupRepository repository) {
        this.repository = repository;
    }

    // ==================== Commands ====================

    @Override
    @Transactional
    public AccountGroupId createGroup(CreateGroupCmd cmd) {
        // Check for duplicate name
        if (repository.existsByProfileIdAndName(cmd.profileId(), cmd.name())) {
            throw new IllegalArgumentException("Account group with name already exists: " + cmd.name());
        }

        AccountGroup group = AccountGroup.create(
            cmd.profileId(),
            cmd.name(),
            cmd.description(),
            cmd.createdBy()
        );

        if (cmd.initialAccounts() != null && !cmd.initialAccounts().isEmpty()) {
            group.addAccounts(cmd.initialAccounts());
        }

        repository.save(group);
        return group.id();
    }

    @Override
    @Transactional
    public void updateGroup(UpdateGroupCmd cmd) {
        AccountGroup group = repository.findById(cmd.groupId())
            .orElseThrow(() -> new IllegalArgumentException("Account group not found: " + cmd.groupId().id()));

        // Check for duplicate name if name is changing
        if (!group.name().equals(cmd.name()) &&
            repository.existsByProfileIdAndName(group.profileId(), cmd.name())) {
            throw new IllegalArgumentException("Account group with name already exists: " + cmd.name());
        }

        group.update(cmd.name(), cmd.description());
        repository.save(group);
    }

    @Override
    @Transactional
    public void deleteGroup(DeleteGroupCmd cmd) {
        AccountGroup group = repository.findById(cmd.groupId())
            .orElseThrow(() -> new IllegalArgumentException("Account group not found: " + cmd.groupId().id()));

        repository.delete(group);
    }

    @Override
    @Transactional
    public void addAccounts(AddAccountsCmd cmd) {
        AccountGroup group = repository.findById(cmd.groupId())
            .orElseThrow(() -> new IllegalArgumentException("Account group not found: " + cmd.groupId().id()));

        group.addAccounts(cmd.accountIds());
        repository.save(group);
    }

    @Override
    @Transactional
    public void removeAccounts(RemoveAccountsCmd cmd) {
        AccountGroup group = repository.findById(cmd.groupId())
            .orElseThrow(() -> new IllegalArgumentException("Account group not found: " + cmd.groupId().id()));

        for (ClientAccountId accountId : cmd.accountIds()) {
            if (group.containsAccount(accountId)) {
                group.removeAccount(accountId);
            }
        }
        repository.save(group);
    }

    @Override
    @Transactional
    public void setAccounts(SetAccountsCmd cmd) {
        AccountGroup group = repository.findById(cmd.groupId())
            .orElseThrow(() -> new IllegalArgumentException("Account group not found: " + cmd.groupId().id()));

        group.clearAccounts();
        group.addAccounts(cmd.accountIds());
        repository.save(group);
    }

    // ==================== Queries ====================

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountGroupDetail> getGroupById(AccountGroupId groupId) {
        return repository.findById(groupId).map(this::toDetail);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountGroupSummary> listGroupsByProfile(ProfileId profileId) {
        return repository.findByProfileId(profileId).stream()
            .map(this::toSummary)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountGroupSummary> findGroupsContainingAccount(ProfileId profileId, ClientAccountId accountId) {
        return repository.findByProfileId(profileId).stream()
            .filter(group -> group.containsAccount(accountId))
            .map(this::toSummary)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByName(ProfileId profileId, String name) {
        return repository.existsByProfileIdAndName(profileId, name);
    }

    // ==================== Mappers ====================

    private AccountGroupSummary toSummary(AccountGroup group) {
        return new AccountGroupSummary(
            group.id().id(),
            group.profileId().urn(),
            group.name(),
            group.description(),
            group.accountCount(),
            group.createdAt(),
            group.createdBy()
        );
    }

    private AccountGroupDetail toDetail(AccountGroup group) {
        Set<String> accountIds = group.accounts().stream()
            .map(ClientAccountId::urn)
            .collect(Collectors.toSet());

        return new AccountGroupDetail(
            group.id().id(),
            group.profileId().urn(),
            group.name(),
            group.description(),
            accountIds,
            group.createdAt(),
            group.createdBy(),
            group.updatedAt()
        );
    }
}
