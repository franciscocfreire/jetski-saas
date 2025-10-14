---
story_id: STORY-001
epic: EPIC-07
title: Setup Projeto KMM (Kotlin Multiplatform Mobile)
status: TODO
priority: HIGH
estimate: 5
assignee: Unassigned
started_at: null
completed_at: null
tags: [mobile, kmm, kotlin, android, ios, setup]
dependencies: []
---

# STORY-001: Setup Projeto KMM (Kotlin Multiplatform Mobile)

## Como
Mobile Developer

## Quero
Projeto KMM configurado com módulos `:shared`, `:androidApp`, `:iosApp`

## Para que
Possamos compartilhar lógica de negócio entre Android e iOS

## Critérios de Aceite

- [ ] **CA1:** Projeto KMM criado com estrutura de 3 módulos
- [ ] **CA2:** SQLDelight configurado para Android e iOS
- [ ] **CA3:** Ktor Client configurado
- [ ] **CA4:** kotlinx.serialization e kotlinx-datetime instalados
- [ ] **CA5:** App Android compila e roda (Hello World)
- [ ] **CA6:** App iOS compila e roda (Hello World)

## Tarefas Técnicas

- [ ] Criar projeto KMM com wizard (Android Studio ou IntelliJ)
- [ ] Configurar `build.gradle.kts` do `:shared`
- [ ] Adicionar deps: Ktor, SQLDelight, kotlinx.serialization, Napier
- [ ] Configurar Cocoa Pods para iOS
- [ ] Criar tela Hello World no Android (Compose)
- [ ] Criar tela Hello World no iOS (SwiftUI)
- [ ] Testar compilação e execução nos 2 alvos

## Links

- **Epic:** [EPIC-07](../../stories/epics/epic-07-mobile-kmm-poc.md)

## Changelog

- 2025-01-15: História criada
