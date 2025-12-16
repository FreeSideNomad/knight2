package com.knight.portal.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.knight.portal.services.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service for profile-related API calls.
 */
@Service
public class ProfileService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ProfileService(@Value("${api.base-url:http://localhost:8080}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Get all profiles for a client (primary and secondary).
     */
    public List<ProfileSummary> getClientProfiles(String clientId) {
        try {
            List<Map<String, Object>> response = restClient.get()
                    .uri("/api/clients/{clientId}/profiles", clientId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null) {
                return Collections.emptyList();
            }

            return response.stream()
                    .map(item -> objectMapper.convertValue(item, ProfileSummary.class))
                    .toList();
        } catch (Exception e) {
            System.err.println("Error fetching client profiles: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get profiles where the client is the primary client.
     */
    public List<ProfileSummary> getPrimaryProfiles(String clientId) {
        try {
            List<Map<String, Object>> response = restClient.get()
                    .uri("/api/clients/{clientId}/profiles/primary", clientId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null) {
                return Collections.emptyList();
            }

            return response.stream()
                    .map(item -> objectMapper.convertValue(item, ProfileSummary.class))
                    .toList();
        } catch (Exception e) {
            System.err.println("Error fetching primary profiles: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get profiles where the client is a secondary client.
     */
    public List<ProfileSummary> getSecondaryProfiles(String clientId) {
        try {
            List<Map<String, Object>> response = restClient.get()
                    .uri("/api/clients/{clientId}/profiles/secondary", clientId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null) {
                return Collections.emptyList();
            }

            return response.stream()
                    .map(item -> objectMapper.convertValue(item, ProfileSummary.class))
                    .toList();
        } catch (Exception e) {
            System.err.println("Error fetching secondary profiles: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Create a new profile.
     */
    public CreateProfileResponse createProfile(CreateProfileRequest request) {
        try {
            return restClient.post()
                    .uri("/api/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(CreateProfileResponse.class);
        } catch (Exception e) {
            System.err.println("Error creating profile: " + e.getMessage());
            throw new RuntimeException("Failed to create profile: " + e.getMessage(), e);
        }
    }

    // ==================== Search Methods ====================

    /**
     * Search profiles with filters.
     */
    public PageResponse<ProfileSummary> searchProfiles(ProfileSearchRequest request) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/api/profiles/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null) {
                return emptyPage();
            }

            PageResponse<ProfileSummary> pageResponse = new PageResponse<>();
            pageResponse.setTotalElements(((Number) response.get("totalElements")).longValue());
            pageResponse.setPage(((Number) response.get("page")).intValue());
            pageResponse.setSize(((Number) response.get("size")).intValue());
            pageResponse.setTotalPages(((Number) response.get("totalPages")).intValue());

            List<?> content = (List<?>) response.get("content");
            if (content != null) {
                pageResponse.setContent(content.stream()
                        .map(item -> objectMapper.convertValue(item, ProfileSummary.class))
                        .toList());
            } else {
                pageResponse.setContent(Collections.emptyList());
            }

            return pageResponse;
        } catch (Exception e) {
            System.err.println("Error searching profiles: " + e.getMessage());
            return emptyPage();
        }
    }

    private PageResponse<ProfileSummary> emptyPage() {
        PageResponse<ProfileSummary> empty = new PageResponse<>();
        empty.setContent(Collections.emptyList());
        empty.setTotalElements(0);
        empty.setPage(0);
        empty.setSize(20);
        empty.setTotalPages(0);
        return empty;
    }

    // ==================== Profile Detail ====================

    /**
     * Get detailed profile information.
     */
    public ProfileDetail getProfileDetail(String profileId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/api/profiles/{profileId}/detail", profileId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null) {
                return null;
            }

            return objectMapper.convertValue(response, ProfileDetail.class);
        } catch (Exception e) {
            System.err.println("Error fetching profile detail: " + e.getMessage());
            return null;
        }
    }

    // ==================== Secondary Client Management ====================

    /**
     * Add a secondary client to a profile.
     */
    public void addSecondaryClient(String profileId, AddSecondaryClientRequest request) {
        try {
            // First check if client is already enrolled
            ProfileDetail detail = getProfileDetail(profileId);
            if (detail != null && detail.getClientEnrollments() != null) {
                boolean isAlreadyEnrolled = detail.getClientEnrollments().stream()
                        .anyMatch(enrollment -> enrollment.getClientId().equals(request.getClientId()));
                
                if (isAlreadyEnrolled) {
                    throw new RuntimeException("Client is already enrolled in this profile");
                }
            }

            restClient.post()
                    .uri("/api/profiles/{profileId}/clients", profileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // If it's the exception we just threw, rethrow it directly
            if ("Client is already enrolled in this profile".equals(e.getMessage())) {
                throw e;
            }
            System.err.println("Error adding secondary client: " + e.getMessage());
            throw new RuntimeException("Failed to add secondary client: " + e.getMessage(), e);
        }
    }

    /**
     * Remove a secondary client from a profile.
     */
    public void removeSecondaryClient(String profileId, String clientId) {
        try {
            restClient.delete()
                    .uri("/api/profiles/{profileId}/clients/{clientId}", profileId, clientId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            System.err.println("Error removing secondary client: " + e.getMessage());
            throw new RuntimeException("Failed to remove secondary client: " + e.getMessage(), e);
        }
    }
}
