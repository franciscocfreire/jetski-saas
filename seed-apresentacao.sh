#!/bin/bash

###############################################################################
# Seed de APRESENTAÇÃO (dev) - MeuJet
#
# Sobrepõe uma persona fictícia coerente ao tenant de dev (acme) para que as
# capturas de tela da apresentação à Capitania não mostrem "ACME", "teste",
# "example.com" nem dados de pessoa real.
#
# É idempotente e serve também para REARMAR a demonstração: zera a emissão
# anterior da reserva de demo, devolve a GRU ao estado "gerada, não paga" e
# recoloca a reserva no dia de hoje. Rode antes de cada sessão de capturas.
#
# NÃO substitui o ./reset-ambiente-dev.sh — rode este DEPOIS do reset.
#
# Uso:
#   ./seed-apresentacao.sh
#   MARINHA_EMAIL=voce@gmail.com ./seed-apresentacao.sh   # ofício vai p/ sua caixa
#
# Para desfazer: ./reset-ambiente-dev.sh (volta ao seed de dev normal).
###############################################################################

set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

PG_USER="jetski"
PG_DB="jetski_dev"
KC_URL="http://localhost:8080"
KC_REALM="jetski-saas"
KC_ADMIN_USER="admin"
KC_ADMIN_PASSWORD="Mazuca@123"
TENANT_SLUG="acme"          # slug original do seed de dev (só p/ referência)
TENANT_ID="a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"

# IDs fixos do seed de demonstração (os mesmos do reset-ambiente-dev.sh 7.16)
CLIENTE_ID="f0000000-0000-0000-0000-000000000001"
RESERVA_ID="f0000000-0000-0000-0000-000000000002"
HABILITACAO_ID="f0000000-0000-0000-0000-000000000003"
INSTRUTOR_ID="f0000000-0000-0000-0000-000000000010"

# --- Persona fictícia -------------------------------------------------------
# Tudo inventado. CPFs e CNPJ têm dígitos verificadores válidos (o sistema
# valida o formato), mas não correspondem a pessoa ou empresa existente.

EMPRESA_RAZAO="Ilha Sul Náutica Ltda."
# O slug aparece embaixo do nome da empresa na barra lateral — em TODA captura.
EMPRESA_SLUG="ilha-sul-nautica"
EMPRESA_CNPJ="41.586.720/0001-68"
EMPRESA_CIDADE="Florianópolis"
EMPRESA_UF="SC"
EMPRESA_WHATSAPP="5548988140072"
CAPITANIA_CODIGO="CPSC"

EAMA_REGISTRO="047/2024-CPSC"
EAMA_VALIDADE="2027-12-31"
RESPONSAVEL_NOME="Rodrigo Menezes Sampaio"
RESPONSAVEL_TELEFONE="(48) 3025-4180"
EMAIL_OFICIAL="eama@ilhasulnautica.com.br"

# Destinatário do ofício. Em dev tudo cai no Mailpit (http://localhost:8025)
# independente do endereço; troque por um e-mail real se quiser capturar a
# mensagem recebida em uma caixa de verdade (slide 17).
MARINHA_EMAIL="${MARINHA_EMAIL:-capitania.sc@ilhasulnautica.com.br}"

INSTRUTOR_NOME="Marcelo Tavares Brandão"
INSTRUTOR_CPF="612.480.357-71"
INSTRUTOR_RG="4.892.117"
INSTRUTOR_ORGAO="SSP/SC"
INSTRUTOR_CHA="MTA-SC-014857"
INSTRUTOR_EMISSAO="2019-03-14"

LOCATARIO_NOME="Helena Andrade Vasconcelos"
LOCATARIO_CPF="84721590350"
LOCATARIO_NASCIMENTO="1994-06-22"
LOCATARIO_RG="38.914.552-7"
LOCATARIO_ORGAO="SSP/SC"
LOCATARIO_EMAIL="helena.vasconcelos@exemplo.com.br"
LOCATARIO_TELEFONE="(48) 99812-4470"

# GRU fictícia, mesmo formato da real (17 dígitos). O idSessao continua com o
# sentinela DEMO-PAGO — é ele, e não o número, que faz "Verificar pagamento"
# devolver CONCLUÍDO sem pagar PIX de novo (GruClient.java:295).
GRU_NUMERO="80893100047512026"
GRU_VALOR="8.00"
GRU_ID_SESSAO="DEMO-PAGO-apresentacao"

OPERADOR_NOME="Juliana Prado Loureiro"
GERENTE_NOME="$RESPONSAVEL_NOME"

# ---------------------------------------------------------------------------

echo -e "${BLUE}=== Seed de apresentação (dev) ===${NC}"

if ! docker compose ps postgres 2>/dev/null | grep -q "Up\|running"; then
    echo -e "${RED}Postgres não está de pé. Suba o ambiente antes (docker compose up -d).${NC}"
    exit 1
fi

echo -e "${YELLOW}1. Empresa, credenciamento EAMA e responsável...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} -v ON_ERROR_STOP=1 <<EOSQL > /dev/null
UPDATE tenant SET
    slug                   = '${EMPRESA_SLUG}',
    razao_social           = '${EMPRESA_RAZAO}',
    cnpj                   = '${EMPRESA_CNPJ}',
    cidade                 = '${EMPRESA_CIDADE}',
    uf                     = '${EMPRESA_UF}',
    whatsapp               = '${EMPRESA_WHATSAPP}',
    marinha_email          = '${MARINHA_EMAIL}',
    emissora_habilitada    = true,
    eama_registro          = '${EAMA_REGISTRO}',
    eama_registro_validade = DATE '${EAMA_VALIDADE}',
    responsavel_nome       = '${RESPONSAVEL_NOME}',
    telefone               = '${RESPONSAVEL_TELEFONE}',
    email_oficial          = '${EMAIL_OFICIAL}',
    capitania_id           = (SELECT id FROM capitania WHERE codigo = '${CAPITANIA_CODIGO}'),
    updated_at             = now()
WHERE id = '${TENANT_ID}';
EOSQL
echo -e "${GREEN}   OK - ${EMPRESA_RAZAO} (slug ${EMPRESA_SLUG}, EAMA ${EAMA_REGISTRO}, ${CAPITANIA_CODIGO})${NC}"

echo -e "${YELLOW}2. Instrutor credenciado (assina o Anexo 5-B-1)...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} -v ON_ERROR_STOP=1 <<EOSQL > /dev/null
-- Só um instrutor ativo, para o combo do balcão não expor "Instrutor de teste"
UPDATE instrutor SET ativo = false WHERE tenant_id = '${TENANT_ID}';

INSERT INTO instrutor (id, tenant_id, nome, cpf, rg, orgao_emissor, cha, data_emissao, ativo)
VALUES ('${INSTRUTOR_ID}', '${TENANT_ID}', '${INSTRUTOR_NOME}', '${INSTRUTOR_CPF}',
        '${INSTRUTOR_RG}', '${INSTRUTOR_ORGAO}', '${INSTRUTOR_CHA}',
        DATE '${INSTRUTOR_EMISSAO}', true)
ON CONFLICT (id) DO UPDATE SET
    nome = EXCLUDED.nome, cpf = EXCLUDED.cpf, rg = EXCLUDED.rg,
    orgao_emissor = EXCLUDED.orgao_emissor, cha = EXCLUDED.cha,
    data_emissao = EXCLUDED.data_emissao, ativo = true, updated_at = now();
EOSQL
echo -e "${GREEN}   OK - ${INSTRUTOR_NOME} (CHA ${INSTRUTOR_CHA})${NC}"

echo -e "${YELLOW}3. Locatária fictícia...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} -v ON_ERROR_STOP=1 <<EOSQL > /dev/null
INSERT INTO cliente (id, tenant_id, nome, documento, data_nascimento, rg, orgao_emissor,
        nacionalidade, naturalidade, email, telefone, whatsapp, origem, status_conta,
        ativo, estrangeiro, endereco)
VALUES ('${CLIENTE_ID}', '${TENANT_ID}', '${LOCATARIO_NOME}', '${LOCATARIO_CPF}',
        DATE '${LOCATARIO_NASCIMENTO}', '${LOCATARIO_RG}', '${LOCATARIO_ORGAO}',
        'Brasileira', 'Florianópolis - SC', '${LOCATARIO_EMAIL}',
        '${LOCATARIO_TELEFONE}', '${LOCATARIO_TELEFONE}', 'BALCAO', 'SEM_LOGIN',
        true, false,
        '{"cep":"88062-105","logradouro":"Rua das Araucárias","numero":"240",
          "complemento":"apto 32","bairro":"Lagoa da Conceição",
          "cidade":"Florianópolis","uf":"SC"}'::jsonb)
ON CONFLICT (id) DO UPDATE SET
    nome = EXCLUDED.nome, documento = EXCLUDED.documento,
    data_nascimento = EXCLUDED.data_nascimento, rg = EXCLUDED.rg,
    orgao_emissor = EXCLUDED.orgao_emissor, nacionalidade = EXCLUDED.nacionalidade,
    naturalidade = EXCLUDED.naturalidade, email = EXCLUDED.email,
    telefone = EXCLUDED.telefone, whatsapp = EXCLUDED.whatsapp,
    endereco = EXCLUDED.endereco, ativo = true, updated_at = now();
EOSQL
echo -e "${GREEN}   OK - ${LOCATARIO_NOME} (CPF ${LOCATARIO_CPF})${NC}"

echo -e "${YELLOW}4. Rearmando a reserva de demonstração...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} -v ON_ERROR_STOP=1 <<EOSQL > /dev/null
-- Emissão anterior fora do caminho (o ledger de créditos NÃO é tocado: é
-- append-only por trigger, e o consumo antigo continua registrado).
DELETE FROM documento_emitido WHERE reserva_id = '${RESERVA_ID}';
DELETE FROM reserva_aceite    WHERE reserva_id = '${RESERVA_ID}';

INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista,
        status, prioridade, sinal_pago, ativo)
VALUES ('${RESERVA_ID}', '${TENANT_ID}',
        (SELECT id FROM modelo WHERE tenant_id = '${TENANT_ID}' AND ativo = true
         ORDER BY created_at LIMIT 1),
        '${CLIENTE_ID}',
        date_trunc('hour', now()) + interval '1 hour',
        date_trunc('hour', now()) + interval '3 hour',
        'RASCUNHO', 'ALTA', false, true)
ON CONFLICT (id) DO UPDATE SET
    cliente_id = EXCLUDED.cliente_id,
    data_inicio = EXCLUDED.data_inicio,
    data_fim_prevista = EXCLUDED.data_fim_prevista,
    status = 'RASCUNHO', ativo = true,
    documento_emitido_em = NULL, updated_at = now();

-- GRU gerada e AINDA NÃO paga: é assim que o slide 10 é capturado (gerar →
-- verificar pagamento → comprovante) sem pagar um PIX novo.
INSERT INTO reserva_habilitacao (id, tenant_id, reserva_id, via, gru_numero, gru_valor,
        gru_pago, gru_id_sessao, gru_pix_copia_e_cola, gru_gerada_em, resolvida)
VALUES ('${HABILITACAO_ID}', '${TENANT_ID}', '${RESERVA_ID}', 'EMA',
        '${GRU_NUMERO}', ${GRU_VALOR}, false, '${GRU_ID_SESSAO}',
        '00020101021226930014br.gov.bcb.pix-DEMO-PAGO', now(), false)
ON CONFLICT (reserva_id) DO UPDATE SET
    gru_numero = EXCLUDED.gru_numero, gru_valor = EXCLUDED.gru_valor,
    gru_id_sessao = EXCLUDED.gru_id_sessao,
    gru_pix_copia_e_cola = EXCLUDED.gru_pix_copia_e_cola,
    gru_gerada_em = now(), gru_pago = false, gru_pago_em = NULL,
    gru_comprovante_s3_key = NULL, resolvida = false, updated_at = now();
EOSQL
echo -e "${GREEN}   OK - Reserva de hoje, GRU ${GRU_NUMERO} pendente de pagamento${NC}"

echo -e "${YELLOW}5. Nomes de quem aparece no cabeçalho do sistema...${NC}"
docker compose exec -T postgres psql -U ${PG_USER} -d ${PG_DB} -v ON_ERROR_STOP=1 <<EOSQL > /dev/null
UPDATE usuario SET nome = '${OPERADOR_NOME}' WHERE email = 'operador@acme.com';
UPDATE usuario SET nome = '${GERENTE_NOME}'  WHERE email = 'gerente@acme.com';
EOSQL
echo -e "${GREEN}   OK - banco: ${OPERADOR_NOME}${NC}"

# O nome do canto superior vem do token do Keycloak, não do banco — sem isto a
# tela continua dizendo "Operador ACME" em toda captura.
kc_renomear() {
    local email="$1" nome="$2" token="$3"
    local first="${nome%% *}" last="${nome#* }"
    local uid
    uid=$(curl -s -H "Authorization: Bearer ${token}" \
        "${KC_URL}/admin/realms/${KC_REALM}/users?email=${email}&exact=true" \
        | python3 -c "import sys,json;u=json.load(sys.stdin);print(u[0]['id'] if u else '')" 2>/dev/null)
    [ -z "$uid" ] && return 1
    curl -s -o /dev/null -X PUT -H "Authorization: Bearer ${token}" \
        -H "Content-Type: application/json" \
        -d "{\"firstName\":\"${first}\",\"lastName\":\"${last}\"}" \
        "${KC_URL}/admin/realms/${KC_REALM}/users/${uid}"
}

KC_TOKEN=$(curl -s -X POST "${KC_URL}/realms/master/protocol/openid-connect/token" \
    -d "client_id=admin-cli" -d "username=${KC_ADMIN_USER}" \
    -d "password=${KC_ADMIN_PASSWORD}" -d "grant_type=password" \
    | python3 -c "import sys,json;print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null) || true

if [ -n "${KC_TOKEN}" ]; then
    kc_renomear "operador@acme.com" "${OPERADOR_NOME}" "${KC_TOKEN}" && \
        echo -e "${GREEN}   OK - Keycloak: ${OPERADOR_NOME}${NC}" || \
        echo -e "${YELLOW}   Keycloak: usuário operador@acme.com não encontrado${NC}"
    kc_renomear "gerente@acme.com" "${GERENTE_NOME}" "${KC_TOKEN}" && \
        echo -e "${GREEN}   OK - Keycloak: ${GERENTE_NOME}${NC}" || true
    echo -e "${YELLOW}   Saia e entre de novo no backoffice para o nome novo aparecer.${NC}"
else
    echo -e "${YELLOW}   Keycloak fora do ar — renomeie à mão em ${KC_URL}${NC}"
    echo -e "${YELLOW}   → realm ${KC_REALM} → Users → operador@acme.com → First/Last name${NC}"
fi

echo ""
echo -e "${BLUE}=== Pronto. Roteiro de capturas ===${NC}"
echo ""
echo -e "  Login:      ${GREEN}operador@acme.com${NC} / operador123"
echo -e "  Balcão:     abrir a reserva de hoje de ${GREEN}${LOCATARIO_NOME}${NC}"
echo -e "  GRU:        ${GREEN}${GRU_NUMERO}${NC} — clicar em 'Verificar pagamento' (slide 10)"
echo -e "  Instrutor:  ${GREEN}${INSTRUTOR_NOME}${NC} (Anexo 5-B-1, slide 13)"
echo -e "  Ofício:     vai para ${GREEN}${MARINHA_EMAIL}${NC}"
echo -e "              em dev, ler no Mailpit → ${BLUE}http://localhost:8025${NC} (slide 17)"
echo -e "  Anexo:      ${GREEN}HELENA ANDRADE VASCONCELOS 84721590350.pdf${NC}"
echo ""
echo -e "${YELLOW}  Falta subir à mão, durante a captura:${NC}"
echo -e "    - foto do documento de identidade (slide 11) — subir a amostra em"
echo -e "      ${GREEN}apresentacao/insumos/documento-amostra.png${NC} (nunca um RG real)"
echo -e "    - assinatura do instrutor, se quiser o 5-B-1 com rubrica"
echo -e "      (Cadastros → Instrutores → ${INSTRUTOR_NOME})"
echo ""
echo -e "${YELLOW}  Rode este script de novo para rearmar a demo entre uma captura e outra.${NC}"
