package com.knight.platform.sharedkernel;

import java.util.regex.Pattern;

/**
 * Client account identifier.
 * URN format: {accountSystem}:{accountType}:{accountNumberSegments}
 *
 * For OFI accounts, accountType is an OfiAccountType (CAN, IBAN, US, SWIFT).
 * For other systems, accountType is an AccountType (DDA, CC, LOC, MTG).
 *
 * Validation rules:
 * - CAN_DDA/FCA/LOC/MTG: transit(5):accountNumber(12)
 * - US_FIN/FIS: accountNumber (variable)
 * - OFI CAN: bank(3):transit(5):accountNumber(12)
 * - OFI IBAN: bic(8-11):ibanAccountNumber(up to 34)
 * - OFI US: abaRouting(9):accountNumber(17)
 * - OFI SWIFT: bic(8-11):bban(up to 34)
 */
public record ClientAccountId(
    AccountSystem accountSystem,
    String accountType,
    String accountNumberSegments
) {

    private static final Pattern CAN_SEGMENTS_PATTERN = Pattern.compile("^\\d{5}:\\d{12}$");
    private static final Pattern OFI_CAN_PATTERN = Pattern.compile("^\\d{3}:\\d{5}:\\d{12}$");
    private static final Pattern OFI_IBAN_PATTERN = Pattern.compile("^[A-Z0-9]{8,11}:[A-Z0-9]{1,34}$");
    private static final Pattern OFI_US_PATTERN = Pattern.compile("^\\d{9}:[A-Z0-9]{1,17}$");
    private static final Pattern OFI_SWIFT_PATTERN = Pattern.compile("^[A-Z0-9]{8,11}:[A-Z0-9]{1,34}$");
    private static final Pattern CAN_GRADS_PATTERN = Pattern.compile("^(OCC|QCC|BCC)\\d{12}$");

    public ClientAccountId {
        if (accountSystem == null) {
            throw new IllegalArgumentException("Account system cannot be null");
        }
        if (accountType == null || accountType.isBlank()) {
            throw new IllegalArgumentException("Account type cannot be null or blank");
        }
        if (accountNumberSegments == null || accountNumberSegments.isBlank()) {
            throw new IllegalArgumentException("Account number segments cannot be null or blank");
        }

        validateAccountTypeAndSegments(accountSystem, accountType, accountNumberSegments);
    }

    private static void validateAccountTypeAndSegments(
            AccountSystem accountSystem,
            String accountType,
            String accountNumberSegments) {
        switch (accountSystem) {
            case CAN_DDA, CAN_FCA, CAN_LOC, CAN_MTG -> {
                // Validate account type is valid AccountType enum
                try {
                    AccountType.valueOf(accountType);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                        "Invalid account type for " + accountSystem + ": " + accountType);
                }

                // Validate format: transit(5):accountNumber(12)
                if (!CAN_SEGMENTS_PATTERN.matcher(accountNumberSegments).matches()) {
                    throw new IllegalArgumentException(
                        "Invalid account number segments for " + accountSystem +
                        ". Expected format: transit(5):accountNumber(12)");
                }
            }
            case US_FIN, US_FIS -> {
                // Validate account type is valid AccountType enum
                try {
                    AccountType.valueOf(accountType);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                        "Invalid account type for " + accountSystem + ": " + accountType);
                }
                // No specific format required for account number segments
            }
            case OFI -> {
                // Validate account type is valid OfiAccountType enum
                OfiAccountType ofiType;
                try {
                    ofiType = OfiAccountType.valueOf(accountType);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                        "Invalid OFI account type: " + accountType +
                        ". Must be one of: CAN, IBAN, US, SWIFT");
                }

                // Validate segments based on OFI type
                validateOfiSegments(ofiType, accountNumberSegments);
            }
            case CAN_GRADS -> {
                try {
                    AccountType type = AccountType.valueOf(accountType);
                    if (type != AccountType.PAD && type != AccountType.PAP) {
                        throw new IllegalArgumentException("CAN_GRADS supports PAD and PAP");
                    }
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid account type for " + accountSystem + ": " + accountType);
                }

                if (!CAN_GRADS_PATTERN.matcher(accountNumberSegments).matches()) {
                    throw new IllegalArgumentException(
                        "Invalid GRADS Account: Must start with OCC, QCC, or BCC followed by 12 digits");
                }
            }
        }
    }

    private static void validateOfiSegments(OfiAccountType ofiType, String accountNumberSegments) {
        switch (ofiType) {
            case CAN -> {
                if (!OFI_CAN_PATTERN.matcher(accountNumberSegments).matches()) {
                    throw new IllegalArgumentException(
                        "Invalid OFI CAN account number segments. " +
                        "Expected format: bank(3):transit(5):accountNumber(12)");
                }
            }
            case IBAN -> {
                if (!OFI_IBAN_PATTERN.matcher(accountNumberSegments).matches()) {
                    throw new IllegalArgumentException(
                        "Invalid OFI IBAN account number segments. " +
                        "Expected format: bic(8-11):ibanAccountNumber(up to 34)");
                }
            }
            case US -> {
                if (!OFI_US_PATTERN.matcher(accountNumberSegments).matches()) {
                    throw new IllegalArgumentException(
                        "Invalid OFI US account number segments. " +
                        "Expected format: abaRouting(9):accountNumber(up to 17)");
                }
            }
            case SWIFT -> {
                if (!OFI_SWIFT_PATTERN.matcher(accountNumberSegments).matches()) {
                    throw new IllegalArgumentException(
                        "Invalid OFI SWIFT account number segments. " +
                        "Expected format: bic(8-11):bban(up to 34)");
                }
            }
        }
    }

    /**
     * Returns the URN representation of this account ID.
     * Format: {accountSystem}:{accountType}:{accountNumberSegments}
     */
    public String urn() {
        return accountSystem + ":" + accountType + ":" + accountNumberSegments;
    }

    /**
     * Parses a URN string into a ClientAccountId.
     *
     * @param urn the URN string to parse
     * @return the parsed ClientAccountId
     * @throws IllegalArgumentException if the URN format is invalid
     */
    public static ClientAccountId of(String urn) {
        if (urn == null || urn.isBlank()) {
            throw new IllegalArgumentException("URN cannot be null or blank");
        }

        String[] parts = urn.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                "Invalid ClientAccountId URN format. " +
                "Expected: {accountSystem}:{accountType}:{accountNumberSegments}");
        }

        AccountSystem system;
        try {
            system = AccountSystem.valueOf(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid account system: " + parts[0]);
        }

        return new ClientAccountId(system, parts[1], parts[2]);
    }
}
