package com.knight.portal.services.dto;

import java.util.Set;

/**
 * Request to add a new user to a profile.
 */
public class AddUserRequest {
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> roles;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
}
