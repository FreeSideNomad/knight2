-- Add MFA preference column to users table
ALTER TABLE users ADD mfa_preference VARCHAR(20);
