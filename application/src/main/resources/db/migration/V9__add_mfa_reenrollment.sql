-- Add MFA re-enrollment support for admin MFA reset functionality
ALTER TABLE users ADD COLUMN allow_mfa_reenrollment BIT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN mfa_reenrollment_requested_at DATETIME2;
ALTER TABLE users ADD COLUMN mfa_reenrollment_requested_by NVARCHAR(255);
