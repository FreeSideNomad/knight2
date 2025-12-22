package com.knight.portal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * DTO for batch detail from API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchDetailDto {
    private String batchId;
    private String batchType;
    private String batchTypeDisplayName;
    private String sourceProfileId;
    private String status;
    private String statusDisplayName;
    private int totalItems;
    private int successCount;
    private int failedCount;
    private int pendingCount;
    private Instant createdAt;
    private String createdBy;
    private Instant startedAt;
    private Instant completedAt;

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public String getBatchType() { return batchType; }
    public void setBatchType(String batchType) { this.batchType = batchType; }

    public String getBatchTypeDisplayName() { return batchTypeDisplayName; }
    public void setBatchTypeDisplayName(String batchTypeDisplayName) { this.batchTypeDisplayName = batchTypeDisplayName; }

    public String getSourceProfileId() { return sourceProfileId; }
    public void setSourceProfileId(String sourceProfileId) { this.sourceProfileId = sourceProfileId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusDisplayName() { return statusDisplayName; }
    public void setStatusDisplayName(String statusDisplayName) { this.statusDisplayName = statusDisplayName; }

    public int getTotalItems() { return totalItems; }
    public void setTotalItems(int totalItems) { this.totalItems = totalItems; }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }

    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
