-- V024: Fix existing tenants with NULL comissao_config
-- The V021 migration only added a DEFAULT but didn't update existing rows

UPDATE tenant
SET comissao_config = '{
    "percentualPadrao": 10.0,
    "percentualAbaixoBase": 5.0,
    "bonusAtivo": true,
    "bonusMetaVendas": 50,
    "bonusValor": 500.00
}'::jsonb
WHERE comissao_config IS NULL;
