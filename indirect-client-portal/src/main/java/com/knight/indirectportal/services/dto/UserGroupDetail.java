package com.knight.indirectportal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserGroupDetail {
    private String groupId;
    private String profileId;
    private String name;
    private String description;
    private Set<UserGroupMember> members;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserGroupMember {
        private String userId;
        private Instant addedAt;
        private String addedBy;
    }
}
