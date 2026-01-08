package com.knight.portal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Set;

/**
 * User summary for profile users list.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileUser {
    private String userId;
    private String loginId;
    private String email;
    private String firstName;
    private String lastName;
    private String status;
    private String statusDisplayName;
    private String lockType;
    private Set<String> roles;
    private boolean canResendInvitation;
    private boolean canLock;
    private boolean canDeactivate;
    private Instant createdAt;
    private Instant lastLoggedInAt;

    // Getters and setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }

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

    public String getStatusDisplayName() { return statusDisplayName; }
    public void setStatusDisplayName(String statusDisplayName) { this.statusDisplayName = statusDisplayName; }

    public String getLockType() { return lockType; }
    public void setLockType(String lockType) { this.lockType = lockType; }

    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }

    public boolean isCanResendInvitation() { return canResendInvitation; }
    public void setCanResendInvitation(boolean canResendInvitation) { this.canResendInvitation = canResendInvitation; }

    public boolean isCanLock() { return canLock; }
    public void setCanLock(boolean canLock) { this.canLock = canLock; }

    public boolean isCanDeactivate() { return canDeactivate; }
    public void setCanDeactivate(boolean canDeactivate) { this.canDeactivate = canDeactivate; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastLoggedInAt() { return lastLoggedInAt; }
    public void setLastLoggedInAt(Instant lastLoggedInAt) { this.lastLoggedInAt = lastLoggedInAt; }
}
