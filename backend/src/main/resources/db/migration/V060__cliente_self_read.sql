-- V060: Identidade única — F1: policy de self-read na FICHA (cliente).
-- Spec: IDENTIDADE_UNICA_SPEC.md §3/§4-D5.
--
-- A resolução multi-loja do escopo /v1/customers/** passa a ser
--   sub → usuario (usuario_identity_provider, global)
--   usuario → fichas (esta policy, GUC app.customer_usuario)
-- substituindo o par V029 (cliente_identity_provider + app.customer_sub),
-- que permanece como FALLBACK para vínculos pré-F0 até a F3.
--
-- ATENÇÃO (a mesma lição da V029, agora numa tabela com dados pessoais):
-- policy permissiva SOMA COM OR à tenant_isolation_cliente. Todo lookup de
-- staff em cliente continua obrigado a filtrar por tenant explicitamente
-- (findByTenantIdAnd...) — nunca confiar só na RLS. O GUC é setado
-- exclusivamente pelos serviços customer-scoped (transaction-local) e o
-- isolamento é validado com role não-superuser no
-- CustomerNonSuperuserIntegrationTest.
DROP POLICY IF EXISTS cliente_self_read ON public.cliente;
CREATE POLICY cliente_self_read ON public.cliente
    FOR SELECT
    USING (
        usuario_id IS NOT NULL
        AND usuario_id = NULLIF(current_setting('app.customer_usuario', true), '')::uuid
    );
