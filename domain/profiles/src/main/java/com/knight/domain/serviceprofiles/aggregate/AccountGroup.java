package com.knight.domain.serviceprofiles.aggregate;

import com.knight.domain.serviceprofiles.types.AccountGroupId;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ProfileId;

import java.time.Instant;
import java.util.*;

/**
 * Account Group Aggregate - groups accounts for permission scoping.
 *
 * An account group allows users to be granted permissions on a subset
 * of accounts within a profile rather than all accounts.
 */
public class AccountGroup {

    private final AccountGroupId id;
    private final ProfileId profileId;
    private String name;
    private String description;
    private final Set<ClientAccountId> accounts;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;

    private AccountGroup(
            AccountGroupId id,
            ProfileId profileId,
            String name,
            String description,
            Set<ClientAccountId> accounts,
            Instant createdAt,
            String createdBy,
            Instant updatedAt) {
        this.id = id;
        this.profileId = profileId;
        this.name = name;
        this.description = description;
        this.accounts = new HashSet<>(accounts);
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
    }

    public static AccountGroup create(
            ProfileId profileId,
            String name,
            String description,
            String createdBy) {

        validateName(name);

        return new AccountGroup(
            AccountGroupId.generate(),
            profileId,
            name,
            description,
            new HashSet<>(),
            Instant.now(),
            createdBy,
            Instant.now()
        );
    }

    public static AccountGroup reconstitute(
            AccountGroupId id,
            ProfileId profileId,
            String name,
            String description,
            Set<ClientAccountId> accounts,
            Instant createdAt,
            String createdBy,
            Instant updatedAt) {

        return new AccountGroup(id, profileId, name, description, accounts, createdAt, createdBy, updatedAt);
    }

    // ==================== Commands ====================

    public void update(String name, String description) {
        validateName(name);
        this.name = name;
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void addAccount(ClientAccountId accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (accounts.contains(accountId)) {
            throw new IllegalArgumentException("Account already in group: " + accountId.urn());
        }
        accounts.add(accountId);
        this.updatedAt = Instant.now();
    }

    public void addAccounts(Collection<ClientAccountId> accountIds) {
        if (accountIds == null) {
            return;
        }
        for (ClientAccountId accountId : accountIds) {
            if (accountId != null && !accounts.contains(accountId)) {
                accounts.add(accountId);
            }
        }
        this.updatedAt = Instant.now();
    }

    public void removeAccount(ClientAccountId accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (!accounts.contains(accountId)) {
            throw new IllegalArgumentException("Account not in group: " + accountId.urn());
        }
        accounts.remove(accountId);
        this.updatedAt = Instant.now();
    }

    public void clearAccounts() {
        accounts.clear();
        this.updatedAt = Instant.now();
    }

    // ==================== Queries ====================

    public boolean containsAccount(ClientAccountId accountId) {
        return accounts.contains(accountId);
    }

    public Set<ClientAccountId> accounts() {
        return Collections.unmodifiableSet(accounts);
    }

    public int accountCount() {
        return accounts.size();
    }

    // ==================== Getters ====================

    public AccountGroupId id() {
        return id;
    }

    public ProfileId profileId() {
        return profileId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    // ==================== Validation ====================

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Account group name is required");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Account group name cannot exceed 100 characters");
        }
    }

}
