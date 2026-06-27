# Avaliação de Esforço e Custo — Plataforma JetSave

> Documento de avaliação técnica e econômica do que foi construído até a data.
> **Data da avaliação:** 26/06/2026 · **Versão do produto:** 0.8.0

---

## 1. Resumo executivo

Estima-se que **reconstruir** o que já existe nesta plataforma, do zero, por um time
profissional e em padrão de produção, exigiria:

| Indicador | Estimativa |
|---|---|
| **Esforço (time sênior enxuto)** | **≈ 3.100 horas-pessoa** |
| **Esforço (agência/consultoria convencional)** | **≈ 5.500 horas-pessoa** |
| **Prazo equivalente (squad de 3–4 pessoas)** | **≈ 7 a 11 meses** |
| **Valor de engenharia (Brasil, PJ sênior)** | **≈ R$ 465 mil a R$ 825 mil** |
| **Valor de engenharia (agência BR)** | **≈ R$ 870 mil a R$ 1,5 milhão** |
| **Valor de engenharia (contratação internacional)** | **≈ US$ 310 mil a US$ 550 mil** |

> ⚠️ Estes números representam o **custo de mercado para reconstruir** o ativo
> (valor de reposição da engenharia), **não** o custo real incorrido neste projeto
> — que foi desenvolvido por **1 pessoa com apoio de IA em ~8,5 meses**, uma fração
> deste valor. Ver §7.

---

## 2. Metodologia

A estimativa combina três técnicas para reduzir viés:

1. **Bottom-up por feature/módulo** — decompõe o produto em ~40 entregáveis e atribui
   horas-pessoa a cada um, em padrão de produção (código + testes + revisão + ajustes).
2. **Cross-check por linhas de código (proxy COCOMO)** — valida o total contra a
   produtividade típica de engenharia enterprise.
3. **Cross-check de cronograma** — confere o total contra prazo×equipe realistas.

As horas são **fully-loaded**: incluem design, implementação, testes automatizados,
code review, depuração e integração — não apenas digitação de código.

---

## 3. Métricas reais do código-fonte

Levantadas diretamente do repositório (exclui dependências, build e gerados):

### Backend — Spring Boot 3.3 / Java 21 (modular monolith)
| Métrica | Valor |
|---|---|
| Arquivos Java (main) | 473 |
| Linhas de código (main) | 53.360 |
| Arquivos de teste | 66 |
| Linhas de teste | 21.656 |
| Métodos `@Test` | 835 |
| Migrations Flyway versionadas | ~52 |
| Controllers REST | 43 |
| Módulos de domínio | ~14 |

### Frontend — Next.js 15 / React 19 (backoffice)
| Métrica | Valor |
|---|---|
| Telas (`page.tsx`) | 33 |
| Componentes (`.tsx`) | 53 |
| Arquivos TS/TSX | 159 |
| Linhas TS/TSX | 31.903 |
| Specs e2e (Playwright) | 5 |
| Serviços de API | 33 |

### Mobile — Kotlin Multiplatform (KMM) — em andamento
| Métrica | Valor |
|---|---|
| Arquivos Kotlin | 89 |
| Linhas Kotlin | 19.528 |
| Documentos de design | 7 |

### Plataforma, segurança e infraestrutura
| Métrica | Valor |
|---|---|
| Políticas OPA (`.rego`) | 18 + 13 de teste |
| Scripts de automação | 6 |
| Arquivos docker-compose | 3 (incl. dev=prod simétrico) |
| Workflows de CI | 3 |
| Documentos de especificação/projeto | 31 |

### Histórico
| Métrica | Valor |
|---|---|
| Commits | 214 |
| Período | 14/out/2025 → 26/jun/2026 (~8,5 meses) |
| Autor | 1 (com apoio de IA) |
| **Total de LOC de aplicação** | **≈ 126 mil** (53k back + 22k testes back + 32k front + 19,5k mobile) |

---

## 4. Estimativa de esforço por área

### 4.1 Backend (API SaaS multi-tenant)

| Entregável | Horas |
|---|---:|
| Fundação multi-tenant (Spring Modulith, RLS, `TenantContext`, filtros, OpenAPI, tratamento de erros) | 220 |
| Autenticação Keycloak 26 (OIDC/PKCE) + OPA ABAC/RBAC | 130 |
| Tenant, usuários, signup, onboarding & convites | 120 |
| Frota (modelos, jetskis) | 55 |
| Reservas (agendamento, conflitos, sinal/pagamento, máquina de estados) | 120 |
| Locações (check-in/out, **motor de cobrança** RN01/RN03, fotos, checklists) | 150 |
| Manutenção (OS, bloqueio de disponibilidade) | 55 |
| Comissões (política hierárquica RN04) | 90 |
| Fechamento diário/mensal (consolidação + travas retroativas) | 80 |
| Combustível (políticas de preço) | 50 |
| Despesas | 30 |
| Pagamentos | 55 |
| Bônus | 40 |
| Dashboard (KPIs) | 45 |
| Marketplace | 45 |
| Auditoria assíncrona | 50 |
| **Integração Marinha/GRU + PagTesouro (PIX/boleto/comprovante) — engenharia reversa HTTP** | 150 |
| Portal do cliente (claim-token, vínculo de identidade) | 80 |
| Armazenamento S3/MinIO + geração/merge de PDF + hashing de fotos | 70 |
| E-mail (templates, perfis dev/prod) | 30 |
| **Subtotal backend** | **1.665** |

### 4.2 Frontend backoffice

| Entregável | Horas |
|---|---:|
| Fundação (Next 15, NextAuth/OIDC, API client, store de tenant, shadcn/ui, layout, design system) | 110 |
| 33 telas + fluxos (CRUD, agenda, **wizard de balcão**, pendências, fila, dashboards, relatórios) | 470 |
| Testes e2e (Playwright) | 40 |
| **Subtotal frontend** | **620** |

### 4.3 Mobile (KMM) — em andamento

| Entregável | Horas |
|---|---:|
| App KMM (shared + Android + iOS): offline-first, câmera, PKCE — ~19,5k LOC | 450 |
| **Subtotal mobile** | **450** |

### 4.4 Plataforma, segurança e qualidade

| Entregável | Horas |
|---|---:|
| Políticas OPA (18) + testes (13) | 80 |
| DevOps: Docker, compose dev=prod, Cloudflare Tunnel, `deploy.sh`, CI/CD, realm Keycloak, scripts de reset | 140 |
| Especificação, documentação (31 docs), gestão e QA manual | 120 |
| **Subtotal plataforma** | **340** |

### 4.5 Total

| Área | Horas |
|---|---:|
| Backend | 1.665 |
| Frontend | 620 |
| Mobile | 450 |
| Plataforma/Infra/Qualidade | 340 |
| **TOTAL (time sênior enxuto)** | **≈ 3.075** |

---

## 5. Cross-checks

**Por LOC (COCOMO simplificado).** ~126 mil LOC de aplicação. A produtividade
enterprise fully-loaded varia de ~10 LOC/h (convencional, com overhead) a ~40 LOC/h
(time sênior com forte uso de scaffolding moderno — Spring starters, MapStruct,
shadcn/ui):

- a 40 LOC/h → ~3.150h (alinhado ao bottom-up)
- a 18 LOC/h → ~7.000h (cenário convencional/agência)

**Por cronograma.** 3.075h ÷ ~150h produtivas/mês = ~20,5 meses-pessoa. Com squad de
3–4 pessoas (back, front, mobile, com sobreposição), o prazo elapsado fica em
**~7 a 11 meses** — consistente com o histórico real do projeto.

Adotamos como faixa de trabalho: **3.100h (enxuto) a 5.500h (convencional/agência)**.

---

## 6. Cenários de custo

Valores de **engenharia** (mão de obra). Não incluem licenças, nuvem, ou custos
operacionais recorrentes. Câmbio de referência: US$ 1 ≈ R$ 5,40 (aproximação).

| Cenário de contratação | Taxa | 3.100 h | 5.500 h |
|---|---:|---:|---:|
| Dev sênior PJ (Brasil) | R$ 150/h | R$ 465 mil | R$ 825 mil |
| Dev sênior PJ (Brasil, premium) | R$ 180/h | R$ 558 mil | R$ 990 mil |
| Squad/agência (Brasil) | R$ 280/h | R$ 868 mil | R$ 1,54 mi |
| Contratação internacional (contractor) | US$ 100/h | US$ 310 mil | US$ 550 mil |

**Faixa central recomendada para comunicação externa:**
**R$ 500 mil – R$ 1,0 milhão** (valor de reposição da engenharia em padrão brasileiro
de mercado).

---

## 7. Mercado × realidade do projeto

Este é o ponto mais importante para uma avaliação honesta:

- **Valor de mercado para reconstruir** (o que um terceiro cobraria): **R$ 500 mil–1,5 mi**.
- **Custo real incorrido**: o produto foi construído por **1 desenvolvedor com apoio
  intensivo de IA**, em **~8,5 meses** (214 commits). O desembolso real ≈ tempo de
  1 pessoa + ferramentas de IA — **uma fração** (estimável em **5–15%**) do valor de mercado.

A diferença é o **ganho de produtividade** capturado pelo método de desenvolvimento
(IA + scaffolding moderno + monólito modular bem fatorado). Para fins de captação,
seguro, valuation ou venda do ativo, o número relevante é o **valor de reposição**
(§6); para fins de fluxo de caixa do projeto, o número relevante é o **custo real**.

---

## 8. Fatores de complexidade (o que encarece)

Itens acima da média de um CRUD SaaS comum, que justificam a faixa estimada:

1. **Multi-tenancy real com RLS** no PostgreSQL + isolamento por `TenantContext` em
   todas as camadas — pervasivo, exige disciplina e testes de isolamento.
2. **Integração com a Marinha (GRU) e PagTesouro** via **engenharia reversa de fluxo
   HTTP** (cookie jar manual, cascata de 7 passos, PIX/boleto/comprovante, sonda de
   pagamento) — trabalho especializado, sem SDK oficial.
3. **Segurança em camadas**: Keycloak (OIDC/PKCE) + **OPA (ABAC/RBAC)** desacoplado.
4. **Motor de cobrança** com regras de tolerância, arredondamento, hora extra e
   políticas de combustível/comissão hierárquicas.
5. **Offline-first mobile (KMM)** com fila de sincronização, câmera e dois targets.
6. **dev = prod simétricos** (Cloudflare Tunnel) e CI/CD com Testcontainers.
7. **835 testes automatizados** no backend — cobertura que adiciona ~25–30% de esforço,
   mas reduz risco e retrabalho.

## 9. Riscos e ressalvas da estimativa

- Estimativas de software têm incerteza intrínseca de **±30%**.
- O **mobile está em andamento** (~19,5k LOC); a conclusão a paridade de produção
  pode adicionar **150–300h**.
- Não foram precificados: design de produto/UX dedicado, gestão de produto formal,
  segurança ofensiva (pentest), e operação/suporte contínuos.
- LOC é proxy imperfeito; o bottom-up por feature é a base principal, com LOC e
  cronograma apenas como validação cruzada.

---

## 10. Conclusão

O que existe hoje equivale a um ativo de software cujo **valor de reposição de
engenharia** está na faixa de **R$ 500 mil a R$ 1,5 milhão** (≈ **3.100–5.500
horas-pessoa**), correspondente a **~7–11 meses de um squad de 3–4 profissionais**.

O produto cobre, em padrão de produção, um SaaS B2B multi-tenant completo (frota,
reservas, locações com cobrança, manutenção, combustível, comissões, fechamentos,
pagamentos, auditoria), com **diferencial técnico relevante** na integração
Marinha/GRU e na arquitetura de segurança multi-tenant — itens que elevam o valor
acima de um SaaS de gestão genérico.

> _Estimativa elaborada a partir de métricas reais do repositório em 26/06/2026.
> Faixa de incerteza: ±30%._
