package com.knight.portal.services.dto;

/**
 * DTO for profile summary in the portal.
 */
public class ProfileSummary {
    private String profileId;
    private String name;
    private String profileType;
    private String status;
    private String primaryClientId;
    private int clientCount;
    private int serviceEnrollmentCount;
    private int accountEnrollmentCount;

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProfileType() { return profileType; }
    public void setProfileType(String profileType) { this.profileType = profileType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPrimaryClientId() { return primaryClientId; }
    public void setPrimaryClientId(String primaryClientId) { this.primaryClientId = primaryClientId; }

    public int getClientCount() { return clientCount; }
    public void setClientCount(int clientCount) { this.clientCount = clientCount; }

    public int getServiceEnrollmentCount() { return serviceEnrollmentCount; }
    public void setServiceEnrollmentCount(int serviceEnrollmentCount) { this.serviceEnrollmentCount = serviceEnrollmentCount; }

    public int getAccountEnrollmentCount() { return accountEnrollmentCount; }
    public void setAccountEnrollmentCount(int accountEnrollmentCount) { this.accountEnrollmentCount = accountEnrollmentCount; }
}
