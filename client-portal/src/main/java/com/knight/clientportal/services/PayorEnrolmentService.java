package com.knight.clientportal.services;

import com.knight.clientportal.services.dto.BatchDetailDto;
import com.knight.clientportal.services.dto.BatchItemDto;
import com.knight.clientportal.services.dto.ValidationResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Service for payor enrolment (batch import) operations.
 * Uses /api/v1/client endpoints for importing indirect clients.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayorEnrolmentService {

    private final WebClient apiWebClient;

    /**
     * Validate payor enrolment JSON file.
     */
    public ValidationResultDto validateFile(byte[] fileContent, String fileName) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(fileContent) {
            @Override
            public String getFilename() {
                return fileName;
            }
        }).contentType(MediaType.APPLICATION_JSON);

        return apiWebClient.post()
                .uri("/payor-enrolment/validate")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build())
                .retrieve()
                .bodyToMono(ValidationResultDto.class)
                .doOnError(e -> log.error("Failed to validate payor enrolment file", e))
                .block();
    }

    /**
     * Execute payor enrolment batch.
     */
    public void executeBatch(String batchId) {
        Map<String, String> request = Map.of("batchId", batchId);
        apiWebClient.post()
                .uri("/payor-enrolment/execute")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(e -> log.error("Failed to execute payor enrolment batch", e))
                .block();
    }

    /**
     * Get batch details.
     */
    public BatchDetailDto getBatchDetail(String batchId) {
        return apiWebClient.get()
                .uri("/batches/{batchId}", batchId)
                .retrieve()
                .bodyToMono(BatchDetailDto.class)
                .onErrorResume(e -> {
                    log.error("Failed to fetch batch detail", e);
                    return Mono.empty();
                })
                .block();
    }

    /**
     * Get batch items.
     */
    public List<BatchItemDto> getBatchItems(String batchId, String status) {
        String uri = status != null
                ? "/batches/{batchId}/items?status=" + status
                : "/batches/{batchId}/items";

        return apiWebClient.get()
                .uri(uri, batchId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<BatchItemDto>>() {})
                .onErrorResume(e -> {
                    log.error("Failed to fetch batch items", e);
                    return Mono.just(List.of());
                })
                .block();
    }
}
