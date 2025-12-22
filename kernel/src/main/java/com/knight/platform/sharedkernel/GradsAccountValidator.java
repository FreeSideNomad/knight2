package com.knight.platform.sharedkernel;

/**
 * Validation utility for GRADS (CAN_GRADS) accounts.
 */
public class GradsAccountValidator {

    public static boolean isValidGradsAccount(ClientAccountId accountId) {
        return accountId != null && accountId.accountSystem() == AccountSystem.CAN_GRADS;
    }

    public static boolean isPapAccount(ClientAccountId accountId) {
        return isValidGradsAccount(accountId) &&
               AccountType.PAP.name().equals(accountId.accountType());
    }

    public static boolean isPdbAccount(ClientAccountId accountId) {
        return isValidGradsAccount(accountId) &&
               AccountType.PDB.name().equals(accountId.accountType());
    }

    public static String extractGsan(ClientAccountId accountId) {
        if (!isValidGradsAccount(accountId)) {
            throw new IllegalArgumentException("Not a GRADS account");
        }
        return accountId.accountNumberSegments();  // GSAN is the entire accountNumberSegments (10 digits)
    }
}
