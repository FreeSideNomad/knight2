package com.knight.clientportal.services;

import com.knight.clientportal.services.dto.IndirectClientDetail;
import com.knight.clientportal.services.dto.IndirectClientSummary;
import com.knight.clientportal.services.dto.RelatedPersonRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service for indirect client operations via Platform API.
 * Uses /api/v1/client endpoints for direct client to manage their indirect clients.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndirectClientService {

    private final WebClient apiWebClient;

    /**
     * Get all indirect clients for the current client.
     */
    public List<IndirectClientSummary> getIndirectClients() {
        return apiWebClient.get()
                .uri("/indirect-clients")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<IndirectClientSummary>>() {})
                .onErrorResume(e -> {
                    log.error("Failed to fetch indirect clients", e);
                    return Mono.just(List.of());
                })
                .block();
    }

    /**
     * Get detailed information about an indirect client.
     */
    public IndirectClientDetail getIndirectClient(String indirectClientId) {
        return apiWebClient.get()
                .uri("/indirect-clients/{id}", indirectClientId)
                .retrieve()
                .bodyToMono(IndirectClientDetail.class)
                .onErrorResume(e -> {
                    log.error("Failed to fetch indirect client detail", e);
                    return Mono.empty();
                })
                .block();
    }

    /**
     * Add a related person (admin) to an indirect client.
     * This triggers user creation for ADMIN role persons.
     */
    public void addRelatedPerson(String indirectClientId, RelatedPersonRequest request) {
        apiWebClient.post()
                .uri("/indirect-clients/{id}/persons", indirectClientId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(e -> log.error("Failed to add related person", e))
                .block();
    }
}
