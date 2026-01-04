package com.knight.indirectportal.services;

import com.knight.indirectportal.services.dto.CreateUserRequest;
import com.knight.indirectportal.services.dto.UserDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service for user operations via Platform API.
 * Uses /api/v1/indirect/users endpoints for the current payor's users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final WebClient apiWebClient;

    /**
     * Get all users for the current indirect client (payor).
     */
    public List<UserDetail> getUsers() {
        return apiWebClient.get()
                .uri("/users")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<UserDetail>>() {})
                .onErrorResume(e -> {
                    log.error("Failed to fetch users", e);
                    return Mono.just(List.of());
                })
                .block();
    }

    /**
     * Get details for a specific user.
     */
    public UserDetail getUser(String userId) {
        return apiWebClient.get()
                .uri("/users/{userId}", userId)
                .retrieve()
                .bodyToMono(UserDetail.class)
                .onErrorResume(e -> {
                    log.error("Failed to fetch user detail", e);
                    return Mono.empty();
                })
                .block();
    }

    /**
     * Alias for getUser for backwards compatibility.
     */
    public UserDetail getUserById(String userId) {
        return getUser(userId);
    }

    /**
     * Create a new user for the current indirect client.
     */
    public UserDetail createUser(CreateUserRequest request) {
        return apiWebClient.post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(UserDetail.class)
                .block();
    }

    /**
     * Lock a user account.
     */
    public void lockUser(String userId, String lockType) {
        apiWebClient.post()
                .uri("/users/{userId}/lock", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LockRequest(lockType))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Lock a user account with default CLIENT lock type.
     */
    public void lockUser(String userId) {
        lockUser(userId, "CLIENT");
    }

    /**
     * Unlock a user account.
     */
    public void unlockUser(String userId) {
        apiWebClient.post()
                .uri("/users/{userId}/unlock", userId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Deactivate (delete) a user account.
     */
    public void deactivateUser(String userId, String reason) {
        apiWebClient.post()
                .uri("/users/{userId}/deactivate", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new DeactivateRequest(reason))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Deactivate (delete) a user account without a reason.
     */
    public void deactivateUser(String userId) {
        deactivateUser(userId, null);
    }

    /**
     * Resend invitation to a pending user.
     */
    public void resendInvitation(String userId) {
        apiWebClient.post()
                .uri("/users/{userId}/resend-invitation", userId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    /**
     * Update user roles.
     */
    public UserDetail updateUserRoles(String userId, java.util.Set<String> roles) {
        return apiWebClient.put()
                .uri("/users/{userId}/roles", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateRolesRequest(roles))
                .retrieve()
                .bodyToMono(UserDetail.class)
                .block();
    }

    /**
     * Reset MFA enrollment for a user.
     */
    public void resetMfa(String userId, String reason, boolean notifyUser) {
        apiWebClient.post()
                .uri("/users/{userId}/mfa/reset", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ResetMfaRequest(reason, notifyUser))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private record LockRequest(String lockType) {}
    private record DeactivateRequest(String reason) {}
    private record UpdateRolesRequest(java.util.Set<String> roles) {}
    private record ResetMfaRequest(String reason, boolean notifyUser) {}
}
