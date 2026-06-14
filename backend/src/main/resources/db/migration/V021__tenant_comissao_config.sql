-- V021: Add comissao_config JSONB column to tenant
-- Configurable commission percentages and bonus settings per tenant

ALTER TABLE tenant ADD COLUMN IF NOT EXISTS comissao_config JSONB DEFAULT '{
    "percentualPadrao": 10.0,
    "percentualAbaixoBase": 5.0,
    "bonusAtivo": true,
    "bonusMetaVendas": 50,
    "bonusValor": 500.00
}'::jsonb;

COMMENT ON COLUMN tenant.comissao_config IS 'Commission and bonus configuration per tenant:
- percentualPadrao: Commission % for sales at or above base price (default 10%)
- percentualAbaixoBase: Commission % for sales below base price (default 5%)
- bonusAtivo: Whether bonus system is enabled
- bonusMetaVendas: Number of sales above base price to earn bonus
- bonusValor: Bonus value in tenant currency';
