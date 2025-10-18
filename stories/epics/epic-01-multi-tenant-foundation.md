---
epic_id: EPIC-01
title: Multi-tenant Foundation
status: TODO
priority: CRITICAL
start_date: 2025-01-15
target_date: 2025-01-28
owner: Team Backend
dependencies: []
---

# EPIC-01: Multi-tenant Foundation

## Objetivo

Estabelecer a infraestrutura base multi-tenant com isolamento lógico de dados por RLS (Row Level Security), autenticação via Keycloak 26, e ambiente de desenvolvimento local funcional.

## Escopo

### Incluído
- [x] Criar projeto Spring Boot 3.3 com estrutura de pacotes
- [ ] Configurar Docker Compose local (PostgreSQL 16 + Redis + Keycloak 26)
- [ ] Migrations Flyway com tabelas multi-tenant (`tenant`, `plano`, `assinatura`, `usuario`, `membro`)
- [ ] Habilitar Row Level Security (RLS) em todas as tabelas operacionais
- [ ] Implementar `TenantContext` (ThreadLocal) e `TenantFilter`
- [ ] Configurar Keycloak realm único com claim `tenant_id`
- [ ] Integrar Spring Security com validação de JWT
- [ ] Criar índices compostos `(tenant_id, *)` para performance
- [ ] Testes de integração com Testcontainers validando isolamento
- [ ] Documentação de setup local

### Excluído (Out of Scope)
- Deploy em ambiente de produção (AWS/EKS)
- Configuração de observabilidade (será EPIC-05)
- Implementação de entidades de negócio (será EPIC-02)

## Histórias Relacionadas

- `backend/stories/story-001-tenant-context-filter.md` (5 pts)
- `backend/stories/story-002-rls-implementation.md` (8 pts)
- `backend/stories/story-003-keycloak-integration.md` (5 pts)
- `backend/stories/story-004-docker-compose-setup.md` (3 pts)
- `backend/stories/story-005-flyway-migrations-base.md` (5 pts)

**Total estimado:** 26 story points (~2 sprints)

## Critérios de Aceite

- [ ] Usuário consegue fazer login via Keycloak e recebe JWT com claim `tenant_id`
- [ ] Todas as queries SQL são automaticamente filtradas por `tenant_id` via RLS
- [ ] Testes de integração comprovam que tenant A não acessa dados do tenant B
- [ ] Docker Compose sobe todos os serviços (PostgreSQL, Redis, Keycloak) com um único comando
- [ ] Documentação permite que novo desenvolvedor configure ambiente em < 30 min
- [ ] Código possui cobertura de testes > 80%

## Riscos

**Risco Alto:**
- **Performance do RLS em queries complexas**: RLS pode impactar performance em queries com muitos joins.
  - **Mitigação**: Criar índices compostos `(tenant_id, fk)` e validar com explain analyze

**Risco Médio:**
- **Complexidade de configuração do Keycloak**: Curva de aprendizado da ferramenta.
  - **Mitigação**: Documentar passo-a-passo e usar scripts de automação

## Dependências

- Keycloak 26 OSS (versão compatível com PKCE e custom claims)
- PostgreSQL 16 (suporte a RLS)

## Métricas de Sucesso

- Tempo de setup do ambiente de desenvolvimento: < 30 minutos
- Cobertura de testes de isolamento multi-tenant: 100%
- Performance de queries com RLS: < 50ms (P95) em dataset de teste com 10 tenants

## Notas

### Decisões Técnicas

**1. RLS vs. Queries Manuais**
- Escolhemos RLS para garantir isolamento automático e evitar vazamento de dados por erro humano
- Alternativa (queries manuais com WHERE tenant_id) foi rejeitada por ser mais propensa a erros

**2. Realm Único vs. Realm por Tenant**
- Realm único com claim `tenant_id` escolhido para MVP pela simplicidade operacional
- Opção de migrar para realm por tenant no futuro se clientes Enterprise exigirem

**3. ThreadLocal para TenantContext**
- Usa ThreadLocal para armazenar tenant_id durante request
- Importante fazer cleanup no finally para evitar vazamento em thread pools

### Referências

- [PostgreSQL RLS Documentation](https://www.postgresql.org/docs/16/ddl-rowsecurity.html)
- [Keycloak Custom Claims](https://www.keycloak.org/docs/latest/server_admin/#_protocol-mappers)
- [Spring Security Multi-tenancy](https://spring.io/blog/2022/02/21/spring-security-without-the-websecurityconfigureradapter)

## Changelog

- 2025-01-15: Épico criado
