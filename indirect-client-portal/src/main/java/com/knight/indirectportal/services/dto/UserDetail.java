package com.knight.indirectportal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDetail {
    private String userId;
    private String loginId;
    private String email;
    private String firstName;
    private String lastName;
    private List<String> roles;
    private String status;
    private String statusDisplayName;
    private String lockType;
    private Instant lastLoggedInAt;
    private Instant createdAt;

    /**
     * Get full name combining first and last name.
     */
    public String getName() {
        if (firstName == null && lastName == null) {
            return null;
        }
        if (firstName == null) return lastName;
        if (lastName == null) return firstName;
        return firstName + " " + lastName;
    }

    /**
     * Get lastLogin alias for lastLoggedInAt for backwards compatibility.
     */
    public Instant getLastLogin() {
        return lastLoggedInAt;
    }
}
