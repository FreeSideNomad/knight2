-- Remove passkey support (passkeys are not being used)

-- Drop passkey tracking fields from users table
ALTER TABLE users DROP COLUMN IF EXISTS passkey_offered;
ALTER TABLE users DROP COLUMN IF EXISTS passkey_enrolled;
ALTER TABLE users DROP COLUMN IF EXISTS passkey_has_uv;

-- Drop passkeys table
DROP TABLE IF EXISTS passkeys;
