-- =====================================================================
-- Sessão de suporte (F3): substitui o god mode implícito do super admin.
--
-- Até aqui o operador de plataforma trocava de empresa no switcher do
-- backoffice e operava como se fosse membro dela: sem motivo declarado, sem
-- prazo e sem trilha. O banco não sabia responder "quem entrou na empresa X,
-- quando e por quê".
--
-- Agora entrar numa empresa é um ato explícito, com motivo obrigatório, prazo
-- curto e registro — e por padrão SOMENTE LEITURA.
--
-- Tabela de PLATAFORMA: sem RLS. O tenant_id aqui é o ALVO da sessão, não o
-- dono da linha — aplicar a policy de isolamento esconderia a própria trilha
-- de quem precisa lê-la (o console opera sem tenant selecionado).
--
-- Segredos guardados como SHA-256: vazamento do banco não entrega sessão viva.
--   codigo_hash → código de uso único (TTL curto) trocado por cookie
--   token_hash  → o cookie de sessão em si
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.plataforma_sessao_suporte (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    operador_id      uuid NOT NULL REFERENCES public.usuario(id),
    tenant_id        uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    motivo           text NOT NULL,
    -- Padrão seguro: suporte olha, não mexe. Escrita é escolha consciente.
    somente_leitura  boolean NOT NULL DEFAULT true,

    -- Handoff console → backoffice. O código trafega na URL (uso único, minutos
    -- de vida); o TOKEN nunca — URL vaza em log de proxy e histórico.
    codigo_hash      varchar(64) NOT NULL,
    codigo_expira_em timestamptz NOT NULL,
    codigo_usado_em  timestamptz,

    -- Cookie de sessão, criado só no resgate do código.
    token_hash       varchar(64),

    iniciada_em      timestamptz NOT NULL DEFAULT now(),
    expira_em        timestamptz NOT NULL,
    encerrada_em     timestamptz,
    encerrada_por    uuid REFERENCES public.usuario(id),
    ip               inet,
    user_agent       text,

    CONSTRAINT chk_sessao_suporte_motivo CHECK (length(btrim(motivo)) >= 5)
);

-- Uso único de verdade: dois resgates do mesmo código não podem coexistir.
CREATE UNIQUE INDEX IF NOT EXISTS ux_sessao_suporte_codigo
    ON public.plataforma_sessao_suporte (codigo_hash);
CREATE UNIQUE INDEX IF NOT EXISTS ux_sessao_suporte_token
    ON public.plataforma_sessao_suporte (token_hash) WHERE token_hash IS NOT NULL;

-- Trilha por empresa (a empresa tem direito de saber quem entrou) e por operador.
CREATE INDEX IF NOT EXISTS idx_sessao_suporte_tenant
    ON public.plataforma_sessao_suporte (tenant_id, iniciada_em DESC);
CREATE INDEX IF NOT EXISTS idx_sessao_suporte_operador
    ON public.plataforma_sessao_suporte (operador_id, iniciada_em DESC);

COMMENT ON TABLE public.plataforma_sessao_suporte IS
    'Sessão de suporte: acesso explícito, com motivo e prazo, de um operador de plataforma a uma empresa. Substitui o god mode implícito do switcher.';
COMMENT ON COLUMN public.plataforma_sessao_suporte.tenant_id IS
    'Empresa ALVO da sessão (não o dono da linha) — por isso a tabela não tem RLS.';

-- Sem GRANT aqui: nenhuma outra migration concede, e o deploy.sh roda
-- "GRANT ... ON ALL TABLES IN SCHEMA public TO jetski_app" depois das migrations.
-- Conceder a um papel que pode não existir no container de teste quebraria a suíte.
