package com.knight.indirectportal.services;

import com.knight.indirectportal.services.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service for indirect client (payor) operations via Platform API.
 * Uses /api/v1/indirect endpoints for the current payor user to manage their own data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndirectClientService {

    private final WebClient apiWebClient;

    /**
     * Get the current indirect client's details.
     */
    public IndirectClientDetail getMyClientDetails() {
        return apiWebClient.get()
                .uri("/me")
                .retrieve()
                .bodyToMono(IndirectClientDetail.class)
                .onErrorResume(e -> {
                    log.error("Failed to fetch client details", e);
                    return Mono.empty();
                })
                .block();
    }

    /**
     * Get OFI accounts for the current indirect client (payor).
     */
    public List<OfiAccountDto> getMyAccounts() {
        return apiWebClient.get()
                .uri("/accounts")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<OfiAccountDto>>() {})
                .onErrorResume(e -> {
                    log.error("Failed to fetch accounts", e);
                    return Mono.just(List.of());
                })
                .block();
    }

    /**
     * Add an OFI account for the current indirect client.
     */
    public void addOfiAccount(AddOfiAccountRequest request) {
        apiWebClient.post()
                .uri("/accounts")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(e -> log.error("Failed to add OFI account", e))
                .block();
    }

    /**
     * Remove an OFI account.
     */
    public void removeOfiAccount(String accountId) {
        apiWebClient.delete()
                .uri("/accounts/{accountId}", accountId)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(e -> log.error("Failed to remove OFI account", e))
                .block();
    }
}
