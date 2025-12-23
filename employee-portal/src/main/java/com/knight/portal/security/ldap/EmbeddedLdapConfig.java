package com.knight.portal.security.ldap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;

/**
 * LDAP configuration for local and Docker development using embedded UnboundID server.
 * This configuration is active when the 'local' or 'docker' profile is enabled.
 */
@Configuration
@Profile({"local", "docker"})
public class EmbeddedLdapConfig {

    @Value("${spring.ldap.embedded.port:8389}")
    private int ldapPort;

    @Value("${spring.ldap.embedded.base-dn:dc=knight,dc=com}")
    private String baseDn;

    @Bean
    public DefaultSpringSecurityContextSource contextSource() {
        return new DefaultSpringSecurityContextSource("ldap://localhost:" + ldapPort + "/" + baseDn);
    }

    @Bean
    public LdapAuthenticationProvider ldapAuthenticationProvider(
            DefaultSpringSecurityContextSource contextSource,
            LdapUserDetailsMapper userDetailsMapper) {

        // User search to find users by uid
        FilterBasedLdapUserSearch userSearch = new FilterBasedLdapUserSearch(
                "ou=people",
                "(uid={0})",
                contextSource
        );

        // Bind authenticator - authenticates by binding to LDAP with user credentials
        BindAuthenticator authenticator = new BindAuthenticator(contextSource);
        authenticator.setUserSearch(userSearch);

        // Authorities populator - fetches group memberships
        DefaultLdapAuthoritiesPopulator authoritiesPopulator = new DefaultLdapAuthoritiesPopulator(
                contextSource,
                "ou=groups"
        );
        authoritiesPopulator.setGroupSearchFilter("(member={0})");
        authoritiesPopulator.setRolePrefix("ROLE_");
        authoritiesPopulator.setConvertToUpperCase(true);

        LdapAuthenticationProvider provider = new LdapAuthenticationProvider(authenticator, authoritiesPopulator);
        provider.setUserDetailsContextMapper(userDetailsMapper);

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(LdapAuthenticationProvider ldapAuthenticationProvider) {
        return authentication -> ldapAuthenticationProvider.authenticate(authentication);
    }
}
