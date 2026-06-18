#!/bin/bash

###############################################################################
# Reset Completo do Ambiente DEV (Docker Compose) - Pega o Jet
# (PostgreSQL porta 5432 + Keycloak porta 8080)
#
# Uso:
#   ./reset-ambiente-dev.sh                    # Usa ngrok padrao
#   ./reset-ambiente-dev.sh https://xxx.ngrok-free.app  # Com ngrok customizado
#   NGROK_URL=https://xxx.ngrok-free.app ./reset-ambiente-dev.sh
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

# URL do ngrok (argumento ou variavel de ambiente ou padrao)
DEFAULT_NGROK_URL="https://pegaojet.com.br"
NGROK_URL="${1:-${NGROK_URL:-$DEFAULT_NGROK_URL}}"

echo -e "${BLUE}========================================"
echo "  RESET AMBIENTE DEV (Docker) - Pega o Jet"
echo -e "========================================${NC}"
echo ""
echo "Configuracoes:"
echo "   PostgreSQL: ${PG_HOST}:${PG_PORT}"
echo "   Keycloak:   ${KC_HOST}:${KC_PORT}"
echo "   Database:   ${PG_DB}"
if [ -n "$NGROK_URL" ]; then
    echo -e "   ${GREEN}Ngrok:      ${NGROK_URL}${NC}"
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
if [ -n "$NGROK_URL" ]; then
    # Iniciar com variaveis do ngrok
    NEXTAUTH_URL="$NGROK_URL" \
    KEYCLOAK_ISSUER="$NGROK_URL/realms/jetski-saas" \
    JETSKI_FRONTEND_URL="$NGROK_URL" \
    JETSKI_EXTERNAL_URL="$NGROK_URL" \
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

ALTER TABLE public.cliente
    ADD COLUMN IF NOT EXISTS origem       varchar(20) NOT NULL DEFAULT 'PORTAL',
    ADD COLUMN IF NOT EXISTS status_conta varchar(20) NOT NULL DEFAULT 'SEM_LOGIN';

ALTER TABLE public.tenant
    ADD COLUMN IF NOT EXISTS marinha_email varchar(255),
    ADD COLUMN IF NOT EXISTS pix_chave     varchar(140);

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
    resolvida boolean DEFAULT false NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL, updated_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT reserva_habilitacao_reserva_uq UNIQUE (reserva_id)
);
ALTER TABLE public.reserva_habilitacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reserva_habilitacao FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_reserva_habilitacao ON public.reserva_habilitacao;
CREATE POLICY tenant_isolation_reserva_habilitacao ON public.reserva_habilitacao USING ((tenant_id = public.get_current_tenant_id()));

-- F3 docs (V010): campos p/ os anexos NORMAM-212 (preenchimento manual, sem OCR)
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS rg character varying(30);
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS orgao_emissor character varying(30);
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS nacionalidade character varying(60);
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS naturalidade character varying(120);
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS estrangeiro boolean DEFAULT false NOT NULL;
ALTER TABLE public.reserva_habilitacao ADD COLUMN IF NOT EXISTS usa_lentes boolean DEFAULT false NOT NULL;
ALTER TABLE public.reserva_habilitacao ADD COLUMN IF NOT EXISTS usa_aparelho boolean DEFAULT false NOT NULL;
ALTER TABLE public.reserva_habilitacao ADD COLUMN IF NOT EXISTS instrutor_id uuid;

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
    pix_chave = '65455888000100'
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

# 11.1 Configurar ngrok no client backoffice (se fornecido)
if [ -n "$NGROK_URL" ]; then
    echo -e "${YELLOW}11.1 Configurando ngrok no Keycloak...${NC}"

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

            # Adicionar URLs do ngrok, garantir cliente confidencial, e configurar PKCE
            UPDATED_CLIENT=$(echo "$CURRENT_CLIENT" | \
              jq --arg ngrok "$NGROK_URL" \
              '.redirectUris += [$ngrok + "/*", $ngrok + "/api/auth/callback/keycloak"] | .redirectUris |= unique |
               .webOrigins += [$ngrok] | .webOrigins |= unique |
               .publicClient = false |
               .clientAuthenticatorType = "client-secret" |
               .secret = "backoffice-secret" |
               .attributes["pkce.code.challenge.method"] = "S256"')

            # Atualizar client
            HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "http://${KC_HOST}:${KC_PORT}/admin/realms/${KC_REALM}/clients/$CLIENT_UUID" \
              -H "Authorization: Bearer $KC_TOKEN" \
              -H "Content-Type: application/json" \
              -d "$UPDATED_CLIENT")

            if [ "$HTTP_CODE" = "204" ]; then
                echo -e "${GREEN}   OK - Ngrok configurado no backoffice: $NGROK_URL${NC}"
            else
                echo -e "${YELLOW}   AVISO - Falha ao configurar ngrok (HTTP $HTTP_CODE)${NC}"
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
if [ -n "$NGROK_URL" ]; then
    echo -e "${GREEN}URLs do ambiente DEV (Ngrok):${NC}"
    echo "   - Frontend:   $NGROK_URL"
    echo "   - Backend:    $NGROK_URL/api"
    echo "   - Keycloak:   $NGROK_URL/realms/jetski-saas"
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
if [ -z "$NGROK_URL" ]; then
    echo "Para expor na internet (ngrok):"
    echo "   ngrok http 80"
    echo "   # Depois execute: ./reset-ambiente-dev.sh https://xxx.ngrok-free.app"
    echo ""
    echo "Para expor na internet (cloudflare):"
    echo "   cloudflared tunnel --url http://localhost:80 run pegaojet"
    echo "   # Depois execute: ./reset-ambiente-dev.sh https://pegaojet.com.br"
    echo ""
fi
