package com.knight.indirectportal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OfiAccountDto {
    private String accountId;
    private String bankCode;
    private String transitNumber;
    private String accountNumber;
    private String accountHolderName;
    private String status;
    private String formattedAccountId;
    private Instant addedAt;
}
