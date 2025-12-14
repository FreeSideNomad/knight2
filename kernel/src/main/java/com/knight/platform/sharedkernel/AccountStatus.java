package com.knight.platform.sharedkernel;

/**
 * Account status enumeration.
 * Represents the current operational state of a bank account.
 */
public enum AccountStatus {
    /**
     * Account is active and can be used for transactions
     */
    ACTIVE,

    /**
     * Account has been closed and cannot be used for transactions
     */
    CLOSED
}
