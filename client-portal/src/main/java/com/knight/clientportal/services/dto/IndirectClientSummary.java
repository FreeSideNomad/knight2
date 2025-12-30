package com.knight.clientportal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndirectClientSummary {
    private String id;
    private String businessName;
    private String externalReference;
    private String status;
    private int accountCount;
    private int personCount;
    private Instant createdAt;
}
