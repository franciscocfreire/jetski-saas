-- =====================================================================
-- P0 (Portal do Cliente) — leitura "self" cross-tenant dos vínculos
--
-- O cliente final autenticado (role CLIENTE, sem tenant no token) precisa
-- listar em quais lojas já tem Cliente vinculado. A tabela
-- cliente_identity_provider tem RLS por tenant; esta policy adicional
-- (permissiva ⇒ OR com a existente) libera SELECT apenas das linhas do
-- próprio sub: o serviço seta app.customer_sub (transaction-local) antes
-- do SELECT. Sem bypass de RLS e sem expor dados de outros clientes.
-- =====================================================================

DROP POLICY IF EXISTS cliente_idp_self_read ON public.cliente_identity_provider;
CREATE POLICY cliente_idp_self_read ON public.cliente_identity_provider
    FOR SELECT
    USING (provider_user_id = current_setting('app.customer_sub', true));
