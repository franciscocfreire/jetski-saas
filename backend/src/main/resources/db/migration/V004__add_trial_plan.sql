-- ============================================================================
-- V004: Add Trial Plan for Self-Service Signup
-- ============================================================================
-- Creates a free Trial plan that new tenants receive when signing up.
-- Trial period: 14 days
-- Limited features to encourage upgrade to paid plans.
-- ============================================================================

-- Plano Trial (gratuito por 14 dias)
INSERT INTO plano (nome, limites, preco_mensal)
SELECT 'Trial',
       '{"frota_max": 3, "usuarios_max": 2, "storage_gb": 1, "locacoes_mes": 50, "trial_days": 14}'::jsonb,
       0.00
WHERE NOT EXISTS (SELECT 1 FROM plano WHERE nome = 'Trial');

-- Add status column to convite table if not exists
ALTER TABLE convite ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'PENDING';

-- Create constraint if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'convite_status_check'
    ) THEN
        ALTER TABLE convite ADD CONSTRAINT convite_status_check
            CHECK (status IN ('PENDING', 'ACTIVATED', 'EXPIRED', 'CANCELLED'));
    END IF;
END $$;
