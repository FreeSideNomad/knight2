package com.knight.domain.users.aggregate;

import com.knight.domain.users.types.UserGroupId;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;

import java.time.Instant;
import java.util.*;

/**
 * User Group Aggregate - groups users for permission management.
 *
 * A user group allows permissions to be assigned to multiple users
 * at once instead of managing individual user permissions.
 */
public class UserGroup {

    private final UserGroupId id;
    private final ProfileId profileId;
    private String name;
    private String description;
    private final Set<UserGroupMember> members;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;

    private UserGroup(
            UserGroupId id,
            ProfileId profileId,
            String name,
            String description,
            Set<UserGroupMember> members,
            Instant createdAt,
            String createdBy,
            Instant updatedAt) {
        this.id = id;
        this.profileId = profileId;
        this.name = name;
        this.description = description;
        this.members = new HashSet<>(members);
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
    }

    public static UserGroup create(
            ProfileId profileId,
            String name,
            String description,
            String createdBy) {

        validateName(name);

        return new UserGroup(
            UserGroupId.generate(),
            profileId,
            name,
            description,
            new HashSet<>(),
            Instant.now(),
            createdBy,
            Instant.now()
        );
    }

    public static UserGroup reconstitute(
            UserGroupId id,
            ProfileId profileId,
            String name,
            String description,
            Set<UserGroupMember> members,
            Instant createdAt,
            String createdBy,
            Instant updatedAt) {

        return new UserGroup(id, profileId, name, description, members, createdAt, createdBy, updatedAt);
    }

    // ==================== Commands ====================

    public void update(String name, String description) {
        validateName(name);
        this.name = name;
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void addMember(UserId userId, String addedBy) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (containsMember(userId)) {
            throw new IllegalArgumentException("User already in group: " + userId.id());
        }
        members.add(new UserGroupMember(userId, Instant.now(), addedBy));
        this.updatedAt = Instant.now();
    }

    public void removeMember(UserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        boolean removed = members.removeIf(m -> m.userId().equals(userId));
        if (!removed) {
            throw new IllegalArgumentException("User not in group: " + userId.id());
        }
        this.updatedAt = Instant.now();
    }

    public void clearMembers() {
        members.clear();
        this.updatedAt = Instant.now();
    }

    // ==================== Queries ====================

    public boolean containsMember(UserId userId) {
        return members.stream().anyMatch(m -> m.userId().equals(userId));
    }

    public Set<UserGroupMember> members() {
        return Collections.unmodifiableSet(members);
    }

    public Set<UserId> memberUserIds() {
        Set<UserId> ids = new HashSet<>();
        for (UserGroupMember member : members) {
            ids.add(member.userId());
        }
        return Collections.unmodifiableSet(ids);
    }

    public int memberCount() {
        return members.size();
    }

    // ==================== Getters ====================

    public UserGroupId id() {
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
            throw new IllegalArgumentException("User group name is required");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("User group name cannot exceed 100 characters");
        }
    }

    // ==================== Value Objects ====================

    public record UserGroupMember(
        UserId userId,
        Instant addedAt,
        String addedBy
    ) {
        public UserGroupMember {
            Objects.requireNonNull(userId, "User ID cannot be null");
            Objects.requireNonNull(addedAt, "Added at cannot be null");
            Objects.requireNonNull(addedBy, "Added by cannot be null");
        }
    }
}
