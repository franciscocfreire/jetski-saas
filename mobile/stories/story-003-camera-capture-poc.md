---
story_id: STORY-003
epic: EPIC-07
title: POC Captura de Fotos (CameraX + AVFoundation)
status: TODO
priority: HIGH
estimate: 5
assignee: Unassigned
started_at: null
completed_at: null
tags: [mobile, camera, android, ios, poc]
dependencies: [STORY-001]
---

# STORY-003: POC Captura de Fotos (CameraX + AVFoundation)

## Como
Mobile Developer

## Quero
Capturar fotos usando câmera nativa com metadados (timestamp, geo)

## Para que
Operadores possam fotografar jetskis no check-in

## Critérios de Aceite

- [ ] **CA1:** Permissão de câmera solicitada no Android e iOS
- [ ] **CA2:** Captura de foto funciona no Android (CameraX)
- [ ] **CA3:** Captura de foto funciona no iOS (AVFoundation)
- [ ] **CA4:** Foto comprimida (< 1MB, JPEG ou WebP)
- [ ] **CA5:** Metadados incluem timestamp e geolocalização
- [ ] **CA6:** Hash SHA-256 calculado para cada foto

## Tarefas Técnicas

- [ ] Adicionar permissões no AndroidManifest.xml
- [ ] Adicionar permissões no Info.plist (iOS)
- [ ] Implementar captura com CameraX (Android)
- [ ] Implementar captura com AVFoundation (iOS)
- [ ] Criar `ImageCompressor` (expect/actual) para compressão
- [ ] Criar `HashCalculator` para SHA-256
- [ ] Adicionar metadados EXIF
- [ ] Testar captura e compressão

## Links

- **Epic:** [EPIC-07](../../stories/epics/epic-07-mobile-kmm-poc.md)

## Changelog

- 2025-01-15: História criada
