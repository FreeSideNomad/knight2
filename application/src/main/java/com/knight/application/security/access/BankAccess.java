package com.knight.application.security.access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation indicating that endpoints require bank administrator access.
 *
 * <p>This annotation marks controllers that should only be accessible to bank employees
 * and administrators. The following token issuers are allowed:</p>
 *
 * <ul>
 *   <li><strong>Entra ID (Azure AD)</strong> - For bank employees authenticating through Microsoft Entra ID</li>
 *   <li><strong>Employee Portal</strong> - For internal gateway tokens from the employee portal application</li>
 * </ul>
 *
 * <p>Auth0 tokens and ANP tokens are NOT permitted for bank admin endpoints.</p>
 *
 * @see ClientAccess
 * @see IndirectClientAccess
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface BankAccess {
}
