package com.knight.indirectportal.services;

import com.knight.indirectportal.services.dto.CreateUserGroupRequest;
import com.knight.indirectportal.services.dto.UserGroupDetail;
import com.knight.indirectportal.services.dto.UserGroupSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for user group operations via Platform API.
 * Uses /api/v1/indirect/groups endpoints.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserGroupService {

    private final WebClient apiWebClient;

    /**
     * Get all user groups.
     */
    public List<UserGroupSummary> getGroups() {
        return apiWebClient.get()
                .uri("/groups")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<UserGroupSummary>>() {})
                .onErrorResume(e -> {
                    log.error("Failed to fetch user groups", e);
                    return Mono.just(List.of());
                })
                .block();
    }

    /**
     * Get group details by ID.
     */
    public UserGroupDetail getGroupById(String groupId) {
        return apiWebClient.get()
                .uri("/groups/{groupId}", groupId)
                .retrieve()
                .bodyToMono(UserGroupDetail.class)
                .onErrorResume(e -> {
                    log.error("Failed to fetch group detail for {}", groupId, e);
                    return Mono.empty();
                })
                .block();
    }

    /**
     * Create a new user group.
     */
    public UserGroupSummary createGroup(CreateUserGroupRequest request) {
        return apiWebClient.post()
                .uri("/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(UserGroupSummary.class)
                .block();
    }

    /**
     * Update a user group.
     */
    public UserGroupSummary updateGroup(String groupId, String name, String description) {
        return apiWebClient.put()
                .uri("/groups/{groupId}", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", name, "description", description != null ? description : ""))
                .retrieve()
                .bodyToMono(UserGroupSummary.class)
                .block();
    }

    /**
     * Delete a user group.
     */
    public void deleteGroup(String groupId) {
        apiWebClient.delete()
                .uri("/groups/{groupId}", groupId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Add members to a group.
     */
    public UserGroupDetail addMembers(String groupId, Set<String> userIds) {
        return apiWebClient.post()
                .uri("/groups/{groupId}/members", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("userIds", userIds))
                .retrieve()
                .bodyToMono(UserGroupDetail.class)
                .block();
    }

    /**
     * Remove members from a group.
     */
    public UserGroupDetail removeMembers(String groupId, Set<String> userIds) {
        return apiWebClient.method(org.springframework.http.HttpMethod.DELETE)
                .uri("/groups/{groupId}/members", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("userIds", userIds))
                .retrieve()
                .bodyToMono(UserGroupDetail.class)
                .block();
    }
}
