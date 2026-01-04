package com.knight.domain.serviceprofiles.api.commands;

import com.knight.domain.serviceprofiles.types.AccountGroupId;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ProfileId;

import java.util.Set;

/**
 * Command interface for Account Group management.
 */
public interface AccountGroupCommands {

    // ==================== Group CRUD ====================

    AccountGroupId createGroup(CreateGroupCmd cmd);

    record CreateGroupCmd(
        ProfileId profileId,
        String name,
        String description,
        Set<ClientAccountId> initialAccounts,
        String createdBy
    ) {}

    void updateGroup(UpdateGroupCmd cmd);

    record UpdateGroupCmd(
        AccountGroupId groupId,
        String name,
        String description
    ) {}

    void deleteGroup(DeleteGroupCmd cmd);

    record DeleteGroupCmd(AccountGroupId groupId) {}

    // ==================== Account Management ====================

    void addAccounts(AddAccountsCmd cmd);

    record AddAccountsCmd(
        AccountGroupId groupId,
        Set<ClientAccountId> accountIds
    ) {}

    void removeAccounts(RemoveAccountsCmd cmd);

    record RemoveAccountsCmd(
        AccountGroupId groupId,
        Set<ClientAccountId> accountIds
    ) {}

    void setAccounts(SetAccountsCmd cmd);

    record SetAccountsCmd(
        AccountGroupId groupId,
        Set<ClientAccountId> accountIds
    ) {}
}
