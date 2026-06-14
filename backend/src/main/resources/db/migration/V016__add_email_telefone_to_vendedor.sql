-- V016: Add email and telefone columns to vendedor table
-- These fields are for contact information and frontend display

ALTER TABLE vendedor ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE vendedor ADD COLUMN IF NOT EXISTS telefone VARCHAR(20);

-- Add index for email lookups (optional, for future use)
CREATE INDEX IF NOT EXISTS idx_vendedor_email ON vendedor(email) WHERE email IS NOT NULL;

COMMENT ON COLUMN vendedor.email IS 'Contact email address';
COMMENT ON COLUMN vendedor.telefone IS 'Contact phone number';
