-- V071: link único para o instrutor assinar remotamente (Anexo 5-B-1).
--
-- O instrutor não é usuário do sistema: a assinatura era desenhada no cadastro, com ele
-- presente. Agora a empresa gera um link de USO ÚNICO (7 dias) e manda ao instrutor; a
-- assinatura enviada substitui a do cadastro pelas mesmas regras da edição (inclusive a
-- reaprovação pela EAMA na emissão delegada — V070).
--
-- Guarda só o HASH (SHA-256) do token: vazamento da tabela não entrega links válidos.
-- Evidências do ato (ip, user_agent, sha256 da imagem, usado_em) ficam na própria linha.
--
-- RLS no padrão do claim-token do cliente (V009): a página pública resolve o link sem
-- tenant na sessão (busca pelo hash); com tenant, cada empresa só vê os seus.

CREATE TABLE public.instrutor_assinatura_link (
    id                uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id         uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    instrutor_id      uuid NOT NULL REFERENCES public.instrutor(id) ON DELETE CASCADE,
    token_hash        varchar(64) NOT NULL,
    expira_em         timestamptz NOT NULL,
    ativo             boolean NOT NULL DEFAULT true,
    usado_em          timestamptz,
    criado_por        uuid,
    ip                varchar(64),
    user_agent        varchar(500),
    assinatura_sha256 varchar(64),
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_instrutor_assinatura_link_token UNIQUE (token_hash)
);

CREATE INDEX idx_instrutor_assinatura_link_tenant_instrutor
    ON public.instrutor_assinatura_link (tenant_id, instrutor_id);

ALTER TABLE public.instrutor_assinatura_link ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.instrutor_assinatura_link FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_instrutor_assinatura_link ON public.instrutor_assinatura_link
    USING (
        CASE
            WHEN public.get_current_tenant_id() IS NULL THEN true
            ELSE (tenant_id = public.get_current_tenant_id())
        END
    );
