package com.knight.clientportal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchItemDto {
    private int sequenceNumber;
    private String businessName;
    private String status;
    private String errorMessage;
}
