-- =====================================================================
-- Módulos por plano (controle de oferta): o super admin define quais
-- módulos do produto cada plano inclui. NULL = todos os módulos
-- (compatibilidade: planos existentes continuam completos até serem
-- configurados). Catálogo de chaves vive no código (enum ModuloPlano):
-- EMISSAO_MARINHA, COMISSOES, MANUTENCAO, FECHAMENTOS, RELATORIOS,
-- DESPESAS. O core operacional (agenda/balcão/frota/clientes) não é
-- gateável. Gating: menu do backoffice + interceptor de API.
-- =====================================================================
ALTER TABLE public.plano ADD COLUMN IF NOT EXISTS modulos jsonb;
COMMENT ON COLUMN public.plano.modulos IS
    'Array JSON de chaves de módulos incluídos; NULL = todos (ver enum ModuloPlano)';
