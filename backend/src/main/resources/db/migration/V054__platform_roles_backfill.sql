-- =====================================================================
-- Papéis de plataforma (F2): separar ALCANCE de PODER.
--
-- Até aqui `usuario_global_roles.unrestricted_access = true` significava as
-- duas coisas ao mesmo tempo: "acessa qualquer empresa" E "pode tudo". Com
-- mais de um tipo de operador (suporte, financeiro, leitura) isso não se
-- sustenta.
--
--   ALCANCE  → segue em unrestricted_access (TenantFilter/RLS deixam o
--              operador enxergar empresas sem ser membro). Setado para
--              QUALQUER papel de plataforma.
--   PODER    → passa a ser o papel PLATFORM_* em roles[], decidido pelo
--              policies/authz/platform.rego.
--
-- Papéis: PLATFORM_ADMIN | PLATFORM_SUPORTE | PLATFORM_FINANCEIRO |
--         PLATFORM_LEITURA
--
-- Sem DDL: a coluna roles text[] já existe desde a V001. O que falta é
-- garantir que todo operador atual tenha um papel explícito — sem isso o
-- platform.rego (que exige papel) os deixaria sem poder nenhum.
-- =====================================================================

-- Backfill: quem tem acesso irrestrito hoje e nenhum papel PLATFORM_* vira
-- PLATFORM_ADMIN (é exatamente o poder que já exercia). Idempotente.
UPDATE public.usuario_global_roles
SET roles = array_append(roles, 'PLATFORM_ADMIN'),
    updated_at = now()
WHERE unrestricted_access = true
  AND NOT EXISTS (
      SELECT 1 FROM unnest(roles) r WHERE r LIKE 'PLATFORM\_%'
  );

-- Coerência inversa: papel de plataforma sem alcance não funcionaria (o
-- TenantFilter barraria o operador antes de chegar ao OPA).
UPDATE public.usuario_global_roles
SET unrestricted_access = true,
    updated_at = now()
WHERE unrestricted_access = false
  AND EXISTS (
      SELECT 1 FROM unnest(roles) r WHERE r LIKE 'PLATFORM\_%'
  );

-- Sem CHECK de nomes de papel aqui de propósito: CHECK do PostgreSQL não
-- aceita subquery, e a alternativa (função IMMUTABLE) viraria DDL obrigatório
-- a cada papel novo. A validação vive no enum PapelPlataforma (fonte única,
-- usada pela API de operadores) e na matriz do platform.rego.

COMMENT ON COLUMN public.usuario_global_roles.unrestricted_access IS
    'ALCANCE: acessa qualquer empresa sem ser membro. NÃO implica poder — o poder vem do papel PLATFORM_* em roles[] (platform.rego).';
