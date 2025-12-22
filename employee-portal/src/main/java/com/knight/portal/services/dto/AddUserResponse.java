package com.knight.portal.services.dto;

import java.time.Instant;
import java.util.Set;

/**
 * Response after adding a user.
 */
public class AddUserResponse {
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private String status;
    private Set<String> roles;
    private String passwordResetUrl;
    private Instant createdAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }

    public String getPasswordResetUrl() { return passwordResetUrl; }
    public void setPasswordResetUrl(String passwordResetUrl) { this.passwordResetUrl = passwordResetUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
