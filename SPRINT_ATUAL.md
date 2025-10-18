# 🚀 Sprint Atual - OPA Integration & Multi-Tenant Auth

**Data Início:** 2025-10-14
**Status:** 🟢 EM ANDAMENTO
**Objetivo:** Implementar autorização com OPA e validar arquitetura multi-tenant

---

## 📋 User Stories Implementadas

### ✅ **STORY-001: Configurar OPA Server**
**Como** desenvolvedor
**Quero** um OPA Server rodando localmente
**Para** testar políticas de autorização de forma isolada

**Critérios de Aceitação:**
- [x] OPA adicionado ao docker-compose.yml
- [x] OPA inicia na porta 8181
- [x] Health check configurado
- [x] Políticas montadas via volume (`./policies`)

**Arquivos:**
- `docker-compose.yml` - Serviço OPA configurado
- `policies/` - Diretório de políticas criado

---

### ✅ **STORY-002: Implementar Políticas RBAC em Rego**
**Como** desenvolvedor
**Quero** políticas RBAC básicas em Rego
**Para** controlar acesso por roles com validação multi-tenant

**Critérios de Aceitação:**
- [x] Política `authz/rbac.rego` implementada
- [x] Validação de `tenant_id` (user vs resource)
- [x] Suporte para roles: OPERADOR, GERENTE, FINANCEIRO, ADMIN_TENANT
- [x] Negado por padrão (`default allow := false`)
- [x] Testado via curl com sucesso

**Regras Implementadas:**
| Ação | OPERADOR | GERENTE | FINANCEIRO | ADMIN |
|------|----------|---------|------------|-------|
| `modelo:list` | ✅ | ✅ | ❌ | ✅ |
| `modelo:create` | ❌ | ✅ | ❌ | ✅ |
| `locacao:checkin` | ✅ | ✅ | ❌ | ✅ |
| `fechamento:diario` | ❌ | ✅ | ✅ | ✅ |
| `fechamento:mensal` | ❌ | ❌ | ✅ | ✅ |

**Arquivos:**
- `policies/authz/rbac.rego`

**Testes Realizados:**
```bash
# ✅ OPERADOR listando modelos (mesmo tenant) → PERMITIDO
# ❌ OPERADOR acessando outro tenant → NEGADO
```

---

### ✅ **STORY-003: Implementar Políticas de Alçada em Rego**
**Como** gestor do negócio
**Quero** regras de alçada (limites de aprovação)
**Para** controlar operações financeiras por valor e hierarquia

**Critérios de Aceitação:**
- [x] Política `authz/alcada.rego` implementada
- [x] Alçadas de desconto (OPERADOR: 5%, GERENTE: 20%)
- [x] Alçadas de OS manutenção (MECANICO: R$ 5k, GERENTE: R$ 20k)
- [x] Alçadas de fechamento (GERENTE: R$ 50k)
- [x] Retorna `requer_aprovacao` e `aprovador_requerido`
- [x] Testado com múltiplos cenários

**Regras Implementadas:**

**Desconto em Locações:**
- OPERADOR: até 5%
- GERENTE: até 20%
- Acima de 20%: requer aprovação ADMIN_TENANT

**Aprovação OS Manutenção:**
- MECANICO: até R$ 5.000
- GERENTE: R$ 5.001 - R$ 20.000
- ADMIN_TENANT: sem limite

**Fechamento Caixa:**
- GERENTE: diário até R$ 50.000
- FINANCEIRO: qualquer valor
- ADMIN: pode reabrir (auditoria)

**Arquivos:**
- `policies/authz/alcada.rego`

**Testes Realizados:**
```bash
# ✅ OPERADOR com 3% desconto → PERMITIDO
# ⏳ GERENTE com 25% desconto → REQUER APROVAÇÃO (ADMIN)
```

---

### ✅ **STORY-004: Documentar OPA e Políticas**
**Como** novo desenvolvedor no time
**Quero** documentação clara de OPA
**Para** entender e modificar políticas facilmente

**Critérios de Aceitação:**
- [x] README.md em `policies/` com guia completo
- [x] Exemplos de uso com curl
- [x] Explicação de sintaxe Rego
- [x] Estrutura de input/output documentada
- [x] Casos de teste documentados

**Arquivos:**
- `policies/README.md`

---

### 🟡 **STORY-005: Integrar Spring Boot com OPA** (EM ANDAMENTO)
**Como** backend
**Quero** consultar OPA antes de executar operações
**Para** validar permissões dinamicamente

**Critérios de Aceitação:**
- [x] DTOs criados (OPAInput, OPARequest)
- [ ] OPAResponse e OPADecision criados
- [ ] OPAAuthorizationService implementado
- [ ] WebClient configurado para OPA
- [ ] Integrado em AuthTestController
- [ ] Testado end-to-end com Keycloak + OPA

**Tarefas Restantes:**
- [ ] Criar `OPAResponse.java` e `OPADecision.java`
- [ ] Criar `OPAAuthorizationService.java`
- [ ] Criar configuração `OPAConfig.java` (WebClient)
- [ ] Adicionar endpoint de teste em AuthTestController
- [ ] Obter token do Keycloak
- [ ] Testar fluxo: Keycloak → Spring Boot → OPA → Response

**Arquivos (criados):**
- `com.jetski.opa.dto.OPAInput`
- `com.jetski.opa.dto.OPARequest`

**Arquivos (pendentes):**
- `com.jetski.opa.dto.OPAResponse`
- `com.jetski.opa.dto.OPADecision`
- `com.jetski.opa.service.OPAAuthorizationService`
- `com.jetski.opa.config.OPAConfig`

---

## 🎯 Épicos do Roadmap

### ÉPICO 1: Autorização e Segurança ✅ 80% Completo
- [x] STORY-001: Configurar OPA Server
- [x] STORY-002: Implementar RBAC em Rego
- [x] STORY-003: Implementar Alçadas em Rego
- [x] STORY-004: Documentar OPA
- [🟡] STORY-005: Integrar Spring Boot + OPA (60%)
- [ ] STORY-006: Testar OAuth2 + OPA End-to-End
- [ ] STORY-007: Implementar Workflow de Aprovações

### ÉPICO 2: Multi-Tenant Core ⏳ 50% Completo
- [x] TenantFilter implementado
- [x] Validação tenant_id (JWT vs Header)
- [x] SecurityConfig com dual chain (public/protected)
- [ ] Entidades JPA (Tenant, Usuario, Membro)
- [ ] PostgreSQL RLS (Row Level Security)
- [ ] Tenant-aware repositories

### ÉPICO 3: Domínio de Negócio 📅 Planejado
- [ ] Entidades core (Modelo, Jetski, Locacao, etc.)
- [ ] Controllers CRUD com autorização OPA
- [ ] Alçadas integradas aos workflows
- [ ] Workflow de aprovações (Aprovacao entity)

### ÉPICO 4: Operações MVP 📅 Planejado
- [ ] Check-in/Check-out com fotos
- [ ] Upload S3 (presigned URLs)
- [ ] Cálculo de valores (horas, desconto, combustível)
- [ ] Fechamento diário/mensal

---

## 📊 Métricas de Progresso

| Categoria | Progresso | Status |
|-----------|-----------|--------|
| **OPA Setup** | 100% | ✅ Completo |
| **Políticas Rego** | 100% | ✅ Completo |
| **Integração Java** | 40% | 🟡 Em Andamento |
| **Testes E2E** | 0% | ⏸️ Aguardando |
| **Entidades JPA** | 0% | ⏸️ Aguardando |
| **PostgreSQL RLS** | 0% | ⏸️ Aguardando |

---

## 🔥 Decisões Técnicas Tomadas

### 1. OPA em vez de AlçadaService In-App
**Decisão:** Usar OPA (Open Policy Agent) para autorização
**Razão:**
- Policy-as-Code versionado em Git
- Flexibilidade para regras complexas (alçadas)
- Desacoplamento (lógica fora do código Java)
- Investimento em conhecimento valioso para o time

**Trade-offs Aceitos:**
- Latência extra (+5-15ms por request ao OPA)
- Nova stack para gerenciar (deploy, monitoring)
- Curva de aprendizado (Rego)

**Mitigação:**
- Cache in-app para decisões frequentes (futuro)
- Sidecar pattern para baixa latência (futuro)

### 2. Rego Policies Estruturadas em Múltiplos Arquivos
**Decisão:** Separar RBAC e Alçadas em arquivos distintos
**Razão:**
- Facilita manutenção
- Permite evoluir independentemente
- Melhora clareza (Single Responsibility)

**Estrutura:**
```
policies/
├── authz/
│   ├── rbac.rego      # Permissões básicas por role
│   └── alcada.rego    # Limites de aprovação (ABAC)
```

### 3. Dual SecurityFilterChain (Public + Protected)
**Decisão:** Manter arquitetura de dois filter chains
**Razão:**
- Endpoints públicos não passam por OAuth2 (performance)
- TenantFilter apenas em protected chain
- Reduz latência em actuator/health

---

## 📚 Próximos Passos (Prioridade)

### Curto Prazo (Esta Sprint)
1. ✅ Completar DTOs OPA (Response, Decision)
2. ✅ Implementar OPAAuthorizationService
3. ✅ Integrar OPA em AuthTestController
4. ✅ Testar fluxo OAuth2 + OPA end-to-end
5. 📝 Atualizar backlog com stories detalhadas

### Médio Prazo (Próxima Sprint)
6. Implementar entidades JPA (Tenant, Usuario, Membro)
7. Configurar PostgreSQL RLS
8. Criar repositories multi-tenant
9. Implementar workflow de aprovações (Aprovacao entity)

### Longo Prazo (Mês 2)
10. Entidades de negócio (Modelo, Jetski, Locacao)
11. Check-in/Check-out com fotos (S3)
12. Fechamentos diários/mensais
13. Comissões

---

## 🧪 Testes Realizados

### Testes OPA (via curl)

**Teste 1: RBAC - Permitir OPERADOR listar modelos**
```bash
curl -X POST http://localhost:8181/v1/data/jetski/authz/rbac/allow \
  -d '{"input": {"action": "modelo:list", "user": {"tenant_id": "T1", "role": "OPERADOR"}, "resource": {"tenant_id": "T1"}}}'
# Resultado: {"result": true} ✅
```

**Teste 2: RBAC - Negar cross-tenant**
```bash
curl -X POST http://localhost:8181/v1/data/jetski/authz/rbac/allow \
  -d '{"input": {"action": "modelo:list", "user": {"tenant_id": "T1", "role": "OPERADOR"}, "resource": {"tenant_id": "T2"}}}'
# Resultado: {"result": false} ✅
```

**Teste 3: Alçada - OPERADOR com 3% desconto**
```bash
curl -X POST http://localhost:8181/v1/data/jetski/authz/alcada \
  -d '{"input": {"action": "desconto:aplicar", "user": {"tenant_id": "T1", "role": "OPERADOR"}, "resource": {"tenant_id": "T1"}, "operation": {"percentual_desconto": 3}}}'
# Resultado: {"result": {"allow": true, "requer_aprovacao": false}} ✅
```

**Teste 4: Alçada - GERENTE com 25% desconto (requer aprovação)**
```bash
curl -X POST http://localhost:8181/v1/data/jetski/authz/alcada \
  -d '{"input": {"action": "desconto:aplicar", "user": {"tenant_id": "T1", "role": "GERENTE"}, "resource": {"tenant_id": "T1"}, "operation": {"percentual_desconto": 25}}}'
# Resultado: {"result": {"allow": false, "requer_aprovacao": true, "aprovador_requerido": "ADMIN_TENANT"}} ✅
```

---

## 🛠️ Comandos Úteis

```bash
# Iniciar OPA
docker-compose up -d opa

# Ver logs do OPA
docker logs -f jetski-opa

# Verificar políticas carregadas
curl http://localhost:8181/v1/policies

# Recarregar políticas (hot reload automático)
# Basta editar arquivos em policies/ - OPA detecta mudanças

# Parar OPA
docker-compose stop opa
```

---

## 📖 Aprendizados do Time

### Sobre Rego
- Rego é declarativo: definimos REGRAS, não procedimentos
- `default allow := false` é crítico (deny-by-default)
- Helpers (funções auxiliares) facilitam reutilização
- Testar políticas antes de integrar (curl é suficiente)

### Sobre OPA
- Hot reload de políticas (sem restart)
- Latência baixa (~5-10ms localhost)
- Logs verbosos ajudam no debug
- Estrutura de input deve ser bem pensada

### Sobre Multi-Tenant
- SEMPRE validar `tenant_id` em TODA regra
- Cross-tenant é o risco #1 em SaaS B2B
- Filtros devem ser defensivos (fail-safe)

---

**Última Atualização:** 2025-10-14
**Responsável:** Claude Code + Equipe Jetski
**Revisão:** Pendente
