-- =====================================================================
-- Reserva é por MODELO, não por unidade (decisão de produto 04/07):
-- por padrão o tenant NÃO controla estoque — reservar nunca é bloqueado
-- por "nenhum jetski disponível" ou capacidade física. Tenants que operam
-- com frota apertada podem ligar o controle (controlar_estoque = true),
-- restaurando os bloqueios de disponibilidade/capacidade.
-- =====================================================================

ALTER TABLE public.reserva_config
    ADD COLUMN IF NOT EXISTS controlar_estoque boolean NOT NULL DEFAULT false;
