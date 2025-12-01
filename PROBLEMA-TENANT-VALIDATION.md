# ❌ PROBLEMA: tenant_valid=false - Divergência Keycloak vs PostgreSQL

**Data**: 2025-11-08
**Status**: IDENTIFICADO - Aguardando reset do ambiente

---

## Sintoma

Após corrigir o routing dos controllers (remoção do prefixo `/api`), todos os endpoints retornam **403 Forbidden** com mensagem do OPA:

```
ABAC DENY: action=comissao:list, tenant_valid=false
```

---

## Causa Raiz

**Divergência total entre Keycloak e PostgreSQL**:

### Keycloak (porta 8081)
```
gerente@acme.com     → UUID: 46f75b71-8a19-4d21-a49f-9408eb81d56a
operador@acme.com    → UUID: da8eefe8-e00b-4af2-b517-005c3949b420
vendedor@acme.com    → UUID: 99f3368c-36fc-4db8-a68a-79463bc8cd8c
mecanico@acme.com    → UUID: fe6d4244-7467-4c62-8090-9c0e10d36c55
admin@acme.com       → UUID: 52a8882f-2526-4ea2-a599-e1e0f2889bf4
admin@plataforma.com → UUID: 297e422b-7443-48b2-a447-1f8a0502ddc9
```

### PostgreSQL (porta 5432)
```sql
SELECT u.id, u.email, m.tenant_id, m.papeis
FROM usuario u
LEFT JOIN membro m ON u.id = m.usuario_id;

-- Resultado: NENHUM usuário @acme.com!
-- Apenas usuários @praiadosol.com.br:
admin@praiadosol.com.br      → UUID: 11111111-1111-1111-1111-111111111111
gerente@praiadosol.com.br    → UUID: 22222222-2222-2222-2222-222222222222
operador@praiadosol.com.br   → UUID: 33333333-3333-3333-3333-333333333333
vendedor@praiadosol.com.br   → UUID: 44444444-4444-4444-4444-444444444444
mecanico@praiadosol.com.br   → UUID: 55555555-5555-5555-5555-555555555555
financeiro@praiadosol.com.br → UUID: 66666666-6666-6666-6666-666666666666
```

---

## Fluxo do Erro

1. **Postman collection** usa `gerente@acme.com` / `gerente123`
2. **Keycloak** autentica e retorna token com `sub=46f75b71-8a19-4d21-a49f-9408eb81d56a`
3. **TenantFilter** do backend extrai o UUID do JWT
4. **Consulta PostgreSQL**: `SELECT * FROM usuario WHERE id = '46f75b71-8a19-4d21-a49f-9408eb81d56a'` → **0 rows**
5. **TenantContext** fica com `usuarioId=null` e `tenantId=null`
6. **OPA** recebe `input.user.tenant_id = null`, `input.resource.tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'`
7. **Policy `multi_tenant.rego`**: `tenant_is_valid` retorna `false`
8. **Resposta**: `403 Forbidden`

---

## Arquitetura Correta (segundo V999__seed_data_dev.sql)

O seed data define a arquitetura de **Identity Provider Mapping**:

```sql
-- Usuários principais (para Keycloak + Postman)
INSERT INTO usuario (id, email, nome, ativo) VALUES
('00000000-aaaa-aaaa-aaaa-000000000001', 'admin@acme.com', 'Admin ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000002', 'gerente@acme.com', 'Gerente ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000003', 'operador@acme.com', 'Operador ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000004', 'vendedor@acme.com', 'Vendedor ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000005', 'mecanico@acme.com', 'Mecanico ACME', TRUE);

-- Mapeamento Keycloak UUID → PostgreSQL UUID
INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id, linked_at) VALUES
('00000000-aaaa-aaaa-aaaa-000000000001', 'keycloak', '52a8882f-2526-4ea2-a599-e1e0f2889bf4', NOW()),
('00000000-aaaa-aaaa-aaaa-000000000002', 'keycloak', '46f75b71-8a19-4d21-a49f-9408eb81d56a', NOW()),
...

-- Membros (tenant + roles)
INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000002', ARRAY['GERENTE'], TRUE);
```

---

## Problema Identificado

O PostgreSQL **NÃO RODOU AS MIGRATIONS FLYWAY**:

```bash
$ docker exec -i jetski-postgres psql -U jetski -d jetski_dev \
  -c "SELECT version FROM flyway_schema_history LIMIT 1;"

ERROR:  relation "flyway_schema_history" does not exist
```

**Conclusão**: O banco foi populado **manualmente** via script SQL (provavelmente `/infra/init-db.sql`), mas este script:
- ❌ NÃO criou a tabela `usuario_identity_provider`
- ❌ NÃO criou os usuários `@acme.com` (apenas `@praiadosol.com.br`)
- ❌ NÃO rodou as migrations Flyway

---

## Solução

### Opção 1: Reset completo do ambiente (RECOMENDADO)

```bash
# Parar backend
pkill -f "spring-boot:run"

# Resetar ambiente (Docker + Keycloak + PostgreSQL)
./reset-ambiente-local.sh

# Aguardar inicialização completa
# - Docker containers up
# - Flyway migrations aplicadas (incluindo V999)
# - Keycloak configurado com setup-keycloak-local.sh
# - usuario_identity_provider populada

# Testar
curl -X POST "http://localhost:8081/realms/jetski-saas/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=jetski-api" \
  -d "client_secret=jetski-secret" \
  -d "username=gerente@acme.com" \
  -d "password=gerente123"
```

### Opção 2: Sincronização manual (TEMPORÁRIO - não resolve a raiz)

```bash
# Mapear manualmente Keycloak UUID → PostgreSQL UUID
docker exec -i jetski-postgres psql -U jetski -d jetski_dev << SQL
CREATE TABLE IF NOT EXISTS usuario_identity_provider (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  usuario_id UUID NOT NULL REFERENCES usuario(id),
  provider VARCHAR(50) NOT NULL,
  provider_user_id UUID NOT NULL,
  linked_at TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE(provider, provider_user_id)
);

INSERT INTO usuario (id, email, nome, ativo) VALUES
('00000000-aaaa-aaaa-aaaa-000000000001', 'admin@acme.com', 'Admin ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000002', 'gerente@acme.com', 'Gerente ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000003', 'operador@acme.com', 'Operador ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000004', 'vendedor@acme.com', 'Vendedor ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000005', 'mecanico@acme.com', 'Mecanico ACME', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000001', ARRAY['ADMIN_TENANT'], TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000002', ARRAY['GERENTE'], TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000003', ARRAY['OPERADOR'], TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000004', ARRAY['VENDEDOR'], TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000005', ARRAY['MECANICO'], TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id) VALUES
('00000000-aaaa-aaaa-aaaa-000000000001', 'keycloak', '52a8882f-2526-4ea2-a599-e1e0f2889bf4'),
('00000000-aaaa-aaaa-aaaa-000000000002', 'keycloak', '46f75b71-8a19-4d21-a49f-9408eb81d56a'),
('00000000-aaaa-aaaa-aaaa-000000000003', 'keycloak', 'da8eefe8-e00b-4af2-b517-005c3949b420'),
('00000000-aaaa-aaaa-aaaa-000000000004', 'keycloak', '99f3368c-36fc-4db8-a68a-79463bc8cd8c'),
('00000000-aaaa-aaaa-aaaa-000000000005', 'keycloak', 'fe6d4244-7467-4c62-8090-9c0e10d36c55')
ON CONFLICT DO NOTHING;
SQL
```

---

## Verificação Pós-Fix

```bash
# 1. Verificar identity mapping
docker exec -i jetski-postgres psql -U jetski -d jetski_dev \
  -c "SELECT u.email, u.id as pg_uuid, uip.provider_user_id as kc_uuid
      FROM usuario u
      JOIN usuario_identity_provider uip ON u.id = uip.usuario_id
      WHERE u.email LIKE '%@acme.com';"

# 2. Testar autenticação
TOKEN=$(curl -s -X POST "http://localhost:8081/realms/jetski-saas/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=jetski-api" \
  -d "client_secret=jetski-secret" \
  -d "username=gerente@acme.com" \
  -d "password=gerente123" | jq -r '.access_token')

# 3. Testar endpoint de comissões
curl -X GET "http://localhost:8090/api/v1/comissoes/pendentes" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"

# Deve retornar 200 OK (não mais 403 tenant_valid=false)
```

---

## Arquivos Modificados

```
# Nenhum arquivo de código modificado
# Problema é de sincronização de dados entre Keycloak e PostgreSQL
```

---

## Lição Aprendida

1. **Flyway migrations** devem ser a ÚNICA fonte de truth para o schema do banco
2. **Scripts manuais** (`/infra/init-db.sql`) não devem duplicar lógica de migrations
3. **Identity Provider Mapping** é crítico para arquitetura multi-IDP
4. **Ambiente local** deve ser sempre iniciado via script de reset (idempotente)

---

## Próximos Passos

✅ **RECOMENDADO**: Executar `./reset-ambiente-local.sh` para garantir ambiente consistente
⚠️ **ALTERNATIVA**: Aplicar fix manual (Opção 2) para teste rápido, mas resetar depois
