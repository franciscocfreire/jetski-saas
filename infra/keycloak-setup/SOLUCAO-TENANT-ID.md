# Solução: tenant_id via Group Attributes

**Data**: 2025-10-17
**Keycloak**: 26.4.1 com PostgreSQL
**Status**: ✅ IMPLEMENTADO E FUNCIONANDO

---

## 📋 Problema Identificado

### User Attributes Não Funcionam no Keycloak 26

Após migração do Keycloak de H2 (dev mode) para PostgreSQL, descobrimos que:

- ❌ **User attributes não são persistidos** mesmo com PostgreSQL
- ❌ Tentativas via REST API falharam
- ❌ Tentativas via `kcadm.sh` falharam
- ❌ Bug conhecido: Keycloak Issues [#31228](https://github.com/keycloak/keycloak/issues/31228), [#36585](https://github.com/keycloak/keycloak/issues/36585)

```bash
# Tentativa de salvar user attribute (NÃO FUNCIONA)
curl -X PUT "${KEYCLOAK_URL}/admin/realms/jetski-saas/users/${USER_ID}" \
  -d '{"attributes": {"tenant_id": ["550e8400-..."]}}'

# Resultado: attribute não é salvo ❌
```

---

## ✅ Solução Implementada: Group Attributes

### Por que funciona?

- ✅ **Group attributes SÃO persistidos** corretamente no Keycloak 26
- ✅ O mapper `oidc-usermodel-attribute-mapper` consegue ler attributes de grupos
- ✅ Quando um usuário pertence a um grupo, o Keycloak lê os attributes do grupo

### Arquitetura da Solução

```
┌─────────────────────────────────────────────────────────┐
│ 1. Criar grupo com attribute                            │
│    Nome: tenant-550e8400-e29b-41d4-a716-446655440001   │
│    Attribute: tenant_id = "550e8400-..."               │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ 2. Adicionar usuário ao grupo                          │
│    operador@tenant1.com → grupo tenant-550e8400-...    │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ 3. Protocol Mapper lê attribute do grupo               │
│    Mapper: oidc-usermodel-attribute-mapper             │
│    User Attribute: tenant_id                           │
│    Claim Name: tenant_id                               │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ 4. JWT contém tenant_id                                │
│    {                                                    │
│      "tenant_id": "550e8400-...",                      │
│      "roles": ["OPERADOR", "GERENTE"],                 │
│      "groups": ["tenant-550e8400-..."]                 │
│    }                                                    │
└─────────────────────────────────────────────────────────┘
```

---

## 🔧 Configuração do Keycloak

### Passo 1: Criar Grupo com Attribute

```bash
curl -X POST "${KEYCLOAK_URL}/admin/realms/jetski-saas/groups" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "name": "tenant-550e8400-e29b-41d4-a716-446655440001",
    "attributes": {
      "tenant_id": ["550e8400-e29b-41d4-a716-446655440001"]
    }
  }'
```

✅ **Group attributes funcionam perfeitamente**

### Passo 2: Adicionar Usuário ao Grupo

```bash
curl -X PUT "${KEYCLOAK_URL}/admin/realms/jetski-saas/users/${USER_ID}/groups/${GROUP_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

### Passo 3: Protocol Mappers (já configurados no setup)

**Mapper 1: tenant-id-mapper** (lê do group attribute)
```json
{
  "name": "tenant-id-mapper",
  "protocolMapper": "oidc-usermodel-attribute-mapper",
  "config": {
    "user.attribute": "tenant_id",
    "claim.name": "tenant_id",
    "access.token.claim": "true"
  }
}
```

**Mapper 2: groups-mapper** (opcional, para ver grupos)
```json
{
  "name": "groups-mapper",
  "protocolMapper": "oidc-group-membership-mapper",
  "config": {
    "claim.name": "groups",
    "full.path": "false",
    "access.token.claim": "true"
  }
}
```

---

## 🎯 Resultado Final

### JWT Token Contém

```json
{
  "tenant_id": "550e8400-e29b-41d4-a716-446655440001",
  "roles": ["OPERADOR", "GERENTE"],
  "groups": ["tenant-550e8400-e29b-41d4-a716-446655440001"],
  "email": "operador@tenant1.com",
  "preferred_username": "operador@tenant1.com"
}
```

### Verificação

```bash
# User attributes (vazios)
curl -X GET "${KEYCLOAK_URL}/admin/realms/jetski-saas/users/${USER_ID}"
# => { "attributes": {} }  ✅ Esperado

# Group attributes (contém tenant_id)
curl -X GET "${KEYCLOAK_URL}/admin/realms/jetski-saas/groups/${GROUP_ID}"
# => {
#      "name": "tenant-550e8400-...",
#      "attributes": {
#        "tenant_id": ["550e8400-..."]
#      }
#    }  ✅ Funciona!
```

---

## 📝 Setup Script Atualizado

O script `setup-keycloak-local.sh` foi atualizado para:

1. ❌ **Não criar mapper hardcoded** (removido)
2. ❌ **Não tentar salvar user attributes** (não funciona)
3. ✅ **Criar grupo com attribute tenant_id**
4. ✅ **Adicionar usuário ao grupo**
5. ✅ **Configurar mapper para ler do grupo**

### Execução

```bash
bash /home/franciscocfreire/repos/jetski/infra/keycloak-setup/setup-keycloak-local.sh
```

**Passos do script** (10 etapas):
1. Obter token de admin
2. Criar realm `jetski-saas`
3. Criar roles (OPERADOR, GERENTE, etc.)
4. Criar client `jetski-api`
5. Obter UUID do client
6. Configurar protocol mappers (tenant_id, roles, groups)
7. Criar usuário de teste
8. Atribuir roles ao usuário
9. **Criar grupo com tenant_id e adicionar usuário** ✨ NOVO
10. Finalizar

---

## 🏗️ Implicações para Produção

### Multi-Tenant com Grupos

Para adicionar novos tenants no futuro:

```bash
# 1. Criar grupo para o tenant
TENANT_ID="NEW-UUID-HERE"
curl -X POST "${KEYCLOAK_URL}/admin/realms/jetski-saas/groups" \
  -d "{
    \"name\": \"tenant-${TENANT_ID}\",
    \"attributes\": {
      \"tenant_id\": [\"${TENANT_ID}\"]
    }
  }"

# 2. Adicionar usuários ao grupo
curl -X PUT "${KEYCLOAK_URL}/admin/realms/jetski-saas/users/${USER_ID}/groups/${GROUP_ID}"
```

### Vantagens da Solução

✅ **Não requer hardcoded values** (cada tenant tem seu grupo)
✅ **Escalável** (fácil adicionar novos tenants)
✅ **Auditável** (grupos aparecem no Admin Console)
✅ **Flexível** (pode adicionar múltiplos attributes ao grupo)
✅ **Funciona no Keycloak 26** (contorna o bug de user attributes)

### Alternativas Consideradas

1. ❌ **Hardcoded mapper** - não escalável, valor fixo
2. ❌ **User attributes** - não funciona no Keycloak 26
3. ❌ **Script-based mapper** - requer JavaScript policy
4. ✅ **Group attributes** - simples, funciona, escalável

---

## 🔍 Troubleshooting

### tenant_id não aparece no token?

```bash
# 1. Verificar se usuário pertence ao grupo
curl -X GET "${KEYCLOAK_URL}/admin/realms/jetski-saas/users/${USER_ID}/groups"

# 2. Verificar attribute do grupo
curl -X GET "${KEYCLOAK_URL}/admin/realms/jetski-saas/groups/${GROUP_ID}"

# 3. Verificar mappers do client
curl -X GET "${KEYCLOAK_URL}/admin/realms/jetski-saas/clients/${CLIENT_UUID}/protocol-mappers/models"
```

### Adicionar tenant_id a usuário existente

```bash
# Buscar ID do grupo tenant-{UUID}
GROUP_ID=$(curl -X GET "${KEYCLOAK_URL}/admin/realms/jetski-saas/groups" | ...)

# Adicionar usuário ao grupo
curl -X PUT "${KEYCLOAK_URL}/admin/realms/jetski-saas/users/${USER_ID}/groups/${GROUP_ID}"
```

---

## 📚 Referências

- [Keycloak Issue #31228](https://github.com/keycloak/keycloak/issues/31228) - User attribute translation bug
- [Keycloak Issue #36585](https://github.com/keycloak/keycloak/issues/36585) - User attribute keys broken
- [Keycloak Protocol Mappers](https://www.keycloak.org/docs/latest/server_admin/#_protocol-mappers)
- [Group Membership Mapper](https://www.keycloak.org/docs/latest/server_admin/#_group-mapper)

---

## ✅ Conclusão

A solução via **group attributes** é:
- ✅ Funcional no Keycloak 26 com PostgreSQL
- ✅ Escalável para multi-tenant
- ✅ Não requer workarounds hardcoded
- ✅ Pronta para produção

**Próximos passos**: Integrar com Spring Boot backend para validação de `tenant_id` no JWT.

**Implementado por**: Claude Code
**Revisado por**: Francisco Freire
**Data**: 2025-10-17
