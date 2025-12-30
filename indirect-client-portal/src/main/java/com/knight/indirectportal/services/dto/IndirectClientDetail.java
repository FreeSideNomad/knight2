package com.knight.indirectportal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndirectClientDetail {
    private String id;
    private String parentClientId;
    private String profileId;
    private String clientType;
    private String businessName;
    private String status;
    private List<RelatedPersonDto> relatedPersons;
    private List<OfiAccount> ofiAccounts;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OfiAccount {
        private String accountId;
        private String bankCode;
        private String transitNumber;
        private String accountNumber;
        private String accountHolderName;
        private String status;
        private String formattedAccountId;
        private Instant addedAt;
    }
}
