-- =====================================================
-- V035: Fix os_manutencao status constraint to use lowercase
-- =====================================================
-- The Java entity converter uses lowercase values for status,
-- but the original constraint expects UPPERCASE values.
-- This migration updates the constraint to match the converter.

-- Drop the old constraint
ALTER TABLE os_manutencao DROP CONSTRAINT IF EXISTS os_status_check;

-- Add the new constraint with lowercase values (matching the Java converter)
ALTER TABLE os_manutencao ADD CONSTRAINT os_status_check
    CHECK (status IN ('aberta', 'em_andamento', 'aguardando_pecas', 'concluida', 'cancelada'));

-- Also fix the tipo constraint if needed (check current status)
ALTER TABLE os_manutencao DROP CONSTRAINT IF EXISTS os_tipo_check;
ALTER TABLE os_manutencao ADD CONSTRAINT os_tipo_check
    CHECK (tipo IN ('preventiva', 'corretiva', 'revisao'));

-- Also fix the prioridade constraint if needed
ALTER TABLE os_manutencao DROP CONSTRAINT IF EXISTS os_prioridade_check;
ALTER TABLE os_manutencao DROP CONSTRAINT IF EXISTS os_manutencao_prioridade_check;
ALTER TABLE os_manutencao ADD CONSTRAINT os_prioridade_check
    CHECK (prioridade IN ('baixa', 'media', 'alta', 'urgente'));

COMMENT ON TABLE os_manutencao IS 'Ordens de Serviço de Manutenção - constraints fixed for lowercase values (V035)';
