-- Migration: Remove restrictive email format constraint from usuario and cliente tables
--
-- Problem: Current regex constraint only allows ASCII characters in emails
--    CONSTRAINT email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
--
-- This blocks international emails (RFC 6531 Internationalized Email) like:
--   - josé.silva@example.com (accented characters)
--   - françois.martin@example.fr (special characters)
--   - müller@example.de (umlauts)
--
-- Solution: Drop the overly restrictive check constraint
-- Backend validation (@Email annotation) will handle basic format validation
--
-- Impact:
--   - Allows international email addresses
--   - Backend validation still enforces valid email format
--   - Improves global user experience

-- Drop email format constraint from usuario table
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS email_format;

-- Drop email format constraint from cliente table (if it exists)
-- Note: V005 migration consolidates cliente contact fields into JSONB column
ALTER TABLE cliente DROP CONSTRAINT IF EXISTS email_format;

COMMENT ON COLUMN usuario.email IS 'User email (supports international characters per RFC 6531)';
-- cliente.email column was consolidated into contato JSONB column in V005, so no comment needed here
