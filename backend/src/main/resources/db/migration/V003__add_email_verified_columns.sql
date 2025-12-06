-- V003: Add email verification columns to usuario table

ALTER TABLE usuario
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ;

-- Update existing users to have email_verified = true (they were created before this feature)
UPDATE usuario SET email_verified = true WHERE email_verified = false;
