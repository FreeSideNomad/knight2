package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for GradsAccountValidator.
 */
@DisplayName("GradsAccountValidator Tests")
class GradsAccountValidatorTest {

    @Nested
    @DisplayName("isValidGradsAccount()")
    class IsValidGradsAccount {

        @Test
        @DisplayName("should return true for CAN_GRADS account")
        void shouldReturnTrueForCanGradsAccount() {
            // Given
            ClientAccountId gradsAccount = new ClientAccountId(AccountSystem.CAN_GRADS, "PAP", "1234567890");

            // When/Then
            assertThat(GradsAccountValidator.isValidGradsAccount(gradsAccount)).isTrue();
        }

        @Test
        @DisplayName("should return false for CAN_DDA account")
        void shouldReturnFalseForCanDdaAccount() {
            // Given
            ClientAccountId ddaAccount = new ClientAccountId(AccountSystem.CAN_DDA, "DDA", "12345:000000000001");

            // When/Then
            assertThat(GradsAccountValidator.isValidGradsAccount(ddaAccount)).isFalse();
        }

        @Test
        @DisplayName("should return false for OFI account")
        void shouldReturnFalseForOfiAccount() {
            // Given
            ClientAccountId ofiAccount = new ClientAccountId(AccountSystem.OFI, "CAN", "001:12345:123456789012");

            // When/Then
            assertThat(GradsAccountValidator.isValidGradsAccount(ofiAccount)).isFalse();
        }

        @Test
        @DisplayName("should return false for null account")
        void shouldReturnFalseForNullAccount() {
            assertThat(GradsAccountValidator.isValidGradsAccount(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isPapAccount()")
    class IsPapAccount {

        @Test
        @DisplayName("should return true for PAP account type")
        void shouldReturnTrueForPapAccountType() {
            // Given
            ClientAccountId papAccount = new ClientAccountId(AccountSystem.CAN_GRADS, "PAP", "1234567890");

            // When/Then
            assertThat(GradsAccountValidator.isPapAccount(papAccount)).isTrue();
        }

        @Test
        @DisplayName("should return false for PDB account type")
        void shouldReturnFalseForPdbAccountType() {
            // Given
            ClientAccountId pdbAccount = new ClientAccountId(AccountSystem.CAN_GRADS, "PDB", "1234567890");

            // When/Then
            assertThat(GradsAccountValidator.isPapAccount(pdbAccount)).isFalse();
        }

        @Test
        @DisplayName("should return false for non-GRADS account")
        void shouldReturnFalseForNonGradsAccount() {
            // Given
            ClientAccountId ddaAccount = new ClientAccountId(AccountSystem.CAN_DDA, "PAP", "12345:000000000001");

            // When/Then
            assertThat(GradsAccountValidator.isPapAccount(ddaAccount)).isFalse();
        }

        @Test
        @DisplayName("should return false for null account")
        void shouldReturnFalseForNullAccount() {
            assertThat(GradsAccountValidator.isPapAccount(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isPdbAccount()")
    class IsPdbAccount {

        @Test
        @DisplayName("should return true for PDB account type")
        void shouldReturnTrueForPdbAccountType() {
            // Given
            ClientAccountId pdbAccount = new ClientAccountId(AccountSystem.CAN_GRADS, "PDB", "1234567890");

            // When/Then
            assertThat(GradsAccountValidator.isPdbAccount(pdbAccount)).isTrue();
        }

        @Test
        @DisplayName("should return false for PAP account type")
        void shouldReturnFalseForPapAccountType() {
            // Given
            ClientAccountId papAccount = new ClientAccountId(AccountSystem.CAN_GRADS, "PAP", "1234567890");

            // When/Then
            assertThat(GradsAccountValidator.isPdbAccount(papAccount)).isFalse();
        }

        @Test
        @DisplayName("should return false for non-GRADS account")
        void shouldReturnFalseForNonGradsAccount() {
            // Given
            ClientAccountId ddaAccount = new ClientAccountId(AccountSystem.CAN_DDA, "PDB", "12345:000000000001");

            // When/Then
            assertThat(GradsAccountValidator.isPdbAccount(ddaAccount)).isFalse();
        }

        @Test
        @DisplayName("should return false for null account")
        void shouldReturnFalseForNullAccount() {
            assertThat(GradsAccountValidator.isPdbAccount(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("extractGsan()")
    class ExtractGsan {

        @Test
        @DisplayName("should extract GSAN from GRADS account")
        void shouldExtractGsanFromGradsAccount() {
            // Given
            ClientAccountId gradsAccount = new ClientAccountId(AccountSystem.CAN_GRADS, "PAP", "1234567890");

            // When
            String gsan = GradsAccountValidator.extractGsan(gradsAccount);

            // Then
            assertThat(gsan).isEqualTo("1234567890");
        }

        @Test
        @DisplayName("should throw when account is not GRADS")
        void shouldThrowWhenAccountIsNotGrads() {
            // Given
            ClientAccountId ddaAccount = new ClientAccountId(AccountSystem.CAN_DDA, "DDA", "12345:000000000001");

            // When/Then
            assertThatThrownBy(() -> GradsAccountValidator.extractGsan(ddaAccount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a GRADS account");
        }

        @Test
        @DisplayName("should throw when account is null")
        void shouldThrowWhenAccountIsNull() {
            assertThatThrownBy(() -> GradsAccountValidator.extractGsan(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a GRADS account");
        }
    }
}
