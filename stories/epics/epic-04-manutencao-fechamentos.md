---
epic_id: EPIC-04
title: Manutenção e Fechamentos
status: TODO
priority: MEDIUM
start_date: 2025-03-05
target_date: 2025-03-18
owner: Team Backend + Frontend
dependencies: [EPIC-03]
---

# EPIC-04: Manutenção e Fechamentos

## Objetivo

Implementar gestão de ordens de serviço (OS) de manutenção com bloqueio automático de agenda, e fechamentos diário/mensal com consolidação de valores, relatórios exportáveis e bloqueio de edições retroativas.

## Escopo

### Incluído
- [ ] Entidade `OSManutencao` com controle de status e custos
- [ ] Abertura de OS: mudar status jetski para "manutenção", bloquear agenda
- [ ] Fechamento de OS: liberar jetski, voltar status "disponível"
- [ ] Histórico de manutenções por jetski
- [ ] Alertas de revisão por horímetro (ex: a cada 50h)
- [ ] Entidade `FechamentoDiario` com totais consolidados
- [ ] Fechamento diário: locações, horas/jetski, receita, custos, caixa
- [ ] Bloqueio de edições retroativas após fechamento
- [ ] Entidade `FechamentoMensal` com KPIs
- [ ] Fechamento mensal: ocupação, ticket médio, margem, comissões por vendedor
- [ ] Exportação de relatórios (PDF/CSV)
- [ ] Interface web: gestão de OS, fechamentos, relatórios

### Excluído (Out of Scope)
- Integração contábil automática (será manual/CSV no MVP)
- Workflow de aprovação de OS
- Gestão de estoque de peças

## Histórias Relacionadas

### Backend
- `backend/stories/story-018-os-manutencao-entity.md` (5 pts)
- `backend/stories/story-019-os-status-control.md` (5 pts)
- `backend/stories/story-020-fechamento-diario.md` (13 pts)
- `backend/stories/story-021-fechamento-mensal.md` (13 pts)
- `backend/stories/story-022-relatorios-exportacao.md` (8 pts)

### Frontend
- `frontend/stories/story-012-os-manutencao-page.md` (5 pts)
- `frontend/stories/story-013-fechamento-diario-page.md` (8 pts)
- `frontend/stories/story-014-fechamento-mensal-page.md` (8 pts)

**Total estimado:** 65 story points (~3-4 sprints)

## Critérios de Aceite

### Manutenção
- [ ] Ao abrir OS, jetski muda automaticamente para status "manutenção"
- [ ] Jetski em manutenção não aparece em listagens de disponíveis
- [ ] Tentativa de reservar jetski em manutenção é bloqueada
- [ ] Ao fechar OS, jetski volta para status "disponível"
- [ ] Alerta é disparado quando jetski atinge marco de horímetro (50h, 100h)
- [ ] Histórico de manutenções exibe todas as OS por jetski

### Fechamento Diário
- [ ] Consolidação calcula corretamente: total locações, horas por jetski, receita bruta/líquida
- [ ] Custos de combustível são somados corretamente
- [ ] Após fechamento, edições em locações do dia são bloqueadas (exceto admin com justificativa)
- [ ] Relatório pode ser exportado em PDF e CSV
- [ ] Fechamento pode ser reaberto por admin se necessário

### Fechamento Mensal
- [ ] Ocupação calculada como: (horas locadas / horas disponíveis) × 100
- [ ] Ticket médio = receita total / número de locações
- [ ] Margem = (receita - custos) / receita × 100
- [ ] Comissões por vendedor listadas com status (prevista/aprovada/paga)
- [ ] Relatório mensal pode ser exportado para contabilidade (CSV padrão)

## Riscos

**Risco Médio:**
- **Performance do cálculo de fechamento mensal**: Muitas agregações SQL.
  - **Mitigação**: Usar views materializadas ou cache Redis para cálculos pesados

**Risco Baixo:**
- **Complexidade de reabertura de fechamento**: Controle de auditoria.
  - **Mitigação**: Registrar quem reabriu, quando e motivo na tabela `auditoria`

## Dependências

- EPIC-03 concluído (locações e abastecimentos funcionando)

## Métricas de Sucesso

- Tempo de geração de fechamento mensal com 1000 locações: < 10 segundos
- Precisão dos cálculos: 100% (validado com cenários de teste)
- Taxa de reaberturas de fechamento: < 5%

## Notas

### Cálculo de Ocupação

```sql
WITH horas_disponiveis AS (
  SELECT
    jetski_id,
    COUNT(DISTINCT DATE(dt)) × 12 AS horas_disponiveis_mes  -- 12h/dia operação
  FROM generate_series(inicio_mes, fim_mes, '1 day') dt
  CROSS JOIN jetski
  WHERE status != 'manutencao'  -- Excluir dias em manutenção
  GROUP BY jetski_id
),
horas_locadas AS (
  SELECT
    jetski_id,
    SUM(minutos_usados) / 60.0 AS horas_locadas_mes
  FROM locacao
  WHERE EXTRACT(MONTH FROM dt_checkout) = mes_alvo
  GROUP BY jetski_id
)
SELECT
  j.id,
  j.serie,
  hl.horas_locadas_mes,
  hd.horas_disponiveis_mes,
  (hl.horas_locadas_mes / hd.horas_disponiveis_mes × 100) AS ocupacao_pct
FROM jetski j
JOIN horas_locadas hl ON j.id = hl.jetski_id
JOIN horas_disponiveis hd ON j.id = hd.jetski_id;
```

### Estrutura de Relatório Mensal (JSON)

```json
{
  "tenant_id": "uuid",
  "ano_mes": "2025-02",
  "totais": {
    "locacoes": 150,
    "receita_bruta": 45000.00,
    "receita_liquida": 42000.00,
    "custos_combustivel": 3500.00,
    "custos_manutencao": 2000.00,
    "margem_pct": 86.67
  },
  "por_modelo": [
    {
      "modelo_id": "uuid",
      "modelo_nome": "Sea-Doo GTI 130",
      "locacoes": 80,
      "horas_usadas": 160,
      "ocupacao_pct": 66.7,
      "receita": 24000.00,
      "ticket_medio": 300.00
    }
  ],
  "comissoes": [
    {
      "vendedor_id": "uuid",
      "vendedor_nome": "João Silva",
      "locacoes": 50,
      "receita_comissionavel": 15000.00,
      "comissao_total": 1500.00,
      "status": "prevista"
    }
  ]
}
```

## Changelog

- 2025-01-15: Épico criado
