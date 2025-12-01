-- Migration: V10003__allow_null_cliente_id_locacao.sql
-- Description: Permitir check-in sem cliente (associação posterior)
-- Date: 2025-11-24

-- Alterar coluna cliente_id para permitir NULL
ALTER TABLE locacao ALTER COLUMN cliente_id DROP NOT NULL;

-- Comentário explicativo
COMMENT ON COLUMN locacao.cliente_id IS 'Cliente da locação - opcional para check-in rápido, pode ser associado posteriormente via PATCH';
