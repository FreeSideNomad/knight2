-- Create client_accounts table
CREATE TABLE client_accounts (
    account_id VARCHAR(255) PRIMARY KEY,     -- URN format
    client_id VARCHAR(255) NOT NULL,         -- FK to clients
    account_system VARCHAR(20) NOT NULL,     -- CAN_DDA, OFI, etc.
    account_type VARCHAR(20) NOT NULL,       -- DDA, CC, etc.
    currency CHAR(3) NOT NULL,               -- ISO 4217 code
    status VARCHAR(20) NOT NULL,             -- ACTIVE, CLOSED
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,

    CONSTRAINT fk_client_accounts_client
        FOREIGN KEY (client_id) REFERENCES clients(client_id)
);

-- Create indexes
CREATE INDEX idx_client_accounts_client ON client_accounts(client_id);
CREATE INDEX idx_client_accounts_status ON client_accounts(status);
