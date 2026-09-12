-- V068: limites personalizados por empresa.
--
-- Até aqui o teto de usuários (e os demais limites) vinha só de plano.limites:
-- toda EAMA no mesmo plano tinha o mesmo número, e a única forma de dar mais
-- usuários a uma empresa era trocar o plano inteiro dela.
--
-- limites_override guarda só as chaves que a plataforma personalizou para ESTA
-- empresa, com a mesma forma de plano.limites ({"usuarios_max": 5}). Chave
-- presente vence a do plano; chave ausente segue o plano; '{}' = sem
-- personalização. O resolvedor é o PlanoLimiteService. Hoje o console só
-- edita usuarios_max, mas a leitura já vale para qualquer chave.
--
-- Vale mesmo sem assinatura ativa (empresa pendente de aprovação): sem plano,
-- o padrão continua sendo ilimitado, a menos que o console defina um teto.

ALTER TABLE public.tenant
    ADD COLUMN IF NOT EXISTS limites_override jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE public.tenant DROP CONSTRAINT IF EXISTS tenant_limites_override_objeto;
ALTER TABLE public.tenant
    ADD CONSTRAINT tenant_limites_override_objeto
    CHECK (jsonb_typeof(limites_override) = 'object');
