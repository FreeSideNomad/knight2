package com.knight.domain.clients.aggregate;

import com.knight.platform.sharedkernel.AccountStatus;
import com.knight.platform.sharedkernel.AccountSystem;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.Currency;
import com.knight.platform.sharedkernel.IndirectClientId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * ClientAccount aggregate root.
 * Represents a bank account belonging to a client or indirect client.
 *
 * For OFI accounts linked to IndirectClients:
 * - clientId is null
 * - indirectClientId references the owning IndirectClient
 * - accountHolderName stores the name of the account holder at the external bank
 *
 * Business rules:
 * - Accounts start as ACTIVE when created
 * - Cannot close an already closed account
 * - Cannot reactivate an active account
 */
public class ClientAccount {

    private final ClientAccountId accountId;
    private final ClientId clientId;          // Null for OFI accounts linked to IndirectClient
    private final UUID indirectClientId;      // Set for OFI accounts linked to IndirectClient
    private final Currency currency;
    private final String accountHolderName;   // For OFI accounts
    private AccountStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private ClientAccount(ClientAccountId accountId, ClientId clientId, UUID indirectClientId,
                         Currency currency, String accountHolderName) {
        this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
        this.clientId = clientId;  // Can be null for OFI accounts
        this.indirectClientId = indirectClientId;  // Can be null for regular accounts
        this.currency = Objects.requireNonNull(currency, "currency cannot be null");
        this.accountHolderName = accountHolderName;  // Can be null
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Creates a new ClientAccount with ACTIVE status.
     *
     * @param accountId the unique account identifier
     * @param clientId the client who owns this account
     * @param currency the currency of the account (ISO 4217 3-letter code)
     * @return a new ClientAccount instance
     * @throws NullPointerException if any parameter is null
     */
    public static ClientAccount create(ClientAccountId accountId, ClientId clientId, Currency currency) {
        Objects.requireNonNull(clientId, "clientId cannot be null for regular accounts");
        return new ClientAccount(accountId, clientId, null, currency, null);
    }

    /**
     * Creates a new OFI account linked to an IndirectClient.
     *
     * @param accountId the unique account identifier (must be OFI account type)
     * @param indirectClientId the indirect client who owns this account
     * @param currency the currency of the account
     * @param accountHolderName the name of the account holder at the external bank (optional)
     * @return a new ClientAccount instance
     */
    public static ClientAccount createOfiAccount(ClientAccountId accountId, UUID indirectClientId,
                                                  Currency currency, String accountHolderName) {
        Objects.requireNonNull(indirectClientId, "indirectClientId cannot be null for OFI accounts");
        if (accountId.accountSystem() != AccountSystem.OFI) {
            throw new IllegalArgumentException("OFI accounts must use AccountSystem.OFI");
        }
        return new ClientAccount(accountId, null, indirectClientId, currency, accountHolderName);
    }

    /**
     * Reconstructs a ClientAccount from persistence with all attributes.
     * This factory method is used by the persistence layer to restore domain objects.
     *
     * @param accountId the unique account identifier
     * @param clientId the client who owns this account (null for OFI accounts)
     * @param indirectClientId the indirect client who owns this account (null for regular accounts)
     * @param currency the currency of the account
     * @param accountHolderName the account holder name (for OFI accounts)
     * @param status the account status
     * @param createdAt the creation timestamp
     * @param updatedAt the last update timestamp
     * @return a reconstructed ClientAccount instance
     */
    public static ClientAccount reconstruct(
            ClientAccountId accountId,
            ClientId clientId,
            UUID indirectClientId,
            Currency currency,
            String accountHolderName,
            AccountStatus status,
            Instant createdAt,
            Instant updatedAt) {

        ClientAccount account = new ClientAccount(accountId, clientId, indirectClientId, currency, accountHolderName);
        account.status = Objects.requireNonNull(status, "status cannot be null");
        // Override the auto-set timestamps with persisted values
        // Use reflection workaround since fields are final
        try {
            java.lang.reflect.Field createdAtField = ClientAccount.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(account, Objects.requireNonNull(createdAt, "createdAt cannot be null"));

            account.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to reconstruct ClientAccount", e);
        }

        return account;
    }

    /**
     * Closes the account by setting its status to CLOSED.
     *
     * @throws IllegalStateException if the account is already closed
     */
    public void close() {
        if (this.status == AccountStatus.CLOSED) {
            throw new IllegalStateException("Cannot close an already closed account");
        }
        this.status = AccountStatus.CLOSED;
        this.updatedAt = Instant.now();
    }

    /**
     * Reactivates a closed account by setting its status to ACTIVE.
     *
     * @throws IllegalStateException if the account is already active
     */
    public void reactivate() {
        if (this.status == AccountStatus.ACTIVE) {
            throw new IllegalStateException("Cannot reactivate an already active account");
        }
        this.status = AccountStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    // Getters
    public ClientAccountId accountId() {
        return accountId;
    }

    public ClientId clientId() {
        return clientId;
    }

    public UUID indirectClientId() {
        return indirectClientId;
    }

    public Currency currency() {
        return currency;
    }

    public String accountHolderName() {
        return accountHolderName;
    }

    public AccountStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Returns true if this is an OFI account linked to an IndirectClient.
     */
    public boolean isOfiAccount() {
        return accountId.accountSystem() == AccountSystem.OFI;
    }

    /**
     * Returns true if this account is linked to a direct Client.
     */
    public boolean isClientAccount() {
        return clientId != null;
    }

    /**
     * Returns true if this account is linked to an IndirectClient.
     */
    public boolean isIndirectClientAccount() {
        return indirectClientId != null;
    }
}
