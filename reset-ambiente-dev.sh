#!/bin/bash

###############################################################################
# Reset Completo do Ambiente DEV (Docker Compose) - MeuJet
# (PostgreSQL porta 5432 + Keycloak porta 8080)
#
# Uso:
#   ./reset-ambiente-dev.sh                    # Usa ngrok padrao
#   ./reset-ambiente-dev.sh https://xxx.ngrok-free.app  # Com ngrok customizado
#   PUBLIC_URL=https://xxx.ngrok-free.app ./reset-ambiente-dev.sh
###############################################################################

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuracoes do ambiente DEV (Docker)
PG_HOST="localhost"
PG_PORT="5432"
PG_USER="jetski"
PG_PASSWORD="dev123"
PG_DB="jetski_dev"

KC_HOST="localhost"
KC_PORT="8080"
KC_ADMIN_USER="admin"
KC_ADMIN_PASSWORD="Mazuca@123"
KC_REALM="jetski-saas"

# Tenant ACME (padrao)
TENANT_ID="a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"

# URL publica do tunel (argumento, variavel de ambiente ou padrao)
DEFAULT_PUBLIC_URL="https://www.pegaojet.com.br"
PUBLIC_URL="${1:-${PUBLIC_URL:-$DEFAULT_PUBLIC_URL}}"
# Exportado para o `docker compose` substituir KC_BACKOFFICE_URL/REDIRECT no
# import do realm (jetski-backoffice público + redirects do túnel).
export PUBLIC_URL

# Login com Google (opcional): habilita o IdP no import do realm se houver
# credenciais no ambiente ou no .env da raiz. Sem credenciais o IdP nasce
# DESABILITADO (nenhum botão na tela do Keycloak, nada quebra).
# grep|cut em vez de source para não sobrescrever outras vars do script.
if [ -f .env ]; then
    : "${GOOGLE_CLIENT_ID:=$(grep -E '^GOOGLE_CLIENT_ID=' .env | head -1 | cut -d= -f2-)}"
    : "${GOOGLE_CLIENT_SECRET:=$(grep -E '^GOOGLE_CLIENT_SECRET=' .env | head -1 | cut -d= -f2-)}"
fi
if [ -n "${GOOGLE_CLIENT_ID:-}" ] && [ -n "${GOOGLE_CLIENT_SECRET:-}" ]; then
    GOOGLE_IDP_ENABLED=true
else
    GOOGLE_IDP_ENABLED=false
fi
export GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET GOOGLE_IDP_ENABLED

echo -e "${BLUE}========================================"
echo "  RESET AMBIENTE DEV (Docker) - MeuJet"
echo -e "========================================${NC}"
echo ""
echo "Configuracoes:"
echo "   PostgreSQL: ${PG_HOST}:${PG_PORT}"
echo "   Keycloak:   ${KC_HOST}:${KC_PORT}"
echo "   Database:   ${PG_DB}"
if [ -n "$PUBLIC_URL" ]; then
    echo -e "   ${GREEN}Tunel:      ${PUBLIC_URL}${NC}"
fi
if [ "$GOOGLE_IDP_ENABLED" = "true" ]; then
    echo -e "   ${GREEN}Login Google: habilitado (credenciais encontradas)${NC}"
else
    echo "   Login Google: desabilitado (sem GOOGLE_CLIENT_ID/SECRET no .env)"
fi
echo ""

# Verificar se docker compose esta disponivel
if ! command -v docker &> /dev/null; then
    echo -e "${RED}ERRO: Docker nao encontrado!${NC}"
    exit 1
fi

cd "$SCRIPT_DIR"

# 1. Parar todos os containers
echo -e "${YELLOW}1. Parando todos os containers...${NC}"
docker compose down --remove-orphans 2>/dev/null || true
sleep 2
echo -e "${GREEN}   OK - Containers parados!${NC}"

# 2. Remover volumes (dados persistentes)
echo -e "${YELLOW}2. Removendo volumes (reset completo dos dados)...${NC}"
docker volume rm jetski_postgres_data 2>/dev/null || true
docker volume rm jetski_redis_data 2>/dev/null || true
docker volume rm jetski_keycloak_data 2>/dev/null || true
echo -e "${GREEN}   OK - Volumes removidos!${NC}"

# 3. Rebuild e iniciar containers
echo -e "${YELLOW}3. Iniciando containers (com rebuild)...${NC}"
if [ -n "$PUBLIC_URL" ]; then
    # Iniciar com variaveis do ngrok
    NEXTAUTH_URL="$PUBLIC_URL" \
    PORTAL_PUBLIC_URL="${PORTAL_PUBLIC_URL:-$(echo "$PUBLIC_URL" | sed 's#//www\.#//cliente.#')}" \
    PORTAL_NEXTAUTH_URL="${PORTAL_PUBLIC_URL:-$(echo "$PUBLIC_URL" | sed 's#//www\.#//cliente.#')}" \
    KEYCLOAK_ISSUER="$PUBLIC_URL/realms/jetski-saas" \
    JETSKI_FRONTEND_URL="$PUBLIC_URL" \
    JETSKI_EXTERNAL_URL="$PUBLIC_URL" \
    docker compose up -d --build
else
    docker compose up -d --build
fi
echo -e "${GREEN}   OK - Containers iniciados!${NC}"

# 4. Aguardar PostgreSQL ficar pronto
echo -e "${YELLOW}4. Aguardando PostgreSQL...${NC}"
for i in {1..30}; do
    if docker compose exec -T postgres pg_isready -U $PG_USER > /dev/null 2>&1; then
        echo -e "${GREEN}   OK - PostgreSQL pronto!${NC}"
        break
    fi
    printf "."
    sleep 2
done

# 4.1 Criar usuario da aplicacao (sem SUPERUSER/BYPASSRLS para RLS funcionar)
echo -e "${YELLOW}4.1 Criando usuario jetski_app (RLS-safe)...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jetski_app') THEN
        CREATE ROLE jetski_app WITH LOGIN PASSWORD 'dev123' NOSUPERUSER NOBYPASSRLS;
    END IF;
END
$$;
GRANT USAGE ON SCHEMA public TO jetski_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO jetski_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO jetski_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO jetski_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO jetski_app;
EOSQL
echo -e "${GREEN}   OK - Usuario jetski_app criado!${NC}"

# 5. Aguardar Redis ficar pronto
echo -e "${YELLOW}5. Aguardando Redis...${NC}"
for i in {1..15}; do
    if docker compose exec -T redis redis-cli ping > /dev/null 2>&1; then
        echo -e "${GREEN}   OK - Redis pronto!${NC}"
        break
    fi
    printf "."
    sleep 1
done

# 6. Aguardar Keycloak ficar pronto
echo -e "${YELLOW}6. Aguardando Keycloak (pode demorar ~90s)...${NC}"
KC_READY=false
for i in {1..90}; do
    if curl -sf "http://${KC_HOST}:${KC_PORT}/realms/master" > /dev/null 2>&1; then
        echo ""
        echo -e "${GREEN}   OK - Keycloak pronto!${NC}"
        KC_READY=true
        break
    fi
    printf "."
    sleep 2
done

# Verificar se Keycloak subiu
if [ "$KC_READY" != "true" ]; then
    echo ""
    echo -e "${YELLOW}   AVISO: Keycloak ainda inicializando, continuando...${NC}"
    echo "   (O Keycloak pode demorar mais no primeiro boot)"
fi

# 7. Executar migrations do Flyway (via Docker)
echo -e "${YELLOW}7. Executando migrations do banco...${NC}"

# Rodar Flyway via container temporario conectado a rede Docker
docker run --rm \
    --network jetski_jetski-network \
    -v "$BACKEND_DIR/src/main/resources/db/migration:/flyway/sql:ro" \
    flyway/flyway:10-alpine \
    -url=jdbc:postgresql://postgres:5432/${PG_DB} \
    -user=${PG_USER} \
    -password=${PG_PASSWORD} \
    -baselineOnMigrate=true \
    -baselineVersion=0 \
    -outOfOrder=true \
    migrate 2>/dev/null && echo -e "${GREEN}   OK - Migrations executadas!${NC}" || {
    echo -e "${YELLOW}   Tentando metodo alternativo (psql)...${NC}"
    # Fallback: executar migrations via psql
    for f in "$BACKEND_DIR/src/main/resources/db/migration"/*.sql; do
        if [ -f "$f" ]; then
            filename=$(basename "$f")
            echo "   Aplicando: $filename"
            docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} -f - < "$f" 2>/dev/null || true
        fi
    done
    echo -e "${GREEN}   OK - Migrations aplicadas via psql!${NC}"
}

# 7.1 Garantir permissoes para jetski_app nas tabelas criadas pelas migrations
echo -e "${YELLOW}7.1 Atualizando permissoes para jetski_app...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO jetski_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO jetski_app;
EOSQL
echo -e "${GREEN}   OK - Permissoes atualizadas!${NC}"

# 7.1b Fase 1 (balcão) - garantir schema/RLS (idempotente; canônico = Flyway V003-V008)
echo -e "${YELLOW}7.1b Garantindo schema da Fase 1 (balcão)...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- Colunas (idempotente). CHECK constraints vêm do Flyway V003-V005.
ALTER TABLE public.reserva
    ADD COLUMN IF NOT EXISTS pagamento_tipo            varchar(10),
    ADD COLUMN IF NOT EXISTS pagamento_status          varchar(20) NOT NULL DEFAULT 'AGUARDANDO',
    ADD COLUMN IF NOT EXISTS pagamento_valor_informado numeric(10,2),
    ADD COLUMN IF NOT EXISTS pagamento_validado_por    uuid,
    ADD COLUMN IF NOT EXISTS pagamento_validado_em     timestamptz,
    ADD COLUMN IF NOT EXISTS pagamento_motivo_recusa   text,
    ADD COLUMN IF NOT EXISTS valor_total               numeric(10,2),
    ADD COLUMN IF NOT EXISTS documento_emitido_em      timestamptz;

-- V018 + V035: estados RASCUNHO e NO_SHOW no CHECK de status (idempotente).
ALTER TABLE public.reserva DROP CONSTRAINT IF EXISTS reserva_status_check;
ALTER TABLE public.reserva ADD CONSTRAINT reserva_status_check
    CHECK ((status)::text = ANY (ARRAY['RASCUNHO','PENDENTE','CONFIRMADA','CANCELADA','FINALIZADA','EXPIRADA','NO_SHOW']::varchar[]::text[]));

ALTER TABLE public.cliente
    ADD COLUMN IF NOT EXISTS origem       varchar(20) NOT NULL DEFAULT 'PORTAL',
    ADD COLUMN IF NOT EXISTS status_conta varchar(20) NOT NULL DEFAULT 'SEM_LOGIN';

ALTER TABLE public.tenant
    ADD COLUMN IF NOT EXISTS marinha_email   varchar(255),
    ADD COLUMN IF NOT EXISTS email_remetente varchar(255),
    ADD COLUMN IF NOT EXISTS pix_chave       varchar(140),
    ADD COLUMN IF NOT EXISTS smtp_host       varchar(255),
    ADD COLUMN IF NOT EXISTS smtp_port       integer,
    ADD COLUMN IF NOT EXISTS smtp_username   varchar(255),
    ADD COLUMN IF NOT EXISTS smtp_password   varchar(255),
    ADD COLUMN IF NOT EXISTS smtp_from       varchar(255),
    ADD COLUMN IF NOT EXISTS smtp_starttls   boolean DEFAULT true,
    ADD COLUMN IF NOT EXISTS documento_config jsonb,
    ADD COLUMN IF NOT EXISTS assinatura_config jsonb;

-- OTP no aceite (Fase B do reforço jurídico da assinatura)
ALTER TABLE public.reserva_aceite
    ADD COLUMN IF NOT EXISTS otp_verificado boolean,
    ADD COLUMN IF NOT EXISTS otp_canal   varchar(20),
    ADD COLUMN IF NOT EXISTS otp_destino varchar(160);

-- Certificado auto-assinado da plataforma p/ assinatura PAdES (Fase C2)
CREATE TABLE IF NOT EXISTS public.assinatura_certificado (
    id          uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    subject     varchar(255) NOT NULL,
    cert_pem    text NOT NULL,
    key_pem_enc text NOT NULL,
    algoritmo   varchar(40) NOT NULL DEFAULT 'SHA256withRSA',
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.reserva_comprovante (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    reserva_id uuid NOT NULL REFERENCES public.reserva(id) ON DELETE CASCADE,
    s3_key varchar(500) NOT NULL, url text, hash_sha256 varchar(64),
    tipo varchar(20) DEFAULT 'PIX' NOT NULL,
    enviado_em timestamptz DEFAULT now() NOT NULL, ativo boolean DEFAULT true NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL, updated_at timestamptz DEFAULT now() NOT NULL
);
CREATE TABLE IF NOT EXISTS public.documento_emitido (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    reserva_id uuid NOT NULL REFERENCES public.reserva(id) ON DELETE CASCADE,
    s3_key varchar(500) NOT NULL, hash_sha256 varchar(64) NOT NULL, destinos jsonb,
    emitido_em timestamptz DEFAULT now() NOT NULL, created_at timestamptz DEFAULT now() NOT NULL
);
CREATE TABLE IF NOT EXISTS public.cliente_identity_provider (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    cliente_id uuid NOT NULL REFERENCES public.cliente(id) ON DELETE CASCADE,
    provider varchar(50) NOT NULL, provider_user_id varchar(255) NOT NULL,
    linked_at timestamptz DEFAULT now() NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL, updated_at timestamptz DEFAULT now() NOT NULL
);

-- RLS por tenant (idempotente)
ALTER TABLE public.reserva_comprovante ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reserva_comprovante FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_reserva_comprovante ON public.reserva_comprovante;
CREATE POLICY tenant_isolation_reserva_comprovante ON public.reserva_comprovante USING ((tenant_id = public.get_current_tenant_id()));

ALTER TABLE public.documento_emitido ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.documento_emitido FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_documento_emitido ON public.documento_emitido;
CREATE POLICY tenant_isolation_documento_emitido ON public.documento_emitido USING ((tenant_id = public.get_current_tenant_id()));

ALTER TABLE public.cliente_identity_provider ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cliente_identity_provider FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_cliente_identity_provider ON public.cliente_identity_provider;
CREATE POLICY tenant_isolation_cliente_identity_provider ON public.cliente_identity_provider USING ((tenant_id = public.get_current_tenant_id()));

-- V035+V036: folio financeiro (reserva E/OU locação) — pagamentos, estornos e cobranças
CREATE TABLE IF NOT EXISTS public.reserva_lancamento (
    id             uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id      uuid NOT NULL,
    reserva_id     uuid REFERENCES public.reserva(id) ON DELETE CASCADE,
    locacao_id     uuid REFERENCES public.locacao(id) ON DELETE CASCADE,
    tipo           varchar(20) NOT NULL,
    forma          varchar(20),
    valor          numeric(10,2) NOT NULL CHECK (valor > 0),
    observacao     text,
    registrado_por uuid,
    created_at     timestamptz NOT NULL DEFAULT now()
);
-- V036 (idempotente também para banco criado na forma V035)
ALTER TABLE public.reserva_lancamento
    ADD COLUMN IF NOT EXISTS locacao_id uuid REFERENCES public.locacao(id) ON DELETE CASCADE;
ALTER TABLE public.reserva_lancamento ALTER COLUMN reserva_id DROP NOT NULL;
ALTER TABLE public.reserva_lancamento ALTER COLUMN forma DROP NOT NULL;
ALTER TABLE public.reserva_lancamento DROP CONSTRAINT IF EXISTS reserva_lancamento_tipo_check;
ALTER TABLE public.reserva_lancamento ADD CONSTRAINT reserva_lancamento_tipo_check
    CHECK (tipo IN ('PAGAMENTO', 'ESTORNO', 'COBRANCA_ALUGUEL', 'COBRANCA_COMBUSTIVEL', 'COBRANCA_EXTRAS'));
ALTER TABLE public.reserva_lancamento DROP CONSTRAINT IF EXISTS reserva_lancamento_forma_check;
ALTER TABLE public.reserva_lancamento ADD CONSTRAINT reserva_lancamento_forma_check
    CHECK (
        (tipo IN ('PAGAMENTO', 'ESTORNO') AND forma IN ('DINHEIRO', 'PIX', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'OUTRO'))
        OR (tipo IN ('COBRANCA_ALUGUEL', 'COBRANCA_COMBUSTIVEL', 'COBRANCA_EXTRAS') AND forma IS NULL)
    );
ALTER TABLE public.reserva_lancamento DROP CONSTRAINT IF EXISTS reserva_lancamento_ancora_check;
ALTER TABLE public.reserva_lancamento ADD CONSTRAINT reserva_lancamento_ancora_check
    CHECK (reserva_id IS NOT NULL OR locacao_id IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_reserva_lancamento_reserva ON public.reserva_lancamento (tenant_id, reserva_id);
CREATE INDEX IF NOT EXISTS idx_reserva_lancamento_locacao ON public.reserva_lancamento (tenant_id, locacao_id) WHERE locacao_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_reserva_lancamento_dia ON public.reserva_lancamento (tenant_id, created_at);
ALTER TABLE public.reserva_lancamento ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reserva_lancamento FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_reserva_lancamento ON public.reserva_lancamento;
CREATE POLICY tenant_isolation_reserva_lancamento ON public.reserva_lancamento USING ((tenant_id = public.get_current_tenant_id()));
-- V037: backfill de pagamentos confirmados antes do folio (idempotente via NOT EXISTS)
INSERT INTO public.reserva_lancamento
    (tenant_id, reserva_id, tipo, forma, valor, observacao, registrado_por, created_at)
SELECT r.tenant_id, r.id, 'PAGAMENTO',
       CASE WHEN r.canal = 'PORTAL' THEN 'PIX' ELSE 'OUTRO' END,
       COALESCE(r.valor_sinal, r.valor_total),
       'backfill V037 — pagamento confirmado antes do folio',
       r.pagamento_validado_por,
       COALESCE(r.sinal_pago_em, r.pagamento_validado_em, now())
FROM public.reserva r
WHERE r.pagamento_status = 'CONFIRMADO'
  AND COALESCE(r.valor_sinal, r.valor_total) > 0
  AND NOT EXISTS (SELECT 1 FROM public.reserva_lancamento l
                  WHERE l.reserva_id = r.id AND l.tipo = 'PAGAMENTO');

-- V025: metering de emissões por tenant (DOCUMENTO/GRU/PREVIA — base p/ cobrança futura)
CREATE TABLE IF NOT EXISTS public.emissao_uso (
    id            uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id     uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    tipo          varchar(20) NOT NULL CHECK (tipo IN ('DOCUMENTO', 'GRU', 'PREVIA')),
    referencia_id uuid NOT NULL,
    destinos      varchar(60),
    ocorrido_em   timestamptz NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_emissao_uso_ref ON public.emissao_uso (tipo, referencia_id, ocorrido_em);
CREATE INDEX IF NOT EXISTS idx_emissao_uso_tenant_data ON public.emissao_uso (tenant_id, ocorrido_em);
ALTER TABLE public.emissao_uso ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.emissao_uso FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_emissao_uso ON public.emissao_uso;
CREATE POLICY tenant_isolation_emissao_uso ON public.emissao_uso USING ((tenant_id = public.get_current_tenant_id()));
UPDATE public.plano SET limites = limites || '{"emissoes_mes": -1}'::jsonb WHERE NOT (limites ? 'emissoes_mes');

-- V026: créditos de emissão pré-pagos (ledger append-only)
CREATE TABLE IF NOT EXISTS public.credito_lancamento (
    id            uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id     uuid NOT NULL REFERENCES public.tenant(id) ON DELETE RESTRICT,
    tipo          varchar(20) NOT NULL CHECK (tipo IN ('ADESAO', 'AJUSTE', 'CONSUMO', 'ESTORNO')),
    quantidade    integer NOT NULL CHECK (quantidade <> 0),
    saldo_apos    integer NOT NULL,
    referencia_id uuid,
    motivo        varchar(200),
    criado_por    uuid,
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_credito_adesao_unica ON public.credito_lancamento (tenant_id) WHERE tipo = 'ADESAO';
CREATE UNIQUE INDEX IF NOT EXISTS ux_credito_consumo_por_doc ON public.credito_lancamento (referencia_id) WHERE tipo = 'CONSUMO';
CREATE INDEX IF NOT EXISTS idx_credito_lancamento_tenant ON public.credito_lancamento (tenant_id, created_at);
CREATE OR REPLACE FUNCTION public.forbid_credito_lancamento_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'credito_lancamento é append-only: % não é permitido', TG_OP;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_credito_lancamento_append_only ON public.credito_lancamento;
CREATE TRIGGER trg_credito_lancamento_append_only
    BEFORE UPDATE OR DELETE ON public.credito_lancamento
    FOR EACH ROW EXECUTE FUNCTION public.forbid_credito_lancamento_mutation();
ALTER TABLE public.credito_lancamento ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.credito_lancamento FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_credito_lancamento ON public.credito_lancamento;
CREATE POLICY tenant_isolation_credito_lancamento ON public.credito_lancamento USING ((tenant_id = public.get_current_tenant_id()));
-- Seed dev: créditos de adesão para os tenants seed (sem isso a emissão EMA bloqueia em dev)
INSERT INTO public.credito_lancamento (tenant_id, tipo, quantidade, saldo_apos, motivo)
SELECT t.id, 'ADESAO', 100, 100, 'Créditos de adesão (seed dev)'
FROM public.tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM public.credito_lancamento c WHERE c.tenant_id = t.id AND c.tipo = 'ADESAO'
);

-- V027: compras de créditos via PIX (aprovação manual do super admin)
CREATE TABLE IF NOT EXISTS public.credito_compra (
    id            uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id     uuid NOT NULL REFERENCES public.tenant(id) ON DELETE RESTRICT,
    quantidade    integer NOT NULL CHECK (quantidade > 0),
    pix_txid      varchar(80) NOT NULL,
    status        varchar(20) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE', 'APROVADA', 'REJEITADA')),
    criado_por    uuid,
    decidido_por  uuid,
    decidido_em   timestamptz,
    observacao    varchar(200),
    lancamento_id uuid,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_credito_compra_txid ON public.credito_compra (tenant_id, pix_txid);
CREATE INDEX IF NOT EXISTS idx_credito_compra_tenant_status ON public.credito_compra (tenant_id, status);
ALTER TABLE public.credito_compra ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.credito_compra FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_credito_compra ON public.credito_compra;
CREATE POLICY tenant_isolation_credito_compra ON public.credito_compra USING ((tenant_id = public.get_current_tenant_id()));

-- V028: compra por valor + preço do crédito configurável (plataforma_config)
CREATE TABLE IF NOT EXISTS public.plataforma_config (
    chave      varchar(60) NOT NULL PRIMARY KEY,
    valor      text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid
);
INSERT INTO public.plataforma_config (chave, valor) VALUES ('creditos_preco_unitario', '5.00')
ON CONFLICT (chave) DO NOTHING;
ALTER TABLE public.credito_compra
    ADD COLUMN IF NOT EXISTS valor_pago     numeric(10,2),
    ADD COLUMN IF NOT EXISTS preco_unitario numeric(10,2);

-- V034: reserva por modelo — controle de estoque opcional por tenant
ALTER TABLE public.reserva_config
    ADD COLUMN IF NOT EXISTS controlar_estoque boolean NOT NULL DEFAULT false;

-- V033: notificações in-app do cliente do portal (sininho)
CREATE TABLE IF NOT EXISTS public.cliente_notificacao (
    id          uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id   uuid NOT NULL,
    cliente_id  uuid NOT NULL,
    tipo        varchar(40) NOT NULL,
    titulo      varchar(160) NOT NULL,
    mensagem    text,
    link        varchar(300),
    lida        boolean NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cliente_notificacao_cliente
    ON public.cliente_notificacao (tenant_id, cliente_id, lida, created_at DESC);
ALTER TABLE public.cliente_notificacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cliente_notificacao FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_cliente_notificacao ON public.cliente_notificacao;
CREATE POLICY tenant_isolation_cliente_notificacao ON public.cliente_notificacao
    USING (tenant_id = public.get_current_tenant_id());

-- V032: identidade GLOBAL do cliente do portal (CPF/RG seguem a pessoa)
CREATE TABLE IF NOT EXISTS public.customer_profile (
    id               uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    provider         varchar(50) NOT NULL,
    provider_user_id varchar(255) NOT NULL,
    nome             varchar(120),
    cpf              varchar(20),
    rg               varchar(30),
    orgao_emissor    varchar(20),
    nacionalidade    varchar(60),
    naturalidade     varchar(80),
    estrangeiro      boolean NOT NULL DEFAULT false,
    data_nascimento  date,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT customer_profile_provider_uq UNIQUE (provider, provider_user_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_customer_profile_cpf_uq
    ON public.customer_profile (cpf) WHERE cpf IS NOT NULL;

-- V031: portal do cliente — avaliações de locação (nota 1-5 + média pública)
CREATE TABLE IF NOT EXISTS public.avaliacao (
    id          uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id   uuid NOT NULL,
    locacao_id  uuid NOT NULL REFERENCES public.locacao(id) ON DELETE CASCADE,
    cliente_id  uuid NOT NULL,
    modelo_id   uuid NOT NULL,
    nota        integer NOT NULL,
    comentario  text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT avaliacao_locacao_uq UNIQUE (locacao_id),
    CONSTRAINT avaliacao_nota_check CHECK (nota BETWEEN 1 AND 5)
);
CREATE INDEX IF NOT EXISTS idx_avaliacao_tenant_modelo ON public.avaliacao (tenant_id, modelo_id);
CREATE INDEX IF NOT EXISTS idx_avaliacao_modelo ON public.avaliacao (modelo_id);
ALTER TABLE public.avaliacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.avaliacao FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_avaliacao ON public.avaliacao;
CREATE POLICY tenant_isolation_avaliacao ON public.avaliacao
    USING (tenant_id = public.get_current_tenant_id());
-- V041: leitura pública ESCOPADA à vitrine (policy permissiva soma com OR —
-- USING (true) derrotava a tenant_isolation da tabela inteira)
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

-- V030: portal do cliente — canal de origem da reserva (BALCAO | PORTAL)
ALTER TABLE public.reserva ADD COLUMN IF NOT EXISTS canal varchar(20) NOT NULL DEFAULT 'BALCAO';
ALTER TABLE public.reserva DROP CONSTRAINT IF EXISTS reserva_canal_check;
ALTER TABLE public.reserva ADD CONSTRAINT reserva_canal_check
    CHECK ((canal)::text = ANY (ARRAY['BALCAO'::text, 'PORTAL'::text]));
CREATE INDEX IF NOT EXISTS idx_reserva_canal_pagamento
    ON public.reserva (tenant_id, canal, pagamento_status) WHERE ativo = true;

-- V029: portal do cliente — leitura "self" cross-tenant dos vínculos do cliente
-- autenticado (o serviço seta app.customer_sub antes do SELECT; policy permissiva
-- soma-se à de tenant, liberando só as linhas do próprio sub)
DROP POLICY IF EXISTS cliente_idp_self_read ON public.cliente_identity_provider;
CREATE POLICY cliente_idp_self_read ON public.cliente_identity_provider
    FOR SELECT
    USING (provider_user_id = current_setting('app.customer_sub', true));

-- Super admin de dev (espelha PLATFORM_ADMIN_EMAILS do compose; o seeder do backend
-- só roda no boot — este bloco garante o acesso logo após o reset, sem restart)
INSERT INTO public.usuario_global_roles (usuario_id, roles, unrestricted_access)
SELECT id, ARRAY['PLATFORM_ADMIN'], true FROM public.usuario WHERE email = 'admin@acme.com'
ON CONFLICT (usuario_id) DO UPDATE SET unrestricted_access = true, updated_at = now();

-- F2.3: habilitação do condutor (CHA/EMA + GRU)
CREATE TABLE IF NOT EXISTS public.reserva_habilitacao (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    reserva_id uuid NOT NULL REFERENCES public.reserva(id) ON DELETE CASCADE,
    via varchar(10) NOT NULL,
    cha_categoria varchar(40), cha_numero varchar(60), cha_validade date,
    videoaula_em timestamptz,
    anexo_saude boolean DEFAULT false NOT NULL, anexo_regras boolean DEFAULT false NOT NULL, anexo_residencia boolean DEFAULT false NOT NULL,
    gru_numero varchar(60), gru_valor numeric(10,2), gru_pago boolean DEFAULT false NOT NULL, gru_pago_em timestamptz,
    gru_pix_copia_e_cola text, gru_pix_expiracao timestamp, gru_id_marinha varchar(40), gru_gerada_em timestamp, gru_pdf_s3_key text, gru_id_sessao text, gru_comprovante_s3_key text,
    resolvida boolean DEFAULT false NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL, updated_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT reserva_habilitacao_reserva_uq UNIQUE (reserva_id)
);
ALTER TABLE public.reserva_habilitacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reserva_habilitacao FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_reserva_habilitacao ON public.reserva_habilitacao;
CREATE POLICY tenant_isolation_reserva_habilitacao ON public.reserva_habilitacao USING ((tenant_id = public.get_current_tenant_id()));

-- Anexos do cliente (V017): documento de identidade, comprovante de residência, selfie (p/ o PDF)
CREATE TABLE IF NOT EXISTS public.cliente_anexo (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    cliente_id uuid NOT NULL REFERENCES public.cliente(id) ON DELETE CASCADE,
    tipo varchar(30) NOT NULL,
    s3_key text NOT NULL,
    content_type varchar(80),
    created_at timestamptz DEFAULT now() NOT NULL, updated_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT cliente_anexo_cliente_tipo_uq UNIQUE (cliente_id, tipo)
);
ALTER TABLE public.cliente_anexo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cliente_anexo FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_cliente_anexo ON public.cliente_anexo;
CREATE POLICY tenant_isolation_cliente_anexo ON public.cliente_anexo USING ((tenant_id = public.get_current_tenant_id()));
CREATE INDEX IF NOT EXISTS idx_cliente_anexo_cliente ON public.cliente_anexo(tenant_id, cliente_id);

-- F3 docs (V010): campos p/ os anexos NORMAM-212 (preenchimento manual, sem OCR)
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS rg character varying(30);
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS orgao_emissor character varying(30);
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS nacionalidade character varying(60);
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS naturalidade character varying(120);
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS estrangeiro boolean DEFAULT false NOT NULL;
ALTER TABLE public.reserva_habilitacao ADD COLUMN IF NOT EXISTS usa_lentes boolean DEFAULT false NOT NULL;
ALTER TABLE public.reserva_habilitacao ADD COLUMN IF NOT EXISTS usa_aparelho boolean DEFAULT false NOT NULL;
ALTER TABLE public.reserva_habilitacao ADD COLUMN IF NOT EXISTS instrutor_id uuid;

-- V038: devolutiva da Marinha (CHA-MTA-E confirmada)
ALTER TABLE public.reserva_habilitacao ADD COLUMN IF NOT EXISTS marinha_confirmada_em  timestamptz;
ALTER TABLE public.reserva_habilitacao ADD COLUMN IF NOT EXISTS marinha_confirmada_por uuid;
ALTER TABLE public.reserva_habilitacao ADD COLUMN IF NOT EXISTS cha_mtae_s3_key        text;

-- V039: resultado do envio dos e-mails da emissão (Marinha/cliente)
ALTER TABLE public.documento_emitido ADD COLUMN IF NOT EXISTS marinha_enviado_em timestamptz;
ALTER TABLE public.documento_emitido ADD COLUMN IF NOT EXISTS cliente_enviado_em timestamptz;

-- V040: captura de leads (origem LEAD + observacoes + capturado_por)
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS observacoes   text;
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS capturado_por uuid;
DO $$ BEGIN
    ALTER TABLE public.cliente
        ADD CONSTRAINT cliente_capturado_por_fk
            FOREIGN KEY (capturado_por) REFERENCES public.usuario (id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
ALTER TABLE public.cliente DROP CONSTRAINT IF EXISTS cliente_origem_check;
ALTER TABLE public.cliente
    ADD CONSTRAINT cliente_origem_check
        CHECK ((origem)::text = ANY (ARRAY['PORTAL'::text, 'BALCAO'::text, 'LEAD'::text]));

-- V043: habilitação temporária como dado DO CLIENTE (global, by design) —
-- sobrevive a reset/exclusão da loja de origem; sem RLS como customer_profile
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
    pdf_s3_key            text,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT customer_habilitacao_gru_uq UNIQUE (gru_numero)
);
CREATE INDEX IF NOT EXISTS idx_customer_habilitacao_cpf ON public.customer_habilitacao (cpf);
CREATE INDEX IF NOT EXISTS idx_customer_habilitacao_sub ON public.customer_habilitacao (provider, provider_user_id);

-- V046: módulos por plano (NULL = todos)
ALTER TABLE public.plano ADD COLUMN IF NOT EXISTS modulos jsonb;

-- V047: emissão delegada (fundação) — catálogo capitania + perfil emissor no tenant + split de módulos
CREATE TABLE IF NOT EXISTS public.capitania (
    id            uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    codigo        varchar(12)  NOT NULL UNIQUE,
    nome          varchar(120) NOT NULL,
    uf            char(2),
    email_oficial varchar(255),
    ativa         boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);
ALTER TABLE public.tenant ADD COLUMN IF NOT EXISTS capitania_id uuid REFERENCES public.capitania(id);
ALTER TABLE public.tenant ADD COLUMN IF NOT EXISTS emissora_habilitada boolean NOT NULL DEFAULT false;
ALTER TABLE public.tenant ADD COLUMN IF NOT EXISTS eama_registro varchar(60);
ALTER TABLE public.tenant ADD COLUMN IF NOT EXISTS eama_registro_validade date;
CREATE INDEX IF NOT EXISTS idx_tenant_capitania ON public.tenant (capitania_id);
UPDATE public.plano
   SET modulos = replace(modulos::text, '"EMISSAO_MARINHA"', '"EMISSAO_PROPRIA"')::jsonb
 WHERE modulos::text LIKE '%EMISSAO_MARINHA%';
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
  ('CPRS',  'Capitania dos Portos do Rio Grande do Sul',   'RS')
ON CONFLICT (codigo) DO NOTHING;

-- V048: emissão delegada — vínculo operadora×EAMA + espelho do emissor + snapshot + metering
CREATE TABLE IF NOT EXISTS public.vinculo_emissao (
    id                   uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_operador_id   uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    tenant_emissor_id    uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    status               varchar(12) NOT NULL DEFAULT 'CONVIDADO'
                         CHECK (status IN ('CONVIDADO', 'ATIVO', 'BLOQUEADO', 'REVOGADO')),
    convidado_por_tenant uuid NOT NULL,
    convidado_por        uuid,
    convidado_em         timestamptz NOT NULL DEFAULT now(),
    aceito_por           uuid,
    aceito_em            timestamptz,
    termo_aceite_em      timestamptz,
    termo_texto          text,
    bloqueado_em         timestamptz,
    revogado_por         uuid,
    revogado_em          timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT vinculo_emissao_lados_distintos CHECK (tenant_operador_id <> tenant_emissor_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_vinculo_emissao_operador_vivo
    ON public.vinculo_emissao (tenant_operador_id)
    WHERE status IN ('CONVIDADO', 'ATIVO', 'BLOQUEADO');
CREATE INDEX IF NOT EXISTS idx_vinculo_emissao_emissor ON public.vinculo_emissao (tenant_emissor_id, status);
ALTER TABLE public.vinculo_emissao ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vinculo_emissao FORCE ROW LEVEL SECURITY;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'vinculo_emissao' AND policyname = 'vinculo_emissao_partes') THEN
        CREATE POLICY vinculo_emissao_partes ON public.vinculo_emissao
            USING (tenant_operador_id = public.get_current_tenant_id()
                OR tenant_emissor_id = public.get_current_tenant_id());
    END IF;
END $$;
CREATE TABLE IF NOT EXISTS public.emissao_delegada (
    id                  uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id           uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    vinculo_id          uuid REFERENCES public.vinculo_emissao(id) ON DELETE SET NULL,
    documento_id        uuid REFERENCES public.documento_emitido(id) ON DELETE SET NULL,
    documento_hash      varchar(64),
    s3_key              text,
    operadora_tenant_id uuid NOT NULL,
    operadora_nome      varchar(200),
    condutor_nome       varchar(200),
    condutor_cpf        varchar(20),
    instrutor_id        uuid,
    instrutor_nome      varchar(200),
    gru_numero          varchar(40),
    emitido_em          timestamptz NOT NULL,
    reenviado_em        timestamptz,
    reenviado_para      varchar(255),
    created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_emissao_delegada_documento
    ON public.emissao_delegada (documento_id) WHERE documento_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_emissao_delegada_tenant_data ON public.emissao_delegada (tenant_id, emitido_em);
CREATE INDEX IF NOT EXISTS idx_emissao_delegada_tenant_operadora
    ON public.emissao_delegada (tenant_id, operadora_tenant_id);
ALTER TABLE public.emissao_delegada ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.emissao_delegada FORCE ROW LEVEL SECURITY;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'emissao_delegada' AND policyname = 'tenant_isolation_emissao_delegada') THEN
        CREATE POLICY tenant_isolation_emissao_delegada ON public.emissao_delegada
            USING (tenant_id = public.get_current_tenant_id());
    END IF;
END $$;
ALTER TABLE public.documento_emitido ADD COLUMN IF NOT EXISTS emissor_tenant_id uuid;
ALTER TABLE public.documento_emitido ADD COLUMN IF NOT EXISTS emissor_snapshot jsonb;
ALTER TABLE public.emissao_uso ADD COLUMN IF NOT EXISTS emissor_tenant_id uuid;

-- V049: instrutores designados por parceria (vazio = todos os ativos da EAMA)
CREATE TABLE IF NOT EXISTS public.vinculo_emissao_instrutor (
    id           uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    vinculo_id   uuid NOT NULL REFERENCES public.vinculo_emissao(id) ON DELETE CASCADE,
    instrutor_id uuid NOT NULL REFERENCES public.instrutor(id) ON DELETE CASCADE,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_vinculo_emissao_instrutor UNIQUE (vinculo_id, instrutor_id)
);
CREATE INDEX IF NOT EXISTS idx_vinculo_emissao_instrutor_vinculo
    ON public.vinculo_emissao_instrutor (vinculo_id);
ALTER TABLE public.vinculo_emissao_instrutor ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vinculo_emissao_instrutor FORCE ROW LEVEL SECURITY;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'vinculo_emissao_instrutor' AND policyname = 'vinculo_emissao_instrutor_partes') THEN
        CREATE POLICY vinculo_emissao_instrutor_partes ON public.vinculo_emissao_instrutor
            USING (EXISTS (
                SELECT 1 FROM public.vinculo_emissao v
                WHERE v.id = vinculo_id
                  AND (v.tenant_operador_id = public.get_current_tenant_id()
                    OR v.tenant_emissor_id = public.get_current_tenant_id())
            ));
    END IF;
END $$;

-- V050: grandfathering — quem já emite/está configurado entra habilitado como emissora
UPDATE public.tenant t
   SET emissora_habilitada = true
 WHERE emissora_habilitada = false
   AND (EXISTS (SELECT 1 FROM public.documento_emitido d WHERE d.tenant_id = t.id)
     OR t.marinha_email IS NOT NULL);

-- V051: auditoria global (tenant_id NULL) — merge de contas por CPF do portal.
-- Policy INSERT-ONLY: grava linhas globais sem expô-las em SELECT de tenant.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public' AND tablename = 'auditoria'
          AND policyname = 'auditoria_global_insert'
    ) THEN
        CREATE POLICY auditoria_global_insert ON public.auditoria
            FOR INSERT
            WITH CHECK (tenant_id IS NULL);
    END IF;
END $$;

-- V045: billing manual assistido — fatura mensal da assinatura (PIX plataforma)
CREATE TABLE IF NOT EXISTS public.fatura (
    id              uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id       uuid NOT NULL REFERENCES public.tenant(id),
    competencia     date NOT NULL,
    plano_nome      varchar(60) NOT NULL,
    valor           numeric(10,2) NOT NULL CHECK (valor > 0),
    status          varchar(20) NOT NULL DEFAULT 'ABERTA'
                    CHECK (status IN ('ABERTA', 'EM_CONFERENCIA', 'PAGA', 'CANCELADA')),
    vencimento      date NOT NULL,
    pix_copia_e_cola text,
    txid_informado  varchar(80),
    informado_em    timestamptz,
    pago_em         timestamptz,
    decidido_por    uuid,
    observacao      varchar(300),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fatura_tenant_competencia_uq UNIQUE (tenant_id, competencia)
);
CREATE INDEX IF NOT EXISTS idx_fatura_tenant_status ON public.fatura (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_fatura_status_vencimento ON public.fatura (status, vencimento);
ALTER TABLE public.fatura ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_fatura ON public.fatura;
CREATE POLICY tenant_isolation_fatura ON public.fatura
    USING (tenant_id = public.get_current_tenant_id());

-- V044: exclusão de empresa (agendamento do expurgo + tombstone)
ALTER TABLE public.tenant ADD COLUMN IF NOT EXISTS exclusao_agendada_em timestamptz;
ALTER TABLE public.tenant ADD COLUMN IF NOT EXISTS excluido_em timestamptz;

-- V042: RLS na tabela tenant (backstop p/ segredos por loja: smtp_password etc.)
-- Contexto nulo (signup/login/marketplace/jobs) = liberado; tenant-scoped = só a
-- própria linha; superadmin = GUC app.unrestricted (TenantAwareDataSource).
-- A marketplace_public_read sai: vitrine roda sem contexto (ramo nulo).
DROP POLICY IF EXISTS marketplace_public_read ON public.tenant;
ALTER TABLE public.tenant ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_tenant ON public.tenant;
CREATE POLICY tenant_isolation_tenant ON public.tenant
    USING (
        public.get_current_tenant_id() IS NULL
        OR id = public.get_current_tenant_id()
        OR current_setting('app.unrestricted', true) = 'true'
    );

-- F3 instrutores (V011): cadastro p/ o Atestado de Demonstração 5-B-1
CREATE TABLE IF NOT EXISTS public.instrutor (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    nome varchar(200) NOT NULL,
    rg varchar(30), orgao_emissor varchar(30), cpf varchar(20), cha varchar(60),
    ativo boolean DEFAULT true NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL, updated_at timestamptz DEFAULT now() NOT NULL
);
ALTER TABLE public.instrutor ADD COLUMN IF NOT EXISTS data_emissao date;
ALTER TABLE public.instrutor ADD COLUMN IF NOT EXISTS assinatura_s3_key varchar(500);
CREATE INDEX IF NOT EXISTS idx_instrutor_tenant ON public.instrutor (tenant_id, ativo);
ALTER TABLE public.instrutor ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.instrutor FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_instrutor ON public.instrutor;
CREATE POLICY tenant_isolation_instrutor ON public.instrutor USING ((tenant_id = public.get_current_tenant_id()));

-- F2.4: aceite/assinatura no balcão (evidências)
CREATE TABLE IF NOT EXISTS public.reserva_aceite (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    reserva_id uuid NOT NULL REFERENCES public.reserva(id) ON DELETE CASCADE,
    operador_id uuid,
    metodo varchar(20) NOT NULL,
    assinatura_s3_key varchar(500), hash_sha256 varchar(64),
    ip varchar(64), user_agent text,
    origem varchar(20) DEFAULT 'BALCAO' NOT NULL,
    aceito_em timestamptz DEFAULT now() NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL
);
ALTER TABLE public.reserva_aceite ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reserva_aceite FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_reserva_aceite ON public.reserva_aceite;
CREATE POLICY tenant_isolation_reserva_aceite ON public.reserva_aceite USING ((tenant_id = public.get_current_tenant_id()));

-- F2.7: claim-token de ativação de conta do cliente (balcão)
CREATE TABLE IF NOT EXISTS public.cliente_claim_token (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    cliente_id uuid NOT NULL REFERENCES public.cliente(id) ON DELETE CASCADE,
    token varchar(64) NOT NULL,
    temporary_password_hash varchar(255) NOT NULL,
    canais varchar(100),
    expira_em timestamptz NOT NULL,
    usado_em timestamptz,
    ativo boolean DEFAULT true NOT NULL,
    criado_por uuid,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT cliente_claim_token_token_uq UNIQUE (token)
);
CREATE INDEX IF NOT EXISTS idx_cliente_claim_token_cliente ON public.cliente_claim_token (cliente_id, ativo);
ALTER TABLE public.cliente_claim_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cliente_claim_token FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_cliente_claim_token ON public.cliente_claim_token;
-- Carve-out p/ validação pública por token (V009): sem tenant no contexto, permite
-- (autorização = conhecimento do token); com contexto, isola por tenant.
CREATE POLICY tenant_isolation_cliente_claim_token ON public.cliente_claim_token
  USING (CASE WHEN public.get_current_tenant_id() IS NULL THEN true
              ELSE (tenant_id = public.get_current_tenant_id()) END);

-- Re-grant (tabelas criadas aqui, se houver)
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO jetski_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO jetski_app;
EOSQL
echo -e "${GREEN}   OK - Schema da Fase 1 garantido!${NC}"

# 7.2 Corrigir schema da tabela convite (alinhar com entidade JPA)
echo -e "${YELLOW}7.2 Corrigindo schema da tabela convite...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- Renomear accepted_at para activated_at (se existir)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'convite' AND column_name = 'accepted_at') THEN
        ALTER TABLE convite RENAME COLUMN accepted_at TO activated_at;
    END IF;
END $$;

-- Renomear temporary_password para temporary_password_hash (se existir)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'convite' AND column_name = 'temporary_password') THEN
        ALTER TABLE convite RENAME COLUMN temporary_password TO temporary_password_hash;
    END IF;
END $$;

-- Adicionar colunas que faltam
ALTER TABLE convite ADD COLUMN IF NOT EXISTS usuario_id UUID;
ALTER TABLE convite ADD COLUMN IF NOT EXISTS password_reset_link TEXT;
ALTER TABLE convite ADD COLUMN IF NOT EXISTS email_sent_count INTEGER NOT NULL DEFAULT 0;

-- Adicionar FK para usuario_id (se nao existir)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_name = 'convite_usuario_id_fkey' AND table_name = 'convite') THEN
        ALTER TABLE convite ADD CONSTRAINT convite_usuario_id_fkey
            FOREIGN KEY (usuario_id) REFERENCES usuario(id);
    END IF;
END $$;
EOSQL
echo -e "${GREEN}   OK - Schema da tabela convite corrigido!${NC}"

# 7.3 Corrigir politicas RLS para permitir signup (INSERT sem tenant_id)
echo -e "${YELLOW}7.3 Corrigindo politicas RLS para signup...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- Remover política ALL e criar políticas específicas para assinatura
DROP POLICY IF EXISTS tenant_isolation_assinatura ON assinatura;
DROP POLICY IF EXISTS assinatura_tenant_select ON assinatura;
DROP POLICY IF EXISTS assinatura_tenant_update ON assinatura;
DROP POLICY IF EXISTS assinatura_tenant_delete ON assinatura;
DROP POLICY IF EXISTS assinatura_tenant_insert ON assinatura;

-- Política para SELECT (permite ver todas quando não há tenant, ou filtrado por tenant)
CREATE POLICY assinatura_tenant_select ON assinatura
  FOR SELECT
  USING (tenant_id = COALESCE(current_setting('app.tenant_id', true)::uuid, tenant_id));

-- Política para UPDATE/DELETE (restrito por tenant)
CREATE POLICY assinatura_tenant_update ON assinatura
  FOR UPDATE
  USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

CREATE POLICY assinatura_tenant_delete ON assinatura
  FOR DELETE
  USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Política para INSERT (permite sempre - signup cria assinatura sem tenant na sessão)
CREATE POLICY assinatura_tenant_insert ON assinatura
  FOR INSERT
  WITH CHECK (true);

-- =====================================================
-- Corrigir políticas RLS para fuel_policy (signup também cria isso)
-- =====================================================
DROP POLICY IF EXISTS tenant_isolation_fuel_policy ON fuel_policy;
DROP POLICY IF EXISTS fuel_policy_tenant_select ON fuel_policy;
DROP POLICY IF EXISTS fuel_policy_tenant_update ON fuel_policy;
DROP POLICY IF EXISTS fuel_policy_tenant_delete ON fuel_policy;
DROP POLICY IF EXISTS fuel_policy_tenant_insert ON fuel_policy;

CREATE POLICY fuel_policy_tenant_select ON fuel_policy
  FOR SELECT
  USING (tenant_id = COALESCE(current_setting('app.tenant_id', true)::uuid, tenant_id));

CREATE POLICY fuel_policy_tenant_update ON fuel_policy
  FOR UPDATE
  USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

CREATE POLICY fuel_policy_tenant_delete ON fuel_policy
  FOR DELETE
  USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

CREATE POLICY fuel_policy_tenant_insert ON fuel_policy
  FOR INSERT
  WITH CHECK (true);
EOSQL
echo -e "${GREEN}   OK - Politicas RLS corrigidas!${NC}"

# 7.4 Corrigir politicas RLS para marketplace publico (leitura sem tenant)
echo -e "${YELLOW}7.4 Corrigindo politicas RLS para marketplace publico...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- =====================================================
-- Corrigir tenant_isolation_modelo para tratar string vazia
-- HikariCP pode reusar conexoes com app.tenant_id = ''
-- NULLIF converte '' para NULL, evitando erro de cast UUID
-- =====================================================
DROP POLICY IF EXISTS tenant_isolation_modelo ON modelo;

CREATE POLICY tenant_isolation_modelo ON modelo
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    );

COMMENT ON POLICY tenant_isolation_modelo ON modelo IS 'Tenant isolation using NULLIF for safe UUID handling';

-- =====================================================
-- Politica para leitura publica do marketplace (modelo)
-- Permite SELECT quando modelo e tenant estao marcados para exibicao
-- =====================================================
DROP POLICY IF EXISTS marketplace_public_read ON modelo;

CREATE POLICY marketplace_public_read ON modelo
    FOR SELECT
    USING (
        exibir_no_marketplace = true
        AND ativo = true
        AND EXISTS (
            SELECT 1 FROM tenant t
            WHERE t.id = modelo.tenant_id
            AND t.status = 'ATIVO'
            AND t.exibir_no_marketplace = true
        )
    );

COMMENT ON POLICY marketplace_public_read ON modelo IS 'Allows public read access to marketplace-visible models';

-- =====================================================
-- Politica para leitura publica do marketplace (tenant)
-- Permite SELECT de tenants marcados para exibicao
-- =====================================================
DROP POLICY IF EXISTS marketplace_public_read ON tenant;

CREATE POLICY marketplace_public_read ON tenant
    FOR SELECT
    USING (
        exibir_no_marketplace = true
        AND status = 'ATIVO'
    );

COMMENT ON POLICY marketplace_public_read ON tenant IS 'Allows public read access to marketplace-visible tenants';
EOSQL
echo -e "${GREEN}   OK - Politicas RLS do marketplace configuradas!${NC}"

# 7.5 Atualizar dados seed para marketplace (tenant ACME)
echo -e "${YELLOW}7.5 Configurando dados do marketplace...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- Atualizar tenant ACME com dados de contato e marketplace
UPDATE tenant SET
    whatsapp = '5548999999999',
    cidade = 'Florianópolis',
    uf = 'SC',
    exibir_no_marketplace = true,
    prioridade_marketplace = 50,
    marinha_email = 'capitania.dev@example.com',
    pix_chave = '65455888000100',
    branding = '{"cor_primaria": "#0066CC", "cor_secundaria": "#C9A24B"}'::jsonb
WHERE slug = 'acme';

-- V050: emissão própria exige emissora_habilitada — o seed de dev entra
-- habilitado (com capitania) para o fluxo de emissão continuar funcionando
UPDATE tenant SET
    emissora_habilitada = true,
    eama_registro = 'EAMA-DEV-SEED',
    capitania_id = (SELECT id FROM capitania WHERE codigo = 'CPSC')
WHERE slug = 'acme';

-- Marcar modelos como visiveis no marketplace
UPDATE modelo SET exibir_no_marketplace = true WHERE ativo = true;
EOSQL
echo -e "${GREEN}   OK - Dados do marketplace configurados!${NC}"

# 7.6 Configurar politicas RLS para despesa_operacional
echo -e "${YELLOW}7.6 Configurando politicas RLS para despesa_operacional...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- Verificar se a tabela existe antes de criar politicas
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'despesa_operacional') THEN
        -- Drop politica existente
        DROP POLICY IF EXISTS tenant_isolation_despesa_operacional ON despesa_operacional;

        -- Criar politica com NULLIF para tratar string vazia
        CREATE POLICY tenant_isolation_despesa_operacional ON despesa_operacional
            FOR ALL
            USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

        COMMENT ON POLICY tenant_isolation_despesa_operacional ON despesa_operacional IS 'Tenant isolation using NULLIF for safe UUID handling';
    END IF;
END $$;
EOSQL
echo -e "${GREEN}   OK - Politicas RLS do despesa_operacional configuradas!${NC}"

# 7.8 Configurar politicas RLS para modelo_midia
echo -e "${YELLOW}7.8 Configurando politicas RLS para modelo_midia...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- Verificar se a tabela existe antes de criar politicas
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'modelo_midia') THEN
        -- Habilitar RLS
        ALTER TABLE modelo_midia ENABLE ROW LEVEL SECURITY;
        ALTER TABLE modelo_midia FORCE ROW LEVEL SECURITY;

        -- Drop politicas existentes
        DROP POLICY IF EXISTS tenant_isolation_modelo_midia ON modelo_midia;
        DROP POLICY IF EXISTS marketplace_public_read ON modelo_midia;

        -- Politica de isolamento por tenant
        CREATE POLICY tenant_isolation_modelo_midia ON modelo_midia
            FOR ALL
            USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

        -- Politica de leitura publica para marketplace
        CREATE POLICY marketplace_public_read ON modelo_midia
            FOR SELECT
            USING (
                EXISTS (
                    SELECT 1 FROM modelo m
                    JOIN tenant t ON m.tenant_id = t.id
                    WHERE m.id = modelo_midia.modelo_id
                    AND m.ativo = true
                    AND m.exibir_no_marketplace = true
                    AND t.status = 'ATIVO'
                    AND t.exibir_no_marketplace = true
                )
            );
    END IF;
END $$;
EOSQL
echo -e "${GREEN}   OK - Politicas RLS do modelo_midia configuradas!${NC}"

# 7.9 Configurar politicas RLS para presenca_vendedor
echo -e "${YELLOW}7.9 Configurando politicas RLS para presenca_vendedor...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- Verificar se a tabela existe antes de criar politicas
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'presenca_vendedor') THEN
        -- Habilitar RLS
        ALTER TABLE presenca_vendedor ENABLE ROW LEVEL SECURITY;
        ALTER TABLE presenca_vendedor FORCE ROW LEVEL SECURITY;

        -- Drop politica existente
        DROP POLICY IF EXISTS tenant_isolation_presenca_vendedor ON presenca_vendedor;

        -- Criar politica com NULLIF para tratar string vazia
        CREATE POLICY tenant_isolation_presenca_vendedor ON presenca_vendedor
            FOR ALL
            USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

        COMMENT ON POLICY tenant_isolation_presenca_vendedor ON presenca_vendedor IS 'Tenant isolation using NULLIF for safe UUID handling';
    END IF;
END $$;
EOSQL
echo -e "${GREEN}   OK - Politicas RLS do presenca_vendedor configuradas!${NC}"

# 7.10 Atualizar vendedores com diaria_base e PIX de exemplo
echo -e "${YELLOW}7.10 Atualizando vendedores com diaria_base e PIX...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- Atualizar vendedores existentes com valores de diaria_base e PIX
UPDATE vendedor SET
    diaria_base = 100.00,
    chave_pix = '123.456.789-00',
    tipo_chave_pix = 'CPF'
WHERE diaria_base IS NULL OR diaria_base = 0;

-- Atualizar vendedor com tipo diferente de PIX para teste
UPDATE vendedor SET
    chave_pix = 'vendedor@acme.com.br',
    tipo_chave_pix = 'EMAIL'
WHERE nome LIKE '%Parceiro%' OR nome LIKE '%Agencia%'
    AND (chave_pix IS NULL OR chave_pix = '');
EOSQL
echo -e "${GREEN}   OK - Vendedores atualizados com diaria_base e PIX!${NC}"

# 7.10.1 Configurar politicas RLS para pagamento_vendedor
echo -e "${YELLOW}7.10.1 Configurando politicas RLS para pagamento_vendedor...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- Verificar se a tabela existe antes de criar politicas
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'pagamento_vendedor') THEN
        -- Habilitar RLS
        ALTER TABLE pagamento_vendedor ENABLE ROW LEVEL SECURITY;
        ALTER TABLE pagamento_vendedor FORCE ROW LEVEL SECURITY;

        -- Drop politica existente
        DROP POLICY IF EXISTS tenant_isolation_pagamento_vendedor ON pagamento_vendedor;

        -- Criar politica com NULLIF para tratar string vazia
        CREATE POLICY tenant_isolation_pagamento_vendedor ON pagamento_vendedor
            FOR ALL
            USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

        COMMENT ON POLICY tenant_isolation_pagamento_vendedor ON pagamento_vendedor IS 'Tenant isolation using NULLIF for safe UUID handling';
    END IF;
END $$;
EOSQL
echo -e "${GREEN}   OK - Politicas RLS do pagamento_vendedor configuradas!${NC}"

# 7.10.2 Configurar politicas RLS para bonus_vendedor
echo -e "${YELLOW}7.10.2 Configurando politicas RLS para bonus_vendedor...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- Verificar se a tabela existe antes de criar politicas
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'bonus_vendedor') THEN
        -- Habilitar RLS
        ALTER TABLE bonus_vendedor ENABLE ROW LEVEL SECURITY;
        ALTER TABLE bonus_vendedor FORCE ROW LEVEL SECURITY;

        -- Drop politica existente
        DROP POLICY IF EXISTS tenant_isolation_bonus_vendedor ON bonus_vendedor;

        -- Criar politica com NULLIF para tratar string vazia
        CREATE POLICY tenant_isolation_bonus_vendedor ON bonus_vendedor
            FOR ALL
            USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

        COMMENT ON POLICY tenant_isolation_bonus_vendedor ON bonus_vendedor IS 'Tenant isolation using NULLIF for safe UUID handling';
    END IF;
END $$;
EOSQL
echo -e "${GREEN}   OK - Politicas RLS do bonus_vendedor configuradas!${NC}"

# 7.11 Inserir midias de exemplo para modelos
echo -e "${YELLOW}7.11 Inserindo midias de exemplo...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- Inserir midias de exemplo para os modelos existentes
DO $$
DECLARE
    v_tenant_id UUID;
    v_modelo_id UUID;
BEGIN
    -- Obter tenant ACME
    SELECT id INTO v_tenant_id FROM tenant WHERE slug = 'acme' LIMIT 1;

    IF v_tenant_id IS NOT NULL THEN
        -- Para cada modelo, adicionar imagens de exemplo
        FOR v_modelo_id IN SELECT id FROM modelo WHERE tenant_id = v_tenant_id AND ativo = true LOOP
            -- Verificar se ja tem midias
            IF NOT EXISTS (SELECT 1 FROM modelo_midia WHERE modelo_id = v_modelo_id) THEN
                -- Inserir imagem principal (Sea-Doo)
                INSERT INTO modelo_midia (tenant_id, modelo_id, tipo, url, ordem, principal, titulo)
                VALUES (
                    v_tenant_id,
                    v_modelo_id,
                    'IMAGEM',
                    'https://sea-doo.brp.com/content/dam/global/en/sea-doo/my26/studio/recreation/gti/SEA-MY26-GTI-Standard-NoSS-M130-Bright-White-Neo-Mint-00038TB00-Studio-RSIDE-CU.png',
                    0,
                    true,
                    'Imagem principal'
                );

                -- Inserir segunda imagem
                INSERT INTO modelo_midia (tenant_id, modelo_id, tipo, url, ordem, principal, titulo)
                VALUES (
                    v_tenant_id,
                    v_modelo_id,
                    'IMAGEM',
                    'https://sea-doo.brp.com/content/dam/global/en/sea-doo/my26/studio/performance/rxt-x/SEA-MY26-RXT-X-X-Integrated100W-M325-Gulfstream-Blue-Premium-00022TD00-Studio-RSIDE-CU.png',
                    1,
                    false,
                    'Imagem secundaria'
                );

                -- Inserir video de exemplo (YouTube)
                INSERT INTO modelo_midia (tenant_id, modelo_id, tipo, url, thumbnail_url, ordem, principal, titulo)
                VALUES (
                    v_tenant_id,
                    v_modelo_id,
                    'VIDEO',
                    'https://www.youtube.com/embed/dQw4w9WgXcQ',
                    'https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg',
                    2,
                    false,
                    'Video demonstrativo'
                );
            END IF;
        END LOOP;
    END IF;
END $$;
EOSQL
echo -e "${GREEN}   OK - Midias de exemplo inseridas!${NC}"

# 7.12 Corrigir schema da tabela os_manutencao (alinhar com entidade JPA)
echo -e "${YELLOW}7.12 Corrigindo schema da tabela os_manutencao...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- =====================================================
-- Alinhar os_manutencao com entidade OSManutencao
-- (fallback caso migration V034 não tenha executado)
-- =====================================================

-- Renomear colunas existentes (se existirem com nome antigo)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'os_manutencao' AND column_name = 'descricao') THEN
        ALTER TABLE os_manutencao RENAME COLUMN descricao TO descricao_problema;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'os_manutencao' AND column_name = 'aberta_em') THEN
        ALTER TABLE os_manutencao RENAME COLUMN aberta_em TO dt_abertura;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'os_manutencao' AND column_name = 'fechada_em') THEN
        ALTER TABLE os_manutencao RENAME COLUMN fechada_em TO dt_conclusao;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'os_manutencao' AND column_name = 'responsavel_id') THEN
        ALTER TABLE os_manutencao RENAME COLUMN responsavel_id TO mecanico_id;
    END IF;
END $$;

-- Remover colunas antigas que foram substituídas
ALTER TABLE os_manutencao DROP COLUMN IF EXISTS custo_estimado;
ALTER TABLE os_manutencao DROP COLUMN IF EXISTS custo_real;

-- Remover FK antiga do responsavel_id
ALTER TABLE os_manutencao DROP CONSTRAINT IF EXISTS os_manutencao_responsavel_id_fkey;

-- Adicionar novas colunas requeridas pela entidade
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS diagnostico TEXT;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS solucao TEXT;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS pecas_json JSONB;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS valor_pecas DECIMAL(10,2) DEFAULT 0;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS valor_mao_obra DECIMAL(10,2) DEFAULT 0;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS valor_total DECIMAL(10,2) DEFAULT 0;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS horimetro_abertura DECIMAL(10,2);
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS horimetro_conclusao DECIMAL(10,2);
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS observacoes TEXT;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS dt_prevista_inicio TIMESTAMP WITH TIME ZONE;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS dt_inicio_real TIMESTAMP WITH TIME ZONE;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS dt_prevista_fim TIMESTAMP WITH TIME ZONE;

-- Atualizar tamanho da coluna prioridade
ALTER TABLE os_manutencao ALTER COLUMN prioridade TYPE VARCHAR(20);

-- Garantir que descricao_problema não seja null
UPDATE os_manutencao SET descricao_problema = 'Descrição não informada' WHERE descricao_problema IS NULL;

-- Remover check constraint antigo
ALTER TABLE os_manutencao DROP CONSTRAINT IF EXISTS os_manutencao_prioridade_check;

-- Corrigir constraints para usar valores lowercase (V035)
ALTER TABLE os_manutencao DROP CONSTRAINT IF EXISTS os_status_check;
ALTER TABLE os_manutencao DROP CONSTRAINT IF EXISTS os_tipo_check;
ALTER TABLE os_manutencao DROP CONSTRAINT IF EXISTS os_prioridade_check;

ALTER TABLE os_manutencao ADD CONSTRAINT os_status_check
    CHECK (status IN ('aberta', 'em_andamento', 'aguardando_pecas', 'concluida', 'cancelada'));
ALTER TABLE os_manutencao ADD CONSTRAINT os_tipo_check
    CHECK (tipo IN ('preventiva', 'corretiva', 'revisao'));
ALTER TABLE os_manutencao ADD CONSTRAINT os_prioridade_check
    CHECK (prioridade IN ('baixa', 'media', 'alta', 'urgente'));

-- Adicionar índice para mecanico_id
CREATE INDEX IF NOT EXISTS idx_os_manutencao_mecanico ON os_manutencao(tenant_id, mecanico_id);

COMMENT ON TABLE os_manutencao IS 'Ordens de Serviço de Manutenção - OSManutencao entity aligned';
EOSQL
echo -e "${GREEN}   OK - Schema da tabela os_manutencao corrigido!${NC}"

# 7.13 Configurar politicas RLS para os_manutencao
echo -e "${YELLOW}7.13 Configurando politicas RLS para os_manutencao...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- Verificar se a tabela existe antes de criar politicas
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'os_manutencao') THEN
        -- Habilitar RLS
        ALTER TABLE os_manutencao ENABLE ROW LEVEL SECURITY;
        ALTER TABLE os_manutencao FORCE ROW LEVEL SECURITY;

        -- Drop politica existente
        DROP POLICY IF EXISTS tenant_isolation_os_manutencao ON os_manutencao;

        -- Criar politica com NULLIF para tratar string vazia
        CREATE POLICY tenant_isolation_os_manutencao ON os_manutencao
            FOR ALL
            USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

        COMMENT ON POLICY tenant_isolation_os_manutencao ON os_manutencao IS 'Tenant isolation using NULLIF for safe UUID handling';
    END IF;
END $$;
EOSQL
echo -e "${GREEN}   OK - Politicas RLS do os_manutencao configuradas!${NC}"

# 7.14 Inserir OS de manutenção para jetski ULTRA001 (consistência com status MANUTENCAO)
echo -e "${YELLOW}7.14 Inserindo OS de manutencao para jetski ULTRA001...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- Desabilitar RLS temporariamente para inserir dados
SET LOCAL app.tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';

-- Inserir OS para o jetski ULTRA001 que está com status MANUTENCAO
-- Usando ON CONFLICT para evitar erro se já existir
INSERT INTO os_manutencao (
    id,
    tenant_id,
    jetski_id,
    tipo,
    prioridade,
    status,
    descricao_problema,
    diagnostico,
    horimetro_abertura,
    dt_abertura,
    dt_prevista_inicio,
    dt_prevista_fim,
    observacoes,
    created_at,
    updated_at
) VALUES (
    'e1111111-1111-1111-1111-111111111111',
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'd5555555-5555-5555-5555-555555555555',  -- ULTRA001
    'preventiva',
    'media',
    'em_andamento',
    'Revisão preventiva de 50 horas - verificação geral do motor e sistema de propulsão',
    'Motor em bom estado. Necessário troca de óleo e verificação de velas.',
    45.7,
    NOW() - INTERVAL '2 days',
    NOW() - INTERVAL '1 day',
    NOW() + INTERVAL '1 day',
    'Manutenção preventiva programada para Kawasaki Ultra 310. Jetski premium.',
    NOW(),
    NOW()
) ON CONFLICT (id) DO NOTHING;
EOSQL
echo -e "${GREEN}   OK - OS de manutencao inserida para ULTRA001!${NC}"

# 7.15 Configurar tabela despesa_manutencao
echo -e "${YELLOW}7.15 Configurando tabela despesa_manutencao...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << 'EOSQL' > /dev/null 2>&1
-- Criar tabela despesa_manutencao se não existir
CREATE TABLE IF NOT EXISTS despesa_manutencao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    os_manutencao_id UUID NOT NULL REFERENCES os_manutencao(id),
    dt_vencimento DATE NOT NULL,
    numero_parcela INTEGER NOT NULL DEFAULT 1,
    total_parcelas INTEGER NOT NULL DEFAULT 1,
    valor NUMERIC(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    aprovado_por INTEGER REFERENCES membro(id),
    aprovado_em TIMESTAMPTZ,
    pago_por INTEGER REFERENCES membro(id),
    pago_em TIMESTAMPTZ,
    referencia_pagamento VARCHAR(100),
    observacoes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT despesa_manutencao_status_check
        CHECK (status IN ('PENDENTE', 'APROVADA', 'REJEITADA', 'PAGA', 'CANCELADA')),
    CONSTRAINT despesa_manutencao_valor_positivo CHECK (valor > 0),
    CONSTRAINT despesa_manutencao_parcela_valida CHECK (numero_parcela > 0 AND numero_parcela <= total_parcelas)
);

-- Verificar se a tabela existe antes de criar politicas
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'despesa_manutencao') THEN
        -- Habilitar RLS
        ALTER TABLE despesa_manutencao ENABLE ROW LEVEL SECURITY;
        ALTER TABLE despesa_manutencao FORCE ROW LEVEL SECURITY;

        -- Drop politica existente
        DROP POLICY IF EXISTS tenant_isolation_despesa_manutencao ON despesa_manutencao;

        -- Criar politica com NULLIF para tratar string vazia
        CREATE POLICY tenant_isolation_despesa_manutencao ON despesa_manutencao
            FOR ALL
            USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

        COMMENT ON POLICY tenant_isolation_despesa_manutencao ON despesa_manutencao IS 'Tenant isolation using NULLIF for safe UUID handling';
    END IF;
END $$;

-- Criar indexes se não existirem
CREATE INDEX IF NOT EXISTS idx_despesa_manutencao_tenant ON despesa_manutencao(tenant_id);
CREATE INDEX IF NOT EXISTS idx_despesa_manutencao_os ON despesa_manutencao(os_manutencao_id);
CREATE INDEX IF NOT EXISTS idx_despesa_manutencao_vencimento ON despesa_manutencao(tenant_id, dt_vencimento);
CREATE INDEX IF NOT EXISTS idx_despesa_manutencao_status ON despesa_manutencao(tenant_id, status);
EOSQL
echo -e "${GREEN}   OK - Tabela despesa_manutencao configurada!${NC}"

# 7.16 Seed de demonstracao: GRU com idSessao sentinela DEMO-PAGO
# Permite testar "Verificar pagamento" + comprovante sem pagar outro PIX.
# O backend reconhece idSessao "DEMO-PAGO*" e devolve CONCLUIDO sintetico
# (idSessao reais expiram em poucas horas; por isso usamos o sentinela).
echo -e "${YELLOW}7.16 Inserindo reserva demo com GRU paga (verificar pagamento)...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} << EOSQL > /dev/null 2>&1
-- Cliente THALIA (CPF do pagamento real no PagTesouro)
INSERT INTO cliente (id, tenant_id, nome, documento, origem, status_conta, ativo, endereco, created_at, updated_at)
VALUES ('f0000000-0000-0000-0000-000000000001', '${TENANT_ID}', 'THALIA I G N', '23472084898',
        'BALCAO', 'SEM_LOGIN', true,
        '{"cep":"11095460","logradouro":"Rua A","numero":"1","bairro":"Alemoa","cidade":"Santos","uf":"SP"}'::jsonb,
        now(), now())
ON CONFLICT (id) DO UPDATE SET nome=EXCLUDED.nome, documento=EXCLUDED.documento, ativo=true;

-- Reserva de hoje (aparece na visao Dia da Agenda)
INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista,
        status, prioridade, sinal_pago, ativo, created_at, updated_at)
VALUES ('f0000000-0000-0000-0000-000000000002', '${TENANT_ID}',
        (SELECT id FROM modelo WHERE tenant_id='${TENANT_ID}' AND ativo=true ORDER BY created_at LIMIT 1),
        'f0000000-0000-0000-0000-000000000001',
        date_trunc('hour', now()) + interval '1 hour', date_trunc('hour', now()) + interval '3 hour',
        'PENDENTE', 'ALTA', false, true, now(), now())
ON CONFLICT (id) DO UPDATE SET data_inicio=EXCLUDED.data_inicio,
        data_fim_prevista=EXCLUDED.data_fim_prevista, status='PENDENTE', ativo=true;

-- Habilitacao EMA: GRU gerada com idSessao PAGO, mas gru_pago=false (p/ testar "Verificar pagamento")
INSERT INTO reserva_habilitacao (id, tenant_id, reserva_id, via, gru_numero, gru_valor, gru_pago,
        gru_id_sessao, gru_pix_copia_e_cola, gru_gerada_em, resolvida, created_at, updated_at)
VALUES ('f0000000-0000-0000-0000-000000000003', '${TENANT_ID}', 'f0000000-0000-0000-0000-000000000002',
        'EMA', '80893100021762026', 8.00, false,
        'DEMO-PAGO-55e08f7a',
        '00020101021226930014br.gov.bcb.pix-DEMO-PAGO',
        now(), false, now(), now())
ON CONFLICT (reserva_id) DO UPDATE SET gru_id_sessao=EXCLUDED.gru_id_sessao,
        gru_numero=EXCLUDED.gru_numero, gru_valor=EXCLUDED.gru_valor,
        gru_pago=false, gru_pago_em=NULL, gru_comprovante_s3_key=NULL, resolvida=false;
EOSQL
echo -e "${GREEN}   OK - Reserva demo (THALIA, GRU 80893100021762026, idSessao pago) criada!${NC}"

# 8. Verificar se realm foi importado automaticamente
echo -e "${YELLOW}8. Verificando realm Keycloak...${NC}"
if curl -sf "http://${KC_HOST}:${KC_PORT}/realms/${KC_REALM}" > /dev/null 2>&1; then
    echo -e "${GREEN}   OK - Realm ${KC_REALM} existe (importado automaticamente)!${NC}"
else
    echo -e "${YELLOW}   Realm nao encontrado, criando manualmente...${NC}"
    # Executar setup manual se necessario
    bash "$SCRIPT_DIR/infra/keycloak-setup/setup-keycloak-dev.sh" 2>/dev/null || true
fi

# 9. Configurar usuarios no Keycloak
echo -e "${YELLOW}9. Configurando usuarios no Keycloak...${NC}"
bash "$SCRIPT_DIR/infra/keycloak-setup/setup-keycloak-dev.sh"
echo -e "${GREEN}   OK - Usuarios configurados!${NC}"

# 10. Adicionar client mobile
echo -e "${YELLOW}10. Configurando client mobile...${NC}"
KEYCLOAK_URL="http://${KC_HOST}:${KC_PORT}" bash "$SCRIPT_DIR/infra/keycloak-setup/add-mobile-client-dev.sh" 2>/dev/null || true
echo -e "${GREEN}   OK - Client mobile configurado!${NC}"

# 11. Adicionar client backoffice
echo -e "${YELLOW}11. Configurando client backoffice...${NC}"
KEYCLOAK_URL="http://${KC_HOST}:${KC_PORT}" bash "$SCRIPT_DIR/infra/keycloak-setup/add-backoffice-client-dev.sh" 2>/dev/null || true
echo -e "${GREEN}   OK - Client backoffice configurado!${NC}"

# 11.1 Configurar URLs do túnel no client backoffice (se fornecido)
if [ -n "$PUBLIC_URL" ]; then
    echo -e "${YELLOW}11.1 Configurando URLs do túnel no Keycloak...${NC}"

    # Aguardar um pouco para garantir que o client foi criado
    sleep 3

    # Obter token admin (usando jq para parsing mais confiavel)
    KC_TOKEN=$(curl -s -X POST "http://${KC_HOST}:${KC_PORT}/realms/master/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      -d "username=${KC_ADMIN_USER}" \
      -d "password=${KC_ADMIN_PASSWORD}" \
      -d "grant_type=password" \
      -d "client_id=admin-cli" | jq -r '.access_token')

    if [ -n "$KC_TOKEN" ] && [ "$KC_TOKEN" != "null" ]; then
        # Obter UUID do client (usando jq)
        CLIENT_UUID=$(curl -s "http://${KC_HOST}:${KC_PORT}/admin/realms/${KC_REALM}/clients?clientId=jetski-backoffice" \
          -H "Authorization: Bearer $KC_TOKEN" | jq -r '.[0].id')

        if [ -n "$CLIENT_UUID" ] && [ "$CLIENT_UUID" != "null" ]; then
            # Obter client atual
            CURRENT_CLIENT=$(curl -s "http://${KC_HOST}:${KC_PORT}/admin/realms/${KC_REALM}/clients/$CLIENT_UUID" \
              -H "Authorization: Bearer $KC_TOKEN")

            # Adicionar URLs do túnel, manter client público e configurar PKCE
            UPDATED_CLIENT=$(echo "$CURRENT_CLIENT" | \
              jq --arg ngrok "$PUBLIC_URL" \
              '.redirectUris += [$ngrok + "/*", $ngrok + "/api/auth/callback/keycloak"] | .redirectUris |= unique |
               .webOrigins += [$ngrok] | .webOrigins |= unique |
               .publicClient = true |
               .attributes["pkce.code.challenge.method"] = "S256"')

            # Atualizar client
            HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "http://${KC_HOST}:${KC_PORT}/admin/realms/${KC_REALM}/clients/$CLIENT_UUID" \
              -H "Authorization: Bearer $KC_TOKEN" \
              -H "Content-Type: application/json" \
              -d "$UPDATED_CLIENT")

            if [ "$HTTP_CODE" = "204" ]; then
                echo -e "${GREEN}   OK - Túnel configurado no backoffice: $PUBLIC_URL${NC}"
            else
                echo -e "${YELLOW}   AVISO - Falha ao configurar túnel (HTTP $HTTP_CODE)${NC}"
            fi
        else
            echo -e "${YELLOW}   AVISO - Client jetski-backoffice nao encontrado${NC}"
        fi
    else
        echo -e "${YELLOW}   AVISO - Nao foi possivel obter token admin${NC}"
    fi
fi

# 12. Criar usuario multi-tenant
echo -e "${YELLOW}12. Criando usuario multi-tenant...${NC}"
KEYCLOAK_URL="http://${KC_HOST}:${KC_PORT}" bash "$SCRIPT_DIR/infra/keycloak-setup/create-multi-tenant-user-dev.sh" 2>/dev/null || true
echo -e "${GREEN}   OK - Usuario multi-tenant criado!${NC}"

# 12.1 Criar client de teste (com direct access grants para testes via curl/Postman)
echo -e "${YELLOW}12.1 Criando client de teste (jetski-test)...${NC}"
TEST_TOKEN=$(curl -s -X POST "http://${KC_HOST}:${KC_PORT}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=${KC_ADMIN_USER}" \
  -d "password=${KC_ADMIN_PASSWORD}" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -n "$TEST_TOKEN" ]; then
    # Verificar se client ja existe
    EXISTING_CLIENT=$(curl -s "http://${KC_HOST}:${KC_PORT}/admin/realms/${KC_REALM}/clients?clientId=jetski-test" \
      -H "Authorization: Bearer $TEST_TOKEN" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

    if [ -z "$EXISTING_CLIENT" ]; then
        # Criar client de teste
        curl -s -X POST "http://${KC_HOST}:${KC_PORT}/admin/realms/${KC_REALM}/clients" \
          -H "Authorization: Bearer $TEST_TOKEN" \
          -H "Content-Type: application/json" \
          -d '{
            "clientId": "jetski-test",
            "name": "Jetski Test Client",
            "description": "Client para testes via curl/Postman (direct access grants habilitado)",
            "enabled": true,
            "publicClient": true,
            "directAccessGrantsEnabled": true,
            "standardFlowEnabled": false,
            "implicitFlowEnabled": false,
            "serviceAccountsEnabled": false,
            "protocol": "openid-connect"
          }' > /dev/null 2>&1
        echo -e "${GREEN}   OK - Client jetski-test criado!${NC}"
    else
        echo -e "${GREEN}   OK - Client jetski-test ja existe!${NC}"
    fi
else
    echo -e "${YELLOW}   AVISO: Nao foi possivel criar client de teste${NC}"
fi

# 13. Criar mapeamentos de identity provider
echo -e "${YELLOW}13. Criando mapeamentos de identity provider...${NC}"

# Aguardar Keycloak estar pronto para admin
sleep 5

# Obter token admin
ADMIN_TOKEN=$(curl -s -X POST "http://${KC_HOST}:${KC_PORT}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=${KC_ADMIN_USER}" \
  -d "password=${KC_ADMIN_PASSWORD}" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -n "$ADMIN_TOKEN" ]; then
    # Incluir todos os usuarios: ACME + multi-tenant
    USERS=("admin@acme.com" "gerente@acme.com" "operador@acme.com" "vendedor@acme.com" "mecanico@acme.com" "gerente.multi@example.com")

    for EMAIL in "${USERS[@]}"; do
        # Buscar UUID no PostgreSQL (via docker exec)
        PG_USER_ID=$(docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} -t -c "SELECT id FROM usuario WHERE email = '${EMAIL}' LIMIT 1;" 2>/dev/null | xargs)

        if [ -n "$PG_USER_ID" ] && [ "$PG_USER_ID" != "" ]; then
            # Buscar UUID no Keycloak
            KC_USER_ID=$(curl -s -X GET "http://${KC_HOST}:${KC_PORT}/admin/realms/${KC_REALM}/users?username=${EMAIL}" \
              -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

            if [ -n "$KC_USER_ID" ]; then
                # Deletar mapeamento antigo e inserir novo (via docker exec)
                docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} -c "DELETE FROM usuario_identity_provider WHERE usuario_id = '${PG_USER_ID}' AND provider = 'keycloak';" > /dev/null 2>&1
                docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} -c "INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id, linked_at, created_at, updated_at) VALUES ('${PG_USER_ID}', 'keycloak', '${KC_USER_ID}', NOW(), NOW(), NOW());" > /dev/null 2>&1
                echo -e "   ${GREEN}OK${NC} - ${EMAIL} -> KC: ${KC_USER_ID:0:8}..."
            fi
        fi
    done
else
    echo -e "${YELLOW}   AVISO: Nao foi possivel obter token de admin (Keycloak ainda inicializando?)${NC}"
fi

# 14. Limpar cache Redis
echo -e "${YELLOW}14. Limpando cache Redis...${NC}"
docker compose exec -T redis redis-cli FLUSHDB > /dev/null 2>&1 || true
echo -e "${GREEN}   OK - Cache limpo!${NC}"

# 15. Aguardar backend ficar pronto
echo -e "${YELLOW}15. Aguardando backend...${NC}"
for i in {1..60}; do
    if curl -sf "http://localhost:8090/api/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}   OK - Backend pronto!${NC}"
        break
    fi
    printf "."
    sleep 2
done

# 16. Verificar saude dos servicos
echo -e "${YELLOW}16. Verificando saude dos servicos...${NC}"
echo ""
BACKEND_STATUS=$(curl -s http://localhost:8090/api/actuator/health | grep -o '"status":"[^"]*' | head -1 | cut -d'"' -f4)
echo "   Backend:   ${BACKEND_STATUS:-DOWN}"
echo "   Keycloak:  $(curl -s -o /dev/null -w '%{http_code}' http://${KC_HOST}:${KC_PORT}/realms/${KC_REALM})"
echo "   PostgreSQL: $(docker compose exec -T postgres pg_isready -U $PG_USER > /dev/null 2>&1 && echo 'UP' || echo 'DOWN')"
echo "   Redis:     $(docker compose exec -T redis redis-cli ping 2>/dev/null || echo 'DOWN')"
echo "   OPA:       $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8181/health)"
echo "   Frontend:  $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"

# 17. Exibir dados seed
echo ""
echo -e "${YELLOW}17. Dados seed carregados:${NC}"
echo "   Tenants:"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} -t -c "SELECT '      ' || slug || ' - ' || razao_social || CASE WHEN exibir_no_marketplace THEN ' [MARKETPLACE]' ELSE '' END FROM tenant;" 2>/dev/null || echo "      (nenhum)"
echo ""
echo "   Usuarios:"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} -t -c "SELECT '      ' || email || ' (' || nome || ')' FROM usuario LIMIT 5;" 2>/dev/null || echo "      (nenhum)"
echo ""
echo "   Modelos (marketplace):"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} -t -c "SELECT '      ' || nome || ' - R\$' || preco_base_hora || '/h' || CASE WHEN exibir_no_marketplace THEN ' [PUBLICO]' ELSE '' END FROM modelo LIMIT 5;" 2>/dev/null || echo "      (nenhum)"
echo ""
echo "   Jetskis:"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} -t -c "SELECT '      ' || serie || ' - ' || status FROM jetski LIMIT 5;" 2>/dev/null || echo "      (nenhum)"

echo ""
echo -e "${BLUE}========================================"
echo "  RESET FINALIZADO COM SUCESSO!"
echo -e "========================================${NC}"
echo ""
echo -e "${RED}IMPORTANTE: Após o reset, limpe os cookies do navegador!${NC}"
echo "   O reset recriou todos os usuários no Keycloak."
echo "   Se você estava logado antes, precisa:"
echo "   1. Abrir DevTools (F12) → Application → Cookies"
echo "   2. Limpar todos os cookies do site (ou fazer logout)"
echo "   3. Limpar sessionStorage (DevTools → Application → Session Storage)"
echo "   4. Fazer login novamente"
echo ""
if [ -n "$PUBLIC_URL" ]; then
    echo -e "${GREEN}URLs do ambiente DEV (Túnel):${NC}"
    echo "   - Frontend:   $PUBLIC_URL"
    echo "   - Backend:    $PUBLIC_URL/api"
    echo "   - Keycloak:   $PUBLIC_URL/realms/jetski-saas"
    echo ""
fi
echo "URLs do ambiente DEV (Local):"
echo "   - Frontend:   http://localhost:3001 (via nginx: http://localhost)"
echo "   - Marketplace: http://localhost/ (pagina publica)"
echo "   - Backend:    http://localhost:8090/api"
echo "   - Marketplace API: http://localhost/api/v1/public/marketplace/modelos"
echo "   - Keycloak:   http://localhost:8080 (admin/Mazuca@123)"
echo "   - PostgreSQL: localhost:5432 (jetski/dev123)"
echo "   - Redis:      localhost:6379"
echo "   - OPA:        http://localhost:8181"
echo "   - Swagger:    http://localhost:8090/api/swagger-ui.html"
echo ""
echo "Usuarios de teste:"
echo "   - admin@acme.com (senha: admin123) - ADMIN_TENANT"
echo "   - gerente@acme.com (senha: gerente123) - GERENTE"
echo "   - operador@acme.com (senha: operador123) - OPERADOR"
echo "   - vendedor@acme.com (senha: vendedor123) - VENDEDOR"
echo "   - mecanico@acme.com (senha: mecanico123) - MECANICO"
echo ""
if [ -z "$PUBLIC_URL" ]; then
    echo "Para expor na internet (ngrok):"
    echo "   ngrok http 80"
    echo "   # Depois execute: ./reset-ambiente-dev.sh https://xxx.ngrok-free.app"
    echo ""
    echo "Para expor na internet (cloudflare):"
    echo "   cloudflared tunnel --url http://localhost:80 run pegaojet"
    echo "   # Depois execute: ./reset-ambiente-dev.sh https://pegaojet.com.br"
    echo ""
fi
