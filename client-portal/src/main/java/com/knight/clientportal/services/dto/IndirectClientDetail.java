package com.knight.clientportal.services.dto;

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
    private String businessName;
    private String externalReference;
    private String status;
    private List<RelatedPerson> relatedPersons;
    private List<OfiAccount> ofiAccounts;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RelatedPerson {
        private String personId;
        private String name;
        private String role;
        private String email;
        private String phone;
        private Instant addedAt;
    }

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
