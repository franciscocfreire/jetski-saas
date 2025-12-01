# API de Manutenção - Exemplos de Uso

Este documento contém exemplos práticos de requisições para todos os endpoints do módulo de Manutenção (OS Manutenção).

## 📋 Índice
1. [Criar Nova OS](#1-criar-nova-os)
2. [Listar OSs](#2-listar-oss)
3. [Obter OS por ID](#3-obter-os-por-id)
4. [Atualizar OS](#4-atualizar-os)
5. [Iniciar Trabalho](#5-iniciar-trabalho)
6. [Aguardar Peças](#6-aguardar-peças)
7. [Retomar Trabalho](#7-retomar-trabalho)
8. [Finalizar OS](#8-finalizar-os)
9. [Cancelar OS](#9-cancelar-os)
10. [Verificar Disponibilidade](#10-verificar-disponibilidade)

---

## Variáveis de Ambiente

```bash
# Configure estas variáveis antes de executar os exemplos
export BASE_URL="http://localhost:8090/api"
export TENANT_ID="a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
export ACCESS_TOKEN="your_keycloak_access_token_here"
export JETSKI_ID="7c9e6679-7425-40de-944b-e07fc1f90ae7"
export MECANICO_ID="9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"
```

---

## 1. Criar Nova OS

### 1.1 Manutenção Preventiva (50 horas)

```bash
curl -X POST "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d '{
    "jetskiId": "'"${JETSKI_ID}"'",
    "mecanicoId": "'"${MECANICO_ID}"'",
    "tipo": "PREVENTIVA",
    "prioridade": "MEDIA",
    "dtPrevistaInicio": "2025-11-20T08:00:00Z",
    "dtPrevistaFim": "2025-11-20T18:00:00Z",
    "descricaoProblema": "Manutenção preventiva de 50 horas - troca de óleo e vela",
    "horimetroAbertura": 125.5,
    "observacoes": "Última manutenção realizada em 15/10/2025 (75.5h)"
  }'
```

**Resposta (201 Created):**
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "tenantId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
  "jetskiId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "mecanicoId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "tipo": "PREVENTIVA",
  "prioridade": "MEDIA",
  "status": "ABERTA",
  "dtAbertura": "2025-11-18T19:30:00Z",
  "dtPrevistaInicio": "2025-11-20T08:00:00Z",
  "dtPrevistaFim": "2025-11-20T18:00:00Z",
  "descricaoProblema": "Manutenção preventiva de 50 horas - troca de óleo e vela",
  "horimetroAbertura": 125.5,
  "valorPecas": 0,
  "valorMaoObra": 0,
  "valorTotal": 0,
  "observacoes": "Última manutenção realizada em 15/10/2025 (75.5h)",
  "createdAt": "2025-11-18T19:30:00Z",
  "updatedAt": "2025-11-18T19:30:00Z"
}
```

**Efeito Colateral**: Jetski automaticamente bloqueado (status=MANUTENCAO) ✅

### 1.2 Manutenção Corretiva Urgente

```bash
curl -X POST "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d '{
    "jetskiId": "'"${JETSKI_ID}"'",
    "mecanicoId": "'"${MECANICO_ID}"'",
    "tipo": "CORRETIVA",
    "prioridade": "URGENTE",
    "descricaoProblema": "Motor falhando em altas rotações - cliente reportou perda de potência",
    "diagnostico": "Possível vela desgastada ou filtro de combustível entupido"
  }'
```

---

## 2. Listar OSs

### 2.1 Listar Todas as OSs Ativas

```bash
curl -X GET "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes?includeFinished=false" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}"
```

### 2.2 Listar OSs por Jetski

```bash
curl -X GET "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes?jetskiId=${JETSKI_ID}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}"
```

### 2.3 Listar OSs por Mecânico

```bash
curl -X GET "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes?mecanicoId=${MECANICO_ID}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}"
```

### 2.4 Listar OSs por Status

```bash
curl -X GET "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes?status=EM_ANDAMENTO" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}"
```

### 2.5 Listar OSs por Tipo

```bash
curl -X GET "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes?tipo=PREVENTIVA" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}"
```

---

## 3. Obter OS por ID

```bash
export OS_ID="f47ac10b-58cc-4372-a567-0e02b2c3d479"

curl -X GET "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes/${OS_ID}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}"
```

---

## 4. Atualizar OS

```bash
curl -X PUT "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes/${OS_ID}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d '{
    "diagnostico": "Vela desgastada confirmada. Filtro de combustível também precisa ser trocado.",
    "solucao": "Substituição de vela NGK e limpeza completa do sistema de combustível",
    "pecasJson": "[{\"nome\":\"Vela NGK\",\"qtd\":2,\"valor\":45.00},{\"nome\":\"Filtro combustível\",\"qtd\":1,\"valor\":80.00}]",
    "valorPecas": 125.00,
    "valorMaoObra": 200.00,
    "prioridade": "ALTA",
    "observacoes": "Cliente solicitou prioridade. Peças em estoque."
  }'
```

**Resposta**: OS atualizada com `valorTotal` = R$ 325,00 (calculado automaticamente)

---

## 5. Iniciar Trabalho

**Transição**: ABERTA → EM_ANDAMENTO

```bash
curl -X POST "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes/${OS_ID}/start" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}"
```

**Resposta**:
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "EM_ANDAMENTO",
  "dtInicioReal": "2025-11-20T08:15:00Z",
  ...
}
```

---

## 6. Aguardar Peças

**Transição**: EM_ANDAMENTO → AGUARDANDO_PECAS

```bash
curl -X POST "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes/${OS_ID}/wait-for-parts" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}"
```

**Uso típico**: Mecânico iniciou trabalho, mas descobriu que precisa de peças adicionais.

---

## 7. Retomar Trabalho

**Transição**: AGUARDANDO_PECAS → EM_ANDAMENTO

```bash
curl -X POST "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes/${OS_ID}/resume" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}"
```

**Uso típico**: Peças chegaram, mecânico retoma o trabalho.

---

## 8. Finalizar OS

**Transição**: EM_ANDAMENTO → CONCLUIDA

```bash
curl -X POST "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes/${OS_ID}/finish" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}"
```

**Resposta**:
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "CONCLUIDA",
  "dtConclusao": "2025-11-20T16:45:00Z",
  "horimetroConclusao": 125.8,
  ...
}
```

**Efeito Colateral**: Jetski liberado (status=DISPONIVEL) se não houver outras OSs ativas ✅

---

## 9. Cancelar OS

```bash
curl -X DELETE "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes/${OS_ID}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}"
```

**Resposta**: OS com `status=CANCELADA`

**Efeito Colateral**: Jetski liberado (status=DISPONIVEL) se não houver outras OSs ativas ✅

---

## 10. Verificar Disponibilidade

**Uso**: Verificar se jetski pode ser reservado (não possui OSs ativas bloqueando)

```bash
curl -X GET "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes/check-availability?jetskiId=${JETSKI_ID}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}"
```

**Resposta**:
```json
false  // Jetski disponível (sem OSs ativas)
```
ou
```json
true   // Jetski bloqueado (possui OSs ativas)
```

**Business Rule RN06.1**: Se retorna `true`, o jetski NÃO pode ser reservado!

---

## 🔄 Fluxo Completo de Manutenção

```bash
# 1. Criar OS
OS_ID=$(curl -s -X POST "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d '{
    "jetskiId": "'"${JETSKI_ID}"'",
    "tipo": "PREVENTIVA",
    "descricaoProblema": "Manutenção de 50h"
  }' | jq -r '.id')

echo "OS criada: ${OS_ID}"

# 2. Iniciar trabalho
curl -X POST "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes/${OS_ID}/start" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}"

# 3. Atualizar com peças e custos
curl -X PUT "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes/${OS_ID}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d '{
    "diagnostico": "Vela desgastada",
    "solucao": "Troca de vela",
    "valorPecas": 45.00,
    "valorMaoObra": 100.00
  }'

# 4. Finalizar OS
curl -X POST "${BASE_URL}/v1/tenants/${TENANT_ID}/manutencoes/${OS_ID}/finish" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}"

echo "Manutenção concluída! Jetski liberado para uso."
```

---

## 🎯 Regras de Negócio Implementadas

### RN06: Bloqueio Automático de Jetski
- ✅ Quando OS está **ABERTA**, **EM_ANDAMENTO** ou **AGUARDANDO_PECAS** → Jetski bloqueado (status=MANUTENCAO)
- ✅ Quando OS é **CONCLUIDA** ou **CANCELADA** → Jetski liberado (status=DISPONIVEL)
- ✅ **Liberação inteligente**: Jetski só é liberado se NÃO houver outras OSs ativas

### RN06.1: Validação de Reservas
- ✅ Endpoint `/check-availability` retorna `true` se jetski tem OSs ativas
- ✅ Sistema de reservas deve consultar este endpoint antes de permitir reserva

---

## 🔐 Permissões (RBAC)

| Endpoint | ADMIN_TENANT | GERENTE | MECANICO | OPERADOR |
|----------|--------------|---------|----------|----------|
| GET (listar/obter) | ✅ | ✅ | ✅ | ✅ (read-only) |
| POST (criar) | ✅ | ✅ | ❌ | ❌ |
| PUT (atualizar) | ✅ | ✅ | ✅ | ❌ |
| POST (workflow) | ✅ | ✅ | ✅ | ❌ |
| DELETE (cancelar) | ✅ | ✅ | ❌ | ❌ |

---

## 📝 Notas Importantes

1. **Horímetro automático**: Se `horimetroAbertura` não for informado na criação, o sistema captura automaticamente do jetski.

2. **Cálculo de valor total**: Sempre calculado automaticamente como `valorPecas + valorMaoObra`.

3. **Validações**:
   - `descricaoProblema` é obrigatório
   - Não é possível alterar OS com status CONCLUIDA ou CANCELADA
   - Transições de status devem seguir o workflow definido

4. **Multi-tenancy**: Todas as requisições requerem header `X-Tenant-Id` e token JWT com claim `tenant_id` correspondente.

---

## 🚀 Próximos Passos

- [ ] Adicionar estes exemplos à Postman Collection
- [ ] Criar testes E2E para jornada completa
- [ ] Integrar com sistema de notificações (alertas de manutenção preventiva)
- [ ] Dashboard de OSs ativas (backoffice web)

---

**Documentação gerada em**: 2025-11-18
**Versão da API**: 0.1.0-SNAPSHOT
**Módulo**: Manutenção (OS Manutenção)
