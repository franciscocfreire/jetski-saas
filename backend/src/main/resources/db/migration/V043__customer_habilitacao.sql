-- =====================================================================
-- Habilitação temporária (CHA-MTA-E) como dado DO CLIENTE — by design.
--
-- A habilitação é da pessoa (emitida pela Marinha; GRU paga pelo cliente
-- à União) — a loja é só o canal. Antes, a prova do direito (nº da GRU,
-- validade, devolutiva) vivia apenas em reserva_habilitacao da loja de
-- origem: reset/exclusão da loja (ou suspensão) destruiria um direito
-- vigente do titular. Esta tabela GLOBAL nasce junto com a emissão e
-- sobrevive à loja.
--
-- Global (sem tenant, sem RLS) como customer_profile: acesso apenas
-- pelos endpoints self do portal (filtrados pelo sub) e pelo sync
-- interno. Chave humana = CPF (só dígitos) — cliente de balcão pode não
-- ter conta no portal; provider/provider_user_id são preenchidos quando
-- houver vínculo. tenant/reserva de origem são INFORMATIVOS, sem FK —
-- a loja de origem pode ser expurgada.
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.customer_habilitacao (
    id                    uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    cpf                   varchar(14) NOT NULL,
    provider              varchar(50),
    provider_user_id      varchar(255),
    gru_numero            varchar(60) NOT NULL,
    categoria             varchar(40) NOT NULL DEFAULT 'CHA-MTA-E',
    emitida_em            timestamptz NOT NULL,
    valida_ate            date NOT NULL,
    marinha_confirmada_em timestamptz,
    loja_origem_nome      varchar(200),
    tenant_origem         uuid,
    reserva_origem        uuid,
    -- Cópia do PDF da devolutiva em prefixo da PLATAFORMA (fora do prefixo do
    -- tenant, que é apagado no expurgo): _platform/customers/{cpf}/...
    pdf_s3_key            text,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT customer_habilitacao_gru_uq UNIQUE (gru_numero)
);

CREATE INDEX IF NOT EXISTS idx_customer_habilitacao_cpf
    ON public.customer_habilitacao (cpf);
CREATE INDEX IF NOT EXISTS idx_customer_habilitacao_sub
    ON public.customer_habilitacao (provider, provider_user_id);

-- ---------------------------------------------------------------------
-- Backfill único: temporárias já emitidas e pagas (mesmos critérios do
-- CustomerHabilitacaoService.minhasTemporarias). Validade = emissão + 30
-- dias (HabilitacaoService.VALIDADE_TEMPORARIA_DIAS). Roda como superuser
-- (migration) — RLS não interfere. Idempotente via ON CONFLICT.
-- ---------------------------------------------------------------------
INSERT INTO public.customer_habilitacao
    (cpf, provider, provider_user_id, gru_numero, emitida_em, valida_ate,
     marinha_confirmada_em, loja_origem_nome, tenant_origem, reserva_origem)
SELECT regexp_replace(c.documento, '\D', '', 'g'),
       ip.provider, ip.provider_user_id,
       h.gru_numero,
       r.documento_emitido_em,
       (r.documento_emitido_em + interval '30 days')::date,
       h.marinha_confirmada_em,
       t.razao_social,
       r.tenant_id,
       r.id
  FROM public.reserva_habilitacao h
  JOIN public.reserva r  ON r.id = h.reserva_id
  JOIN public.cliente c  ON c.id = r.cliente_id
  JOIN public.tenant t   ON t.id = r.tenant_id
  LEFT JOIN public.cliente_identity_provider ip ON ip.cliente_id = c.id
 WHERE h.via = 'EMA'
   AND h.gru_numero IS NOT NULL
   AND h.gru_pago = true
   AND r.documento_emitido_em IS NOT NULL
   AND c.documento IS NOT NULL
   AND regexp_replace(c.documento, '\D', '', 'g') <> ''
ON CONFLICT (gru_numero) DO NOTHING;

-- Grants: cobertos pelo infra/prod/01-init-roles.sql (re-executado no deploy,
-- com ALTER DEFAULT PRIVILEGES) — padrão das demais migrations.
