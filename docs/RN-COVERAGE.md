# Cobertura das Regras de Negócio (RN ↔ BDD ↔ Testes)

Mapeia as regras de negócio (`inicial.md`) e seus cenários BDD aos testes automatizados.
Objetivo: fechar o loop **spec → teste** e expor lacunas (drift) explicitamente.

| RN | Descrição | Cenários BDD (`inicial.md`) | Teste(s) | Status |
|----|-----------|------------------------------|----------|--------|
| **RN01** | Horas faturáveis: tolerância + arredondamento 15 min | 1.1, 1.2, 1.3 | `LocacaoCalculatorServiceTest` | ✅ Coberto |
| **RN02** | Cancelamento / no-show (reembolso por antecedência, multa) | — | — | ❌ **Não implementado** (existe só cancelamento básico de reserva em `ReservaControllerTest`) |
| **RN03** | Combustível: 3 modos (Incluso / Medido / Taxa fixa) | 2.1, 2.2, 2.3 | `FuelPolicyServiceTest`, `AbastecimentoControllerIntegrationTest`, `FuelPolicyControllerIntegrationTest` | ✅ Coberto |
| **RN04** | Comissão: hierarquia (campanha > modelo > duração > vendedor); receita comissionável | 3.1, 3.2, 3.3, 3.4, 3.5 | `CommissionServiceTest` | ✅ Coberto |
| **RN05** | Fotos obrigatórias + checklist no check-out; caução/danos | 4.1, 4.2 | `ChecklistValidationTest`, `PhotoControllerTest` | 🟡 Parcial — fotos/checklist ✅; **caução e desconto por danos não implementados** |
| **RN06** | Disponibilidade: jetski em manutenção não reserva; OS fecha → reabre | 5.1, 5.2 | `OSManutencaoServiceTest`, `ReservaControllerTest` | ✅ Coberto |
| **RN07** | Revisão por horas: alerta em marcos (50h, 100h…) | — | `frota/PreventiveMaintenanceScheduler` (impl.) | 🟡 Implementado, **sem teste dedicado** |
| Fechamento | Fechar dia tranca edições; consolidação de comissões | 6.1, 6.2 | `FechamentoServiceTest` | ✅ Coberto |
| Agenda | Conflito de horário | 7.1 | `ReservaControllerTest` | ✅ Coberto |

## Lacunas priorizadas

1. **RN02 (cancelamento/no-show)** — regra de negócio **não implementada** (não é só falta de teste). Decidir se entra no escopo.
2. **RN05 caução/danos** — fotos OK, mas bloqueio de caução e desconto por avarias não existem.
3. **RN07** — scheduler existe; falta teste do disparo de alerta por marco de horímetro.

## Como manter

- Toda nova RN/feature deve nascer com seu teste (cenário BDD → teste JUnit/integração).
- O CI (`.github/workflows/ci.yml`) roda toda a suíte a cada push/PR — uma RN sem teste passa despercebida; uma RN com teste falho **bloqueia o merge**.
