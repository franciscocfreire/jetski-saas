-- =====================================================
-- V10002: Force Row Level Security on all multi-tenant tables
-- =====================================================
-- This migration forces RLS to apply even to the table owner.
-- Without FORCE, the table owner bypasses RLS policies.
-- =====================================================

-- FORCE RLS on all operational tables
-- This ensures that even the 'jetski' user (table owner) must comply with RLS policies
ALTER TABLE jetski FORCE ROW LEVEL SECURITY;
ALTER TABLE modelo FORCE ROW LEVEL SECURITY;
ALTER TABLE reserva FORCE ROW LEVEL SECURITY;
ALTER TABLE locacao FORCE ROW LEVEL SECURITY;
ALTER TABLE foto FORCE ROW LEVEL SECURITY;
ALTER TABLE abastecimento FORCE ROW LEVEL SECURITY;
ALTER TABLE os_manutencao FORCE ROW LEVEL SECURITY;
ALTER TABLE vendedor FORCE ROW LEVEL SECURITY;
ALTER TABLE cliente FORCE ROW LEVEL SECURITY;
ALTER TABLE comissao FORCE ROW LEVEL SECURITY;
ALTER TABLE politica_comissao FORCE ROW LEVEL SECURITY;
ALTER TABLE fuel_policy FORCE ROW LEVEL SECURITY;
ALTER TABLE fuel_price_day FORCE ROW LEVEL SECURITY;
ALTER TABLE fechamento_diario FORCE ROW LEVEL SECURITY;
ALTER TABLE fechamento_mensal FORCE ROW LEVEL SECURITY;

-- Important Note:
-- FORCE ROW LEVEL SECURITY ensures that RLS policies are applied to ALL users,
-- including the table owner (jetski user in this case).
-- Without FORCE, the table owner would bypass RLS and see all rows.
-- This is critical for multi-tenant isolation.
