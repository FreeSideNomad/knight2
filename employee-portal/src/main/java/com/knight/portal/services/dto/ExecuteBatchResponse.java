package com.knight.portal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for batch execution response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecuteBatchResponse {
    private String batchId;
    private String status;
    private int totalItems;
    private String message;

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalItems() { return totalItems; }
    public void setTotalItems(int totalItems) { this.totalItems = totalItems; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
