package com.knight.application.security.access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation indicating that endpoints require indirect client access.
 *
 * <p>This annotation marks controllers that should be accessible to indirect clients
 * (related persons of direct clients). The following token issuers are allowed:</p>
 *
 * <ul>
 *   <li><strong>Auth0</strong> - For indirect clients authenticating through the Auth0 identity provider</li>
 * </ul>
 *
 * <p>Entra ID tokens, Employee Portal tokens, and ANP tokens are NOT permitted for
 * indirect client endpoints. Only Auth0 authentication is supported for indirect clients.</p>
 *
 * @see BankAccess
 * @see ClientAccess
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface IndirectClientAccess {
}
