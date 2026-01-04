package com.knight.domain.users.api.commands;

import com.knight.domain.users.types.UserGroupId;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;

import java.util.Set;

/**
 * Command interface for User Group management.
 */
public interface UserGroupCommands {

    // ==================== Group CRUD ====================

    UserGroupId createGroup(CreateGroupCmd cmd);

    record CreateGroupCmd(
        ProfileId profileId,
        String name,
        String description,
        String createdBy
    ) {}

    void updateGroup(UpdateGroupCmd cmd);

    record UpdateGroupCmd(
        UserGroupId groupId,
        String name,
        String description
    ) {}

    void deleteGroup(DeleteGroupCmd cmd);

    record DeleteGroupCmd(UserGroupId groupId) {}

    // ==================== Member Management ====================

    void addMembers(AddMembersCmd cmd);

    record AddMembersCmd(
        UserGroupId groupId,
        Set<UserId> userIds,
        String addedBy
    ) {}

    void removeMembers(RemoveMembersCmd cmd);

    record RemoveMembersCmd(
        UserGroupId groupId,
        Set<UserId> userIds
    ) {}
}
