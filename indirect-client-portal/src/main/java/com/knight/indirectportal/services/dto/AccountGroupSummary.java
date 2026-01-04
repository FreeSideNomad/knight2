package com.knight.indirectportal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountGroupSummary {
    private String groupId;
    private String profileId;
    private String name;
    private String description;
    private int accountCount;
    private Instant createdAt;
    private String createdBy;
}
