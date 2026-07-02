# Meu Jet — Guia de Marca

> Identidade visual **náutico premium** da plataforma. Preview aprovável em artifact
> (sessão de design, jul/2026). Aplicação: backoffice, portal do cliente, site público,
> e-mails. Logo escolhido: **Crista Dupla** (recomendação da proposta v1).

## Nome

- **"Meu Jet"** (com espaço) em qualquer texto voltado a pessoas: títulos, e-mails,
  from-name, rodapés, metadata.
- **Wordmark**: `MEU JET` em Playfair Display SemiBold, caixa alta, tracking largo
  (`letter-spacing: .2em`).
- Identificadores **técnicos permanecem `meujet`** (domínio meujet.com.br, slugs,
  `spring.application.name`) e o **CN do certificado PAdES permanece `MeuJet`**
  (mudar CN invalida/complica a cadeia de assinatura — decisão registrada).

## Logo — Crista Dupla

Duas ondas em movimento (o rastro do jet): traço **dourado** (esteira ao sol) sobre
traço **navy** (em fundo claro) ou **espuma** (em fundo escuro). Paths SVG canônicos
(viewBox `0 0 64 40`, `stroke-linecap="round"`, `stroke-width` 3.6–4.4):

```svg
<path d="M5 15.5 C 15 15.5, 19 6, 30 6 C 39.5 6, 42 12.5, 59 10.5"
      fill="none" stroke="#C9A24B"/>                     <!-- onda dourada -->
<path d="M5 29 C 13 29, 18.5 20.5, 28 20.5 C 37 20.5, 42.5 27.5, 59 25"
      fill="none" stroke="#1E4266"/>  <!-- navy em claro; #F8F4EA em escuro -->
```

Assets: `frontend/jetski-backoffice/public/{logo.svg, logo-dark.svg, icon.svg}` +
`app/icon.svg` (favicon nativo Next 15). Componente: `components/logo.tsx`.

## Paleta

Tokens em **oklch** no código (`globals.css`); hex aqui por legibilidade.

### Marca

| Token | oklch | hex | Uso |
|---|---|---|---|
| Navy Abissal (navy-950) | `oklch(0.19 0.042 255)` | `#0A1628` | bg dark, sidebar (sempre), hero |
| Navy Profundo (navy-900) | `oklch(0.22 0.05 252)` | `#12263F` | card dark, headings |
| Navy Marinha (navy-700) | `oklch(0.36 0.075 252)` | `#1E4266` | **--primary light** |
| Navy Oceano (navy-500) | `oklch(0.50 0.09 248)` | `#33689A` | links, hover |
| Navy Céu (navy-400) | `oklch(0.65 0.09 245)` | `#5E95C3` | **--primary dark** |
| Dourado (gold-500) | `oklch(0.75 0.12 85)` | `#C9A24B` | --accent · superfícies/ícones/bordas |
| Bronze (gold-600) | `oklch(0.66 0.115 80)` | `#B78934` | dourado com mais contraste |
| Champagne (gold-300) | `oklch(0.85 0.08 90)` | `#E3CF9E` | --accent dark |

### Neutros (areia)

| Token | hex | Uso |
|---|---|---|
| Espuma (sand-50) | `#FCFAF6` | --background light |
| Areia (sand-100) | `#F8F4EA` | --secondary, painéis |
| Duna (sand-300) | `#E3D9C2` | bordas premium |
| Espuma clara | `#EFEAE0` | texto primário sobre navy |
| Névoa | `#B9C4D2` | texto secundário sobre navy |
| Aço | `#7E8DA1` | texto terciário sobre navy |

### Semânticas (status — separadas da marca)

| Token | hex | Uso |
|---|---|---|
| --success | `#2C965D` | disponível, pago, concluído |
| --warning | `#DFA11A` | pendência, manutenção |
| --info | `#3788BB` | em andamento |
| --destructive | `#D64545` | atraso, erro, exclusão |

### Gráficos (categóricas — validadas para CVD e contraste ≥ 3:1)

Ordem fixa; a cor segue a série, nunca a posição.

| Slot | Light | Dark | oklch (light / dark) |
|---|---|---|---|
| chart-1 navy | `#0E5794` | `#4D92CA` | `0.45 0.12 250` / `0.64 0.11 245` |
| chart-2 bronze | `#A87600` | `#B48C2B` | `0.60 0.13 80` / `0.66 0.12 85` |
| chart-3 mar | `#008F90` | `#009D9E` | `0.58 0.12 195` / `0.63 0.11 195` |
| chart-4 violeta | `#7E5DB1` | `#9076BE` | `0.55 0.13 300` / `0.62 0.11 300` |
| chart-5 coral | `#D2643C` | `#D37040` | `0.63 0.15 40` / `0.65 0.14 45` |

Superfícies de validação: light `#FFFFFF` (card), dark `#071B31` (card de gráfico).

## Tipografia

- **Display**: Playfair Display (títulos de página, hero, números de destaque, wordmark).
  Classe utilitária `.font-display`.
- **Interface**: Geist (corpo, formulários, tabelas, navegação).
  Valores financeiros sempre com `font-variant-numeric: tabular-nums`.
- **Mono**: Geist Mono (códigos, IDs).
- Carregadas via `next/font/google` em `app/layout.tsx`.

## Regras de aplicação

**Sempre**
- Dourado como *acento de posição*: item ativo, filete, ícone, selo, CTA de destaque no site.
- Ação primária navy no app claro; Navy Céu no escuro.
- Sidebar sempre Navy Abissal, nos dois modos — é a âncora da marca.
- Status sempre com ponto/ícone + rótulo, nunca só cor.
- Playfair só em display; corpo e formulários sempre Geist.

**Nunca**
- Texto dourado pequeno sobre fundo claro (contraste 2,5:1 — reprova WCAG).
- Laranja e azul-céu antigos (`#f97316`, `#0ea5e9`) — fora da identidade.
- Dourado para estados de alerta (warning é âmbar semântico, separado da marca).
- Playfair em tabelas, inputs ou textos longos.

## White-label por tenant (Fase 6)

Tenants podem personalizar cor primária, cor secundária e logo em
**Configurações › Marca** (`tenant.branding` jsonb; endpoints
`GET/PUT /v1/tenants/{id}/config/branding` + upload de logo). Regras:

- **Fallback sempre Meu Jet**: campos nulos ⇒ tokens padrão do `globals.css`.
- **Guardrail de contraste**: o backend rejeita cor primária com luminância
  relativa > 0.5 (texto branco em botões ficaria ilegível).
- **Logo**: PNG/JPEG/WebP ≤ 512 KB. **SVG não é aceito** (risco XSS). Servido
  como data URL no GET de branding.
- Aplicação em runtime via CSS variables (`--primary`, `--ring`,
  `--sidebar-primary`, `--gold`) pelo `TenantThemeProvider` — só no grupo
  `(dashboard)`; login, site público e e-mails permanecem Meu Jet.
- Fora de escopo (futuro): paleta dark própria por tenant, branding em
  e-mails/PDFs, branding real no portal do cliente (aguarda portal com backend).
