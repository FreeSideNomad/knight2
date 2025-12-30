package com.knight.clientportal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValidationResultDto {
    private boolean valid;
    private String batchId;
    private int payorCount;
    private List<ValidationErrorDto> errors;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ValidationErrorDto {
        private int payorIndex;
        private String businessName;
        private String field;
        private String message;
    }
}
