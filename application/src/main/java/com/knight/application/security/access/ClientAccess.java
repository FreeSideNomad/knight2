package com.knight.application.security.access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation indicating that endpoints require direct client access.
 *
 * <p>This annotation marks controllers that should be accessible to direct clients
 * of the bank. The following token issuers are allowed:</p>
 *
 * <ul>
 *   <li><strong>Auth0</strong> - For clients authenticating through the Auth0 identity provider</li>
 *   <li><strong>ANP (future)</strong> - For clients authenticating through the ANP identity provider</li>
 * </ul>
 *
 * <p>Entra ID tokens and Employee Portal tokens are NOT permitted for direct client endpoints.</p>
 *
 * @see BankAccess
 * @see IndirectClientAccess
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ClientAccess {
}
