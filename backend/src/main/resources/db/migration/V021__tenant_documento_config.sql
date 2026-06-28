-- Parametrização por tenant do que vai em cada destino (Marinha vs Cliente) na
-- emissão dos documentos. JSONB no padrão de tenant.comissao_config.
ALTER TABLE public.tenant
    ADD COLUMN IF NOT EXISTS documento_config jsonb;
