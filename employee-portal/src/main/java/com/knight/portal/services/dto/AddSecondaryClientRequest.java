package com.knight.portal.services.dto;

import java.util.List;

/**
 * DTO for adding a secondary client to a profile.
 */
public class AddSecondaryClientRequest {
    private String clientId;
    private String accountEnrollmentType;  // AUTOMATIC or MANUAL
    private List<String> accountIds;       // Required for MANUAL, ignored for AUTOMATIC

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getAccountEnrollmentType() { return accountEnrollmentType; }
    public void setAccountEnrollmentType(String accountEnrollmentType) { this.accountEnrollmentType = accountEnrollmentType; }

    public List<String> getAccountIds() { return accountIds; }
    public void setAccountIds(List<String> accountIds) { this.accountIds = accountIds; }
}
