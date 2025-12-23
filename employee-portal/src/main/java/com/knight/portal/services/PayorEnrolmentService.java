package com.knight.portal.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.knight.portal.services.dto.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service for payor enrolment API calls.
 */
@Service
public class PayorEnrolmentService {

    private final RestClient restClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public PayorEnrolmentService(
            @Qualifier("apiRestClient") RestClient restClient,
            @Qualifier("apiWebClient") WebClient webClient) {
        this.restClient = restClient;
        this.webClient = webClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Validate payor enrolment JSON file.
     */
    public ValidationResultDto validateFile(String profileId, byte[] fileContent, String fileName) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            }).contentType(MediaType.APPLICATION_JSON);

            Map<String, Object> response = webClient.post()
                    .uri("/api/profiles/{profileId}/payor-enrolment/validate", profileId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(builder.build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null) {
                throw new RuntimeException("No response from validation");
            }

            return objectMapper.convertValue(response, ValidationResultDto.class);
        } catch (Exception e) {
            System.err.println("Error validating file: " + e.getMessage());
            throw new RuntimeException("Failed to validate file: " + e.getMessage(), e);
        }
    }

    /**
     * Start batch execution.
     */
    public ExecuteBatchResponse executeBatch(String profileId, String batchId) {
        try {
            Map<String, Object> request = Map.of("batchId", batchId);

            Map<String, Object> response = restClient.post()
                    .uri("/api/profiles/{profileId}/payor-enrolment/execute", profileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null) {
                throw new RuntimeException("No response from batch execution");
            }

            return objectMapper.convertValue(response, ExecuteBatchResponse.class);
        } catch (Exception e) {
            System.err.println("Error executing batch: " + e.getMessage());
            throw new RuntimeException("Failed to execute batch: " + e.getMessage(), e);
        }
    }

    /**
     * Get batch details.
     */
    public BatchDetailDto getBatchDetail(String batchId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/api/batches/{batchId}", batchId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null) {
                return null;
            }

            return objectMapper.convertValue(response, BatchDetailDto.class);
        } catch (Exception e) {
            System.err.println("Error fetching batch detail: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get batch items.
     */
    public List<BatchItemDto> getBatchItems(String batchId, String status) {
        try {
            String uri = status != null
                    ? "/api/batches/{batchId}/items?status=" + status
                    : "/api/batches/{batchId}/items";

            List<Map<String, Object>> response = restClient.get()
                    .uri(uri, batchId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null) {
                return Collections.emptyList();
            }

            return response.stream()
                    .map(item -> objectMapper.convertValue(item, BatchItemDto.class))
                    .toList();
        } catch (Exception e) {
            System.err.println("Error fetching batch items: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * List batches for a profile.
     */
    public List<BatchSummaryDto> listBatches(String profileId) {
        try {
            List<Map<String, Object>> response = restClient.get()
                    .uri("/api/profiles/{profileId}/payor-enrolment/batches", profileId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null) {
                return Collections.emptyList();
            }

            return response.stream()
                    .map(item -> objectMapper.convertValue(item, BatchSummaryDto.class))
                    .toList();
        } catch (Exception e) {
            System.err.println("Error fetching batches: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
