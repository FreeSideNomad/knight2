package com.knight.domain.clients.aggregate;

import com.knight.platform.sharedkernel.AccountStatus;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.Currency;

import java.time.Instant;
import java.util.Objects;

/**
 * ClientAccount aggregate root.
 * Represents a bank account belonging to a client.
 *
 * Business rules:
 * - Accounts start as ACTIVE when created
 * - Cannot close an already closed account
 * - Cannot reactivate an active account
 */
public class ClientAccount {

    private final ClientAccountId accountId;
    private final ClientId clientId;
    private final Currency currency;
    private AccountStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private ClientAccount(ClientAccountId accountId, ClientId clientId, Currency currency) {
        this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
        this.clientId = Objects.requireNonNull(clientId, "clientId cannot be null");
        this.currency = Objects.requireNonNull(currency, "currency cannot be null");
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
        return new ClientAccount(accountId, clientId, currency);
    }

    /**
     * Reconstructs a ClientAccount from persistence with all attributes.
     * This factory method is used by the persistence layer to restore domain objects.
     *
     * @param accountId the unique account identifier
     * @param clientId the client who owns this account
     * @param currency the currency of the account
     * @param status the account status
     * @param createdAt the creation timestamp
     * @param updatedAt the last update timestamp
     * @return a reconstructed ClientAccount instance
     * @throws NullPointerException if any parameter is null
     */
    public static ClientAccount reconstruct(
            ClientAccountId accountId,
            ClientId clientId,
            Currency currency,
            AccountStatus status,
            Instant createdAt,
            Instant updatedAt) {

        ClientAccount account = new ClientAccount(accountId, clientId, currency);
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

    public Currency currency() {
        return currency;
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
}
