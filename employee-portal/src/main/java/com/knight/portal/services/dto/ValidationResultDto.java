package com.knight.portal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * DTO for validation result from payor enrolment API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValidationResultDto {
    private boolean valid;
    private int payorCount;
    private List<ValidationErrorDto> errors;
    private String batchId;

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public int getPayorCount() { return payorCount; }
    public void setPayorCount(int payorCount) { this.payorCount = payorCount; }

    public List<ValidationErrorDto> getErrors() { return errors; }
    public void setErrors(List<ValidationErrorDto> errors) { this.errors = errors; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ValidationErrorDto {
        private int payorIndex;
        private String businessName;
        private String field;
        private String message;

        public int getPayorIndex() { return payorIndex; }
        public void setPayorIndex(int payorIndex) { this.payorIndex = payorIndex; }

        public String getBusinessName() { return businessName; }
        public void setBusinessName(String businessName) { this.businessName = businessName; }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
