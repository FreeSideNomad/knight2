package com.knight.application.persistence.usergroups.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_group_members")
@IdClass(UserGroupMemberEntity.UserGroupMemberId.class)
public class UserGroupMemberEntity {

    @Id
    @Column(name = "group_id")
    private UUID groupId;

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "added_by", nullable = false)
    private String addedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", insertable = false, updatable = false)
    private UserGroupEntity group;

    public UserGroupMemberEntity() {}

    public UserGroupMemberEntity(UUID groupId, String userId, Instant addedAt, String addedBy) {
        this.groupId = groupId;
        this.userId = userId;
        this.addedAt = addedAt;
        this.addedBy = addedBy;
    }

    // Getters and Setters

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }

    public UserGroupEntity getGroup() {
        return group;
    }

    public void setGroup(UserGroupEntity group) {
        this.group = group;
        if (group != null) {
            this.groupId = group.getGroupId();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserGroupMemberEntity that = (UserGroupMemberEntity) o;
        return Objects.equals(groupId, that.groupId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, userId);
    }

    public static class UserGroupMemberId implements Serializable {
        private UUID groupId;
        private String userId;

        public UserGroupMemberId() {}

        public UserGroupMemberId(UUID groupId, String userId) {
            this.groupId = groupId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserGroupMemberId that = (UserGroupMemberId) o;
            return Objects.equals(groupId, that.groupId) && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, userId);
        }
    }
}
