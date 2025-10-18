# Mobile Stories

Histórias (User Stories) específicas do módulo **Mobile** (KMM - Kotlin Multiplatform).

## Épicos Relacionados

- [EPIC-07: Mobile KMM POC](../../stories/epics/epic-07-mobile-kmm-poc.md)
- [EPIC-03: Reservas e Locações](../../stories/epics/epic-03-reservas-locacoes.md) (captura de fotos)

## Histórias

### EPIC-07: Mobile KMM POC
- [STORY-001](./story-001-kmm-project-setup.md): Setup Projeto KMM (5 pts)
- [STORY-002](./story-002-appauth-integration.md): AppAuth para Keycloak (PKCE) (8 pts)
- [STORY-003](./story-003-camera-capture-poc.md): POC Captura de Fotos (5 pts)

**Total POC Setup:** 18 story points

### Próximas Histórias
- STORY-004: Camera Capture para Check-in (4 fotos obrigatórias) (8 pts)
- STORY-005: Upload de Fotos para S3 (presigned URL) (5 pts)
- STORY-006: Offline Queue com SQLDelight (8 pts)
- STORY-007: Background Sync Workers (8 pts)

## Como Buscar Histórias

```bash
# Por status
grep -r "status: IN_PROGRESS" .

# Por tag
grep -r "tags:.*kmm" .
grep -r "tags:.*camera" .

# Por épico
grep -r "epic: EPIC-07" .
```

## Convenções

- IDs seguem formato: `STORY-XXX`
- Prioridade: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`
- Estimativa em story points (Fibonacci: 1, 2, 3, 5, 8, 13)
- Status: `TODO`, `IN_PROGRESS`, `IN_REVIEW`, `TESTING`, `DONE`, `BLOCKED`, `CANCELLED`
- Tags importantes: `android`, `ios`, `kmm`, `shared`

## Estrutura do Projeto

```
mobile/
├── shared/           # Código compartilhado (Kotlin Multiplatform)
├── androidApp/       # App Android (Jetpack Compose)
└── iosApp/           # App iOS (SwiftUI)
```

## Links

- [Project Board](../../stories/project-board.md)
- [Épicos](../../stories/epics/)
