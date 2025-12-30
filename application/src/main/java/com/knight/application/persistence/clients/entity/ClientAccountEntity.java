package com.knight.application.persistence.clients.entity;

import com.knight.platform.sharedkernel.AccountStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA entity representing a client account in the database.
 * Maps to the client_accounts table.
 *
 * For OFI accounts linked to IndirectClients:
 * - clientId is optional (may be null)
 * - indirectClientId references the IndirectClient that owns this account (by client_id URN)
 * - accountHolderName stores the name of the account holder at the external bank
 */
@Entity
@Table(name = "client_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientAccountEntity {

    @Id
    @Column(name = "account_id", nullable = false)
    private String accountId;  // URN string format: {accountSystem}:{accountType}:{accountNumberSegments}

    @Column(name = "client_id")
    private String clientId;   // URN string format (FK conceptually, nullable for OFI accounts)

    @Column(name = "indirect_client_id", length = 50)
    private String indirectClientId;  // URN reference to indirect_clients.client_id (for OFI accounts)

    @Column(name = "account_system", nullable = false)
    private String accountSystem;  // e.g., "CAN_DDA", "OFI"

    @Column(name = "account_type", nullable = false)
    private String accountType;    // e.g., "DDA", "CC" (for non-OFI) or "CAN", "IBAN" (for OFI)

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;       // ISO 4217 code (e.g., "CAD", "USD")

    @Column(name = "account_holder_name")
    private String accountHolderName;  // For OFI accounts: name of account holder at external bank

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
