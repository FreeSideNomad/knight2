package com.knight.application.persistence.usergroups.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_groups")
public class UserGroupEntity {

    @Id
    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "profile_id", nullable = false)
    private String profileId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<UserGroupMemberEntity> members = new HashSet<>();

    public UserGroupEntity() {}

    // Getters and Setters

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Set<UserGroupMemberEntity> getMembers() {
        return members;
    }

    public void setMembers(Set<UserGroupMemberEntity> members) {
        this.members = members;
    }

    public void addMember(UserGroupMemberEntity member) {
        members.add(member);
        member.setGroup(this);
    }

    public void removeMember(UserGroupMemberEntity member) {
        members.remove(member);
        member.setGroup(null);
    }

    public void clearMembers() {
        for (UserGroupMemberEntity member : members) {
            member.setGroup(null);
        }
        members.clear();
    }
}
