package com.knight.portal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

/**
 * DTO for individual batch item.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchItemDto {
    private String batchItemId;
    private int sequenceNumber;
    private String businessName;
    private String status;
    private String statusDisplayName;
    private BatchItemResultDto result;
    private String errorMessage;
    private Instant processedAt;

    public String getBatchItemId() { return batchItemId; }
    public void setBatchItemId(String batchItemId) { this.batchItemId = batchItemId; }

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusDisplayName() { return statusDisplayName; }
    public void setStatusDisplayName(String statusDisplayName) { this.statusDisplayName = statusDisplayName; }

    public BatchItemResultDto getResult() { return result; }
    public void setResult(BatchItemResultDto result) { this.result = result; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BatchItemResultDto {
        private String indirectClientId;
        private String profileId;
        private List<String> userIds;

        public String getIndirectClientId() { return indirectClientId; }
        public void setIndirectClientId(String indirectClientId) { this.indirectClientId = indirectClientId; }

        public String getProfileId() { return profileId; }
        public void setProfileId(String profileId) { this.profileId = profileId; }

        public List<String> getUserIds() { return userIds; }
        public void setUserIds(List<String> userIds) { this.userIds = userIds; }
    }
}
