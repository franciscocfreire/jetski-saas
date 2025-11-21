-- V1019: Add checklist columns to locacao table (RN05)
-- Migration to add checklist_saida_json and checklist_entrada_json columns

-- Add checklist_saida_json column (check-in checklist)
ALTER TABLE locacao
ADD COLUMN IF NOT EXISTS checklist_saida_json JSONB;

-- Add checklist_entrada_json column (check-out checklist)
ALTER TABLE locacao
ADD COLUMN IF NOT EXISTS checklist_entrada_json JSONB;

-- Add comments for documentation
COMMENT ON COLUMN locacao.checklist_saida_json IS 'RN05: Check-in checklist JSON array, e.g. ["motor_ok", "casco_ok", "gasolina_ok"]';
COMMENT ON COLUMN locacao.checklist_entrada_json IS 'RN05: Check-out checklist JSON array (mandatory), e.g. ["motor_ok", "casco_ok", "limpeza_ok"]';
