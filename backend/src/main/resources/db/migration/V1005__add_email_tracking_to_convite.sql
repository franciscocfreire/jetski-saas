-- V1005: Add email tracking columns to convite table
--
-- Tracks password setup email sending history:
-- - password_reset_link: Full link sent in the email
-- - email_sent_at: When the last email was sent
-- - email_sent_count: How many times email was sent (for monitoring)
--
-- Author: Jetski Team
-- Date: 2025-10-18

ALTER TABLE convite
    ADD COLUMN password_reset_link TEXT,
    ADD COLUMN email_sent_at TIMESTAMP,
    ADD COLUMN email_sent_count INTEGER DEFAULT 0 NOT NULL;

-- Index for querying recent email sends (for rate limiting queries if needed)
CREATE INDEX idx_convite_email_sent_at ON convite(email_sent_at) WHERE email_sent_at IS NOT NULL;

-- Comment the new columns
COMMENT ON COLUMN convite.password_reset_link IS 'Full password setup link sent to user via email';
COMMENT ON COLUMN convite.email_sent_at IS 'Timestamp of last password setup email sent';
COMMENT ON COLUMN convite.email_sent_count IS 'Counter of how many times password setup email was sent';
