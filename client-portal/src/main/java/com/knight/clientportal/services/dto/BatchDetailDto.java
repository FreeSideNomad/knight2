package com.knight.clientportal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchDetailDto {
    private String batchId;
    private String status;
    private String statusDisplayName;
    private int totalItems;
    private int successCount;
    private int failedCount;
    private int pendingCount;
    private Instant createdAt;
    private Instant completedAt;
}
