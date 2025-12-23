package com.knight.portal.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.knight.portal.services.dto.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service for indirect client-related API calls.
 */
@Service
public class IndirectClientService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public IndirectClientService(@Qualifier("apiRestClient") RestClient restClient) {
        this.restClient = restClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Get all indirect clients for a parent client.
     */
    public List<IndirectClientSummary> getByParentClient(String clientId) {
        try {
            List<Map<String, Object>> response = restClient.get()
                    .uri("/api/indirect-clients/by-client/{clientId}", clientId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null) {
                return Collections.emptyList();
            }

            return response.stream()
                    .map(item -> objectMapper.convertValue(item, IndirectClientSummary.class))
                    .toList();
        } catch (Exception e) {
            System.err.println("Error fetching indirect clients by parent: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get all indirect clients for a profile.
     */
    public List<IndirectClientSummary> getByProfile(String profileId) {
        try {
            List<Map<String, Object>> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/api/indirect-clients/by-profile")
                        .queryParam("profileId", profileId)
                        .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null) {
                return Collections.emptyList();
            }

            return response.stream()
                    .map(item -> objectMapper.convertValue(item, IndirectClientSummary.class))
                    .toList();
        } catch (Exception e) {
            System.err.println("Error fetching indirect clients by profile: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get detailed information about an indirect client.
     */
    public IndirectClientDetail getDetail(String indirectClientId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/api/indirect-clients/{id}", indirectClientId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null) {
                return null;
            }

            return objectMapper.convertValue(response, IndirectClientDetail.class);
        } catch (Exception e) {
            System.err.println("Error fetching indirect client detail: " + e.getMessage());
            return null;
        }
    }

    /**
     * Create a new indirect client.
     */
    public CreateIndirectClientResponse create(CreateIndirectClientRequest request) {
        try {
            return restClient.post()
                    .uri("/api/indirect-clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(CreateIndirectClientResponse.class);
        } catch (Exception e) {
            System.err.println("Error creating indirect client: " + e.getMessage());
            throw new RuntimeException("Failed to create indirect client: " + e.getMessage(), e);
        }
    }

    /**
     * Add a related person to an indirect client.
     */
    public void addRelatedPerson(String indirectClientId, RelatedPersonRequest request) {
        try {
            restClient.post()
                    .uri("/api/indirect-clients/{id}/persons", indirectClientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            System.err.println("Error adding related person: " + e.getMessage());
            throw new RuntimeException("Failed to add related person: " + e.getMessage(), e);
        }
    }

    /**
     * Update an existing related person.
     */
    public void updateRelatedPerson(String indirectClientId, String personId, RelatedPersonRequest request) {
        try {
            restClient.put()
                    .uri("/api/indirect-clients/{id}/persons/{personId}", indirectClientId, personId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            System.err.println("Error updating related person: " + e.getMessage());
            throw new RuntimeException("Failed to update related person: " + e.getMessage(), e);
        }
    }

    /**
     * Remove a related person from an indirect client.
     */
    public void removeRelatedPerson(String indirectClientId, String personId) {
        try {
            restClient.delete()
                    .uri("/api/indirect-clients/{id}/persons/{personId}", indirectClientId, personId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            System.err.println("Error removing related person: " + e.getMessage());
            throw new RuntimeException("Failed to remove related person: " + e.getMessage(), e);
        }
    }

    /**
     * Add an OFI account to an indirect client.
     */
    public IndirectClientDetail.OfiAccount addOfiAccount(String indirectClientId, AddOfiAccountRequest request) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/api/indirect-clients/{id}/accounts", indirectClientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null) {
                return null;
            }
            return objectMapper.convertValue(response, IndirectClientDetail.OfiAccount.class);
        } catch (Exception e) {
            System.err.println("Error adding OFI account: " + e.getMessage());
            throw new RuntimeException("Failed to add OFI account: " + e.getMessage(), e);
        }
    }

    /**
     * Get OFI accounts for an indirect client.
     */
    public List<IndirectClientDetail.OfiAccount> getOfiAccounts(String indirectClientId) {
        try {
            List<Map<String, Object>> response = restClient.get()
                    .uri("/api/indirect-clients/{id}/accounts", indirectClientId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null) {
                return Collections.emptyList();
            }

            return response.stream()
                    .map(item -> objectMapper.convertValue(item, IndirectClientDetail.OfiAccount.class))
                    .toList();
        } catch (Exception e) {
            System.err.println("Error fetching OFI accounts: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Deactivate an OFI account.
     */
    public void deactivateOfiAccount(String indirectClientId, String accountId) {
        try {
            restClient.delete()
                    .uri("/api/indirect-clients/{id}/accounts/{accountId}", indirectClientId, accountId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            System.err.println("Error deactivating OFI account: " + e.getMessage());
            throw new RuntimeException("Failed to deactivate OFI account: " + e.getMessage(), e);
        }
    }
}
