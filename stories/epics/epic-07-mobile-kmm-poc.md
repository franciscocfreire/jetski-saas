---
epic_id: EPIC-07
title: Mobile KMM POC
status: TODO
priority: LOW
start_date: 2025-03-26
target_date: 2025-04-08
owner: Team Mobile
dependencies: [EPIC-01, EPIC-03]
---

# EPIC-07: Mobile KMM POC (Kotlin Multiplatform)

## Objetivo

Desenvolver POC (Proof of Concept) do app mobile usando Kotlin Multiplatform Mobile (KMM) com funcionalidades essenciais: autenticação Keycloak, captura de fotos, check-in offline-first e sincronização com backend.

## Escopo

### Incluído (POC)
- [ ] Setup projeto KMM com módulo `:shared`, `:androidApp`, `:iosApp`
- [ ] Configurar SQLDelight para banco local
- [ ] Configurar Ktor para chamadas HTTP
- [ ] Implementar `SecureStore` (expect/actual) para Keystore/Keychain
- [ ] Autenticação via AppAuth (PKCE) com Keycloak
- [ ] Seleção de tenant (login)
- [ ] Tela de check-in: capturar 4 fotos obrigatórias
- [ ] Validação de horímetro inicial
- [ ] Checklist de segurança (EPIs, boia, lacres)
- [ ] Calcular SHA-256 das fotos
- [ ] Obter presigned URL do backend
- [ ] Upload de fotos para S3 (chunked se > 2MB)
- [ ] Fila offline: persistir check-ins pendentes no SQLDelight
- [ ] Sincronização automática com backoff exponencial
- [ ] Background workers (WorkManager/BGTaskScheduler)

### Excluído (fora do POC)
- Tela de check-out (será após POC)
- Funcionalidades de reserva/agenda (será após POC)
- Telemetria/GPS em tempo real
- Assinatura digital eletrônica

## Histórias Relacionadas

### Mobile
- `mobile/stories/story-001-kmm-project-setup.md` (5 pts)
- `mobile/stories/story-002-appauth-integration.md` (8 pts)
- `mobile/stories/story-003-camera-capture-poc.md` (5 pts)
- `mobile/stories/story-004-camera-capture-checkin.md` (8 pts)
- `mobile/stories/story-005-photo-upload-s3.md` (5 pts)
- `mobile/stories/story-006-offline-queue-sqldelight.md` (8 pts)
- `mobile/stories/story-007-sync-background-workers.md` (8 pts)

**Total estimado:** 47 story points (~3 sprints)

## Critérios de Aceite

### Autenticação
- [ ] Operador faz login via Keycloak (PKCE) no Android
- [ ] Operador faz login via Keycloak (PKCE) no iOS
- [ ] Tokens são armazenados de forma segura (Keystore/Keychain)
- [ ] Refresh token funciona automaticamente

### Captura de Fotos
- [ ] App solicita permissão de câmera (Android/iOS)
- [ ] Operador captura 4 fotos: frente, laterais, painel horímetro, (opcional) casco
- [ ] Fotos são comprimidas (WebP ou JPEG, < 1MB cada)
- [ ] Metadados incluem timestamp e geolocalização
- [ ] SHA-256 é calculado para cada foto

### Check-in
- [ ] Operador seleciona jetski da lista (disponíveis)
- [ ] Operador insere horímetro inicial
- [ ] Operador preenche checklist (checkboxes)
- [ ] App não permite concluir check-in sem 4 fotos
- [ ] Check-in é salvo localmente (SQLDelight) se offline

### Upload e Sincronização
- [ ] App solicita presigned URL do backend
- [ ] Upload de foto usa presigned URL para S3
- [ ] Upload com retry automático (3 tentativas com backoff)
- [ ] Se offline, check-in fica na fila e sincroniza quando online
- [ ] Background worker sincroniza pendências a cada 15 minutos
- [ ] Progresso de upload é exibido (0-100%)

### Offline-first
- [ ] App funciona sem conexão (captura fotos, salva check-in)
- [ ] Fila de pendências é exibida ao usuário
- [ ] Sincronização automática quando conexão é restaurada

## Riscos

**Risco Alto:**
- **Complexidade do KMM**: Curva de aprendizado da tecnologia.
  - **Mitigação**: Time de Mobile faz treinamento prévio, começar com POC simples

**Risco Médio:**
- **Fragmentação Android**: Muitas versões e dispositivos diferentes.
  - **Mitigação**: Suportar apenas Android 8+ (API 26+) no POC

**Risco Médio:**
- **Review da App Store**: Permissões de câmera e localização podem ser questionadas.
  - **Mitigação**: Justificativas claras nas strings de permissão

## Dependências

- Keycloak configurado com client público (PKCE)
- Backend com endpoint de presigned URL funcionando (EPIC-03)
- Bucket S3 com CORS configurado

## Métricas de Sucesso

- Taxa de sucesso de upload: > 95%
- Tempo médio de check-in (4 fotos): < 2 minutos
- Taxa de sincronização offline: 100% (nenhuma perda de dados)

## Notas

### Stack Tecnológico

**Shared (`:shared`)**
- Kotlin Multiplatform
- Ktor Client (HTTP)
- SQLDelight (banco local)
- kotlinx.serialization (JSON)
- kotlinx-datetime
- Napier (logging)

**Android (`:androidApp`)**
- Jetpack Compose
- CameraX (captura de fotos)
- WorkManager (background sync)
- AppAuth Android
- Security Crypto (EncryptedSharedPreferences)

**iOS (`:iosApp`)**
- SwiftUI
- AVFoundation (captura de fotos)
- BGTaskScheduler (background sync)
- AppAuth iOS
- Keychain

### Estrutura do Projeto

```
mobile/
├── shared/
│   ├── src/
│   │   ├── commonMain/kotlin/
│   │   │   ├── domain/
│   │   │   ├── data/
│   │   │   │   ├── repository/
│   │   │   │   ├── network/
│   │   │   │   └── local/
│   │   │   ├── sync/
│   │   │   └── auth/
│   │   ├── androidMain/kotlin/
│   │   │   └── SecureStore.android.kt
│   │   └── iosMain/kotlin/
│   │       └── SecureStore.ios.kt
│   └── build.gradle.kts
├── androidApp/
│   └── src/main/kotlin/
│       ├── ui/
│       ├── camera/
│       └── MainActivity.kt
└── iosApp/
    └── iosApp/
        ├── UI/
        ├── Camera/
        └── AppDelegate.swift
```

### Exemplo de Expect/Actual (SecureStore)

```kotlin
// shared/commonMain
expect class SecureStore() {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
}

// shared/androidMain
actual class SecureStore {
    private val prefs = EncryptedSharedPreferences.create(...)

    actual fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun get(key: String): String? {
        return prefs.getString(key, null)
    }

    actual fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

// shared/iosMain
actual class SecureStore {
    actual fun put(key: String, value: String) {
        // Keychain implementation
    }

    actual fun get(key: String): String? {
        // Keychain implementation
    }

    actual fun remove(key: String) {
        // Keychain implementation
    }
}
```

### Fila de Sincronização (SQLDelight)

```sql
CREATE TABLE sync_queue (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,  -- 'checkin', 'checkout', 'foto'
    payload TEXT NOT NULL,  -- JSON
    created_at INTEGER NOT NULL,
    retry_count INTEGER DEFAULT 0,
    last_retry_at INTEGER,
    status TEXT DEFAULT 'pending'  -- 'pending', 'syncing', 'failed', 'completed'
);
```

## Changelog

- 2025-01-15: Épico criado
