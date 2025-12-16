package com.knight.portal.services.dto;

/**
 * Response DTO for profile creation.
 */
public class CreateProfileResponse {
    private String profileId;
    private String name;

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
