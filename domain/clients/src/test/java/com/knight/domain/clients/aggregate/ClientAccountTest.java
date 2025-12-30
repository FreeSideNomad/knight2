package com.knight.domain.clients.aggregate;

import com.knight.platform.sharedkernel.AccountStatus;
import com.knight.platform.sharedkernel.AccountSystem;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.Currency;
import com.knight.platform.sharedkernel.SrfClientId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ClientAccount Aggregate Tests")
class ClientAccountTest {

    private static final SrfClientId TEST_CLIENT_ID = new SrfClientId("123456789");
    private static final ClientAccountId TEST_ACCOUNT_ID = new ClientAccountId(
        AccountSystem.CAN_DDA,
        "DDA",
        "12345:123456789012"
    );
    private static final Currency TEST_CURRENCY = Currency.CAD;
    private static final String TEST_INDIRECT_CLIENT_ID = "ind:12345678-1234-1234-1234-123456789012";

    @Nested
    @DisplayName("create() - regular bank account")
    class CreateBankAccountTests {

        @Test
        @DisplayName("should create bank account with valid inputs")
        void shouldCreateBankAccountWithValidInputs() {
            // When
            ClientAccount account = ClientAccount.create(TEST_ACCOUNT_ID, TEST_CLIENT_ID, TEST_CURRENCY);

            // Then
            assertThat(account).isNotNull();
            assertThat(account.accountId()).isEqualTo(TEST_ACCOUNT_ID);
            assertThat(account.clientId()).isEqualTo(TEST_CLIENT_ID);
            assertThat(account.indirectClientId()).isNull();
            assertThat(account.currency()).isEqualTo(TEST_CURRENCY);
            assertThat(account.accountHolderName()).isNull();
            assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(account.createdAt()).isNotNull();
            assertThat(account.updatedAt()).isNotNull();
            assertThat(account.updatedAt()).isEqualTo(account.createdAt());
        }

        @Test
        @DisplayName("should create account with USD currency")
        void shouldCreateAccountWithUsdCurrency() {
            // When
            ClientAccount account = ClientAccount.create(TEST_ACCOUNT_ID, TEST_CLIENT_ID, Currency.USD);

            // Then
            assertThat(account.currency()).isEqualTo(Currency.USD);
        }

        @Test
        @DisplayName("should mark account as client account")
        void shouldMarkAsClientAccount() {
            // When
            ClientAccount account = ClientAccount.create(TEST_ACCOUNT_ID, TEST_CLIENT_ID, TEST_CURRENCY);

            // Then
            assertThat(account.isClientAccount()).isTrue();
            assertThat(account.isIndirectClientAccount()).isFalse();
            assertThat(account.isOfiAccount()).isFalse();
        }

        @Test
        @DisplayName("should throw NullPointerException when accountId is null")
        void shouldThrowExceptionWhenAccountIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> ClientAccount.create(null, TEST_CLIENT_ID, TEST_CURRENCY))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("accountId cannot be null");
        }

        @Test
        @DisplayName("should throw NullPointerException when clientId is null")
        void shouldThrowExceptionWhenClientIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> ClientAccount.create(TEST_ACCOUNT_ID, null, TEST_CURRENCY))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clientId cannot be null for regular accounts");
        }

        @Test
        @DisplayName("should throw NullPointerException when currency is null")
        void shouldThrowExceptionWhenCurrencyIsNull() {
            // When/Then
            assertThatThrownBy(() -> ClientAccount.create(TEST_ACCOUNT_ID, TEST_CLIENT_ID, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("currency cannot be null");
        }
    }

    @Nested
    @DisplayName("createOfiAccount() - OFI account with indirect client")
    class CreateOfiAccountTests {

        private static final ClientAccountId OFI_ACCOUNT_ID = new ClientAccountId(
            AccountSystem.OFI,
            "CAN",
            "001:12345:123456789012"
        );

        @Test
        @DisplayName("should create OFI account with valid inputs")
        void shouldCreateOfiAccountWithValidInputs() {
            // When
            ClientAccount account = ClientAccount.createOfiAccount(
                OFI_ACCOUNT_ID,
                TEST_INDIRECT_CLIENT_ID,
                TEST_CURRENCY,
                "John Doe"
            );

            // Then
            assertThat(account).isNotNull();
            assertThat(account.accountId()).isEqualTo(OFI_ACCOUNT_ID);
            assertThat(account.clientId()).isNull();
            assertThat(account.indirectClientId()).isEqualTo(TEST_INDIRECT_CLIENT_ID);
            assertThat(account.currency()).isEqualTo(TEST_CURRENCY);
            assertThat(account.accountHolderName()).isEqualTo("John Doe");
            assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(account.createdAt()).isNotNull();
            assertThat(account.updatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should create OFI account without account holder name")
        void shouldCreateOfiAccountWithoutHolderName() {
            // When
            ClientAccount account = ClientAccount.createOfiAccount(
                OFI_ACCOUNT_ID,
                TEST_INDIRECT_CLIENT_ID,
                TEST_CURRENCY,
                null
            );

            // Then
            assertThat(account.accountHolderName()).isNull();
        }

        @Test
        @DisplayName("should create OFI IBAN account")
        void shouldCreateOfiIbanAccount() {
            // Given
            ClientAccountId ibanAccountId = new ClientAccountId(
                AccountSystem.OFI,
                "IBAN",
                "DEUTDEFF:DE89370400440532013000"
            );

            // When
            ClientAccount account = ClientAccount.createOfiAccount(
                ibanAccountId,
                TEST_INDIRECT_CLIENT_ID,
                Currency.EUR,
                "ACME GmbH"
            );

            // Then
            assertThat(account.accountId()).isEqualTo(ibanAccountId);
            assertThat(account.currency()).isEqualTo(Currency.EUR);
        }

        @Test
        @DisplayName("should create OFI US account")
        void shouldCreateOfiUsAccount() {
            // Given
            ClientAccountId usAccountId = new ClientAccountId(
                AccountSystem.OFI,
                "US",
                "123456789:12345678901234567"
            );

            // When
            ClientAccount account = ClientAccount.createOfiAccount(
                usAccountId,
                TEST_INDIRECT_CLIENT_ID,
                Currency.USD,
                "John Smith"
            );

            // Then
            assertThat(account.accountId()).isEqualTo(usAccountId);
        }

        @Test
        @DisplayName("should mark account as OFI and indirect client account")
        void shouldMarkAsOfiAccount() {
            // When
            ClientAccount account = ClientAccount.createOfiAccount(
                OFI_ACCOUNT_ID,
                TEST_INDIRECT_CLIENT_ID,
                TEST_CURRENCY,
                "John Doe"
            );

            // Then
            assertThat(account.isOfiAccount()).isTrue();
            assertThat(account.isIndirectClientAccount()).isTrue();
            assertThat(account.isClientAccount()).isFalse();
        }

        @Test
        @DisplayName("should throw NullPointerException when indirectClientId is null")
        void shouldThrowExceptionWhenIndirectClientIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> ClientAccount.createOfiAccount(
                OFI_ACCOUNT_ID,
                null,
                TEST_CURRENCY,
                "John Doe"
            )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("indirectClientId cannot be null for OFI accounts");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when accountSystem is not OFI")
        void shouldThrowExceptionWhenNotOfiSystem() {
            // When/Then
            assertThatThrownBy(() -> ClientAccount.createOfiAccount(
                TEST_ACCOUNT_ID,  // CAN_DDA account
                TEST_INDIRECT_CLIENT_ID,
                TEST_CURRENCY,
                "John Doe"
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OFI accounts must use AccountSystem.OFI");
        }
    }

    @Nested
    @DisplayName("close()")
    class CloseTests {

        @Test
        @DisplayName("should close active account")
        void shouldCloseActiveAccount() {
            // Given
            ClientAccount account = ClientAccount.create(TEST_ACCOUNT_ID, TEST_CLIENT_ID, TEST_CURRENCY);
            Instant originalUpdatedAt = account.updatedAt();

            // When
            account.close();

            // Then
            assertThat(account.status()).isEqualTo(AccountStatus.CLOSED);
            assertThat(account.updatedAt()).isAfter(originalUpdatedAt);
        }

        @Test
        @DisplayName("should throw IllegalStateException when account is already closed")
        void shouldThrowExceptionWhenAlreadyClosed() {
            // Given
            ClientAccount account = ClientAccount.create(TEST_ACCOUNT_ID, TEST_CLIENT_ID, TEST_CURRENCY);
            account.close();

            // When/Then
            assertThatThrownBy(() -> account.close())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot close an already closed account");
        }
    }

    @Nested
    @DisplayName("reactivate()")
    class ReactivateTests {

        @Test
        @DisplayName("should reactivate closed account")
        void shouldReactivateClosedAccount() {
            // Given
            ClientAccount account = ClientAccount.create(TEST_ACCOUNT_ID, TEST_CLIENT_ID, TEST_CURRENCY);
            account.close();
            Instant originalUpdatedAt = account.updatedAt();

            // When
            account.reactivate();

            // Then
            assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(account.updatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        }

        @Test
        @DisplayName("should throw IllegalStateException when account is already active")
        void shouldThrowExceptionWhenAlreadyActive() {
            // Given
            ClientAccount account = ClientAccount.create(TEST_ACCOUNT_ID, TEST_CLIENT_ID, TEST_CURRENCY);

            // When/Then
            assertThatThrownBy(() -> account.reactivate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot reactivate an already active account");
        }
    }

    @Nested
    @DisplayName("reconstruct()")
    class ReconstructTests {

        @Test
        @DisplayName("should reconstruct bank account from persistence")
        void shouldReconstructBankAccount() {
            // Given
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant updatedAt = Instant.now().minusSeconds(1800);

            // When
            ClientAccount account = ClientAccount.reconstruct(
                TEST_ACCOUNT_ID,
                TEST_CLIENT_ID,
                null,
                TEST_CURRENCY,
                null,
                AccountStatus.ACTIVE,
                createdAt,
                updatedAt
            );

            // Then
            assertThat(account.accountId()).isEqualTo(TEST_ACCOUNT_ID);
            assertThat(account.clientId()).isEqualTo(TEST_CLIENT_ID);
            assertThat(account.indirectClientId()).isNull();
            assertThat(account.currency()).isEqualTo(TEST_CURRENCY);
            assertThat(account.accountHolderName()).isNull();
            assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(account.createdAt()).isEqualTo(createdAt);
            assertThat(account.updatedAt()).isEqualTo(updatedAt);
        }

        @Test
        @DisplayName("should reconstruct closed account")
        void shouldReconstructClosedAccount() {
            // Given
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant updatedAt = Instant.now().minusSeconds(1800);

            // When
            ClientAccount account = ClientAccount.reconstruct(
                TEST_ACCOUNT_ID,
                TEST_CLIENT_ID,
                null,
                TEST_CURRENCY,
                null,
                AccountStatus.CLOSED,
                createdAt,
                updatedAt
            );

            // Then
            assertThat(account.status()).isEqualTo(AccountStatus.CLOSED);
        }

        @Test
        @DisplayName("should reconstruct OFI account from persistence")
        void shouldReconstructOfiAccount() {
            // Given
            ClientAccountId ofiAccountId = new ClientAccountId(
                AccountSystem.OFI,
                "CAN",
                "001:12345:123456789012"
            );
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant updatedAt = Instant.now().minusSeconds(1800);

            // When
            ClientAccount account = ClientAccount.reconstruct(
                ofiAccountId,
                null,
                TEST_INDIRECT_CLIENT_ID,
                TEST_CURRENCY,
                "John Doe",
                AccountStatus.ACTIVE,
                createdAt,
                updatedAt
            );

            // Then
            assertThat(account.accountId()).isEqualTo(ofiAccountId);
            assertThat(account.clientId()).isNull();
            assertThat(account.indirectClientId()).isEqualTo(TEST_INDIRECT_CLIENT_ID);
            assertThat(account.accountHolderName()).isEqualTo("John Doe");
            assertThat(account.isOfiAccount()).isTrue();
            assertThat(account.isIndirectClientAccount()).isTrue();
        }

        @Test
        @DisplayName("should throw NullPointerException when status is null")
        void shouldThrowExceptionWhenStatusIsNull() {
            // Given
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant updatedAt = Instant.now().minusSeconds(1800);

            // When/Then
            assertThatThrownBy(() -> ClientAccount.reconstruct(
                TEST_ACCOUNT_ID,
                TEST_CLIENT_ID,
                null,
                TEST_CURRENCY,
                null,
                null,
                createdAt,
                updatedAt
            )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("status cannot be null");
        }

        @Test
        @DisplayName("should throw NullPointerException when createdAt is null")
        void shouldThrowExceptionWhenCreatedAtIsNull() {
            // Given
            Instant updatedAt = Instant.now().minusSeconds(1800);

            // When/Then
            assertThatThrownBy(() -> ClientAccount.reconstruct(
                TEST_ACCOUNT_ID,
                TEST_CLIENT_ID,
                null,
                TEST_CURRENCY,
                null,
                AccountStatus.ACTIVE,
                null,
                updatedAt
            )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("createdAt cannot be null");
        }

        @Test
        @DisplayName("should throw NullPointerException when updatedAt is null")
        void shouldThrowExceptionWhenUpdatedAtIsNull() {
            // Given
            Instant createdAt = Instant.now().minusSeconds(3600);

            // When/Then
            assertThatThrownBy(() -> ClientAccount.reconstruct(
                TEST_ACCOUNT_ID,
                TEST_CLIENT_ID,
                null,
                TEST_CURRENCY,
                null,
                AccountStatus.ACTIVE,
                createdAt,
                null
            )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("updatedAt cannot be null");
        }
    }

    @Nested
    @DisplayName("Status transition validations")
    class StatusTransitionTests {

        @Test
        @DisplayName("should allow full lifecycle: create -> close -> reactivate")
        void shouldAllowCloseAndReactivate() {
            // Given
            ClientAccount account = ClientAccount.create(TEST_ACCOUNT_ID, TEST_CLIENT_ID, TEST_CURRENCY);

            // When/Then
            assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);

            account.close();
            assertThat(account.status()).isEqualTo(AccountStatus.CLOSED);

            account.reactivate();
            assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("should allow multiple close and reactivate cycles")
        void shouldAllowMultipleCycles() {
            // Given
            ClientAccount account = ClientAccount.create(TEST_ACCOUNT_ID, TEST_CLIENT_ID, TEST_CURRENCY);

            // When/Then - cycle 1
            account.close();
            assertThat(account.status()).isEqualTo(AccountStatus.CLOSED);
            account.reactivate();
            assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);

            // When/Then - cycle 2
            account.close();
            assertThat(account.status()).isEqualTo(AccountStatus.CLOSED);
            account.reactivate();
            assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Account type identification")
    class AccountTypeIdentificationTests {

        @Test
        @DisplayName("should identify CAN_DDA as non-OFI bank account")
        void shouldIdentifyCanDdaAsNonOfi() {
            // When
            ClientAccount account = ClientAccount.create(TEST_ACCOUNT_ID, TEST_CLIENT_ID, TEST_CURRENCY);

            // Then
            assertThat(account.isOfiAccount()).isFalse();
            assertThat(account.isClientAccount()).isTrue();
            assertThat(account.isIndirectClientAccount()).isFalse();
        }

        @Test
        @DisplayName("should identify US_FIN as non-OFI bank account")
        void shouldIdentifyUsFisAsNonOfi() {
            // Given
            ClientAccountId usFisAccountId = new ClientAccountId(
                AccountSystem.US_FIS,
                "DDA",
                "123456789"
            );

            // When
            ClientAccount account = ClientAccount.create(usFisAccountId, TEST_CLIENT_ID, TEST_CURRENCY);

            // Then
            assertThat(account.isOfiAccount()).isFalse();
        }

        @Test
        @DisplayName("should identify OFI CAN account correctly")
        void shouldIdentifyOfiCanAccount() {
            // Given
            ClientAccountId ofiCanAccountId = new ClientAccountId(
                AccountSystem.OFI,
                "CAN",
                "001:12345:123456789012"
            );

            // When
            ClientAccount account = ClientAccount.createOfiAccount(
                ofiCanAccountId,
                TEST_INDIRECT_CLIENT_ID,
                TEST_CURRENCY,
                null
            );

            // Then
            assertThat(account.isOfiAccount()).isTrue();
            assertThat(account.isIndirectClientAccount()).isTrue();
            assertThat(account.isClientAccount()).isFalse();
        }

        @Test
        @DisplayName("should identify OFI IBAN account correctly")
        void shouldIdentifyOfiIbanAccount() {
            // Given
            ClientAccountId ofiIbanAccountId = new ClientAccountId(
                AccountSystem.OFI,
                "IBAN",
                "DEUTDEFF:DE89370400440532013000"
            );

            // When
            ClientAccount account = ClientAccount.createOfiAccount(
                ofiIbanAccountId,
                TEST_INDIRECT_CLIENT_ID,
                Currency.EUR,
                null
            );

            // Then
            assertThat(account.isOfiAccount()).isTrue();
        }

        @Test
        @DisplayName("should identify OFI US account correctly")
        void shouldIdentifyOfiUsAccount() {
            // Given
            ClientAccountId ofiUsAccountId = new ClientAccountId(
                AccountSystem.OFI,
                "US",
                "123456789:12345678901234567"
            );

            // When
            ClientAccount account = ClientAccount.createOfiAccount(
                ofiUsAccountId,
                TEST_INDIRECT_CLIENT_ID,
                Currency.USD,
                null
            );

            // Then
            assertThat(account.isOfiAccount()).isTrue();
        }

        @Test
        @DisplayName("should identify OFI SWIFT account correctly")
        void shouldIdentifyOfiSwiftAccount() {
            // Given
            ClientAccountId ofiSwiftAccountId = new ClientAccountId(
                AccountSystem.OFI,
                "SWIFT",
                "DEUTDEFF:DE89370400440532013000"
            );

            // When
            ClientAccount account = ClientAccount.createOfiAccount(
                ofiSwiftAccountId,
                TEST_INDIRECT_CLIENT_ID,
                Currency.EUR,
                null
            );

            // Then
            assertThat(account.isOfiAccount()).isTrue();
        }
    }
}
