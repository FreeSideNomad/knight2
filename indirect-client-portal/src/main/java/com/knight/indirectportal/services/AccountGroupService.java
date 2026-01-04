package com.knight.indirectportal.services;

import com.knight.indirectportal.services.dto.AccountGroupDetail;
import com.knight.indirectportal.services.dto.AccountGroupSummary;
import com.knight.indirectportal.services.dto.CreateAccountGroupRequest;
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
 * Service for account group operations via Platform API.
 * Uses /api/v1/indirect/account-groups endpoints.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountGroupService {

    private final WebClient apiWebClient;

    /**
     * Get all account groups.
     */
    public List<AccountGroupSummary> getGroups() {
        return apiWebClient.get()
                .uri("/account-groups")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<AccountGroupSummary>>() {})
                .onErrorResume(e -> {
                    log.error("Failed to fetch account groups", e);
                    return Mono.just(List.of());
                })
                .block();
    }

    /**
     * Get group details by ID.
     */
    public AccountGroupDetail getGroupById(String groupId) {
        return apiWebClient.get()
                .uri("/account-groups/{groupId}", groupId)
                .retrieve()
                .bodyToMono(AccountGroupDetail.class)
                .onErrorResume(e -> {
                    log.error("Failed to fetch account group detail for {}", groupId, e);
                    return Mono.empty();
                })
                .block();
    }

    /**
     * Create a new account group.
     */
    public AccountGroupSummary createGroup(CreateAccountGroupRequest request) {
        return apiWebClient.post()
                .uri("/account-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AccountGroupSummary.class)
                .block();
    }

    /**
     * Update an account group.
     */
    public AccountGroupSummary updateGroup(String groupId, String name, String description) {
        return apiWebClient.put()
                .uri("/account-groups/{groupId}", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", name, "description", description != null ? description : ""))
                .retrieve()
                .bodyToMono(AccountGroupSummary.class)
                .block();
    }

    /**
     * Delete an account group.
     */
    public void deleteGroup(String groupId) {
        apiWebClient.delete()
                .uri("/account-groups/{groupId}", groupId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Add accounts to a group.
     */
    public AccountGroupDetail addAccounts(String groupId, Set<String> accountIds) {
        return apiWebClient.post()
                .uri("/account-groups/{groupId}/accounts", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("accountIds", accountIds))
                .retrieve()
                .bodyToMono(AccountGroupDetail.class)
                .block();
    }

    /**
     * Remove accounts from a group.
     */
    public AccountGroupDetail removeAccounts(String groupId, Set<String> accountIds) {
        return apiWebClient.method(org.springframework.http.HttpMethod.DELETE)
                .uri("/account-groups/{groupId}/accounts", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("accountIds", accountIds))
                .retrieve()
                .bodyToMono(AccountGroupDetail.class)
                .block();
    }
}
