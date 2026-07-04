# Autorização no Meu Jet — mapa e regras de ouro

> Registrado em 04/07/2026, após a decisão de produto "staff também é
> cliente da plataforma". Este documento explica **onde cada papel vive**,
> **quem é a autoridade** em cada camada e **como criar regras novas**.

## As duas personas

O sistema tem duas personas com modelos de autorização DIFERENTES de
propósito:

| | **Staff** (equipe da loja) | **Cliente** (consumidor final) |
|---|---|---|
| Escopo de URL | `/v1/tenants/{tenantId}/**` | `/v1/customers/**` |
| Tenant | do path + `X-Tenant-Id` (validado) | nenhum no request — resolvido por operação |
| Identidade | `usuario` + `membro` (por tenant) | `cliente_identity_provider` (vínculos por `sub`) |
| Papéis | `membro.papeis[]` (por tenant) | **persona derivada do escopo** (ver abaixo) |
| Isolamento | RLS por `app.tenant_id` da sessão | RLS + `set_config` transaction-local por vínculo |

## Onde o papel CLIENTE vive (spoiler: em lugar nenhum que importe)

Desde `c2ca506` (04/07/2026), **CLIENTE não é um papel que alguém "tem" —
é uma persona que o escopo confere**:

1. `SecurityConfig`: `/v1/customers/**` exige apenas `authenticated()`.
2. `TenantFilter`: TODO usuário autenticado nesse escopo recebe
   `TenantContext.userRoles = [CLIENTE]`. Os papéis de staff **não entram**
   no contexto aqui — como cliente, um GERENTE não carrega poder extra.
3. OPA (`authorization.rego`): permite `customer:*` para a persona CLIENTE.
4. O realm role `CLIENTE` no Keycloak (atribuído no signup do portal) é
   **vestigial**: marca "conta nascida no portal", nada depende dele.

Motivos do desenho: staff de um tenant é cliente da plataforma; o
isolamento real do escopo customer sempre foi por `sub` → vínculos → RLS
por tenant; um papel gerenciado para clientes seria só mais uma coisa a
esquecer de atribuir.

## Onde os papéis de STAFF vivem (dois sistemas — atenção)

1. **`membro.papeis[]` no banco** — POR TENANT (GERENTE na loja A pode ser
   VENDEDOR na B). `TenantFilter` → `TenantAccessService` carrega para o
   contexto e alimenta o **OPA**. **Esta é a fonte da verdade.**
2. **Realm roles no Keycloak** — GLOBAIS, viram `ROLE_*` no Spring via
   `JwtAuthenticationConverter` e alimentam os `@PreAuthorize`/`hasAnyRole`
   (~140 usos) e o card "Acesso da equipe" do portal.

**A assimetria conhecida:** o realm role é global e NÃO expressa
multi-loja — um GERENTE-global passa no `@PreAuthorize` até no tenant onde
é só VENDEDOR. Quem segura a linha fina é o OPA (com `membro.papeis` do
tenant certo). Isso é defesa em profundidade intencional, com hierarquia:

> **REGRA DE OURO: o OPA é a autoridade. `@PreAuthorize` é pré-filtro
> grosso (barato, mas global). Nenhuma decisão de negócio pode depender
> SÓ do `@PreAuthorize`.**

## Como criar uma regra nova (checklist)

- [ ] Ação nomeada no `ActionExtractor` (`dominio:acao`; escopo customer
      gera `customer:*` automaticamente pelo path)
- [ ] Regra no `policies/authz/rbac.rego` (staff) ou confirmação de que a
      ação cai na regra `customer:*` (cliente). Lembrar: regra nova precisa
      de `default ... := false` (result colapsa se referenciar undefined);
      OPA dev não tem hot-reload (restart)
- [ ] `@PreAuthorize` opcional como pré-filtro — nunca como única barreira
- [ ] Teste distinguindo **403 (deny de autorização)** de **400 (deny de
      negócio/BusinessException)**
- [ ] Endpoints staff: validar tenant do path = contexto (padrão dos
      controllers existentes)

## Evoluções possíveis (registradas, sem urgência)

- Parar de atribuir o realm role `CLIENTE` no signup (nada quebra).
- Migrar `@PreAuthorize` de papel para OPA-only (remove a assimetria
  global×tenant) — cirurgia nos ~140 pontos; fazer por módulo, se um dia
  a assimetria virar problema real (ex.: staff multi-loja com papéis
  muito distintos).
- Se surgir "cliente PJ com operadores", NÃO criar papéis de cliente —
  modelar como vínculos adicionais (o desenho por sub já comporta).
