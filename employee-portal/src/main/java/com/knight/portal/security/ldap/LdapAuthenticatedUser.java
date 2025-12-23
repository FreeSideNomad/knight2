package com.knight.portal.security.ldap;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * Custom UserDetails implementation for LDAP authenticated users.
 * Contains user attributes extracted from LDAP directory.
 */
@Getter
@Builder
public class LdapAuthenticatedUser implements UserDetails {

    private final String username;
    private final String email;
    private final String displayName;
    private final String firstName;
    private final String lastName;
    private final String employeeId;
    private final String department;
    private final Collection<? extends GrantedAuthority> authorities;

    @Override
    public String getPassword() {
        // Password is not stored after LDAP authentication
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
