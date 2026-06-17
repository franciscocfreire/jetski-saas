-- =====================================================================
-- V009 — RLS do claim-token compatível com validação pública (balcão F2.7)
-- ---------------------------------------------------------------------
-- O endpoint público POST /v1/public/clientes/claim/validar não tem
-- contexto de tenant (requisição anônima). Sob um role não-superuser
-- (jetski_app, produção), a policy estrita de cliente_claim_token filtra
-- o token (get_current_tenant_id() = NULL ⇒ nenhuma linha) e a validação
-- falha com "Token inválido".
--
-- Adota-se o MESMO carve-out já usado pela tabela `convite` (ativação de
-- staff, também pública): quando NÃO há tenant no contexto, permite a
-- leitura (autorização = conhecimento do token, 40 chars); quando HÁ
-- contexto, mantém o isolamento por tenant. Após resolver o token, o
-- serviço fixa app.tenant_id (do próprio token) para as escritas em
-- cliente/cliente_identity_provider, que seguem com RLS estrita.
-- =====================================================================

DROP POLICY IF EXISTS tenant_isolation_cliente_claim_token ON public.cliente_claim_token;
CREATE POLICY tenant_isolation_cliente_claim_token ON public.cliente_claim_token
    USING (
        CASE
            WHEN public.get_current_tenant_id() IS NULL THEN true
            ELSE (tenant_id = public.get_current_tenant_id())
        END
    );
