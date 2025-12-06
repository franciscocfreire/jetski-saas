-- V006: Align convite table with Convite entity
-- Fix column names and add missing columns

-- Rename accepted_at to activated_at
ALTER TABLE convite RENAME COLUMN accepted_at TO activated_at;

-- Rename temporary_password to temporary_password_hash
ALTER TABLE convite RENAME COLUMN temporary_password TO temporary_password_hash;

-- Add missing columns
ALTER TABLE convite ADD COLUMN IF NOT EXISTS usuario_id UUID;
ALTER TABLE convite ADD COLUMN IF NOT EXISTS password_reset_link TEXT;
ALTER TABLE convite ADD COLUMN IF NOT EXISTS email_sent_count INTEGER NOT NULL DEFAULT 0;

-- Add foreign key constraint for usuario_id
ALTER TABLE convite ADD CONSTRAINT convite_usuario_id_fkey
    FOREIGN KEY (usuario_id) REFERENCES usuario(id);

-- Add comment for clarity
COMMENT ON COLUMN convite.password_reset_link IS 'Stores the login link sent in the invitation email (legacy name)';
COMMENT ON COLUMN convite.usuario_id IS 'Reference to usuario created when invitation is activated';
COMMENT ON COLUMN convite.email_sent_count IS 'Number of times the invitation email was sent';
