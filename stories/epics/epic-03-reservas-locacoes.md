---
epic_id: EPIC-03
title: Reservas e Locações
status: TODO
priority: CRITICAL
start_date: 2025-02-12
target_date: 2025-03-04
owner: Team Backend + Mobile
dependencies: [EPIC-02]
---

# EPIC-03: Reservas e Locações

## Objetivo

Implementar o core operacional do sistema: agenda de reservas com detecção de conflitos, check-in/check-out com fotos obrigatórias, cálculo automático de valores (tolerância, combustível, comissões) e armazenamento seguro de imagens no S3.

## Escopo

### Incluído
- [ ] Entidade `Reserva` com validação de conflitos de agenda
- [ ] Algoritmo de detecção de conflitos e sugestão de alternativas
- [ ] Entidade `Locacao` com relacionamento `Reserva`
- [ ] Check-in: validação de fotos obrigatórias, horímetro inicial, checklist
- [ ] Check-out: fotos finais, horímetro final, cálculo de valores
- [ ] Motor de cálculo: tolerância, arredondamento, combustível, comissões
- [ ] Gestão de fotos: presigned URLs S3, hash SHA-256, metadados
- [ ] Entidade `Abastecimento` vinculada a locação/jetski
- [ ] Políticas de combustível (incluso/medido/taxa fixa)
- [ ] Políticas de comissão com hierarquia (campanha → modelo → duração → padrão)
- [ ] Geração de termo de responsabilidade (PDF)
- [ ] Mudança automática de status do jetski (disponível ↔ locado)
- [ ] Interface web: calendário de agenda, check-in/out
- [ ] App mobile: POC de captura de 4 fotos obrigatórias

### Excluído (Out of Scope)
- Integração com gateway de pagamento (futuro)
- Assinatura digital eletrônica (será mock no MVP)
- Telemetria/GPS em tempo real

## Histórias Relacionadas

### Backend
- `backend/stories/story-010-reserva-entity-crud.md` (5 pts)
- `backend/stories/story-011-agenda-conflict-detection.md` (8 pts)
- `backend/stories/story-012-locacao-checkin.md` (8 pts)
- `backend/stories/story-013-locacao-checkout-calculo.md` (13 pts)
- `backend/stories/story-014-foto-service-s3.md` (8 pts)
- `backend/stories/story-015-abastecimento-entity.md` (5 pts)
- `backend/stories/story-016-fuel-policy-implementation.md` (8 pts)
- `backend/stories/story-017-commission-policy-implementation.md` (8 pts)

### Frontend
- `frontend/stories/story-008-calendario-agenda.md` (8 pts)
- `frontend/stories/story-009-reserva-form.md` (5 pts)
- `frontend/stories/story-010-checkin-interface.md` (8 pts)
- `frontend/stories/story-011-checkout-interface.md` (8 pts)

### Mobile
- `mobile/stories/story-004-camera-capture-checkin.md` (8 pts)
- `mobile/stories/story-005-photo-upload-s3.md` (5 pts)

**Total estimado:** 105 story points (~5-6 sprints)

## Critérios de Aceite

### Reservas
- [ ] Sistema detecta conflito de horário e impede reserva duplicada
- [ ] Sugestões de horários alternativos são apresentadas
- [ ] Reserva pode ser cancelada seguindo RN02 (política de cancelamento)

### Check-in
- [ ] Operador não consegue fazer check-in sem capturar 4 fotos obrigatórias
- [ ] Horímetro inicial é validado (>= horímetro atual do jetski)
- [ ] Jetski muda automaticamente para status "locado"
- [ ] Termo de responsabilidade é gerado em PDF

### Check-out
- [ ] Horímetro final > horímetro inicial
- [ ] Sistema calcula corretamente: minutos usados, tolerância, arredondamento
- [ ] Política de combustível é aplicada corretamente (incluso/medido/taxa fixa)
- [ ] Comissão calculada usando hierarquia de políticas
- [ ] Jetski volta para status "disponível"

### Fotos
- [ ] Fotos são enviadas via presigned URL para S3
- [ ] Hash SHA-256 é calculado e armazenado
- [ ] Metadados incluem timestamp, geolocalização, tenant_id
- [ ] Fotos ficam isoladas por tenant no bucket (prefixo `tenant_id/`)

### Cálculo (exemplos de teste)
- [ ] Uso de 68 min, tolerância 5 min, arredondamento 15 min → faturável 60 min
- [ ] Combustível taxa fixa R$30/h não entra na base comissionável
- [ ] Comissão de campanha (12%) sobrescreve comissão por modelo (10%)

## Riscos

**Risco Alto:**
- **Complexidade do motor de cálculo**: Muitas regras de negócio interdependentes.
  - **Mitigação**: TDD rigoroso com cenários BDD do `inicial.md`, testes parametrizados

**Risco Médio:**
- **Performance de upload de fotos**: Fotos grandes podem demorar.
  - **Mitigação**: Compressão no mobile antes de upload, chunked upload se necessário

**Risco Médio:**
- **Detecção de conflitos em alta concorrência**: Duas reservas simultâneas.
  - **Mitigação**: Lock pessimista ou otimista na tabela `reserva`

## Dependências

- EPIC-02 concluído (entidades Modelo, Jetski, Vendedor cadastradas)
- Bucket S3 configurado (ou LocalStack para dev)

## Métricas de Sucesso

- Precisão do motor de cálculo: 100% nos cenários BDD
- Tempo de upload de foto (2MB): < 5 segundos
- Taxa de erro em detecção de conflitos: 0%

## Notas

### Motor de Cálculo (Pseudocódigo)

```
1. minutos_usados = (horimetro_fim - horimetro_ini) × 60
2. minutos_apos_tolerancia = max(0, minutos_usados - tolerancia_min)
3. minutos_faturavel = arredondar(minutos_apos_tolerancia, base=15, direcao=CIMA)
4. valor_locacao_base = (minutos_faturavel / 60) × preco_base_hora
5. valor_combustivel = aplicar_politica_combustivel(locacao)
6. valor_total_bruto = valor_locacao_base + taxas - descontos
7. receita_comissionavel = valor_locacao_base - itens_nao_comissionaveis
8. comissao = aplicar_hierarquia_politica_comissao(receita_comissionavel)
```

### Políticas de Combustível

**Incluso:**
```sql
-- Não cobrar do cliente, mas registrar custo operacional
combustivel_cobrado = 0
custo_operacional += abastecimentos.custo_total
```

**Medido:**
```sql
litros_consumidos = abastecimento_pos - abastecimento_pre
preco_dia = avg(abastecimentos_do_dia.custo / litros)
combustivel_cobrado = litros_consumidos × preco_dia
```

**Taxa Fixa:**
```sql
combustivel_cobrado = taxa_fixa_hora × (minutos_faturavel / 60)
```

### Hierarquia de Comissão

```
1. Buscar política de campanha ativa para a data
   → Se encontrar, aplicar e PARAR
2. Buscar política específica por modelo_id
   → Se encontrar, aplicar e PARAR
3. Buscar política por faixa de duração (minutos)
   → Se encontrar, aplicar e PARAR
4. Aplicar política padrão do vendedor
```

## Changelog

- 2025-01-15: Épico criado
