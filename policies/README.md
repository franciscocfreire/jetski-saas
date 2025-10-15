# OPA (Open Policy Agent) - Jetski SaaS

## 📋 Sumário

Este diretório contém as políticas de autorização (ABAC/RBAC) do Jetski SaaS, implementadas em Rego e executadas pelo Open Policy Agent.

## 🎯 Por Que OPA?

- **Policy-as-Code**: Políticas versionadas em Git
- **Flexibilidade**: ABAC para regras complexas (alçadas, contexto temporal)
- **Desacoplamento**: Lógica de autorização separada do código Java
- **Testabilidade**: Unit tests de políticas
- **Auditoria**: Decisões rastreáveis

## 📁 Estrutura

```
policies/
├── authz/              # Políticas de autorização
│   ├── rbac.rego       # Role-Based Access Control
│   └── alcada.rego     # Alçadas (approval authorities)
├── data/               # Dados de configuração
│   └── config.json     # (futuro) Config por tenant
└── test/               # Testes de políticas
    └── *.rego         # (futuro) Unit tests
```

## 🚀 Quickstart

### 1. Iniciar OPA

```bash
docker-compose up -d opa
```

### 2. Verificar Saúde

```bash
curl http://localhost:8181/health
```

### 3. Testar Política (RBAC)

```bash
curl -X POST http://localhost:8181/v1/data/jetski/authz/rbac/allow \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "action": "modelo:list",
      "user": {
        "id": "user-123",
        "tenant_id": "tenant-abc",
        "role": "OPERADOR"
      },
      "resource": {
        "tenant_id": "tenant-abc"
      }
    }
  }'

# Resposta: {"result": true}
```

### 4. Testar Política (Alçada)

```bash
curl -X POST http://localhost:8181/v1/data/jetski/authz/alcada \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "action": "desconto:aplicar",
      "user": {
        "id": "user-123",
        "tenant_id": "tenant-abc",
        "role": "OPERADOR"
      },
      "resource": {
        "tenant_id": "tenant-abc"
      },
      "operation": {
        "percentual_desconto": 3
      }
    }
  }'

# Resposta:
# {
#   "result": {
#     "allow": true,
#     "requer_aprovacao": false
#   }
# }
```

## 📖 Rego Basics

### Sintaxe Fundamental

```rego
package jetski.authz.exemplo

# Default: negado por padrão
default allow := false

# Regra: OPERADOR pode listar
allow if {
    input.user.role == "OPERADOR"
    input.action == "list"
}

# Condição múltipla (AND implícito)
allow if {
    input.user.role in ["GERENTE", "ADMIN"]
    input.operation.valor <= 10000
    tenant_is_valid
}

# Helper function
tenant_is_valid if {
    input.user.tenant_id == input.resource.tenant_id
}
```

### Input Structure (enviado pelo Spring Boot)

```json
{
  "input": {
    "action": "desconto:aplicar",          // Ação sendo executada
    "user": {
      "id": "uuid",                        // ID do usuário
      "tenant_id": "uuid",                 // Tenant do usuário
      "role": "OPERADOR"                   // Role principal
    },
    "resource": {
      "tenant_id": "uuid",                 // Tenant do recurso
      "id": "uuid"                         // ID do recurso
    },
    "operation": {
      "percentual_desconto": 10,           // Atributos específicos
      "valor_os": 15000,                   // da operação
      "justificativa": "..."
    }
  }
}
```

## 🔐 Políticas Implementadas

### 1. RBAC (`authz/rbac.rego`)

Controle de acesso baseado em roles:

| Ação | OPERADOR | GERENTE | FINANCEIRO | ADMIN_TENANT |
|------|----------|---------|------------|--------------|
| `modelo:list` | ✅ | ✅ | ❌ | ✅ |
| `modelo:create` | ❌ | ✅ | ❌ | ✅ |
| `locacao:checkin` | ✅ | ✅ | ❌ | ✅ |
| `fechamento:diario` | ❌ | ✅ | ✅ | ✅ |
| `fechamento:mensal` | ❌ | ❌ | ✅ | ✅ |

### 2. Alçadas (`authz/alcada.rego`)

Limites de aprovação baseados em valor/contexto:

**Desconto em Locações:**
- OPERADOR: até 5%
- GERENTE: até 20%
- ADMIN_TENANT: sem limite
- Acima de 20%: requer aprovação de ADMIN

**Aprovação de OS Manutenção:**
- MECANICO: até R$ 5.000
- GERENTE: R$ 5.001 - R$ 20.000
- ADMIN_TENANT: sem limite

**Fechamento de Caixa:**
- GERENTE: diário até R$ 50.000
- FINANCEIRO: qualquer valor
- ADMIN: pode reabrir (auditoria)

## 🧪 Testes

### Caso de Teste 1: Cross-Tenant (deve negar)

```bash
curl -X POST http://localhost:8181/v1/data/jetski/authz/rbac/allow \
  -d '{
    "input": {
      "action": "modelo:list",
      "user": {"tenant_id": "tenant-A", "role": "OPERADOR"},
      "resource": {"tenant_id": "tenant-B"}
    }
  }'

# Esperado: {"result": false}
```

### Caso de Teste 2: Alçada Excedida

```bash
curl -X POST http://localhost:8181/v1/data/jetski/authz/alcada \
  -d '{
    "input": {
      "action": "desconto:aplicar",
      "user": {"tenant_id": "T1", "role": "GERENTE"},
      "resource": {"tenant_id": "T1"},
      "operation": {"percentual_desconto": 25}
    }
  }'

# Esperado:
# {
#   "result": {
#     "allow": false,
#     "requer_aprovacao": true,
#     "aprovador_requerido": "ADMIN_TENANT"
#   }
# }
```

## 🔄 Integração com Spring Boot

(Próximo passo - a ser implementado)

```java
@Service
public class OPAAuthorizationService {

    private final WebClient opaClient;

    public OPADecision authorize(OPAInput input) {
        return opaClient.post()
            .uri("/v1/data/jetski/authz/alcada")
            .bodyValue(Map.of("input", input))
            .retrieve()
            .bodyToMono(OPAResponse.class)
            .map(r -> r.getResult())
            .block();
    }
}
```

## 📚 Aprendendo Rego

### Recursos Recomendados

- [Rego Playground](https://play.openpolicyagent.org/)
- [OPA Docs](https://www.openpolicyagent.org/docs/latest/)
- [Rego Cheat Sheet](https://www.openpolicyagent.org/docs/latest/policy-language/)

### Conceitos-Chave

1. **Rules**: `allow if { ... }`
2. **Defaults**: `default allow := false`
3. **Helpers**: Funções reutilizáveis
4. **Input**: Dados do request
5. **Data**: Dados estáticos (config)

## ✅ Boas Práticas

1. ✅ Sempre validar `tenant_id` (multi-tenant)
2. ✅ Default deny (negado por padrão)
3. ✅ Comentar regras complexas
4. ✅ Usar helpers para lógica compartilhada
5. ✅ Retornar metadata para auditoria
6. ✅ Testar políticas antes de deploy

## 🚧 Próximos Passos

- [ ] Integrar Spring Boot com OPA (WebClient)
- [ ] Criar unit tests de políticas (`.rego_test`)
- [ ] Implementar bundle OPA (versionamento)
- [ ] Adicionar monitoring/metrics
- [ ] Configurações por tenant (`data/config.json`)
- [ ] CI/CD pipeline para validar políticas

## 📊 Decisões de Arquitetura

**Por que Rego e não Java?**
- Policy-as-Code versionado
- Menor acoplamento
- Facilita auditoria
- Time aprende tecnologia valiosa

**Por que OPA Server e não biblioteca?**
- Reutilização entre serviços futuros
- Políticas centralizadas
- Hot reload de políticas sem redeploy

## 🐛 Troubleshooting

### OPA não inicia

```bash
docker logs jetski-opa
docker-compose restart opa
```

### Política não carrega

```bash
# Verificar sintaxe
docker exec jetski-opa opa check /policies/

# Ver políticas carregadas
curl http://localhost:8181/v1/policies
```

### Debugging

```bash
# Logs do OPA
docker logs -f jetski-opa

# Ver decisão completa
curl -X POST http://localhost:8181/v1/data/jetski/authz/alcada/decision \
  -d '{"input": {...}}'
```

---

**Autor**: Claude Code
**Data**: 2025-10-14
**Versão**: 1.0.0
