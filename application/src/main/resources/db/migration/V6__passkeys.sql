-- =====================================================
-- PASSKEY/WEBAUTHN SUPPORT
-- =====================================================

-- Add passkey tracking fields to users table
ALTER TABLE users ADD passkey_offered BIT NOT NULL DEFAULT 0;
ALTER TABLE users ADD passkey_enrolled BIT NOT NULL DEFAULT 0;
ALTER TABLE users ADD passkey_has_uv BIT NOT NULL DEFAULT 0;

-- Passkeys table for WebAuthn credentials
CREATE TABLE passkeys (
    passkey_id UNIQUEIDENTIFIER PRIMARY KEY,
    user_id UNIQUEIDENTIFIER NOT NULL,
    credential_id VARCHAR(1024) NOT NULL UNIQUE,  -- Base64URL encoded, can be long
    public_key VARCHAR(2048) NOT NULL,             -- Base64URL encoded COSE public key
    aaguid VARCHAR(36),                            -- Authenticator Attestation GUID
    display_name NVARCHAR(255) NOT NULL,           -- User-friendly name
    sign_count BIGINT NOT NULL DEFAULT 0,          -- Signature counter for replay detection
    user_verification BIT NOT NULL DEFAULT 0,      -- Whether UV is supported
    backup_eligible BIT NOT NULL DEFAULT 0,        -- Credential can be backed up (synced)
    backup_state BIT NOT NULL DEFAULT 0,           -- Credential is currently backed up
    transports VARCHAR(255),                       -- Comma-separated: usb,nfc,ble,internal,hybrid
    last_used_at DATETIME2,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,

    CONSTRAINT FK_passkeys_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_passkeys_user ON passkeys(user_id);
CREATE INDEX idx_passkeys_credential_id ON passkeys(credential_id);
CREATE INDEX idx_passkeys_last_used ON passkeys(last_used_at DESC);
