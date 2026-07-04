---
name: rodar-testes
description: Rodar e depurar os testes do backend jetski (mvn/Testcontainers) com os requisitos não-óbvios do ambiente. Use antes de commit/push de backend e para diagnosticar CI vermelho.
---

# Testes do backend

## Rodar
```bash
cd backend
mvn test                          # suíte completa (~920 testes; demora)
mvn test -Dtest='NomeDoTeste'     # um teste
mvn test -Dtest='ModuleStructureTest'  # fronteiras Modulith (nome EXATO)
```
Pré-requisito: Docker rodando (Testcontainers sobe Postgres + Redis).

## Armadilhas conhecidas
- **`ModulithStructureTest` é nome ERRADO** — casa zero testes e "passa" falsamente. O certo é `ModuleStructureTest`. Ele quebra se um módulo usar tipo de `shared.<subpkg>` sem `@NamedInterface` no `package-info.java`.
- **Fuso**: surefire usa `-Duser.timezone=America/Sao_Paulo` via `@{argLine}` no pom (preserva JaCoCo). Não remover; sem isso, testes de check-out falham em runner UTC.
- **Redis**: `AbstractIntegrationTest` sobe redis:7-alpine. Teste de integração novo deve estender essa base, não assumir Redis local.
- **Pool de conexões**: cada combinação nova de `@MockBean` cria um contexto Spring cacheado com pool Hikari vivo. Evitar combinações novas; o Postgres de teste roda com `max_connections=400` via `withCreateContainerCmdModifier` (`withCommand` NÃO funciona).
- **"Failed to load ApplicationContext" em cascata**: a causa real está no FUNDO da stack trace, atrás do flywayInitializer. Frequentemente é `too many clients` ou migration quebrada.
- **CORS**: `SecurityConfigTest` afirma a lista exata de origens — mexeu no CORS, atualize o teste.
- **RBAC**: 403 = deny de autorização (OPA/@PreAuthorize); 400 = deny de negócio (BusinessException). Não confundir nas asserções.

## Depurar CI vermelho
1. O `ci.yml` imprime `surefire-reports/*.txt` no log quando falha — ler a stack lá.
2. E2E Newman (`e2e.yml`): a rede do compose é descoberta dinamicamente (`<pasta>_jetski-network`); local = `jetski`, CI = `jetski-saas`.
3. **Atenção**: CI verde na main dispara o CD → deploy automático em produção com migrations. Não fazer merge "só para ver o CI passar".
