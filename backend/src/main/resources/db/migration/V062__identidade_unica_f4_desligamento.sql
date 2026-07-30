-- V062: Identidade única — F4: desligamento do caminho legado.
-- Spec: IDENTIDADE_UNICA_SPEC.md §5/F4. Pré-lançamento: dado real = zero.
--
-- O vínculo canônico é cliente.usuario_id; a tabela cliente_identity_provider
-- (e sua policy de self-read V029) morre. O perfil civil e a habilitação
-- passam a ser da PESSOA (usuario_id) — provider fields caem.

-- 1. O unique herdado do vínculo legado: a pessoa tem NO MÁXIMO 1 ficha por loja
CREATE UNIQUE INDEX IF NOT EXISTS ux_cliente_tenant_usuario
    ON public.cliente (tenant_id, usuario_id) WHERE usuario_id IS NOT NULL;

-- 2. A tabela legada morre (leva junto a policy V029 e os índices)
DROP TABLE IF EXISTS public.cliente_identity_provider;

-- 3. Perfil civil: chave é a pessoa. Perfis órfãos (sem pessoa) são lixo de
--    teste pré-F3 — o obter() provisiona a pessoa em toda porta autenticada.
DELETE FROM public.customer_profile WHERE usuario_id IS NULL;
ALTER TABLE public.customer_profile
    ALTER COLUMN usuario_id SET NOT NULL,
    DROP COLUMN IF EXISTS provider,
    DROP COLUMN IF EXISTS provider_user_id;

-- 4. Habilitação: pessoa opcional (balcão sem conta), CPF segue a chave humana
DROP INDEX IF EXISTS public.idx_customer_habilitacao_sub;
ALTER TABLE public.customer_habilitacao
    DROP COLUMN IF EXISTS provider,
    DROP COLUMN IF EXISTS provider_user_id;
CREATE INDEX IF NOT EXISTS idx_customer_habilitacao_usuario
    ON public.customer_habilitacao (usuario_id) WHERE usuario_id IS NOT NULL;
