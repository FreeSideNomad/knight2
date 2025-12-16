package com.knight.portal.services.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Request DTO for creating a profile.
 */
public class CreateProfileRequest {
    private String profileType;
    private String name;
    private List<ClientAccountSelection> clients;

    public String getProfileType() { return profileType; }
    public void setProfileType(String profileType) { this.profileType = profileType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<ClientAccountSelection> getClients() { return clients; }
    public void setClients(List<ClientAccountSelection> clients) { this.clients = clients; }

    public static class ClientAccountSelection {
        private String clientId;
        @JsonProperty("isPrimary")
        private boolean isPrimary;
        private String accountEnrollmentType;
        private List<String> accountIds;

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        @JsonProperty("isPrimary")
        public boolean isPrimary() { return isPrimary; }
        public void setIsPrimary(boolean isPrimary) { this.isPrimary = isPrimary; }

        public String getAccountEnrollmentType() { return accountEnrollmentType; }
        public void setAccountEnrollmentType(String accountEnrollmentType) { this.accountEnrollmentType = accountEnrollmentType; }

        public List<String> getAccountIds() { return accountIds; }
        public void setAccountIds(List<String> accountIds) { this.accountIds = accountIds; }
    }
}
