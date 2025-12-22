package com.knight.portal.services.dto;

import java.util.List;

/**
 * Request DTO for enrolling a service to a profile.
 */
public class EnrollServiceRequest {
    private String serviceType;
    private String configuration;
    private List<AccountLink> accountLinks;

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public List<AccountLink> getAccountLinks() {
        return accountLinks;
    }

    public void setAccountLinks(List<AccountLink> accountLinks) {
        this.accountLinks = accountLinks;
    }

    /**
     * Links an account to a service.
     */
    public static class AccountLink {
        private String clientId;
        private String accountId;

        public AccountLink() {}

        public AccountLink(String clientId, String accountId) {
            this.clientId = clientId;
            this.accountId = accountId;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }
    }
}
