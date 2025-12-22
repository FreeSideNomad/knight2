package com.knight.portal.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.knight.portal.services.dto.AddUserRequest;
import com.knight.portal.services.dto.AddUserResponse;
import com.knight.portal.services.dto.ProfileUser;
import com.knight.portal.services.dto.UserDetail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service for managing users via REST API.
 */
@Service
public class UserService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public UserService(@Value("${api.base-url:http://localhost:8080}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Get all users for a profile.
     */
    public List<ProfileUser> getProfileUsers(String profileId) {
        try {
            List<Map<String, Object>> response = restClient.get()
                    .uri("/api/profiles/{profileId}/users", profileId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null) {
                return Collections.emptyList();
            }

            return response.stream()
                    .map(item -> objectMapper.convertValue(item, ProfileUser.class))
                    .toList();
        } catch (Exception e) {
            System.err.println("Error fetching profile users: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get user details.
     */
    public UserDetail getUserDetail(String profileId, String userId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/api/profiles/{profileId}/users/{userId}", profileId, userId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null) {
                return null;
            }

            return objectMapper.convertValue(response, UserDetail.class);
        } catch (Exception e) {
            System.err.println("Error fetching user detail: " + e.getMessage());
            return null;
        }
    }

    /**
     * Add a new user to a profile.
     */
    public AddUserResponse addUser(String profileId, AddUserRequest request) {
        Map<String, Object> response = restClient.post()
                .uri("/api/profiles/{profileId}/users", profileId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response == null) {
            throw new RuntimeException("Failed to add user");
        }

        return objectMapper.convertValue(response, AddUserResponse.class);
    }

    /**
     * Update user name.
     */
    public UserDetail updateUser(String userId, String firstName, String lastName) {
        Map<String, String> request = Map.of(
                "firstName", firstName,
                "lastName", lastName
        );

        Map<String, Object> response = restClient.put()
                .uri("/api/users/{userId}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response == null) {
            throw new RuntimeException("Failed to update user");
        }

        return objectMapper.convertValue(response, UserDetail.class);
    }

    /**
     * Resend invitation email to user.
     */
    public String resendInvitation(String userId) {
        Map<String, String> response = restClient.post()
                .uri("/api/users/{userId}/resend-invitation", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, String>>() {});
        return response != null ? response.get("passwordResetUrl") : null;
    }

    /**
     * Lock a user.
     */
    public void lockUser(String userId, String reason) {
        restClient.put()
                .uri("/api/users/{userId}/lock", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", reason))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Unlock a user.
     */
    public void unlockUser(String userId) {
        restClient.put()
                .uri("/api/users/{userId}/unlock", userId)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Deactivate a user.
     */
    public void deactivateUser(String userId, String reason) {
        restClient.put()
                .uri("/api/users/{userId}/deactivate", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", reason))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Activate a user.
     */
    public void activateUser(String userId) {
        restClient.put()
                .uri("/api/users/{userId}/activate", userId)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Add a role to a user.
     */
    public void addRole(String userId, String role) {
        restClient.post()
                .uri("/api/users/{userId}/roles", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("role", role))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Remove a role from a user.
     */
    public void removeRole(String userId, String role) {
        restClient.delete()
                .uri("/api/users/{userId}/roles/{role}", userId, role)
                .retrieve()
                .toBodilessEntity();
    }
}
