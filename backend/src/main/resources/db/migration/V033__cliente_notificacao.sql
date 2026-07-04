-- =====================================================================
-- Notificações in-app do CLIENTE do portal (backlog P4)
--
-- Eventos que já acontecem no sistema (sinal confirmado/recusado, GRU
-- paga, documentos emitidos, pré-reserva expirada) viram registros que o
-- portal exibe no sininho. Tenant-scoped (o evento pertence à loja);
-- o cliente lê agregando os vínculos, como reservas/locações.
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.cliente_notificacao (
    id          uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id   uuid NOT NULL,
    cliente_id  uuid NOT NULL,
    tipo        varchar(40) NOT NULL,
    titulo      varchar(160) NOT NULL,
    mensagem    text,
    link        varchar(300),
    lida        boolean NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cliente_notificacao_cliente
    ON public.cliente_notificacao (tenant_id, cliente_id, lida, created_at DESC);

ALTER TABLE public.cliente_notificacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cliente_notificacao FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_cliente_notificacao ON public.cliente_notificacao;
CREATE POLICY tenant_isolation_cliente_notificacao ON public.cliente_notificacao
    USING (tenant_id = public.get_current_tenant_id());
