# Status de Implementação — Meu Jet

**Data:** 2026-07-11 · **Estado:** em produção · **Testes:** ~1072 (`mvn test`) · **Migrations:** V001–V046
**Arquitetura:** monolito modular (Spring Modulith) — Java 21 / Spring Boot 3.3

Produção: `www.meujet.com.br` (site + marketplace) · `app.meujet.com.br` (backoffice) ·
`cliente.meujet.com.br` (portal do cliente). Dev espelha em `*.pegaojet.com.br`.

## Entregue (em produção)

### Núcleo operacional (backoffice)
- Frota (modelos, jetskis, mídias de marketplace), agenda/reservas com conflito e prontidão,
  balcão em 7 passos (cliente → passeio → habilitação → documentos → termos → pagamento → emissão).
- Check-in/check-out com fotos obrigatórias, itens opcionais, combustível (RN03, 3 modos),
  manutenção/OS (RN06 bloqueia agenda), despesas.
- Financeiro: folio por reserva/locação (`reserva_lancamento`, V035–V037), pagamento presencial
  integral no balcão (com **cobrança PIX**: QR Code, copia-e-cola por e-mail e WhatsApp), estorno
  manual, NO_SHOW (RN02), fechamento diário por forma de pagamento e mensal com comissões
  (RN04, hierarquia campanha → modelo → faixa → default) e bônus/pagamento de vendedores.
- Clientes: ficha completa, captura de lead (V040), anexos/documentos LGPD com propagação de
  identidade, pré-conta com convite (claim-token).
- Perfil self-service do staff (`/dashboard/perfil`, V052): nome/telefone/avatar (tabela global
  `usuario`, storage `usuarios/{id}/`), troca de senha in-app com validação da senha atual via
  direct grant (client `jetski-password-check`) — oculta para conta só-Google; menu do usuário
  na sidebar com nome/foto reais e links Perfil/Configurações.

### Documentação náutica (NORMAM-212)
- Emissão CHA/EMA com anexos 5-B-1/5-B-2/5-C, instrutores, envio à Marinha por e-mail com
  gate de documentação completa, devolutiva da Marinha anexável (V038), PDF consolidado.
- **Robô GRU** (HTTP em Java, validado no site real): geração PIX/boleto, verificação de
  pagamento, comprovante — com fallback manual (ver `GRU_HTTP_CONTRACT.md`).
- Assinatura eletrônica de termos: fases A (auditoria + carimbo RFC 3161), B (OTP e-mail/
  WhatsApp) e C2 (PAdES opt-in) — C1 (selfie/geoloc) e C3 (ICP/gov.br) pendentes.
- **Habilitação temporária é dado GLOBAL do cliente** (`customer_habilitacao`, V043): nasce na
  emissão e sobrevive a reset/exclusão/suspensão da loja de origem (reuso entre lojas garantido).
- **Emissão delegada** (V047/V048, `EMISSAO_DELEGADA_SPEC.md`): operadora não licenciada emite
  CHA-MTE em nome de EAMA parceira da MESMA capitania — catálogo de capitanias, perfil emissor
  validado pelo superadmin, vínculo bilateral com termo + kill switch da EAMA, estorno
  anti-fraude do bônus, snapshot do emissor no documento, espelho/painel da EAMA com reenvio
  sem crédito, metering por emissor. Split de módulos EMISSAO_PROPRIA × EMISSAO_DELEGADA.
  Pendências de polish na §12 da spec (UI superadmin, grandfathering do portão da própria).

### Portal do cliente (P0–P4 completos)
- Identidade própria (login e-mail/CPF, role CLIENTE, vínculo explícito — nunca JIT),
  perfil global (`customer_profile`), reserva online com sinal PIX 30% + comprovante,
  termos/CHA remotos, EMA/GRU self-service, histórico, avaliações, white-label por loja.
- Login com Google (IdP broker no realm; botões no portal e no backoffice via
  `kc_idp_hint`; sem credenciais `GOOGLE_*` o IdP fica desabilitado) + dedupe de
  contas por CPF: gate obrigatório no primeiro acesso (escape p/ estrangeiro) e
  colisão de CPF vira unificação verificada por OTP (código ao e-mail da conta
  dona; identidade Google transferida, duplicata descartada, auditoria global V051).
- Tela de login com a marca Meu Jet: tema Keycloak "meujet" em React/Keycloakify
  (`infra/keycloak-theme/`, 15 telas, pt-BR/en), servido por imagem custom do
  Keycloak buildada no deploy; "esqueci minha senha" cria senha para conta
  só-Google (orientado na própria tela).
- Login identifier-first no portal: cliente digita e-mail ou CPF → tela 2 com
  SENHA à frente ("Entrando como X", esqueci minha senha) e "Receber código
  por e-mail" sob demanda — nenhum e-mail sai sem o cliente pedir (código de
  6 dígitos: TTL 10 min, 5 tentativas, cooldown 60 s, single-use). Ambos
  validados no próprio SPI; anti-enumeração nas duas vias (tela 2 aparece
  sempre, erro genérico único) e brute force do realm contando explicitamente
  (código e senha; reset no sucesso). Google segue na tela 1. SPI próprio
  `meujet-email-code`
  (`infra/keycloak-extensions/email-code/`, com testes) + flow `portal-browser`
  com override só no client do portal (backoffice intocado); e-mail via email
  theme `meujet-email` no JAR do SPI (Mailpit dev / Gmail prod). Realm vivo
  converge via `infra/prod/configure-keycloak-email-code.sh` (`ROLLBACK=1` =
  kill switch, volta ao flow de senha sem restart); login por código também
  marca e-mail verificado e limpa `UPDATE_PASSWORD` pendente do balcão.
- 2FA opt-in unificado (TOTP + WebAuthn/passkey): fatores vivem no Keycloak e
  valem pra QUALQUER porta de entrada da conta — código, senha e Google (via
  post-broker flow no IdP) — nos dois apps (identidade única). Subflow
  condicional `portal-2fa` (condition-user-configured: só exige de quem
  cadastrou); cadastro/remoção self-service nos perfis do portal e do
  backoffice via AIA (`kc_action=CONFIGURE_TOTP` / `webauthn-register` /
  `delete_credential:id`, remoção com step-up); listagem via
  `/v1/user/me/credentials` e `/v1/customers/self/credentials`. Converge via
  `infra/prod/configure-keycloak-2fa.sh` (`ROLLBACK=1` = kill switch). Toggle
  explícito ativar/desativar nos perfis: ativar = cadastrar fator; **desativar
  = remove TODOS os fatores de uma vez** (Required Action custom `mj-2fa-disable`
  no SPI) — sempre com **step-up** (`max_age=0` força reautenticação completa,
  desafiando o próprio fator antes de removê-lo); remover fator individual idem.

### Plataforma (super admin)
- Onboarding self-service: signup → aprovação → trial 14 dias com expiração/suspensão
  automática → checklist de primeiros passos (7 itens). Créditos de adesão automáticos.
- Créditos de emissão: ledger append-only (trigger de banco anti-DELETE), débito na emissão,
  compra via PIX com conferência manual do super admin. Metering (DOCUMENTO/GRU/PREVIA).
- **Reset de empresa** em 3 níveis (operacional/frota/total) com preview, classificação
  obrigatória das tabelas (teste-guarda) e export automático prévio.
- **Export de arquivamento** (.zip: dados JSON de todas as tabelas + arquivos do storage).
- **Exclusão de empresa**: carência 30 dias (cancelável) ou imediata; expurgo com tombstone
  (slug liberado, sensíveis anonimizados; ledger/metering/auditoria preservados); job diário
  (05:45) executa expurgos vencidos e remove exports >90 dias.
- **Billing manual assistido** (V045): fatura mensal por plano pago (job 06:00), PIX da
  plataforma, empresa informa txid → conferência no painel → PAGA; inadimplente suspende
  após carência de 7 dias. Página "Plano e faturas" (uso × limites + faturas + PIX).
- **Enforcement de limites do plano** (`plano.limites` jsonb): usuários, frota e locações/mês
  com negação de negócio e mensagem de upgrade.
- **Módulos por plano** (V046, `plano.modulos` jsonb): super admin define a oferta por plano
  (Emissão à Marinha, Comissões, Manutenção, Fechamentos, Relatórios, Despesas, Marketplace,
  Loja online); NULL = todos. Gating em três camadas: menu do backoffice (itens somem), API
  (`ModuloPlanoInterceptor`, 400 com pedido de upgrade; superadmin isento; cache Redis com
  evict na troca) e canais públicos — Marketplace tira a empresa do marketplace agregado;
  Loja online desativa a vitrine própria, a disponibilidade pública e a reserva online.

### Infra/segurança/operacional
- CI (testes + Modulith + E2E Newman 75 asserções) → CD automático em produção (Oracle ARM,
  Docker Compose, Cloudflare Tunnel). Deploy não-destrutivo com verificação de RLS.
- RLS em todas as tabelas multi-tenant + guarda no deploy (02-verify-rls) + RLS na tabela
  `tenant` (V042, GUC `app.unrestricted` p/ superadmin); policies de marketplace escopadas.
- Backup diário automatizado (systemd timer 04:00) com off-site Google Drive; restore testado.
- Observabilidade: Grafana/Loki/Prometheus, 5 alertas por e-mail. Storage MinIO com prefixo
  por tenant e URLs presignadas.
- Termos de Uso e Política de Privacidade publicados (`/termos`, `/privacidade`) — Fcf
  Tecnologia Ltda; revisão jurídica pendente.

## Pendências conhecidas

**Curto prazo:**
- E-mail transacional dedicado (hoje: Gmail com fallback `PLATFORM_SMTP_*` já preparado —
  trocar de provedor é só configuração).
- Validação server-side de upload presignado (content-type/tamanho).

**Backlog (v2/estrutural):**
- Gateway de pagamento (billing hoje é manual assistido); cobrança por metering de emissões.
- LGPD titular: exportação/anonimização a pedido (hoje soft-delete de cliente).
- HA/segunda VM (produção é VM única); retenção de logs >7 dias.
- Assinatura C1 (selfie/geoloc) e C3 (ICP-Brasil/gov.br); troca de CPF do cliente com OTP;
  RN05 (caução/danos).
- Mobile (KMM): código no working dir separado `/mnt/c/repos/jetski-mobile` (login, check-in,
  fotos, offline em desenvolvimento) — docs em `mobile/*.md`.

## Como manter este documento
Atualize a data e os contadores ao fechar blocos de trabalho (`mvn test` para o número real;
`ls backend/src/main/resources/db/migration | tail -1` para a última migration). Specs de
projeto (`PORTAL_CLIENTE_SPEC.md`, `GRU_ROBO_SPEC.md`, `inicial.md`) são históricas — o estado
vigente é ESTE arquivo + `CLAUDE.md` + `DEPLOY.md`.
