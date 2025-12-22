package com.knight.portal.services.dto;

import java.time.Instant;
import java.util.Set;

/**
 * Detailed user information.
 */
public class UserDetail {
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private String status;
    private String userType;
    private String identityProvider;
    private String profileId;
    private String identityProviderUserId;
    private Set<String> roles;
    private boolean passwordSet;
    private boolean mfaEnrolled;
    private Instant createdAt;
    private String createdBy;
    private Instant lastSyncedAt;
    private String lockReason;
    private String deactivationReason;

    // Getters and setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return firstName != null ? firstName : (lastName != null ? lastName : "");
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getIdentityProvider() { return identityProvider; }
    public void setIdentityProvider(String identityProvider) { this.identityProvider = identityProvider; }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getIdentityProviderUserId() { return identityProviderUserId; }
    public void setIdentityProviderUserId(String identityProviderUserId) { this.identityProviderUserId = identityProviderUserId; }

    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }

    public boolean isPasswordSet() { return passwordSet; }
    public void setPasswordSet(boolean passwordSet) { this.passwordSet = passwordSet; }

    public boolean isMfaEnrolled() { return mfaEnrolled; }
    public void setMfaEnrolled(boolean mfaEnrolled) { this.mfaEnrolled = mfaEnrolled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

    public String getLockReason() { return lockReason; }
    public void setLockReason(String lockReason) { this.lockReason = lockReason; }

    public String getDeactivationReason() { return deactivationReason; }
    public void setDeactivationReason(String deactivationReason) { this.deactivationReason = deactivationReason; }
}
