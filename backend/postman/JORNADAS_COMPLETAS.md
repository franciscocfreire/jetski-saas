# Jornadas Completas do Sistema Jetski SaaS

Este documento descreve todas as jornadas implementadas no sistema, organizadas por módulo e persona.

## 📋 Índice de Jornadas

1. [Setup Inicial](#1-setup-inicial)
2. [Jornada 1: Reserva → Locação → Abastecimento → Fechamento](#2-jornada-1-reserva--locação--abastecimento--fechamento)
3. [Jornada 2: Comissões - Do Cálculo ao Pagamento](#3-jornada-2-comissões---do-cálculo-ao-pagamento)
4. [Jornada 3: Fechamento Diário Completo](#4-jornada-3-fechamento-diário-completo)
5. [Jornada 4: Fechamento Mensal com Comissões](#5-jornada-4-fechamento-mensal-com-comissões)
6. [Jornada 5: Gestão de Políticas de Combustível](#6-jornada-5-gestão-de-políticas-de-combustível)
7. [Jornada 6: Gestão de Políticas de Comissão](#7-jornada-6-gestão-de-políticas-de-comissão)

---

## 1. Setup Inicial

### Variáveis de Ambiente (Local)
```json
{
  "baseUrl": "http://localhost:8090/api",
  "tenantId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
  "keycloakUrl": "http://localhost:8081",
  "operador_token": "{{obtido_via_keycloak}}",
  "gerente_token": "{{obtido_via_keycloak}}",
  "vendedor_token": "{{obtido_via_keycloak}}",
  "financeiro_token": "{{obtido_via_keycloak}}"
}
```

### Autenticação Keycloak
```bash
# Operador
POST {{keycloakUrl}}/realms/jetski-saas/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
&client_id=jetski-local
&username=operador.teste@example.com
&password={{senha}}

# Gerente
username=gerente.teste@example.com

# Vendedor
username=vendedor.teste@example.com

# Financeiro
username=financeiro.teste@example.com
```

---

## 2. Jornada 1: Reserva → Locação → Abastecimento → Fechamento

**Personas Envolvidas:** 💼 VENDEDOR, 👔 OPERADOR, 👨‍💼 GERENTE
**Módulos:** Reservas, Locações, Combustível (RN03), Fechamento (RN06)

### Passo 1: Vendedor cria Reserva
```http
POST {{baseUrl}}/v1/reservas
Authorization: Bearer {{vendedor_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "modeloId": "{{modelo_id}}",
  "clienteId": "{{cliente_id}}",
  "vendedorId": "{{vendedor_id}}",
  "dataHoraPrevistaInicio": "2025-11-01T09:00:00Z",
  "dataHoraPrevistaFim": "2025-11-01T11:00:00Z",
  "observacoes": "Cliente regular, previsão 2h"
}
```

**Resposta:** `201 Created` com `reserva_id`

### Passo 2: Operador faz Check-in a partir da Reserva
```http
POST {{baseUrl}}/v1/locacoes/checkin-from-reserva
Authorization: Bearer {{operador_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "reservaId": "{{reserva_id}}",
  "jetskiId": "{{jetski_id}}",
  "odometroInicio": 1500,
  "nivelCombustivelInicio": "CHEIO",
  "fotosCheckin": [
    "https://storage.example.com/checkin-1.jpg",
    "https://storage.example.com/checkin-2.jpg"
  ],
  "observacoesCheckin": "Jetski em perfeito estado"
}
```

**Resposta:** `201 Created` com `locacao_id`, status `EM_ANDAMENTO`

### Passo 3: Operador faz Check-out
```http
POST {{baseUrl}}/v1/locacoes/{{locacao_id}}/checkout
Authorization: Bearer {{operador_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "odometroFim": 1550,
  "nivelCombustivelFim": "MEIO",
  "fotosCheckout": [
    "https://storage.example.com/checkout-1.jpg",
    "https://storage.example.com/checkout-2.jpg"
  ],
  "observacoesCheckout": "Cliente satisfeito",
  "incidentes": []
}
```

**Resposta:** `200 OK`
- Cálculo automático: `tempoUsado = 120min`, `valorTotal = R$ 250.00`
- Tolerância aplicada (RN01): `tempoCobravel = 120min` (sem excedente)

### Passo 4: Registrar Abastecimento (RN03)
```http
POST {{baseUrl}}/v1/abastecimentos
Authorization: Bearer {{operador_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "jetskiId": "{{jetski_id}}",
  "locacaoId": "{{locacao_id}}",
  "tipoAbastecimento": "POS_LOCACAO",
  "litros": 15.5,
  "precoLitro": 5.89,
  "custoTotal": 91.30,
  "dataHora": "2025-11-01T11:05:00Z",
  "observacoes": "Reabastecimento completo"
}
```

**Resposta:** `201 Created`
- Custo rastreado para fechamento diário
- Política aplicável: MEDIDO (cliente paga 50% do combustível consumido)

### Passo 5: Consultar Locação com Detalhes
```http
GET {{baseUrl}}/v1/locacoes/{{locacao_id}}
Authorization: Bearer {{operador_token}}
X-Tenant-Id: {{tenantId}}
```

**Resposta:**
```json
{
  "id": "{{locacao_id}}",
  "status": "FINALIZADA",
  "valorBase": 240.00,
  "valorCombustivel": 45.65,
  "valorTotal": 285.65,
  "tempoUsado": 120,
  "tempoCobravel": 120,
  "vendedor": {
    "id": "{{vendedor_id}}",
    "nome": "João Vendedor",
    "comissaoPendente": true
  }
}
```

### Passo 6: Gerente Consulta Resumo do Dia
```http
GET {{baseUrl}}/v1/fechamento/dia/data/2025-11-01
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
```

**Resposta:** Resumo com locações, abastecimentos, comissões pendentes

---

## 3. Jornada 2: Comissões - Do Cálculo ao Pagamento

**Personas Envolvidas:** 💼 VENDEDOR, 👨‍💼 GERENTE, 💰 FINANCEIRO
**Módulo:** Comissões (RN04)

### Passo 1: Configurar Política de Comissão (Gerente)
```http
POST {{baseUrl}}/v1/politicas-comissao
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "nivel": "MODELO",
  "modeloId": "{{modelo_id}}",
  "tipoComissao": "PERCENTUAL",
  "percentual": 12.0,
  "descricao": "Comissão padrão para Jet Ski Sea-Doo GTI 130",
  "ativo": true
}
```

**Resposta:** `201 Created` - Política nivel 2 (MODELO)

### Passo 2: Consultar Políticas Aplicáveis
```http
GET {{baseUrl}}/v1/politicas-comissao?nivel=MODELO&modeloId={{modelo_id}}
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
```

**Hierarquia de Políticas (first-match-wins):**
1. **CAMPANHA** (nivel=1) - maior prioridade
2. **MODELO** (nivel=2)
3. **FAIXA_DURACAO** (nivel=3)
4. **VENDEDOR** (nivel=4) - menor prioridade

### Passo 3: Vendedor Consulta Suas Comissões
```http
GET {{baseUrl}}/v1/comissoes/vendedor/{{vendedor_id}}
Authorization: Bearer {{vendedor_token}}
X-Tenant-Id: {{tenantId}}
```

**Resposta:**
```json
[
  {
    "id": "{{comissao_id}}",
    "vendedorId": "{{vendedor_id}}",
    "locacaoId": "{{locacao_id}}",
    "valorBase": 240.00,
    "valorComissao": 28.80,
    "taxaAplicada": 12.0,
    "status": "PENDENTE",
    "politicaAplicada": "MODELO",
    "observacoes": "Aguardando aprovação gerencial"
  }
]
```

**Cálculo da Comissão (RN04):**
- `valorComissionavel = valorBase - combustivel - taxas`
- `valorComissionavel = 240.00 - 0 = 240.00` (combustível não é comissionável)
- `valorComissao = 240.00 × 12% = R$ 28.80`

### Passo 4: Gerente Consulta Comissões Pendentes
```http
GET {{baseUrl}}/v1/comissoes/pendentes
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
```

### Passo 5: Gerente Aprova Comissão
```http
POST {{baseUrl}}/v1/comissoes/{{comissao_id}}/aprovar
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "observacoes": "Aprovado - desempenho excelente"
}
```

**Resposta:** `200 OK` - Status mudou para `APROVADA`

**Validação ABAC:** OPA verifica alçada do gerente (autoridade para aprovar comissões)

### Passo 6: Financeiro Consulta Comissões Aprovadas
```http
GET {{baseUrl}}/v1/comissoes/aguardando-pagamento
Authorization: Bearer {{financeiro_token}}
X-Tenant-Id: {{tenantId}}
```

### Passo 7: Financeiro Efetua Pagamento
```http
POST {{baseUrl}}/v1/comissoes/{{comissao_id}}/pagar
Authorization: Bearer {{financeiro_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "dataPagamento": "2025-11-05T10:00:00Z",
  "metodoPagamento": "PIX",
  "comprovante": "https://storage.example.com/comprovante-pix.pdf",
  "observacoes": "Pago via PIX - chave cadastrada"
}
```

**Resposta:** `200 OK` - Status mudou para `PAGA`

**Workflow Completo:**
```
PENDENTE → (Gerente aprova) → APROVADA → (Financeiro paga) → PAGA
```

---

## 4. Jornada 3: Fechamento Diário Completo

**Personas Envolvidas:** 👔 OPERADOR, 👨‍💼 GERENTE
**Módulo:** Fechamento (RN06)

### Passo 1: Operador Consolida o Dia
```http
POST {{baseUrl}}/v1/fechamento/dia/consolidar
Authorization: Bearer {{operador_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "dtReferencia": "2025-11-01",
  "observacoes": "Dia de alta demanda - 15 locações"
}
```

**Resposta:** `201 Created`
```json
{
  "id": "{{fechamento_diario_id}}",
  "dtReferencia": "2025-11-01",
  "status": "aberto",
  "bloqueado": false,
  "totalLocacoes": 15,
  "totalFaturado": 3750.00,
  "totalCombustivel": 685.50,
  "totalComissoes": 450.00,
  "totalDinheiro": 1200.00,
  "totalCartao": 1800.00,
  "totalPix": 750.00,
  "operadorId": "{{operador_id}}",
  "observacoes": "Dia de alta demanda - 15 locações"
}
```

**Agregação Automática:**
- Consulta todas as locações finalizadas no dia via `LocacaoQueryService`
- Consulta abastecimentos via `AbastecimentoService`
- Consulta comissões pendentes via `ComissaoQueryService`
- Calcula totais por forma de pagamento

### Passo 2: Operador Consulta Fechamento
```http
GET {{baseUrl}}/v1/fechamento/dia/data/2025-11-01
Authorization: Bearer {{operador_token}}
X-Tenant-Id: {{tenantId}}
```

### Passo 3: Operador Fecha o Dia (Lock)
```http
POST {{baseUrl}}/v1/fechamento/dia/{{fechamento_diario_id}}/fechar
Authorization: Bearer {{operador_token}}
X-Tenant-Id: {{tenantId}}
```

**Resposta:** `200 OK`
- Status: `aberto` → `fechado`
- `bloqueado = true` - **edições retroativas bloqueadas (RN06)**
- `dtFechamento = now()`

### Passo 4: Gerente Revisa Fechamento
```http
GET {{baseUrl}}/v1/fechamento/dia/{{fechamento_diario_id}}
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
```

### Passo 5: Gerente Reabre (se necessário)
```http
POST {{baseUrl}}/v1/fechamento/dia/{{fechamento_diario_id}}/reabrir
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
```

**Resposta:** `200 OK` se status == `fechado`
**Erro:** `400 Bad Request` se status == `aprovado` (imutável)

### Passo 6: Gerente Aprova Fechamento
```http
POST {{baseUrl}}/v1/fechamento/dia/{{fechamento_diario_id}}/aprovar
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
```

**Resposta:** `200 OK`
- Status: `fechado` → `aprovado`
- **Permanentemente bloqueado** (não pode reabrir)

**Estados Permitidos (RN06):**
```
ABERTO → (fechar) → FECHADO → (aprovar) → APROVADO ⛔
   ↑                   ↓
   └──── (reabrir) ────┘
```

### Passo 7: Listar Fechamentos do Mês
```http
GET {{baseUrl}}/v1/fechamento/dia?dtInicio=2025-11-01&dtFim=2025-11-30
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
```

---

## 5. Jornada 4: Fechamento Mensal com Comissões

**Personas Envolvidas:** 👨‍💼 GERENTE, 💰 FINANCEIRO
**Módulo:** Fechamento (RN06) + Comissões (RN04)

### Passo 1: Gerente Consolida o Mês
```http
POST {{baseUrl}}/v1/fechamento/mes/consolidar
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "mes": 11,
  "ano": 2025,
  "observacoes": "Novembro - alta temporada"
}
```

**Resposta:** `201 Created`
```json
{
  "id": "{{fechamento_mensal_id}}",
  "mes": 11,
  "ano": 2025,
  "status": "aberto",
  "diasOperacao": 30,
  "totalReceita": 112500.00,
  "totalDespesas": 20550.00,
  "totalCombustivel": 20550.00,
  "totalComissoesPendentes": 13500.00,
  "totalComissoesAprovadas": 0.00,
  "totalComissoesPagas": 0.00,
  "observacoes": "Novembro - alta temporada"
}
```

**Agregação Automática:**
- Agrega todos os fechamentos diários do mês
- Calcula receita bruta, despesas (combustível)
- Lista comissões pendentes de aprovação
- Gera divergências (se houver inconsistências)

### Passo 2: Gerente Fecha o Mês
```http
POST {{baseUrl}}/v1/fechamento/mes/{{fechamento_mensal_id}}/fechar
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
```

**Resposta:** `200 OK` - Bloqueia edições, status → `fechado`

### Passo 3: Gerente Aprova o Mês
```http
POST {{baseUrl}}/v1/fechamento/mes/{{fechamento_mensal_id}}/aprovar
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
```

**Resposta:** `200 OK`
- Status → `aprovado`
- **Libera comissões para pagamento** (trigger workflow)

### Passo 4: Financeiro Lista Comissões do Mês
```http
GET {{baseUrl}}/v1/comissoes/periodo?dataInicio=2025-11-01&dataFim=2025-11-30&status=APROVADA
Authorization: Bearer {{financeiro_token}}
X-Tenant-Id: {{tenantId}}
```

### Passo 5: Financeiro Paga Comissões em Lote
```bash
# Pagamento individual para cada vendedor
for comissao_id in $(comissoes_aprovadas); do
  POST {{baseUrl}}/v1/comissoes/${comissao_id}/pagar
done
```

### Passo 6: Consultar Fechamento Mensal Completo
```http
GET {{baseUrl}}/v1/fechamento/mes/2025/11
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
```

**Resposta:**
```json
{
  "id": "{{fechamento_mensal_id}}",
  "mes": 11,
  "ano": 2025,
  "status": "aprovado",
  "diasOperacao": 30,
  "totalReceita": 112500.00,
  "totalDespesas": 20550.00,
  "totalCombustivel": 20550.00,
  "totalComissoesPendentes": 0.00,
  "totalComissoesAprovadas": 0.00,
  "totalComissoesPagas": 13500.00,
  "lucroLiquido": 78450.00,
  "margemLucro": 69.73
}
```

---

## 6. Jornada 5: Gestão de Políticas de Combustível

**Personas Envolvidas:** 👨‍💼 GERENTE
**Módulo:** Combustível (RN03)

### Passo 1: Criar Política Global (Padrão)
```http
POST {{baseUrl}}/v1/fuel-policies
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "nivel": "GLOBAL",
  "modo": "INCLUSO",
  "ativo": true,
  "observacoes": "Política padrão - combustível incluído no preço"
}
```

**Modos de Combustível (RN03):**
1. **INCLUSO**: Combustível incluído no preço, custo operacional
2. **MEDIDO**: Cliente paga por litro consumido (litros × preço_dia)
3. **TAXA_FIXA**: Taxa fixa por hora de locação

### Passo 2: Criar Política por Modelo (Override)
```http
POST {{baseUrl}}/v1/fuel-policies
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "nivel": "MODELO",
  "modeloId": "{{modelo_premium_id}}",
  "modo": "MEDIDO",
  "precoLitroPadrao": 6.50,
  "ativo": true,
  "observacoes": "Modelos premium - cliente paga combustível"
}
```

### Passo 3: Criar Política por Jetski Específico
```http
POST {{baseUrl}}/v1/fuel-policies
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "nivel": "JETSKI",
  "jetskiId": "{{jetski_problema_id}}",
  "modo": "TAXA_FIXA",
  "taxaFixaPorHora": 25.00,
  "ativo": true,
  "observacoes": "Jetski consumo alto - taxa fixa mais justa"
}
```

**Hierarquia de Políticas:**
```
JETSKI (maior prioridade)
  ↓
MODELO
  ↓
GLOBAL (fallback)
```

### Passo 4: Consultar Política Aplicável
```http
GET {{baseUrl}}/v1/fuel-policies/applicable?jetskiId={{jetski_id}}
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
```

**Resposta:** Retorna a política de maior prioridade aplicável

### Passo 5: Atualizar Política
```http
PUT {{baseUrl}}/v1/fuel-policies/{{policy_id}}
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "modo": "INCLUSO",
  "ativo": true,
  "observacoes": "Alterado para modo incluso após feedback clientes"
}
```

### Passo 6: Desativar Política
```http
DELETE {{baseUrl}}/v1/fuel-policies/{{policy_id}}
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
```

**Resposta:** `204 No Content` - Política marcada como `ativo=false`

---

## 7. Jornada 6: Gestão de Políticas de Comissão

**Personas Envolvidas:** 👨‍💼 GERENTE
**Módulo:** Comissões (RN04)

### Passo 1: Criar Política de Campanha (Prioridade Máxima)
```http
POST {{baseUrl}}/v1/politicas-comissao
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "nivel": "CAMPANHA",
  "campanhaId": "{{campanha_black_friday_id}}",
  "tipoComissao": "PERCENTUAL",
  "percentual": 15.0,
  "validadeInicio": "2025-11-20T00:00:00Z",
  "validadeFim": "2025-11-30T23:59:59Z",
  "descricao": "Black Friday - comissão aumentada",
  "ativo": true
}
```

**Resposta:** `201 Created` - Nivel 1 (maior prioridade)

### Passo 2: Criar Política por Faixa de Duração
```http
POST {{baseUrl}}/v1/politicas-comissao
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "nivel": "FAIXA_DURACAO",
  "duracaoMinMinutos": 120,
  "duracaoMaxMinutos": null,
  "tipoComissao": "ESCALONADO",
  "percentual": 10.0,
  "percentualAdicional": 12.0,
  "limiteEscalonamento": 180,
  "descricao": "10% até 3h, 12% acima de 3h",
  "ativo": true
}
```

**Comissão Escalonada:**
- Até 180 min: 10%
- Acima de 180 min: 12%

### Passo 3: Criar Política por Vendedor
```http
POST {{baseUrl}}/v1/politicas-comissao
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "nivel": "VENDEDOR",
  "vendedorId": "{{vendedor_top_id}}",
  "tipoComissao": "PERCENTUAL",
  "percentual": 13.0,
  "descricao": "Vendedor estrela - comissão diferenciada",
  "ativo": true
}
```

### Passo 4: Listar Todas as Políticas Ativas
```http
GET {{baseUrl}}/v1/politicas-comissao/todas?ativo=true
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
```

**Resposta:** Lista ordenada por `nivel ASC` (1=maior prioridade)

### Passo 5: Simular Cálculo de Comissão
**Cenário:** Locação de 4h (240 min) no modelo premium, vendedor top, durante campanha

**Hierarquia Aplicada (first-match-wins):**
1. ✅ **CAMPANHA** (nivel=1, 15%) → **VENCE**
2. ❌ MODELO (nivel=2, 12%) - ignorado
3. ❌ FAIXA_DURACAO (nivel=3, 12% escalonado) - ignorado
4. ❌ VENDEDOR (nivel=4, 13%) - ignorado

**Cálculo:**
```
valorBase = R$ 320.00
valorCombustivel = R$ 50.00 (não comissionável)
valorComissionavel = 320.00 - 50.00 = R$ 270.00
valorComissao = 270.00 × 15% = R$ 40.50
```

### Passo 6: Atualizar Política
```http
PUT {{baseUrl}}/v1/politicas-comissao/{{politica_id}}
Authorization: Bearer {{gerente_token}}
X-Tenant-Id: {{tenantId}}
Content-Type: application/json

{
  "percentual": 16.0,
  "descricao": "Campanha estendida - aumentado para 16%",
  "validadeFim": "2025-12-05T23:59:59Z"
}
```

---

## 📊 Resumo das Jornadas

| # | Jornada | Personas | Endpoints | Duração |
|---|---------|----------|-----------|---------|
| 1 | Reserva → Locação → Abastecimento | VENDEDOR, OPERADOR, GERENTE | 6 | ~30 min |
| 2 | Comissões (Cálculo → Pagamento) | VENDEDOR, GERENTE, FINANCEIRO | 7 | ~2 dias |
| 3 | Fechamento Diário | OPERADOR, GERENTE | 7 | ~1h |
| 4 | Fechamento Mensal | GERENTE, FINANCEIRO | 6 | ~30 min |
| 5 | Gestão Políticas Combustível | GERENTE | 6 | ~15 min |
| 6 | Gestão Políticas Comissão | GERENTE | 6 | ~15 min |

---

## 🔐 Matriz de Autorização (ABAC via OPA)

| Endpoint | ADMIN | GERENTE | OPERADOR | VENDEDOR | FINANCEIRO |
|----------|-------|---------|----------|----------|------------|
| Criar Reserva | ✅ | ✅ | ✅ | ✅ | ❌ |
| Check-in/Check-out | ✅ | ✅ | ✅ | ❌ | ❌ |
| Registrar Abastecimento | ✅ | ✅ | ✅ | ❌ | ❌ |
| Consolidar Dia | ✅ | ✅ | ✅ | ❌ | ❌ |
| Fechar Dia | ✅ | ✅ | ✅ | ❌ | ❌ |
| Aprovar Fechamento Diário | ✅ | ✅ | ❌ | ❌ | ❌ |
| Consolidar Mês | ✅ | ✅ | ❌ | ❌ | ❌ |
| Aprovar Fechamento Mensal | ✅ | ✅ | ❌ | ❌ | ❌ |
| Consultar Comissões (próprias) | ✅ | ✅ | ❌ | ✅ | ❌ |
| Aprovar Comissão | ✅ | ✅ | ❌ | ❌ | ❌ |
| Pagar Comissão | ✅ | ✅ | ❌ | ❌ | ✅ |
| Gerenciar Políticas | ✅ | ✅ | ❌ | ❌ | ❌ |

---

## 🧪 Testes Automatizados

Todos os endpoints possuem **integration tests** com ABAC:
- **ComissaoControllerIntegrationTest**: 12 testes
- **FechamentoControllerIntegrationTest**: 16 testes
- **AbastecimentoControllerTest**: 10 testes
- **FuelPolicyControllerTest**: 8 testes

**Coverage Total**: 723 testes passando (100% success rate)

---

## 📦 Collection Postman

Para importar a collection completa com todas as requisições prontas:

1. Importe `Jetski-Jornadas.postman_collection.json`
2. Configure o environment `Local.postman_environment.json`
3. Autentique cada persona via Keycloak (Setup folder)
4. Execute as jornadas sequencialmente

**Ordem Recomendada:**
1. Setup - Autenticar Personas
2. Jornada 1 (fluxo completo básico)
3. Jornada 5 e 6 (configurar políticas)
4. Jornada 2 (testar comissões)
5. Jornada 3 e 4 (fechamentos)

---

**Gerado em:** 2025-10-31
**Versão do Sistema:** 0.8.0 (Sprint 3)
**Módulos Implementados:** Reservas, Locações, Combustível (RN03), Comissões (RN04), Fechamento (RN06)
