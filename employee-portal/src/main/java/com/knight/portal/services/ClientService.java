package com.knight.portal.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knight.portal.services.dto.ClientAccount;
import com.knight.portal.services.dto.ClientDetail;
import com.knight.portal.services.dto.ClientSearchResult;
import com.knight.portal.services.dto.PageResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class ClientService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClientService(@Qualifier("apiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Search for clients by type and name with pagination
     * @param type Client type (srf, cdr, or null for any)
     * @param name Search term for client name (wildcard search)
     * @param page Page number (0-based)
     * @param size Page size
     * @return Paginated list of matching clients
     */
    public PageResult<ClientSearchResult> searchClients(String type, String name, int page, int size) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/api/v1/bank/clients");
                        if (type != null && !type.isBlank()) {
                            builder.queryParam("type", type);
                        }
                        if (name != null && !name.isBlank()) {
                            builder.queryParam("name", name);
                        }
                        builder.queryParam("page", page);
                        builder.queryParam("size", size);
                        return builder.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return parsePageResult(response, ClientSearchResult.class);
        } catch (Exception e) {
            System.err.println("Error searching clients: " + e.getMessage());
            return emptyPageResult(page, size);
        }
    }

    /**
     * Search for clients by type and client ID/number with pagination
     * @param type Client type (srf, cdr, or null for any)
     * @param clientId Search term for client ID/number (wildcard search)
     * @param page Page number (0-based)
     * @param size Page size
     * @return Paginated list of matching clients
     */
    public PageResult<ClientSearchResult> searchClientsByClientId(String type, String clientId, int page, int size) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/api/v1/bank/clients");
                        if (type != null && !type.isBlank()) {
                            builder.queryParam("type", type);
                        }
                        if (clientId != null && !clientId.isBlank()) {
                            builder.queryParam("clientId", clientId);
                        }
                        builder.queryParam("page", page);
                        builder.queryParam("size", size);
                        return builder.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return parsePageResult(response, ClientSearchResult.class);
        } catch (Exception e) {
            System.err.println("Error searching clients by ID: " + e.getMessage());
            return emptyPageResult(page, size);
        }
    }

    /**
     * Get detailed information about a specific client
     * @param clientId The client ID
     * @return Client details
     */
    public ClientDetail getClient(String clientId) {
        try {
            return restClient.get()
                    .uri("/api/v1/bank/clients/{clientId}", clientId)
                    .retrieve()
                    .body(ClientDetail.class);
        } catch (Exception e) {
            System.err.println("Error fetching client details: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get accounts for a specific client with pagination
     * @param clientId The client ID
     * @param page Page number (0-based)
     * @param size Page size
     * @return Paginated list of client accounts
     */
    public PageResult<ClientAccount> getClientAccounts(String clientId, int page, int size) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/bank/clients/{clientId}/accounts")
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build(clientId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return parsePageResult(response, ClientAccount.class);
        } catch (Exception e) {
            System.err.println("Error fetching client accounts: " + e.getMessage());
            return emptyPageResult(page, size);
        }
    }

    /**
     * Get all profiles for a specific client (returns empty list for MVP)
     * @param clientId The client ID
     * @return List of client profiles (empty for MVP)
     */
    public List<Object> getClientProfiles(String clientId) {
        try {
            return restClient.get()
                    .uri("/api/v1/bank/clients/{clientId}/profiles", clientId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Object>>() {});
        } catch (Exception e) {
            System.err.println("Error fetching client profiles: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> PageResult<T> parsePageResult(Map<String, Object> response, Class<T> contentType) {
        PageResult<T> result = new PageResult<>();
        if (response != null) {
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.get("content");
            if (contentList != null) {
                List<T> content = contentList.stream()
                        .map(item -> objectMapper.convertValue(item, contentType))
                        .toList();
                result.setContent(content);
            } else {
                result.setContent(Collections.emptyList());
            }
            result.setPage(((Number) response.getOrDefault("page", 0)).intValue());
            result.setSize(((Number) response.getOrDefault("size", 20)).intValue());
            result.setTotalElements(((Number) response.getOrDefault("totalElements", 0L)).longValue());
            result.setTotalPages(((Number) response.getOrDefault("totalPages", 0)).intValue());
            result.setFirst((Boolean) response.getOrDefault("first", true));
            result.setLast((Boolean) response.getOrDefault("last", true));
            result.setHasNext((Boolean) response.getOrDefault("hasNext", false));
            result.setHasPrevious((Boolean) response.getOrDefault("hasPrevious", false));
        }
        return result;
    }

    private <T> PageResult<T> emptyPageResult(int page, int size) {
        PageResult<T> result = new PageResult<>();
        result.setContent(Collections.emptyList());
        result.setPage(page);
        result.setSize(size);
        result.setTotalElements(0);
        result.setTotalPages(0);
        result.setFirst(true);
        result.setLast(true);
        result.setHasNext(false);
        result.setHasPrevious(false);
        return result;
    }
}
