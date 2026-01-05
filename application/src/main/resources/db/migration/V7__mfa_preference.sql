-- Add MFA preference column to users table
ALTER TABLE users ADD COLUMN mfa_preference VARCHAR(20);

-- Add comment explaining valid values
COMMENT ON COLUMN users.mfa_preference IS 'User preferred MFA method: GUARDIAN, TOTP, or PASSKEY';
