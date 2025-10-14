---
story_id: STORY-002
epic: EPIC-07
title: Integração AppAuth para Keycloak (PKCE)
status: TODO
priority: CRITICAL
estimate: 8
assignee: Unassigned
started_at: null
completed_at: null
tags: [mobile, auth, appauth, keycloak, pkce]
dependencies: [STORY-001]
---

# STORY-002: Integração AppAuth para Keycloak (PKCE)

## Como
Mobile Developer

## Quero
Login via Keycloak usando AppAuth nativo (Android/iOS) com PKCE

## Para que
Usuários possam autenticar de forma segura nos apps mobile

## Critérios de Aceite

- [ ] **CA1:** AppAuth configurado no Android
- [ ] **CA2:** AppAuth configurado no iOS
- [ ] **CA3:** Fluxo PKCE funciona: login → autorização → callback → token
- [ ] **CA4:** Tokens armazenados de forma segura (Keystore/Keychain)
- [ ] **CA5:** `SecureStore` (expect/actual) implementado
- [ ] **CA6:** `TokenProvider` no `:shared` distribui tokens para Ktor

## Tarefas Técnicas

- [ ] Instalar AppAuth Android (`net.openid:appauth`)
- [ ] Instalar AppAuth iOS (CocoaPods ou SPM)
- [ ] Criar `SecureStore.android.kt` (EncryptedSharedPreferences)
- [ ] Criar `SecureStore.ios.kt` (Keychain)
- [ ] Criar interface `OAuthCoordinator` (expect/actual)
- [ ] Implementar fluxo de login em Android
- [ ] Implementar fluxo de login em iOS
- [ ] Criar `TokenProvider` compartilhado
- [ ] Testar fluxo completo nos 2 alvos

## Links

- **Epic:** [EPIC-07](../../stories/epics/epic-07-mobile-kmm-poc.md)

## Changelog

- 2025-01-15: História criada
