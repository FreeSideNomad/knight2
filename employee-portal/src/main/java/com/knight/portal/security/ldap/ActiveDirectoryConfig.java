package com.knight.portal.security.ldap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;

/**
 * LDAP configuration for production using Microsoft Active Directory.
 * This configuration is active only when the 'prod' profile is enabled.
 */
@Configuration
@Profile("prod")
public class ActiveDirectoryConfig {

    @Value("${ldap.ad.domain}")
    private String adDomain;

    @Value("${ldap.ad.url}")
    private String adUrl;

    @Value("${ldap.ad.root-dn:}")
    private String rootDn;

    @Value("${ldap.ad.search-filter:(sAMAccountName={0})}")
    private String searchFilter;

    @Bean
    public ActiveDirectoryLdapAuthenticationProvider adAuthProvider(LdapUserDetailsMapper userDetailsMapper) {
        ActiveDirectoryLdapAuthenticationProvider provider =
                new ActiveDirectoryLdapAuthenticationProvider(adDomain, adUrl, rootDn);

        provider.setSearchFilter(searchFilter);
        provider.setConvertSubErrorCodesToExceptions(true);
        provider.setUseAuthenticationRequestCredentials(true);
        provider.setUserDetailsContextMapper(userDetailsMapper);

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(ActiveDirectoryLdapAuthenticationProvider adAuthProvider) {
        return new ProviderManager(adAuthProvider);
    }
}
