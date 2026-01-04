package com.knight.domain.users.service;

import com.knight.domain.users.aggregate.UserGroup;
import com.knight.domain.users.aggregate.UserGroup.UserGroupMember;
import com.knight.domain.users.types.UserGroupId;
import com.knight.domain.users.api.commands.UserGroupCommands;
import com.knight.domain.users.api.queries.UserGroupQueries;
import com.knight.domain.users.repository.UserGroupRepository;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Application service for User Group management.
 */
@Service
public class UserGroupApplicationService implements UserGroupCommands, UserGroupQueries {

    private final UserGroupRepository repository;

    public UserGroupApplicationService(UserGroupRepository repository) {
        this.repository = repository;
    }

    // ==================== Commands ====================

    @Override
    @Transactional
    public UserGroupId createGroup(CreateGroupCmd cmd) {
        // Check for duplicate name
        if (repository.existsByProfileIdAndName(cmd.profileId(), cmd.name())) {
            throw new IllegalArgumentException("User group with name already exists: " + cmd.name());
        }

        UserGroup group = UserGroup.create(
            cmd.profileId(),
            cmd.name(),
            cmd.description(),
            cmd.createdBy()
        );

        repository.save(group);
        return group.id();
    }

    @Override
    @Transactional
    public void updateGroup(UpdateGroupCmd cmd) {
        UserGroup group = repository.findById(cmd.groupId())
            .orElseThrow(() -> new IllegalArgumentException("User group not found: " + cmd.groupId().id()));

        // Check for duplicate name if name is changing
        if (!group.name().equals(cmd.name()) &&
            repository.existsByProfileIdAndName(group.profileId(), cmd.name())) {
            throw new IllegalArgumentException("User group with name already exists: " + cmd.name());
        }

        group.update(cmd.name(), cmd.description());
        repository.save(group);
    }

    @Override
    @Transactional
    public void deleteGroup(DeleteGroupCmd cmd) {
        UserGroup group = repository.findById(cmd.groupId())
            .orElseThrow(() -> new IllegalArgumentException("User group not found: " + cmd.groupId().id()));

        repository.delete(group);
    }

    @Override
    @Transactional
    public void addMembers(AddMembersCmd cmd) {
        UserGroup group = repository.findById(cmd.groupId())
            .orElseThrow(() -> new IllegalArgumentException("User group not found: " + cmd.groupId().id()));

        for (UserId userId : cmd.userIds()) {
            if (!group.containsMember(userId)) {
                group.addMember(userId, cmd.addedBy());
            }
        }
        repository.save(group);
    }

    @Override
    @Transactional
    public void removeMembers(RemoveMembersCmd cmd) {
        UserGroup group = repository.findById(cmd.groupId())
            .orElseThrow(() -> new IllegalArgumentException("User group not found: " + cmd.groupId().id()));

        for (UserId userId : cmd.userIds()) {
            if (group.containsMember(userId)) {
                group.removeMember(userId);
            }
        }
        repository.save(group);
    }

    // ==================== Queries ====================

    @Override
    @Transactional(readOnly = true)
    public Optional<UserGroupDetail> getGroupById(UserGroupId groupId) {
        return repository.findById(groupId).map(this::toDetail);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserGroupSummary> listGroupsByProfile(ProfileId profileId) {
        return repository.findByProfileId(profileId).stream()
            .map(this::toSummary)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserGroupSummary> findGroupsByUser(UserId userId) {
        return repository.findByUserId(userId).stream()
            .map(this::toSummary)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByName(ProfileId profileId, String name) {
        return repository.existsByProfileIdAndName(profileId, name);
    }

    // ==================== Mappers ====================

    private UserGroupSummary toSummary(UserGroup group) {
        return new UserGroupSummary(
            group.id().id(),
            group.profileId().urn(),
            group.name(),
            group.description(),
            group.memberCount(),
            group.createdAt(),
            group.createdBy()
        );
    }

    private UserGroupDetail toDetail(UserGroup group) {
        Set<UserGroupMemberInfo> memberInfos = group.members().stream()
            .map(m -> new UserGroupMemberInfo(m.userId().id(), m.addedAt(), m.addedBy()))
            .collect(Collectors.toSet());

        return new UserGroupDetail(
            group.id().id(),
            group.profileId().urn(),
            group.name(),
            group.description(),
            memberInfos,
            group.createdAt(),
            group.createdBy(),
            group.updatedAt()
        );
    }
}
