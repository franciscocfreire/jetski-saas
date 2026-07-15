-- V051: trilha de auditoria GLOBAL (sem tenant) — merge de contas por CPF.
--
-- A tabela auditoria tem FORCE RLS com policy tenant_id = get_current_tenant_id();
-- um INSERT com tenant_id NULL (evento de plataforma, ex.: CONTA_CPF_MERGE do
-- portal — a identidade segue a pessoa, não uma loja) seria rejeitado.
--
-- Policy INSERT-ONLY: permite gravar linhas globais, mas NÃO as expõe em
-- SELECT dentro de contexto de tenant (policies permissivas somam com OR —
-- auditoria RLS 10/jul; leitura de linhas globais fica restrita a acesso
-- administrativo direto ao banco).
CREATE POLICY auditoria_global_insert ON public.auditoria
    FOR INSERT
    WITH CHECK (tenant_id IS NULL);
