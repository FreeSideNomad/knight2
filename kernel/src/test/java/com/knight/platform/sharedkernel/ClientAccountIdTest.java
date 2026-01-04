package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientAccountIdTest {

    // Valid format constants for testing
    private static final String VALID_CAN_SEGMENTS = "12345:123456789012";
    private static final String VALID_OFI_CAN_SEGMENTS = "001:12345:123456789012";
    private static final String VALID_OFI_IBAN_SEGMENTS = "DEUTDEFF:DE89370400440532013000";
    private static final String VALID_OFI_US_SEGMENTS = "123456789:ACCT12345678901";
    private static final String VALID_OFI_SWIFT_SEGMENTS = "DEUTDEFF:DE89370400440532013000";
    private static final String VALID_GRADS_SEGMENTS = "1234567890";

    // ==================== Constructor validation tests ====================

    @Test
    void constructor_withNullAccountSystem_throwsException() {
        assertThatThrownBy(() -> new ClientAccountId(null, "DDA", VALID_CAN_SEGMENTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account system cannot be null");
    }

    @Test
    void constructor_withNullAccountType_throwsException() {
        assertThatThrownBy(() -> new ClientAccountId(AccountSystem.CAN_DDA, null, VALID_CAN_SEGMENTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account type cannot be null or blank");
    }

    @Test
    void constructor_withBlankAccountType_throwsException() {
        assertThatThrownBy(() -> new ClientAccountId(AccountSystem.CAN_DDA, "   ", VALID_CAN_SEGMENTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account type cannot be null or blank");
    }

    @Test
    void constructor_withNullAccountNumberSegments_throwsException() {
        assertThatThrownBy(() -> new ClientAccountId(AccountSystem.CAN_DDA, "DDA", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account number segments cannot be null or blank");
    }

    @Test
    void constructor_withBlankAccountNumberSegments_throwsException() {
        assertThatThrownBy(() -> new ClientAccountId(AccountSystem.CAN_DDA, "DDA", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account number segments cannot be null or blank");
    }

    // ==================== CAN_DDA, CAN_FCA, CAN_LOC, CAN_MTG tests ====================

    @ParameterizedTest
    @EnumSource(value = AccountSystem.class, names = {"CAN_DDA", "CAN_FCA", "CAN_LOC", "CAN_MTG"})
    void constructor_withValidCanSystem_createsClientAccountId(AccountSystem system) {
        ClientAccountId id = new ClientAccountId(system, "DDA", VALID_CAN_SEGMENTS);
        assertThat(id.accountSystem()).isEqualTo(system);
        assertThat(id.accountType()).isEqualTo("DDA");
        assertThat(id.accountNumberSegments()).isEqualTo(VALID_CAN_SEGMENTS);
    }

    @ParameterizedTest
    @EnumSource(value = AccountSystem.class, names = {"CAN_DDA", "CAN_FCA", "CAN_LOC", "CAN_MTG"})
    void constructor_withInvalidAccountTypeForCanSystem_throwsException(AccountSystem system) {
        assertThatThrownBy(() -> new ClientAccountId(system, "INVALID", VALID_CAN_SEGMENTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid account type for " + system + ": INVALID");
    }

    @ParameterizedTest
    @EnumSource(value = AccountSystem.class, names = {"CAN_DDA", "CAN_FCA", "CAN_LOC", "CAN_MTG"})
    void constructor_withInvalidSegmentsForCanSystem_throwsException(AccountSystem system) {
        assertThatThrownBy(() -> new ClientAccountId(system, "DDA", "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid account number segments for " + system +
                        ". Expected format: transit(5):accountNumber(12)");
    }

    @Test
    void constructor_withCanDda_invalidTransitLength_throwsException() {
        assertThatThrownBy(() -> new ClientAccountId(AccountSystem.CAN_DDA, "DDA", "1234:123456789012"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected format: transit(5):accountNumber(12)");
    }

    @Test
    void constructor_withCanDda_invalidAccountNumberLength_throwsException() {
        assertThatThrownBy(() -> new ClientAccountId(AccountSystem.CAN_DDA, "DDA", "12345:12345678901"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected format: transit(5):accountNumber(12)");
    }

    // ==================== US_FIN, US_FIS tests ====================

    @ParameterizedTest
    @EnumSource(value = AccountSystem.class, names = {"US_FIN", "US_FIS"})
    void constructor_withValidUsSystem_createsClientAccountId(AccountSystem system) {
        ClientAccountId id = new ClientAccountId(system, "DDA", "any-format-allowed");
        assertThat(id.accountSystem()).isEqualTo(system);
        assertThat(id.accountType()).isEqualTo("DDA");
        assertThat(id.accountNumberSegments()).isEqualTo("any-format-allowed");
    }

    @ParameterizedTest
    @EnumSource(value = AccountSystem.class, names = {"US_FIN", "US_FIS"})
    void constructor_withInvalidAccountTypeForUsSystem_throwsException(AccountSystem system) {
        assertThatThrownBy(() -> new ClientAccountId(system, "INVALID", "12345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid account type for " + system + ": INVALID");
    }

    // ==================== OFI tests ====================

    @Test
    void constructor_withOfiCan_validSegments_createsClientAccountId() {
        ClientAccountId id = new ClientAccountId(AccountSystem.OFI, "CAN", VALID_OFI_CAN_SEGMENTS);
        assertThat(id.accountSystem()).isEqualTo(AccountSystem.OFI);
        assertThat(id.accountType()).isEqualTo("CAN");
        assertThat(id.accountNumberSegments()).isEqualTo(VALID_OFI_CAN_SEGMENTS);
    }

    @Test
    void constructor_withOfiIban_validSegments_createsClientAccountId() {
        ClientAccountId id = new ClientAccountId(AccountSystem.OFI, "IBAN", VALID_OFI_IBAN_SEGMENTS);
        assertThat(id.accountSystem()).isEqualTo(AccountSystem.OFI);
        assertThat(id.accountType()).isEqualTo("IBAN");
        assertThat(id.accountNumberSegments()).isEqualTo(VALID_OFI_IBAN_SEGMENTS);
    }

    @Test
    void constructor_withOfiUs_validSegments_createsClientAccountId() {
        ClientAccountId id = new ClientAccountId(AccountSystem.OFI, "US", VALID_OFI_US_SEGMENTS);
        assertThat(id.accountSystem()).isEqualTo(AccountSystem.OFI);
        assertThat(id.accountType()).isEqualTo("US");
        assertThat(id.accountNumberSegments()).isEqualTo(VALID_OFI_US_SEGMENTS);
    }

    @Test
    void constructor_withOfiSwift_validSegments_createsClientAccountId() {
        ClientAccountId id = new ClientAccountId(AccountSystem.OFI, "SWIFT", VALID_OFI_SWIFT_SEGMENTS);
        assertThat(id.accountSystem()).isEqualTo(AccountSystem.OFI);
        assertThat(id.accountType()).isEqualTo("SWIFT");
        assertThat(id.accountNumberSegments()).isEqualTo(VALID_OFI_SWIFT_SEGMENTS);
    }

    @Test
    void constructor_withOfi_invalidAccountType_throwsException() {
        assertThatThrownBy(() -> new ClientAccountId(AccountSystem.OFI, "INVALID", VALID_OFI_CAN_SEGMENTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid OFI account type: INVALID. Must be one of: CAN, IBAN, US, SWIFT");
    }

    @Test
    void constructor_withOfiCan_invalidSegments_throwsException() {
        assertThatThrownBy(() -> new ClientAccountId(AccountSystem.OFI, "CAN", "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid OFI CAN account number segments. " +
                        "Expected format: bank(3):transit(5):accountNumber(12)");
    }

    @Test
    void constructor_withOfiIban_invalidSegments_throwsException() {
        assertThatThrownBy(() -> new ClientAccountId(AccountSystem.OFI, "IBAN", "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid OFI IBAN account number segments. " +
                        "Expected format: bic(8-11):ibanAccountNumber(up to 34)");
    }

    @Test
    void constructor_withOfiUs_invalidSegments_throwsException() {
        assertThatThrownBy(() -> new ClientAccountId(AccountSystem.OFI, "US", "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid OFI US account number segments. " +
                        "Expected format: abaRouting(9):accountNumber(up to 17)");
    }

    @Test
    void constructor_withOfiSwift_invalidSegments_throwsException() {
        assertThatThrownBy(() -> new ClientAccountId(AccountSystem.OFI, "SWIFT", "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid OFI SWIFT account number segments. " +
                        "Expected format: bic(8-11):bban(up to 34)");
    }

    // ==================== CAN_GRADS tests ====================

    @Test
    void constructor_withCanGrads_papAccountType_createsClientAccountId() {
        ClientAccountId id = new ClientAccountId(AccountSystem.CAN_GRADS, "PAP", VALID_GRADS_SEGMENTS);
        assertThat(id.accountSystem()).isEqualTo(AccountSystem.CAN_GRADS);
        assertThat(id.accountType()).isEqualTo("PAP");
        assertThat(id.accountNumberSegments()).isEqualTo(VALID_GRADS_SEGMENTS);
    }

    @Test
    void constructor_withCanGrads_pdbAccountType_createsClientAccountId() {
        ClientAccountId id = new ClientAccountId(AccountSystem.CAN_GRADS, "PDB", VALID_GRADS_SEGMENTS);
        assertThat(id.accountSystem()).isEqualTo(AccountSystem.CAN_GRADS);
        assertThat(id.accountType()).isEqualTo("PDB");
        assertThat(id.accountNumberSegments()).isEqualTo(VALID_GRADS_SEGMENTS);
    }

    @Test
    void constructor_withCanGrads_nonPapPdbAccountType_throwsException() {
        // DDA is a valid AccountType but not valid for CAN_GRADS (only PAP and PDB allowed)
        // This triggers the "CAN_GRADS supports PAP and PDB" exception path
        assertThatThrownBy(() -> new ClientAccountId(AccountSystem.CAN_GRADS, "DDA", VALID_GRADS_SEGMENTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid account type for CAN_GRADS: DDA");
    }

    @Test
    void constructor_withCanGrads_unknownAccountType_throwsException() {
        // INVALID is not a valid AccountType, so it fails earlier
        assertThatThrownBy(() -> new ClientAccountId(AccountSystem.CAN_GRADS, "INVALID", VALID_GRADS_SEGMENTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid account type for CAN_GRADS: INVALID");
    }

    @Test
    void constructor_withCanGrads_invalidSegmentsFormat_throwsException() {
        assertThatThrownBy(() -> new ClientAccountId(AccountSystem.CAN_GRADS, "PAP", "123456789")) // 9 digits
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid GRADS Account: Must be 10 digits");
    }

    @Test
    void constructor_withCanGrads_invalidSegmentsWithLetters_throwsException() {
        assertThatThrownBy(() -> new ClientAccountId(AccountSystem.CAN_GRADS, "PAP", "123456789A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid GRADS Account: Must be 10 digits");
    }

    // ==================== URN and of() method tests ====================

    @Test
    void urn_returnsCorrectFormat() {
        ClientAccountId id = new ClientAccountId(AccountSystem.CAN_DDA, "DDA", VALID_CAN_SEGMENTS);
        assertThat(id.urn()).isEqualTo("CAN_DDA:DDA:" + VALID_CAN_SEGMENTS);
    }

    @Test
    void of_withValidUrn_createsClientAccountId() {
        String urn = "CAN_DDA:DDA:" + VALID_CAN_SEGMENTS;
        ClientAccountId id = ClientAccountId.of(urn);
        assertThat(id.accountSystem()).isEqualTo(AccountSystem.CAN_DDA);
        assertThat(id.accountType()).isEqualTo("DDA");
        assertThat(id.accountNumberSegments()).isEqualTo(VALID_CAN_SEGMENTS);
    }

    @Test
    void of_withNullUrn_throwsException() {
        assertThatThrownBy(() -> ClientAccountId.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("URN cannot be null or blank");
    }

    @Test
    void of_withBlankUrn_throwsException() {
        assertThatThrownBy(() -> ClientAccountId.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("URN cannot be null or blank");
    }

    @Test
    void of_withInvalidUrnFormat_throwsException() {
        assertThatThrownBy(() -> ClientAccountId.of("invalid-urn"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid ClientAccountId URN format. " +
                        "Expected: {accountSystem}:{accountType}:{accountNumberSegments}");
    }

    @Test
    void of_withTwoPartUrn_throwsException() {
        assertThatThrownBy(() -> ClientAccountId.of("CAN_DDA:DDA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid ClientAccountId URN format. " +
                        "Expected: {accountSystem}:{accountType}:{accountNumberSegments}");
    }

    @Test
    void of_withInvalidAccountSystem_throwsException() {
        assertThatThrownBy(() -> ClientAccountId.of("INVALID:DDA:" + VALID_CAN_SEGMENTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid account system: INVALID");
    }

    @Test
    void of_parsesOfiCanCorrectly() {
        String urn = "OFI:CAN:" + VALID_OFI_CAN_SEGMENTS;
        ClientAccountId id = ClientAccountId.of(urn);
        assertThat(id.accountSystem()).isEqualTo(AccountSystem.OFI);
        assertThat(id.accountType()).isEqualTo("CAN");
        assertThat(id.accountNumberSegments()).isEqualTo(VALID_OFI_CAN_SEGMENTS);
    }

    // ==================== Equals and hashCode tests ====================

    @Test
    void equals_sameValues_areEqual() {
        ClientAccountId id1 = new ClientAccountId(AccountSystem.CAN_DDA, "DDA", VALID_CAN_SEGMENTS);
        ClientAccountId id2 = new ClientAccountId(AccountSystem.CAN_DDA, "DDA", VALID_CAN_SEGMENTS);
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void equals_differentAccountSystem_areNotEqual() {
        ClientAccountId id1 = new ClientAccountId(AccountSystem.CAN_DDA, "DDA", VALID_CAN_SEGMENTS);
        ClientAccountId id2 = new ClientAccountId(AccountSystem.CAN_FCA, "DDA", VALID_CAN_SEGMENTS);
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void equals_differentAccountType_areNotEqual() {
        ClientAccountId id1 = new ClientAccountId(AccountSystem.CAN_DDA, "DDA", VALID_CAN_SEGMENTS);
        ClientAccountId id2 = new ClientAccountId(AccountSystem.CAN_DDA, "CC", VALID_CAN_SEGMENTS);
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void equals_differentSegments_areNotEqual() {
        ClientAccountId id1 = new ClientAccountId(AccountSystem.CAN_DDA, "DDA", "12345:123456789012");
        ClientAccountId id2 = new ClientAccountId(AccountSystem.CAN_DDA, "DDA", "12345:123456789013");
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void hashCode_sameValues_sameHash() {
        ClientAccountId id1 = new ClientAccountId(AccountSystem.CAN_DDA, "DDA", VALID_CAN_SEGMENTS);
        ClientAccountId id2 = new ClientAccountId(AccountSystem.CAN_DDA, "DDA", VALID_CAN_SEGMENTS);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }
}
