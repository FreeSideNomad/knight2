package com.knight.indirectportal.services;

import com.knight.indirectportal.services.dto.UserDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
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
}
