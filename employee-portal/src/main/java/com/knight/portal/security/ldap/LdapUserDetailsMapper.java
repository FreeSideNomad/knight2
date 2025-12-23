package com.knight.portal.security.ldap;

import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Maps LDAP directory attributes to LdapAuthenticatedUser.
 * Supports both standard LDAP and Active Directory attributes.
 */
@Component
public class LdapUserDetailsMapper implements UserDetailsContextMapper {

    @Override
    public UserDetails mapUserFromContext(DirContextOperations ctx,
                                          String username,
                                          Collection<? extends GrantedAuthority> authorities) {

        String email = ctx.getStringAttribute("mail");
        String displayName = ctx.getStringAttribute("cn");
        String firstName = ctx.getStringAttribute("givenName");
        String lastName = ctx.getStringAttribute("sn");
        String employeeId = ctx.getStringAttribute("employeeNumber");
        String department = ctx.getStringAttribute("department");

        // For Active Directory, also check these attributes
        String userPrincipalName = ctx.getStringAttribute("userPrincipalName");

        return LdapAuthenticatedUser.builder()
                .username(username)
                .email(email != null ? email : userPrincipalName)
                .displayName(displayName)
                .firstName(firstName)
                .lastName(lastName)
                .employeeId(employeeId)
                .department(department)
                .authorities(authorities)
                .build();
    }

    @Override
    public void mapUserToContext(UserDetails user, DirContextAdapter ctx) {
        // Not needed for authentication-only use case
    }
}
