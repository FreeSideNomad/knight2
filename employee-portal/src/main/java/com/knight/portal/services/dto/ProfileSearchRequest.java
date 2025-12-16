package com.knight.portal.services.dto;

import java.util.Set;

/**
 * DTO for profile search requests in the portal.
 */
public class ProfileSearchRequest {
    private String clientId;
    private String clientName;
    private boolean primaryOnly;
    private Set<String> profileTypes;
    private int page;
    private int size;

    public ProfileSearchRequest() {
        this.page = 0;
        this.size = 20;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public boolean isPrimaryOnly() { return primaryOnly; }
    public void setPrimaryOnly(boolean primaryOnly) { this.primaryOnly = primaryOnly; }

    public Set<String> getProfileTypes() { return profileTypes; }
    public void setProfileTypes(Set<String> profileTypes) { this.profileTypes = profileTypes; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
