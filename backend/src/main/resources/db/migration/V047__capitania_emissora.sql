-- =====================================================================
-- Emissão delegada — fundação (EMISSAO_DELEGADA_SPEC §11 passo 1):
-- 1) Catálogo de capitanias: tabela de PLATAFORMA (sem tenant_id/RLS),
--    mantida pelo super admin. email_oficial apenas pré-preenche o
--    marinha_email do tenant — a EAMA continua podendo editar o destino.
-- 2) Perfil emissor no tenant: capitania (toda empresa declara a sua —
--    emissora pela licença, operadora pela área de operação), registro
--    EAMA e a habilitação validada pelo super admin (portão CADASTRAL;
--    o portão COMERCIAL é o módulo do plano).
-- 3) Split do módulo de oferta (enum ModuloPlano): EMISSAO_MARINHA
--    vira EMISSAO_PROPRIA; EMISSAO_DELEGADA é novo. Planos já
--    configurados migram a chave antiga.
-- =====================================================================

CREATE TABLE public.capitania (
    id            uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    codigo        varchar(12)  NOT NULL UNIQUE,
    nome          varchar(120) NOT NULL,
    uf            char(2),
    email_oficial varchar(255),
    ativa         boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE public.capitania IS
    'Catálogo de Capitanias/Delegacias/Agências da Marinha (plataforma, sem tenant_id; mantido pelo super admin)';
COMMENT ON COLUMN public.capitania.email_oficial IS
    'Default para pré-preencher tenant.marinha_email; o tenant emissor pode sobrescrever';

ALTER TABLE public.tenant ADD COLUMN capitania_id uuid REFERENCES public.capitania(id);
ALTER TABLE public.tenant ADD COLUMN emissora_habilitada boolean NOT NULL DEFAULT false;
ALTER TABLE public.tenant ADD COLUMN eama_registro varchar(60);
ALTER TABLE public.tenant ADD COLUMN eama_registro_validade date;
CREATE INDEX idx_tenant_capitania ON public.tenant (capitania_id);

COMMENT ON COLUMN public.tenant.emissora_habilitada IS
    'EAMA emissora validada pelo super admin (portão cadastral da emissão em nome próprio/delegada)';
COMMENT ON COLUMN public.tenant.eama_registro IS
    'Número de inscrição/registro da EAMA na Capitania (declarado pelo tenant, validado pelo super admin)';

-- Planos já configurados: a chave antiga passa a significar emissão própria
UPDATE public.plano
   SET modulos = replace(modulos::text, '"EMISSAO_MARINHA"', '"EMISSAO_PROPRIA"')::jsonb
 WHERE modulos::text LIKE '%EMISSAO_MARINHA%';

-- Seed inicial: Capitanias dos Portos litorâneas (nomes/códigos públicos).
-- E-mail oficial fica NULL de propósito — o super admin preenche/ajusta
-- no painel (semear e-mail errado é pior que não semear).
INSERT INTO public.capitania (codigo, nome, uf) VALUES
  ('CPRJ',  'Capitania dos Portos do Rio de Janeiro',      'RJ'),
  ('CPSP',  'Capitania dos Portos de São Paulo',           'SP'),
  ('CPES',  'Capitania dos Portos do Espírito Santo',      'ES'),
  ('CPBA',  'Capitania dos Portos da Bahia',               'BA'),
  ('CPPE',  'Capitania dos Portos de Pernambuco',          'PE'),
  ('CPCE',  'Capitania dos Portos do Ceará',               'CE'),
  ('CPRN',  'Capitania dos Portos do Rio Grande do Norte', 'RN'),
  ('CPPB',  'Capitania dos Portos da Paraíba',             'PB'),
  ('CPAL',  'Capitania dos Portos de Alagoas',             'AL'),
  ('CPSE',  'Capitania dos Portos de Sergipe',             'SE'),
  ('CPPI',  'Capitania dos Portos do Piauí',               'PI'),
  ('CPMA',  'Capitania dos Portos do Maranhão',            'MA'),
  ('CPAOR', 'Capitania dos Portos da Amazônia Oriental',   'PA'),
  ('CPSC',  'Capitania dos Portos de Santa Catarina',      'SC'),
  ('CPPR',  'Capitania dos Portos do Paraná',              'PR'),
  ('CPRS',  'Capitania dos Portos do Rio Grande do Sul',   'RS');
