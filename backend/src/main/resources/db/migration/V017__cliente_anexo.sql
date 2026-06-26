-- Anexos do cliente (documento de identidade, comprovante de residência, selfie)
-- para incluir no PDF gerado e enviar por e-mail. Um por tipo (re-upload substitui).
CREATE TABLE IF NOT EXISTS public.cliente_anexo (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    cliente_id uuid NOT NULL REFERENCES public.cliente(id) ON DELETE CASCADE,
    tipo varchar(30) NOT NULL,
    s3_key text NOT NULL,
    content_type varchar(80),
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT cliente_anexo_cliente_tipo_uq UNIQUE (cliente_id, tipo)
);
ALTER TABLE public.cliente_anexo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cliente_anexo FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_cliente_anexo ON public.cliente_anexo;
CREATE POLICY tenant_isolation_cliente_anexo ON public.cliente_anexo
    USING ((tenant_id = public.get_current_tenant_id()));
CREATE INDEX IF NOT EXISTS idx_cliente_anexo_cliente ON public.cliente_anexo(tenant_id, cliente_id);
