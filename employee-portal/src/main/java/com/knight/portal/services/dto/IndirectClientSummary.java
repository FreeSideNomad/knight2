package com.knight.portal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndirectClientSummary {
    private String id;
    private String parentClientId;
    private String clientType;
    private String businessName;
    private String status;
    private int relatedPersonCount;
    private Instant createdAt;
}
