# Console da Plataforma (Meu Jet)

App do **operador da plataforma** — separado do backoffice onde as empresas operam.

- Produção: `admin.meujet.com.br` · Dev: `admin.pegaojet.com.br` · Local: `http://localhost:3005`
- Client Keycloak próprio: `jetski-platform-console` (público + PKCE)
- Spec: [`PLATAFORMA_CONSOLE_SPEC.md`](../../PLATAFORMA_CONSOLE_SPEC.md)

## Rotas (F0 + F1)

| Rota | O que faz |
|---|---|
| `/` | visão geral (indicadores de negócio chegam na F4, com o read model) |
| `/empresas` | lista com filtro por status e busca |
| `/empresas/[id]` | aprovar/suspender/reativar, plano e módulos, EAMA, créditos, faturas, emissões e **zona de perigo** (export, reset, exclusão) |
| `/creditos` | fila de compras PIX, preço unitário, saldos por empresa |
| `/faturamento` | faturas em conferência, gerar lote do mês |
| `/emissoes` | metering por empresa e competência |
| `/catalogo` | módulos por plano, capitanias, compressão de imagem |
| `/configuracoes` | rotação de chave de criptografia |

Pendentes: `/operadores` (F2), `/auditoria` e `/saude` (F5) — aparecem no menu marcadas com
a fase.

## Diferenças em relação ao backoffice

| | Backoffice | Console |
|---|---|---|
| Tenant | `X-Tenant-Id` em toda chamada | **nenhum** — alvo vai no path |
| Client OIDC | `jetski-backoffice` | `jetski-platform-console` |
| Login | código por e-mail, 2FA opt-in | **senha + TOTP obrigatório**, sem SSO cookie |
| Cookies | `authjs.*` | `console.*` |
| Sessão | 12h | 8h |
| Autorização | papéis do membro + OPA | `PlatformScopeInterceptor` + OPA |
| Dados | axios + react-query (client) | server components + server actions |

O token nunca chega ao browser: leituras acontecem no servidor e downloads binários (export
`.zip`, comprovante PIX) passam pelo proxy autenticado `/api/download`.

## Rodar

```bash
npm install
cp .env.example .env.local   # ajuste KEYCLOAK_ISSUER se necessário
npm run dev                  # http://localhost:3005
```

Em docker: `./rebuild.sh console` (a partir da raiz do repositório).

## Acesso

Só entra quem tem `usuario_global_roles.unrestricted_access = true`. Autenticar não basta:
o backend responde 403 em `/v1/platform/**` e o console mostra "Acesso restrito". A
concessão hoje é por `PLATFORM_ADMIN_EMAILS` ou SQL — ver [`SUPERADMIN.md`](../../SUPERADMIN.md).
A tela de operadores chega na F2.
