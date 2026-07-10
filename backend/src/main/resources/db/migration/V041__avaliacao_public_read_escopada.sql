-- =====================================================================
-- Segurança (pré-lançamento): escopa a policy pública de avaliacao.
--
-- A policy original (V031) era `USING (true)` — como policies permissivas
-- somam com OR, ela derrotava a tenant_isolation da tabela INTEIRA em
-- qualquer contexto (mesma classe do vazamento de marketplace corrigido
-- em modelo/modelo_midia). Agora a leitura pública vale apenas para
-- avaliações de modelos visíveis na vitrine (mesmo escopo da policy
-- marketplace_public_read de modelo_midia).
-- =====================================================================

DROP POLICY IF EXISTS avaliacao_public_read ON public.avaliacao;
CREATE POLICY avaliacao_public_read ON public.avaliacao
    FOR SELECT USING (
        EXISTS (
            SELECT 1
              FROM public.modelo m
              JOIN public.tenant t ON t.id = m.tenant_id
             WHERE m.id = avaliacao.modelo_id
               AND m.ativo = true
               AND m.exibir_no_marketplace = true
               AND t.status = 'ATIVO'
               AND t.exibir_no_marketplace = true
        )
    );
