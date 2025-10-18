# Visão do Produto

Sistema para gestão de locações de jetski com controle de frota, agenda, operação diária, manutenção, abastecimento, comissões e fechamentos diário/mensal, incluindo captura e guarda de fotos (check‑in/check‑out/incidentes).

---

## Personas

* **Operador de Píer**: realiza check‑in/out, registra abastecimento/fotos, abre/encerra locações.
* **Vendedor/Parceiro**: origina reservas/locações e recebe comissão.
* **Gerente de Operações**: acompanha frota, status, autoriza descontos, fecha o dia.
* **Mecânico**: recebe ordens de serviço de manutenção.
* **Financeiro**: faz conferência de fechamentos, faturamento e repasses de comissão.
* **Cliente**: realiza reserva/assinatura de termos, pagamento e avaliação.

---

## Escopo do MVP

1. Cadastro de jetski, modelos e preços (por tempo e por faixa horária).
2. Agenda e reserva simples (data/hora de saída, duração prevista, responsável/vendedor).
3. Abertura/encerramento de locação com leitura de horímetro/odômetro de horas e fotos (check‑in/out).
4. Cálculo automático de horas usadas e valor devido (com regras de tolerância e hora extra).
5. Registro de abastecimento (litros, custo, antes/depois da locação).
6. Registro básico de manutenção preventiva/corretiva e indisponibilidade.
7. Comissionamento por locação (percentual fixo por vendedor/parceiro ou por modelo).
8. Fechamento diário (caixa do dia) e mensal (consolidação), com relatórios exportáveis.
9. Perfis e permissões (Operador, Vendedor, Gerente, Mecânico, Financeiro, Admin).
10. Armazenamento das imagens em nuvem com vínculo aos eventos.

---

## Modo Multi-tenant (SaaS B2B)

Para atender múltiplas empresas (clientes B2B) com **isolamento lógico de dados** e configurações próprias.

### Objetivos

* Atender várias **empresas** (tenants) em um único stack, com segurança, personalização (branding/domínios), planos e **faturamento** separados.
* Facilitar **onboarding self‑service** e **separação operacional** (fechamentos, comissões, relatórios) por empresa.

### Modelos de Isolamento de Dados

1. **Coluna `tenant_id` + RLS (Row Level Security) no PostgreSQL**  ← *recomendado para o MVP pela simplicidade e custo*.
2. **Schemas por tenant** (migrações por schema; bom para clientes grandes, mais complexo operacionalmente).
3. **Bancos por tenant** (máximo isolamento; custo e operação maiores; considerar em enterprise).

### Identidade e Acesso (Keycloak)

* **Opção A – Realm único multi‑tenant**: usar `tenant_id` (ou `org_id`) como **claim** no token (ex.: `tenant: "acme"`) e **grupos/roles** por tenant (RBAC por organização).
* **Opção B – Realm por tenant**: melhor isolamento e customizações de login/fluxos; aumenta overhead operacional.
* **Sugestão**: começar com **Realm único** + claim `tenant_id`, mapeando perfis (Admin do Tenant, Gerente, Operador, Vendedor, Mecânico, Financeiro).

### Roteamento / Descoberta do Tenant

* Subdomínios: `https://{tenant}.seusistema.com`  **ou** prefixo: `/t/{tenant}`.
* Header opcional de roteamento: `X-Tenant-Id` (validado contra domínio/usuário).
* KMM: guardar `tenant` selecionado (ou deduzido do subdomínio) e incluir em todas as requisições.

### Armazenamento de Imagens

* **Bucket único** com **prefixo por tenant**: `tenant_id/locacao/{id}/foto_{n}.jpg` + política de acesso; ou **bucket por tenant** em enterprise.

### Planos e Cobrança

* **Planos** (Basic/Pro/Enterprise) com limites (frota, usuários, armazenamento, taxa de transação) e **billing mensal** (boleto/PIX/cartão).
* **Uso** (horas locadas, fotos, GB armazenados) para **metering** e upsell.

### Observabilidade

* Labels por `tenant_id` em logs/métricas/traços; rate‑limit por tenant; cotas de storage.

---

## Requisitos Funcionais (RF)

**RF01 – Cadastro**

* RF01.1 Cadastrar **Modelo** (nome, fabricante, potência, capacidade, preço base/hora, caução, foto referência).
* RF01.2 Cadastrar **Jetski** (nº de série/placa, modelo, ano, horímetro atual, status: disponível/locado/manutenção/indisponível).
* RF01.3 Cadastrar **Planos/Preços**: preço por hora, pacotes (ex.: 30/60/120 min), adicional por hora, tarifas por faixa horária (alta/baixa), política de tolerância (minutos grátis para atraso), **taxas** (limpeza, dano, combustível) e **descontos** (cupom, convênio).
* RF01.4 Cadastrar **Vendedor/Parceiro** com **tabela de comissão** (percentual padrão, por modelo, por pacote, por campanha).

**RF02 – Reserva e Agenda**

* RF02.1 Criar reserva (cliente, jetski/modelo, data/hora de saída e retorno previsto, duração, vendedor/parceiro, canal de origem).
* RF02.2 Bloquear agenda do jetski no período reservado.
* RF02.3 Notificar conflitos e sugerir alternativas (outro horário ou outro jetski mesmo modelo).
* RF02.4 Possibilitar pagamento antecipado (sinal) e emissão de comprovante.

**RF03 – Check‑in (Saída)**

* RF03.1 Capturar **fotos** obrigatórias (frente, laterais, painel do horímetro, casco inferior opcional) e armazenar com timestamp e geolocalização.
* RF03.2 Registrar **checklist** (EPIs, boia, lacres, bem conservado?).
* RF03.3 Ler e salvar **horímetro inicial**.
* RF03.4 Gerar **termo de responsabilidade** com assinatura digital do cliente.

**RF04 – Operação e Telemetria (opcional no MVP)**

* RF04.1 Registrar eventos em tempo real (paradas, alertas, SOS) se houver dispositivo/APP.

**RF05 – Check‑out (Retorno)**

* RF05.1 Capturar novas **fotos** (incluindo painel com horímetro final).
* RF05.2 Registrar **horímetro final** e calcular **horas usadas** = (final – inicial).
* RF05.3 Aplicar regras de **tolerância** e **hora extra**.
* RF05.4 Calcular **valor devido** (tempo usado + taxas – descontos – sinal + combustível, se aplicável).
* RF05.5 Emitir **recibo/nota** e encerrar locação, liberando jetski.

**RF06 – Abastecimento**

* RF06.1 Registrar abastecimento (data/hora, litros, preço por litro, custo total, responsável, foto do painel/bomba opcional), vinculado à locação ou à frota.
* RF06.2 Atualizar **custo operacional** da locação/dia.

**RF07 – Manutenção**

* RF07.1 Abrir **OS** de manutenção (preventiva/corretiva), prioridade, estimativa, peças e mão de obra.
* RF07.2 Mudar status do jetski para **manutenção/indisponível** e bloquear agenda.
* RF07.3 Histórico de manutenções por jetski e alertas por horas de uso (ex.: revisão a cada 50h).

**RF08 – Comissões (Aprimorado)**

* RF08.1 **Política de cálculo** (hierarquia de aplicação): 1) Regra específica da campanha ativa → 2) Regra por modelo → 3) Regra por faixa de duração (min) → 4) Regra padrão do vendedor/parceiro. A primeira regra que casar encerra a busca.
* RF08.2 **Base de cálculo**: receita **comissionável** = valor da locação após tolerância e arredondamento **menos** itens não comissionáveis (combustível, taxas de limpeza/avaria, multas) e **antes** de impostos. Configurável por política.
* RF08.3 **Tipos de comissão**: percentual (%), valor fixo por locação, ou escalonada (ex.: 10% até 120 min; 12% acima de 120 min).
* RF08.4 **Exceções**: permitir zerar comissão em cupons/campanhas específicas ou em locações cortesia.
* RF08.5 **Liquidação**: relatório de comissões por período, com status (prevista, aprovada, paga) e centro de custo.
* RF08.6 **Transparência**: demonstrativo por locação com fórmula aplicada, regra vencedora e motivo.

**RF09 – Fechamentos e Relatórios**

* RF09.1 **Fechamento diário**: total de locações, horas usadas por jetski, receita bruta/líquida, custos de combustível, adiantamentos, estornos, caixa final.
* RF09.2 **Fechamento mensal**: consolidação por modelo/jetski/vendedor, ocupação (% horas locadas / horas disponíveis), ticket médio, receita, custos, margem, comissões.
* RF09.3 Exportar PDF/CSV e integração contábil (arquivo padrão).

**RF10 – Imagens**

* RF10.1 Armazenar fotos em storage de nuvem (ex.: S3/GCS) com metadados: locação, jetski, tipo (check‑in/out/avaria/abastecimento), hash de integridade.
* RF10.2 Visualização com comparação lado‑a‑lado (saída × retorno).

**RF11 – Segurança/Acesso**

* RF11.1 Perfis: Admin, Gerente, Operador, Vendedor, Mecânico, Financeiro.
* RF11.2 Trilhas de auditoria (quem fez o quê, quando, IP/dispositivo).
* RF11.3 LGPD: consentimento, política de retenção de imagens/dados, minimização.

**RF12 – Multi‑tenant (novo)**

* RF12.1 **Cadastro de Empresa (Tenant)**: razão social, CNPJ, contatos, domínios permitidos, timezone, moeda, branding (logo/cores), parâmetros fiscais.
* RF12.2 **Assinatura/Plano**: plano, limites (frota, usuários, armazenamento), ciclo de faturamento, meios de pagamento, status (trial/ativo/suspenso).
* RF12.3 **Isolamento de dados** por `tenant_id` em todas as entidades e **RLS** ativo no banco.
* RF12.4 **Domínio e roteamento**: subdomínio `{tenant}.seusistema.com` ou `/t/{tenant}`; validação cruzada de token/tenant.
* RF12.5 **Configurações por tenant**: política de combustível/comissão, tolerância, arredondamento, templates de recibo/termo, feriados.
* RF12.6 **Usuários e papéis por tenant** (RBAC), convites e SSO opcional.
* RF12.7 **Relatórios e fechamentos por tenant** (diário e mensal), com exportação segregada.
* RF12.8 **Quota e rate limit** por tenant (API e storage) e alertas de estouro.

---

## Regras de Negócio (RN)

* **RN01 Horas faturáveis**: arredondamento ao múltiplo de 15 min; tolerância de X min sem cobrança; acima disso, cobra‑se pró‑rata.
* **RN02 Cancelamentos/No‑show**: políticas por antecedência (ex.: 24h reembolso integral; <6h multa de 50%; no‑show cobra 1h mínima).
* **RN03 Combustível (aprimorado)**: três modos configuráveis por **Política de Combustível** (por modelo, por jetski ou global):

  * **Incluso**: preço/hora já inclui combustível (não comissionável). Não registrar cobrança ao cliente, mas manter **abastecimentos** para custo.
  * **Medido**: cobrar litros consumidos = (litros abastecidos pós-locação – litros pré-locação) × preço do dia. Se houver telemetria/boia medidora, usar leitura; senão política de aferição por tanque cheio.
  * **Taxa fixa por hora**: valor fixo × horas faturáveis (não comissionável).
  * Preço do dia = média ponderada dos abastecimentos do dia (ou tabela de preço configurada) com fallback para preço médio semanal.
* **RN04 Comissão (aprimorado)**: calcular sobre **receita comissionável** (ver RF08.2) aplicando a **hierarquia de política** (campanha > modelo > faixa de duração > padrão do vendedor). Itens de combustível e multas **não são comissionáveis** por padrão. Comissão é **apurada no fechamento mensal** e pode ser ajustada por aprovação do gerente.
* **RN05 Caução e danos**: caução bloqueada na abertura; desconto por avarias conforme tabela; permitir anexar laudo/fotos.
* **RN06 Disponibilidade**: jetski “manutenção” não pode ser reservado; retorno reabre agenda.
* **RN07 Revisão por horas**: gerar alerta ao atingir marcos (ex.: 50h, 100h) pela soma do horímetro.

---

## Fluxos Principais (UML leve)

1. **Reserva → Check‑in → Locação em curso → Check‑out → Pagamento/Recibo → Comissão**
2. **Abastecimento vinculado à locação** (antes/depois) → custo do dia.
3. **Indisponibilidade por manutenção** → OS → retorno à disponibilidade.
4. **Fechamento diário** → consolida locações, custos, caixa → bloqueia edição retroativa.

---

## Modelo de Dados (Entidades)**

* **Tenant**(id, slug, razao_social, cnpj, timezone, moeda, contato, status, branding_json)
* **Plano**(id, nome, limites_json, preco_mensal)
* **Assinatura**(id, tenant_id, plano_id, ciclo, dt_inicio, dt_fim?, status, pagamento_cfg_json)
* **Usuario**(id, email, nome, ativo)
* **Membro**(id, tenant_id, usuario_id, papeis[])  ← vínculo usuário‑tenant
* **ConfigDominio**(id, tenant_id, subdominio, custom_domain?)
* **Modelo**(id, tenant_id, nome, fabricante, potência, preço_base_hora, pacotes_json, tolerancia_min, taxa_hora_extra, inclui_combustivel, caucao)
* **Jetski**(id, tenant_id, modelo_id, serie, ano, horimetro_atual, status)
* **Vendedor**(id, tenant_id, nome, documento, tipo, regra_comissao_json)
* **Cliente**(id, tenant_id, nome, documento, contato, termo_aceite)
* **Reserva**(id, tenant_id, cliente_id, jetski_id?, vendedor_id, dt_saida_prev, dt_retorno_prev, duracao_prev_min, sinal_pago, status)
* **Locacao**(id, tenant_id, reserva_id?, jetski_id, vendedor_id, cliente_id, dt_checkin, horimetro_ini, dt_checkout, horimetro_fim, minutos_usados, valor_calculado, descontos, taxas, combustivel_litros, combustivel_custo, valor_pago, status, fuel_policy_id?, commission_policy_id?)
* **Foto**(id, tenant_id, locacao_id?, jetski_id, tipo, url, hash, timestamp, geo)
* **Abastecimento**(id, tenant_id, jetski_id, locacao_id?, dt, litros, preco_litro, custo_total, responsavel, foto_id?)
* **OS_Manutencao**(id, tenant_id, jetski_id, tipo, descricao, prioridade, status, horas_previstas, custo_previsto, custo_real, dt_abertura, dt_fechamento)
* **FechamentoDiario**(id, tenant_id, data, totais_json, caixa_inicial, caixa_final, assinado_por)
* **FechamentoMensal**(id, tenant_id, ano_mes, totais_json, assinado_por)
* **Auditoria**(id, tenant_id, usuario, acao, recurso, recurso_id, timestamp, ip)

> Observação: `tenant_id` presente em **todas** as entidades operacionais.

---

## Esquema Relacional (SQL inicial)

```sql
CREATE TABLE tenant (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug TEXT UNIQUE NOT NULL, -- usado em subdominio/rota
  razao_social TEXT NOT NULL,
  cnpj TEXT,
  timezone TEXT DEFAULT 'America/Sao_Paulo',
  moeda TEXT DEFAULT 'BRL',
  contato JSONB,
  status TEXT CHECK (status IN ('trial','ativo','suspenso','cancelado')) DEFAULT 'trial',
  branding_json JSONB
);

CREATE TABLE plano (
  id SERIAL PRIMARY KEY,
  nome TEXT NOT NULL,
  limites_json JSONB,
  preco_mensal NUMERIC(10,2)
);

CREATE TABLE assinatura (
  id SERIAL PRIMARY KEY,
  tenant_id UUID REFERENCES tenant(id) ON DELETE CASCADE,
  plano_id INT REFERENCES plano(id),
  ciclo TEXT CHECK (ciclo IN ('mensal','anual')) DEFAULT 'mensal',
  dt_inicio DATE NOT NULL,
  dt_fim DATE,
  status TEXT CHECK (status IN ('ativa','suspensa','cancelada')) DEFAULT 'ativa',
  pagamento_cfg_json JSONB
);

-- Usuários e vínculo com tenants
CREATE TABLE usuario (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT UNIQUE NOT NULL,
  nome TEXT,
  ativo BOOLEAN DEFAULT TRUE
);

CREATE TABLE membro (
  id SERIAL PRIMARY KEY,
  tenant_id UUID REFERENCES tenant(id) ON DELETE CASCADE,
  usuario_id UUID REFERENCES usuario(id) ON DELETE CASCADE,
  papeis TEXT[] -- ex.: {'ADMIN_TENANT','GERENTE','OPERADOR'}
);

-- Exemplo de ajuste em tabelas operacionais (mostrar padrão)
ALTER TABLE modelo ADD COLUMN tenant_id UUID REFERENCES tenant(id);
ALTER TABLE jetski ADD COLUMN tenant_id UUID REFERENCES tenant(id);
ALTER TABLE vendedor ADD COLUMN tenant_id UUID REFERENCES tenant(id);
ALTER TABLE cliente ADD COLUMN tenant_id UUID REFERENCES tenant(id);
ALTER TABLE reserva ADD COLUMN tenant_id UUID REFERENCES tenant(id);
ALTER TABLE locacao ADD COLUMN tenant_id UUID REFERENCES tenant(id);
ALTER TABLE foto ADD COLUMN tenant_id UUID REFERENCES tenant(id);
ALTER TABLE abastecimento ADD COLUMN tenant_id UUID REFERENCES tenant(id);
ALTER TABLE os_manutencao ADD COLUMN tenant_id UUID REFERENCES tenant(id);
ALTER TABLE fechamentoDiario ADD COLUMN tenant_id UUID REFERENCES tenant(id);
ALTER TABLE fechamentoMensal ADD COLUMN tenant_id UUID REFERENCES tenant(id);
ALTER TABLE auditoria ADD COLUMN tenant_id UUID REFERENCES tenant(id);

-- RLS (Row Level Security) exemplo
ALTER TABLE locacao ENABLE ROW LEVEL SECURITY;
CREATE POLICY locacao_tenant_isolation ON locacao
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
-- Em cada request, setar: SELECT set_config('app.tenant_id', '<uuid-do-tenant>', true);

-- Índices compostos (tenant + fk) para performance
CREATE INDEX idx_locacao_tenant ON locacao(tenant_id);
CREATE INDEX idx_jetski_tenant ON jetski(tenant_id);
```

```sql
-- Políticas de Comissão
CREATE TABLE commission_policy (
  id SERIAL PRIMARY KEY,
  nome TEXT NOT NULL,
  tipo TEXT CHECK (tipo IN ('padrao_vendedor','por_modelo','por_duracao','campanha')) NOT NULL,
  vendedor_id INT,
  modelo_id INT,
  duracao_min_inicio INT,
  duracao_min_fim INT,
  percentual NUMERIC(5,2),
  valor_fixo NUMERIC(10,2),
  escalonada JSONB, -- ex.: [{"ate_min":120,"percentual":10.0},{"acima_min":120,"percentual":12.0}]
  comissiona_combustivel BOOLEAN DEFAULT FALSE,
  comissiona_taxas BOOLEAN DEFAULT FALSE,
  ativa BOOLEAN DEFAULT TRUE,
  dt_inicio DATE,
  dt_fim DATE
);

-- Políticas de Combustível
CREATE TABLE fuel_policy (
  id SERIAL PRIMARY KEY,
  nome TEXT NOT NULL,
  escopo TEXT CHECK (escopo IN ('global','por_modelo','por_jetski')) NOT NULL,
  modelo_id INT,
  jetski_id INT,
  modo TEXT CHECK (modo IN ('incluso','medido','taxa_fixa')) NOT NULL,
  taxa_fixa_hora NUMERIC(10,2),
  usar_media_semanal BOOLEAN DEFAULT TRUE,
  ativa BOOLEAN DEFAULT TRUE
);

-- Preço de Combustível por Dia (opcional para média)
CREATE TABLE fuel_price_day (
  dt DATE PRIMARY KEY,
  litros NUMERIC(10,2) NOT NULL,
  custo_total NUMERIC(10,2) NOT NULL
);

-- Ajustes nas tabelas existentes (exemplos)
ALTER TABLE locacao ADD COLUMN fuel_policy_id INT REFERENCES fuel_policy(id);
ALTER TABLE locacao ADD COLUMN commission_policy_id INT REFERENCES commission_policy(id);
```

sql
CREATE TABLE modelo (
id SERIAL PRIMARY KEY,
nome TEXT NOT NULL,
fabricante TEXT,
potencia_hp INT,
preco_base_hora NUMERIC(10,2) NOT NULL,
tolerancia_min INT DEFAULT 5,
taxa_hora_extra NUMERIC(10,2) DEFAULT 0,
inclui_combustivel BOOLEAN DEFAULT FALSE,
caucao NUMERIC(10,2) DEFAULT 0
);

CREATE TABLE jetski (
id SERIAL PRIMARY KEY,
modelo_id INT REFERENCES modelo(id),
serie TEXT UNIQUE,
ano INT,
horimetro_atual NUMERIC(10,2) DEFAULT 0,
status TEXT CHECK (status IN ('disponivel','locado','manutencao','indisponivel'))
);

CREATE TABLE vendedor (
id SERIAL PRIMARY KEY,
nome TEXT NOT NULL,
documento TEXT,
tipo TEXT CHECK (tipo IN ('interno','parceiro')),
regra_comissao_json JSONB
);

CREATE TABLE cliente (
id SERIAL PRIMARY KEY,
nome TEXT NOT NULL,
documento TEXT,
contato JSONB,
termo_aceite BOOLEAN DEFAULT FALSE
);

CREATE TABLE reserva (
id SERIAL PRIMARY KEY,
cliente_id INT REFERENCES cliente(id),
jetski_id INT REFERENCES jetski(id),
vendedor_id INT REFERENCES vendedor(id),
dt_saida_prev TIMESTAMP,
dt_retorno_prev TIMESTAMP,
duracao_prev_min INT,
sinal_pago NUMERIC(10,2) DEFAULT 0,
status TEXT CHECK (status IN ('ativa','cancelada','no_show','convertida'))
);

CREATE TABLE locacao (
id SERIAL PRIMARY KEY,
reserva_id INT REFERENCES reserva(id),
jetski_id INT REFERENCES jetski(id) NOT NULL,
vendedor_id INT REFERENCES vendedor(id),
cliente_id INT REFERENCES cliente(id),
dt_checkin TIMESTAMP NOT NULL,
horimetro_ini NUMERIC(10,2) NOT NULL,
dt_checkout TIMESTAMP,
horimetro_fim NUMERIC(10,2),
minutos_usados INT,
valor_calculado NUMERIC(10,2),
descontos NUMERIC(10,2) DEFAULT 0,
taxas NUMERIC(10,2) DEFAULT 0,
combustivel_litros NUMERIC(10,2) DEFAULT 0,
combustivel_custo NUMERIC(10,2) DEFAULT 0,
valor_pago NUMERIC(10,2) DEFAULT 0,
status TEXT CHECK (status IN ('aberta','fechada','pendente_pagto','cancelada'))
);

CREATE TABLE foto (
id SERIAL PRIMARY KEY,
locacao_id INT REFERENCES locacao(id),
jetski_id INT REFERENCES jetski(id),
tipo TEXT CHECK (tipo IN ('checkin','checkout','avaria','abastecimento')),
url TEXT NOT NULL,
hash TEXT,
timestamp TIMESTAMP,
geo JSONB
);

CREATE TABLE abastecimento (
id SERIAL PRIMARY KEY,
jetski_id INT REFERENCES jetski(id),
locacao_id INT REFERENCES locacao(id),
dt TIMESTAMP NOT NULL,
litros NUMERIC(10,2) NOT NULL,
preco_litro NUMERIC(10,4) NOT NULL,
custo_total NUMERIC(10,2) NOT NULL,
responsavel TEXT,
foto_id INT REFERENCES foto(id)
);

CREATE TABLE os_manutencao (
id SERIAL PRIMARY KEY,
jetski_id INT REFERENCES jetski(id),
tipo TEXT,
descricao TEXT,
prioridade TEXT,
status TEXT CHECK (status IN ('aberta','em_execucao','fechada','cancelada')),
horas_previstas NUMERIC(10,2),
custo_previsto NUMERIC(10,2),
custo_real NUMERIC(10,2),
dt_abertura TIMESTAMP,
dt_fechamento TIMESTAMP
);

```

---

## Cálculos‑chave
- **Minutos usados** = (horímetro_fim – horímetro_ini) × 60.
- **Tempo faturável** = arredondar(minutos_usados – tolerancia_min, base=15, mínimo 0).
- **Valor locação (bruto)** = (tempo faturável/60) × preço_base_hora + hora_extra (se aplicável) + taxas – descontos.
- **Combustível**:
  - **Incluso**: 0 para o cliente; custo vai para DRE operacional.
  - **Medido**: `combustível_cobrado = litros_consumidos × preço_combustível_do_dia`.
    - `preço_combustível_do_dia = Σ(custo_total_abastecimentos) / Σ(litros)`, no dia; se vazio, usar média da semana.
  - **Taxa fixa**: `taxa_combustível_hora × (tempo faturável/60)`.
- **Receita comissionável** = valor locação (bruto) – itens não comissionáveis (combustível, limpeza, avarias, multas) *(configurável)*.
- **Comissão** = aplicar **regra vencedora** (percentual/fixa/escalonada) sobre a **receita comissionável**.

**Exemplos**
1) Reserva 60 min, tolerância 5 min, uso 68 min → faturável 60 min. Preço R$300/h. Valor bruto = R$300.
   - Combustível taxa fixa R$30/h → R$30 (não comissionável). Receita comissionável = R$300.
   - Regra modelo = 10% → comissão = R$30.
2) Uso 95 min, arredondamento 15 min → faturável 90 min. Preço R$300/h → R$450.
   - Combustível medido: 8 L × R$7,00 = R$56 (não comissionável). Receita comissionável = R$394.
   - Campanha escalonada: 10% até 120 min → 10% × 394 = R$39,40.
---

## API (rótulo de endpoints MVP) – multi‑tenant
- Roteamento: subdomínio `{tenant}` **ou** prefixo `/t/{tenant}` + validação do claim `tenant` no token.
- Header opcional: `X-Tenant-Id` (server valida coerência com domínio/token).

Endpoints:
- `POST /modelos`, `GET /modelos`
- `POST /jetskis`, `GET /jetskis?status=disponivel`
- `POST /reservas`, `GET /reservas?data=...`
- `POST /locacoes/{id}/checkin`
- `POST /locacoes/{id}/checkout`
- `POST /abastecimentos`
- `POST /manutencoes` / `PATCH /manutencoes/{id}`
- `GET /fechamento/diario?data=AAAA‑MM‑DD`
- `GET /fechamento/mensal?mes=AAAA‑MM`
- `GET /comissoes?periodo=...&vendedor=...`
- `POST /midia`

**Observação**: todos os recursos exigem `tenant_id` resolvido pelo gateway e injetado como `app.tenant_id` no Postgres para RLS.

- `POST /modelos`, `GET /modelos`
- `POST /jetskis`, `GET /jetskis?status=disponivel`
- `POST /reservas`, `GET /reservas?data=...`
- `POST /locacoes/{id}/checkin` (horímetro_ini, fotos, checklist)
- `POST /locacoes/{id}/checkout` (horímetro_fim, fotos) → calcula valores
- `POST /abastecimentos`
- `POST /manutencoes` / `PATCH /manutencoes/{id}`
- `GET /fechamento/diario?data=AAAA‑MM‑DD`
- `GET /fechamento/mensal?mes=AAAA‑MM`
- `GET /comissoes?periodo=...&vendedor=...`
- Upload de **fotos**: `POST /midia` → URL/ID, atrelada aos eventos.

---

## Integrações
- **Pagamentos**: gateway para cartão/PIX; suporte a caução (pré‑autorização).
- **Armazenamento de Imagens**: S3/GCS com políticas de retenção e criptografia.
- **Assinatura Digital**: termo de responsabilidade (e.g., e‑signature).
- **Telemetria** (futuro): GPS/IoT para horas/rotas/alertas.

---

## Segurança e LGPD
- Autenticação OIDC; autorização por papéis **por tenant**.
- Criptografia em repouso e em trânsito; assinatura das fotos (hash) e registro de cadeia de custódia.
- **Isolamento lógico** por `tenant_id` + **RLS**. Opcional: chaves de criptografia **por tenant** (envelope encryption) e segregação de storage.
- Retenção de imagens: configurável por tenant (ex.: 12 meses padrão; 24 para avarias).
- Consentimento e base legal; **DPA** (acordo de processamento de dados) por cliente.

---

## Não‑Funcionais (NFR)
- Disponibilidade 99,5% (MVP), escalável para alta temporada.
- Auditoria completa de alterações e eventos operacionais.
- Tempo de resposta alvo < 300ms p/ APIs principais (P95).
- Mobile‑friendly para operação no píer (PWA ou app nativo leve para fotos offline‑first com sync).

---

## Relatórios e KPIs
- **Ocupação por jetski** (% do tempo disponível locado por dia/mês).
- **Horas usadas por jetski** (diário/mensal) e por **modelo**.
- **Receita bruta/líquida**, **margem**, **custo de combustível**.
- **Comissões por vendedor**.
- **MTBF/MTTR** por jetski (manutenção).

---

## UX/Telas (MVP)
1. **Dashboard**: frota por status, agenda do dia, alertas de revisão.
2. **Reserva/Agenda**: calendário, filtro por modelo/jetski.
3. **Check‑in**: scanner de QR do jetski, checklist, fotos e horímetro inicial.
4. **Check‑out**: fotos, horímetro final, cálculo e pagamento.
5. **Abastecimento**: formulário rápido vinculado à locação.
6. **Manutenção**: OS, status e histórico.
7. **Fechamento**: visão do caixa do dia e exportação.

---

## Backlog Inicial (Épicos → Histórias c/ Critérios de Aceite)
**Épico A – Cadastros**
- Criar Modelo: *Dado* formulário válido, *quando* salvar, *então* modelo fica disponível para precificação e reserva.
- Criar Jetski: *Dado* série única, *quando* salvar, *então* status padrão “disponível”.

**Épico B – Agenda e Reserva**
- Criar reserva: *Se* não há conflito, *então* bloquear período e gerar QR da reserva.
- Cancelar reserva: seguir RN02.

**Épico C – Locações**
- Check‑in: exigir fotos + horímetro; impedir saída sem termo assinado.
- Check‑out: exigir horímetro + fotos; calcular valores e emitir recibo.

**Épico D – Abastecimento**
- Registrar abastecimento: atualizar custos do dia e, se vinculado, da locação.

**Épico E – Manutenção**
- Abrir OS: jetski indisponível automaticamente até fechar OS.

**Épico F – Fechamentos e Comissões**
- Fechar dia: gerar resumo, trancar edições (exceto admin) e exportar.
- Calcular comissões: relatório por período, com filtros e exportação.

---

# Casos de Teste (BDD – Gherkin)

## 1) Tolerância e Arredondamento
**Cenário 1.1 – Tolerância não cobrada**
```

Dado que a tolerância é 5 minutos e o arredondamento é de 15 minutos
E que a locação iniciou com horímetro 100,00 e finalizou em 101,08 (4,8 min)
Quando calcular o tempo faturável
Então o tempo faturável deve ser 0 minuto
E o valor da locação deve ser R$ 0,00 (antes de taxas mínimas)

```

**Cenário 1.2 – Arredondamento para baixo**
```

Dado tolerância 5 min e arredondamento de 15 min
E uso total de 19 min após subtrair a tolerância
Quando calcular
Então faturável = 15 min

```

**Cenário 1.3 – Arredondamento para cima**
```

Dado tolerância 5 min e arredondamento de 15 min
E uso total de 21 min após subtrair a tolerância
Quando calcular
Então faturável = 30 min

```

## 2) Combustível
**Cenário 2.1 – Taxa fixa por hora (não comissionável)**
```

Dado preço base R$ 300/h e taxa combustível R$ 30/h
E tempo faturável de 60 min
Quando calcular valor
Então valor bruto = R$ 300,00
E combustível cobrado = R$ 30,00 (não comissionável)
E receita comissionável = R$ 300,00

```

**Cenário 2.2 – Medido por litros**
```

Dado que o preço do dia é R$ 7,00/L
E litros consumidos = 8L
Quando calcular combustível
Então combustível cobrado = R$ 56,00 (não comissionável)

```

**Cenário 2.3 – Incluso**
```

Dado política de combustível = incluso
Quando encerrar a locação
Então não deve haver cobrança de combustível ao cliente
E o custo de combustível deve ser registrado apenas no DRE

```

## 3) Comissão (Hierarquia e Tipos)
**Cenário 3.1 – Regra por campanha vence a regra por modelo**
```

Dado uma campanha ativa com 12% para todas as locações
E uma regra por modelo com 10%
E receita comissionável de R$ 400,00
Quando calcular comissão
Então usar 12%
E comissão = R$ 48,00

```

**Cenário 3.2 – Escalonada por duração**
```

Dado regra escalonada: 10% até 120 min; 12% acima de 120 min
E tempo faturável = 150 min
E receita comissionável = R$ 600,00
Quando calcular
Então comissão = 12% de 600 = R$ 72,00

```

**Cenário 3.3 – Valor fixo por locação**
```

Dado regra de comissão valor fixo = R$ 20,00
Quando calcular
Então comissão = R$ 20,00

```

**Cenário 3.4 – Itens não comissionáveis**
```

Dado combustível e taxa de limpeza marcados como não comissionáveis
E valor bruto = R$ 500, combustível = R$ 60, limpeza = R$ 40
Quando calcular receita comissionável
Então receita comissionável = R$ 400

```

**Cenário 3.5 – Transparência**
```

Dado demonstrativo de comissão por locação
Quando consultar a locação X
Então devo ver a regra vencedora, fórmula e base de cálculo

```

## 4) Fotos e Checklist
**Cenário 4.1 – Bloqueio de saída sem fotos**
```

Dado que fotos obrigatórias de check-in não foram anexadas
Quando tentar concluir o check-in
Então o sistema deve impedir a saída e exibir quais fotos faltam

```

**Cenário 4.2 – Integridade da imagem**
```

Dado upload de fotos
Quando armazenar
Então deve salvar hash e timestamp e vincular à locação

```

## 5) Manutenção e Disponibilidade
**Cenário 5.1 – Jetski em manutenção não reserva**
```

Dado jetski com status manutenção
Quando tentar reservar
Então o sistema deve bloquear e sugerir outro jetski compatível

```

**Cenário 5.2 – OS fecha → Disponibiliza**
```

Dado uma OS em execução
Quando a OS é fechada
Então o status do jetski muda para disponível e a agenda é liberada

```

## 6) Fechamento Diário/Mensal
**Cenário 6.1 – Fechar dia tranca edições**
```

Dado que o fechamento diário foi executado
Quando um operador tentar editar uma locação do dia fechado
Então o sistema deve impedir a edição (exceto admin com justificativa)

```

**Cenário 6.2 – Consolidação de comissões**
```

Dado locações pagas no mês
Quando executar fechamento mensal
Então o relatório deve trazer comissões por vendedor com status 'prevista'

```

## 7) Agenda e Conflitos
**Cenário 7.1 – Conflito de horário**
```

Dado uma reserva para o jetski A de 10:00 a 11:00
Quando tentar criar outra reserva 10:30 a 11:30 para o mesmo jetski
Então deve apresentar conflito e sugerir A 11:00-12:00 ou jetski B equivalente

```

---

# Wireframes MVP (especificação de telas)
> *Wireframes descritos de forma textual para orientar o design/implementação. Se quiser, posso gerar versões clicáveis depois.*

## 1) Dashboard Operacional
- **Topo**: filtro por data (hoje) | botão "Fechar Dia" (estado/confirm).
- **Cards**: Disponíveis / Locados / Manutenção / Atrasados.
- **Agenda do Dia (kanban/linha do tempo)**: blocos por jetski; cores por status.
- **Alertas**: revisões por horas (50h/100h), OS atrasadas.

## 2) Agenda & Reserva
- **Calendário** (semana/dia) + **lista**.
- Filtros: modelo, vendedor, status.
- **Nova Reserva (modal)**: cliente (auto-complete), modelo/jetski, saída prevista, duração, vendedor, sinal.
- **Validações**: conflito; política de cancelamento.

## 3) Check‑in (Saída)
- **Scanner QR** (reserva/jetski) ou busca por cliente.
- **Checklist** (EPIs, boia, lacres).
- **Fotos obrigatórias** (grid 2×2): frente, laterais, painel horímetro.
- **Horímetro inicial** (campo + captura da foto do painel com OCR opcional).
- **Termo** (preview + assinatura digital).
- **CTA**: "Iniciar Locação" (desabilitado até cumprir requisitos).

## 4) Check‑out (Retorno)
- **Localizar locação em curso** (lista/QR).
- **Fotos de retorno** (grid 2×2) + marcar **avarias**.
- **Horímetro final** (campo + foto do painel).
- **Resumo de cálculo**: minutos usados, faturável, preço/h, hora extra, taxas, **combustível** (conforme política), **valor final**.
- **Pagamento**: PIX/cartão/caução.
- **Recibo** + botão "Abrir OS" se houver avaria.

## 5) Abastecimento
- **Form**: jetski, vincular a locação (opcional), litros, preço/L (auto-preenchido pelo preço do dia com opção de editar), foto da bomba/painel, responsável.
- **Lista do dia** com totais e custo médio.

## 6) Manutenção
- **Lista de OS** com filtros (status, prioridade, jetski).
- **Nova OS**: tipo, descrição, peças/mão de obra, previsão; ao salvar → status do jetski = manutenção.
- **Detalhe OS**: fotos, custos, horas; **Fechar OS** → volta a disponibilidade.

## 7) Fechamento
- **Fechamento Diário**: tabela (locações, horas/jetski, receita, combustível, taxas, caixa), botão **Exportar CSV/PDF**.
- **Fechamento Mensal**: KPIs (ocupação, ticket médio, receita líquida, margem), **Comissões por vendedor** com status (prevista/aprovada/paga) e botão **Gerar Arquivo Contábil**.

## 8) Comissões (Backoffice)
- **Políticas**: grid de `commission_policy` com prioridade e validade.
- **Simulador**: inputs (modelo, duração, valores) → mostra regra vencedora e demonstrativo.

---

# Tabela de Exemplos (Testes Rápidos de Unidade)
| Caso | Tolerância | Arred. | Uso (min) | Faturável (min) | Preço/h | Combustível | Valor Bruto | Receita Comiss. | Comissão |
|---|---:|---:|---:|---:|---:|---|---:|---:|---:|
| A | 5 | 15 | 68 | 60 | 300 | Taxa 30/h | 300,00 | 300,00 | 30,00 (10%) |
| B | 5 | 15 | 95 | 90 | 300 | Medido 8L×7 | 450,00 | 394,00 | 39,40 (10%) |
| C | 0 | 15 | 121 | 120 | 320 | Incluso | 640,00 | 640,00 | 76,80 (12% campanha) |

---

# Próximas Entregas (propostos)
1. Testes automatizados (unit + API) cobrindo os cenários BDD acima.
2. Protótipos clicáveis dos wireframes (ou React com Tailwind no canvas).
3. Serviço de **simulação** de precificação (para operadores e backoffice).


---

# Arquitetura Mobile (KMM) – Keycloak + Publicação nas Lojas
**Resumo**: KMM compartilha **regras de negócio, validações, repositórios e sincronização** no módulo `:shared`, enquanto **UI e integrações nativas** (câmera, AppAuth/Keycloak, background, storage seguro) ficam em `:androidApp` (Jetpack Compose) e `:iosApp` (SwiftUI).

## Compatibilidade com Keycloak
- **Suporte**: ✅ Sim. Use **AppAuth** nativo em cada plataforma com **PKCE** e exponha ao `shared` via interfaces `expect/actual`.
  - **Android**: `net.openid:appauth`, `androidx.security:security-crypto`, WorkManager.
  - **iOS**: `AppAuth` (CocoaPods/SPM), Keychain, `BGTaskScheduler`.
- Fluxo: AppAuth faz Authorization Code + PKCE → `access/refresh` → salva tokens no **Keychain/Keystore** → `TokenProvider` do `shared` distribui para o **Ktor**.

## Publicação (Play Store / App Store)
- ✅ **Publicável normalmente** como apps nativos: `androidApp` gera AAB/APK; `iosApp` gera IPA. Seguir políticas de permissões (Câmera/Fotos/Localização) e privacidade.

## Estrutura de Módulos
```

:androidApp (Compose)
:iosApp (SwiftUI)
:shared (KMM)
├─ domain/ (use cases)
├─ data/ (repos)
├─ network/ (Ktor, Auth)
├─ storage/ (SQLDelight, Settings)
├─ sync/ (coordenador + fila)
├─ auth/ (TokenProvider, OAuthCoordinator)
└─ util/

````

## Interfaces `expect/actual`
```kotlin
// shared
expect class SecureStore() {
  fun put(key: String, value: String)
  fun get(key: String): String?
  fun remove(key: String)
}

interface TokenProvider {
  suspend fun accessToken(): String?
  suspend fun refreshToken(): String?
  suspend fun saveTokens(access: String, refresh: String, expiresAt: Long)
}

interface OAuthCoordinator { // disparado pela UI nativa
  suspend fun authorize(scopes: List<String> = listOf("openid","profile")): Result<Unit>
  suspend fun logout(): Result<Unit>
}
````

```kotlin
// android actual (exemplo)
actual class SecureStore {
  private val prefs = EncryptedSharedPreferences(...)
  fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
  fun get(key: String) = prefs.getString(key, null)
  fun remove(key: String) { prefs.edit().remove(key).apply() }
}
```

## Ktor com Bearer + Refresh

```kotlin
val http = HttpClient(OkHttp) {
  install(ContentNegotiation) { json() }
  install(HttpRequestRetry) { retryOnServerErrors(maxRetries = 3) }
  install(Auth) {
    bearer {
      loadTokens { tokenProvider.accessToken()?.let { BearerTokens(it, tokenProvider.refreshToken() ?: "") } }
      refreshTokens {
        val t = myRefreshWithAppAuth()
        tokenProvider.saveTokens(t.access, t.refresh, t.expiresAt)
        BearerTokens(t.access, t.refresh)
      }
    }
  }
}
```

## Offline‑first & Sincronização

* **SQLDelight**: fila de eventos (`FotoUpload`, `CheckIn`, `CheckOut`, `Abastecimento`).
* **Sync** no `shared` com backoff exponencial, limites por rede e resolução de conflito (last‑write‑wins + auditoria).
* **Background**: Android (WorkManager); iOS (`BGAppRefreshTaskRequest`, `BGProcessingTaskRequest`).

## Câmera & Fotos

* Android: **CameraX** + EXIF; iOS: **AVFoundation**. Compressão (WebP/JPEG/HEIF), **SHA‑256** das imagens, metadados (timestamp/geo).
* **Upload**: URL pré‑assinada (S3), **chunked**, com retomada; salvar `hash`, `timestamp`, `geo`.

## Segurança

* PKCE + armazenamento seguro (Keystore/Keychain) via `SecureStore`.
* TLS pinning (opcional) com Ktor; logs sanitizados.

## Bibliotecas sugeridas

* Shared: **Ktor**, **kotlinx.serialization**, **SQLDelight**, **kotlinx-datetime**, **Napier**.
* Android: **AppAuth**, **CameraX**, **WorkManager**, **Security Crypto**.
* iOS: **AppAuth**, **BGTaskScheduler**, **AVFoundation**.

## Checklist de Publicação

* **Android**: AAB, target SDK vigente, justificativas de permissões; sem `MANAGE_EXTERNAL_STORAGE` (usar scoped storage).
* **iOS**: `NSCameraUsageDescription`, `NSPhotoLibraryAddUsageDescription`; certificados e Provisioning Profiles válidos.
* **Privacidade**: políticas e declarações nas lojas; LGPD para fotos.

## Próximos Passos Mobile (KMM)

1. Configurar projeto KMM (Gradle + SQLDelight + Ktor) e pods SPM/CocoaPods do **AppAuth**.
2. Implementar `SecureStore` (Keystore/Keychain) e `TokenProvider` compartilhado.
3. POC: **Login Keycloak (PKCE)** → check-in com **4 fotos** → fila offline → **upload** com URL pré-assinada.
4. Workers de sync: WorkManager/BGTasks mapeados pelo `shared`.
5. Pipeline CI/CD (fastlane, Gradle, TestFlight/Internal Testing) e *feature flags* para políticas de comissão/combustível.

---

# Stack Tecnológica (Proposta Fechada)

## 1) Backend (API SaaS e Orquestração)

* **Linguagem/Runtime**: **Java 21**
* **Framework**: **Spring Boot 3.3+** (Web, Validation, Security, Data JPA, Actuator)
* **Banco transacional**: **PostgreSQL 16** (RLS habilitado p/ multi‑tenant)
* **Migrações**: **Flyway**
* **Cache/Fila curta**: **Redis** (TTL p/ sessões de upload, rate‑limit por tenant)
* **Armazenamento de imagens**: **Amazon S3** (ou compatível) com **URLs pré‑assinadas**, **SSE‑S3/KMS**
* **Mensageria (futuro)**: **SQS** (MVP) → **Kafka** (quando precisar de eventos/streams)
* **AuthN/AuthZ**: **Keycloak 26 (OSS)** (OIDC, PKCE, RBAC por tenant)
* **Build/Deps**: Maven, **MapStruct** (mapeamento), **Testcontainers** (integração)
* **Observabilidade**: **OpenTelemetry** (traces/metrics/logs) + **Prometheus/Grafana** + **ELK/CloudWatch Logs**
* **Documentação**: **Springdoc OpenAPI**

### Multi‑tenant

* **Roteamento**: subdomínio `{tenant}` → Gateway → injeta `X‑Tenant‑Id`
* **RLS**: `SET LOCAL app.tenant_id` por request (Filtro + `DataSource` wrapper)
* **Chaves por tenant** (opcional): envelope com **AWS KMS**

## 2) Mobile (Operação de Píer) – **KMM**

* **UI Android**: **Jetpack Compose**
* **UI iOS**: **SwiftUI**
* **Módulo compartilhado**: `:shared` com **Ktor**, **SQLDelight**, **kotlinx.serialization**, **kotlinx-datetime**, **Napier**
* **Auth**: **AppAuth** nativo (Android/iOS) + PKCE; tokens em **Keystore/Keychain**
* **Background**: **WorkManager** (Android), **BGTaskScheduler** (iOS)
* **Câmera/Fotos**: CameraX (Android), AVFoundation (iOS)
* **Upload**: URL pré‑assinada (S3), **chunked + retry/backoff**

## 3) Front Backoffice (Relatórios/Comissões/Fechamentos)

* **Next.js 14+ (React, TS)** + **shadcn/ui**
* **Auth**: OIDC (PKCE) via Keycloak
* **Gráficos/KPIs**: Recharts ou ECharts

## 4) Infra & Deploy

* **Cloud**: **AWS**
* **Contêineres**: **EKS** (K8s) para APIs, web e **Keycloak 26 (OSS)** via **Helm chart oficial**
* **Banco do Keycloak**: **RDS Postgres** dedicado
* **Ingress/Gateway**: **AWS ALB Ingress** + **Nginx Ingress** (rate‑limit por tenant)
* **CDN**: **CloudFront** para servir imagens (privadas com assinatura)
* **Segurança**: **AWS WAF**, **Secrets Manager/Parameter Store**, **Security Hub**
* **Backups**: Snapshots do **RDS Postgres** + export dos realms (kcadm) + regras de retenção

### Keycloak 26 (OSS) – Parâmetros recomendados

* réplicas: 3; `proxy=edge`; `hostname` e `hostname-strict=true`
* métricas Prometheus habilitadas; health/readiness
* rotação de chaves habilitada; PKCE obrigatório em clients públicos
* CORS/Origins por tenant; Fine-Grained Admin (delegação limitada)
* `token-exchange` apenas se necessário

## 5) CI/CD & Lojas

* **Backend/Web**: **GitHub Actions** (build, tests, SCA, deploy k8s via ArgoCD ou Helm)
* **Mobile**: **fastlane** + **TestFlight / Internal Testing**
* **Qualidade**: SonarQube, OWASP Dependency Check, SAST/DAST em pipeline

## 6) Padrões & Boas Práticas

* **OpenAPI‑first**, DTOs versionados, Idempotência (chave por tenant + operação)
* **Feature Flags** (comissões/combustível por tenant)
* **LGPD**: política de retenção configurável por tenant, minimização de dados em logs
* **Auditoria**: trilhas por usuário/tenant (correlação `traceId`)

## 7) Roadmap de Infra (Fases)

* **MVP**: EKS + RDS Postgres + S3 + Redis + Keycloak em EKS/Helm, WAF básico
* **Escala**: Particionar storage por tenant grande, Kafka para eventos, chaves KMS dedicadas
* **Enterprise**: Realm por tenant (se exigido), bancos/schemas dedicados a clientes premium
