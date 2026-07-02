-- Parametrização por tenant do reforço jurídico da assinatura (página de auditoria
-- + carimbo de tempo). JSONB no padrão de tenant.documento_config / comissao_config.
ALTER TABLE public.tenant
    ADD COLUMN IF NOT EXISTS assinatura_config jsonb;
