# Introdução ao Kotlin Multiplatform Mobile (KMM)

Este documento explica o que é KMM, como funciona, e por que estamos usando no projeto Jetski.

---

## 🤔 O que é KMM?

**Kotlin Multiplatform Mobile (KMM)** é uma tecnologia da JetBrains que permite:

✅ **Escrever código uma vez** e rodar em Android **e** iOS
✅ **Compartilhar lógica de negócio** (networking, validações, cache)
✅ **Manter UI nativa** (Jetpack Compose no Android, SwiftUI no iOS)

### Analogia Simples

Imagine que você tem duas lojas (Android e iOS):
- **Fachada diferente** (UI nativa, design próprio de cada plataforma)
- **Estoque compartilhado** (mesma lógica de negócio, banco de dados, API calls)

KMM é o "estoque compartilhado".

---

## 🏗️ Arquitetura KMM

### Estrutura de Projeto

```
jetski-mobile/
├── shared/                    # Código Kotlin compartilhado
│   ├── commonMain/           # Código que roda em AMBAS plataformas
│   ├── androidMain/          # Código específico ANDROID
│   └── iosMain/              # Código específico iOS
│
├── androidApp/                # App Android (Jetpack Compose)
│   └── src/main/java/        # UI Android
│
└── iosApp/                    # App iOS (SwiftUI)
    └── iosApp/                # UI iOS
```

### O que vai em cada módulo?

#### `:shared` (Código compartilhado)

**commonMain** - Código multiplataforma:
- ✅ Modelos de dados (DTOs: `Jetski`, `Locacao`, `Reserva`)
- ✅ Lógica de negócio (validações, cálculos)
- ✅ Repositories (cache + API)
- ✅ Networking (Ktor Client)
- ✅ Banco de dados local (SQLDelight)
- ✅ Serialização JSON (kotlinx.serialization)

**androidMain** - Específico Android:
- Implementações dependentes de APIs Android
- Exemplo: `SecureStore` usando `EncryptedSharedPreferences`

**iosMain** - Específico iOS:
- Implementações dependentes de APIs iOS
- Exemplo: `SecureStore` usando `Keychain`

#### `:androidApp` (UI Android)

- Jetpack Compose (UI declarativa)
- Navigation
- ViewModels
- Activities/Screens
- Permissões (Camera, Storage)

#### `:iosApp` (UI iOS)

- SwiftUI (UI declarativa)
- Navigation
- Views
- iOS-specific permissions

---

## 🔑 Conceito-Chave: `expect/actual`

### O Problema

Algumas funcionalidades precisam de código específico por plataforma:
- **Armazenar token seguro**: Android usa `EncryptedSharedPreferences`, iOS usa `Keychain`
- **Acessar câmera**: Android usa CameraX, iOS usa AVFoundation

### A Solução: `expect/actual`

**1. Declarar interface comum (`expect`)**

```kotlin
// shared/src/commonMain/kotlin/storage/SecureStore.kt
expect class SecureStore() {
    fun saveToken(key: String, value: String)
    fun getToken(key: String): String?
    fun deleteToken(key: String)
}
```

**2. Implementar para Android (`actual`)**

```kotlin
// shared/src/androidMain/kotlin/storage/SecureStore.android.kt
actual class SecureStore(private val context: Context) {
    private val encryptedPrefs = EncryptedSharedPreferences.create(...)

    actual fun saveToken(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }

    actual fun getToken(key: String): String? {
        return encryptedPrefs.getString(key, null)
    }

    actual fun deleteToken(key: String) {
        encryptedPrefs.edit().remove(key).apply()
    }
}
```

**3. Implementar para iOS (`actual`)**

```kotlin
// shared/src/iosMain/kotlin/storage/SecureStore.ios.kt
actual class SecureStore() {
    actual fun saveToken(key: String, value: String) {
        // Usar Keychain via interop com iOS APIs
        KeychainWrapper.save(key, value)
    }

    actual fun getToken(key: String): String? {
        return KeychainWrapper.get(key)
    }

    actual fun deleteToken(key: String) {
        KeychainWrapper.delete(key)
    }
}
```

**4. Usar em código compartilhado**

```kotlin
// shared/src/commonMain/kotlin/auth/AuthRepository.kt
class AuthRepository(private val secureStore: SecureStore) {

    suspend fun login(username: String, password: String) {
        val token = apiService.authenticate(username, password)

        // Funciona em AMBAS plataformas! 🎉
        secureStore.saveToken("access_token", token.accessToken)
    }

    fun getAccessToken(): String? {
        return secureStore.getToken("access_token")
    }
}
```

**Mágica**: Kotlin compila o código certo para cada plataforma automaticamente!

---

## 📚 Bibliotecas Multiplataforma

### Principais libs que usaremos:

#### 1. **Ktor Client** (Networking)
```kotlin
// Fazer requisições HTTP
val client = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

val jetskis: List<Jetski> = client.get("https://api.jetski.com/jetskis")
```

**Por quê?** Funciona em Android, iOS, e até Web/Desktop.

#### 2. **kotlinx.serialization** (JSON)
```kotlin
@Serializable
data class Jetski(
    val id: String,
    val serie: String,
    val status: String
)

// Auto-converte JSON ↔ objeto
val json = """{"id":"123","serie":"ABC","status":"DISPONIVEL"}"""
val jetski = Json.decodeFromString<Jetski>(json)
```

**Por quê?** Multiplataforma e type-safe (erros em compile-time).

#### 3. **SQLDelight** (Banco de dados local)
```sql
-- Escrever SQL puro
CREATE TABLE Jetski (
  id TEXT PRIMARY KEY,
  serie TEXT NOT NULL,
  status TEXT NOT NULL
);

-- Queries type-safe geradas automaticamente
SELECT * FROM Jetski WHERE status = ?;
```

```kotlin
// Usar em Kotlin
val jetskis = database.jetskiQueries.selectByStatus("DISPONIVEL").executeAsList()
```

**Por quê?** Type-safe, rápido, e funciona em ambas plataformas.

#### 4. **kotlinx-datetime** (Datas)
```kotlin
val now = Clock.System.now()
val instant = Instant.parse("2025-01-15T10:30:00Z")
val formatted = instant.toLocalDateTime(TimeZone.UTC)
```

**Por quê?** Substituição multiplataforma para `java.time` e `NSDate`.

#### 5. **Napier** (Logging)
```kotlin
Napier.d("Check-in iniciado", tag = "LocacaoRepository")
Napier.e("Erro no upload", throwable = exception, tag = "PhotoRepository")
```

**Por quê?** Logs que funcionam em Android (Logcat) e iOS (NSLog).

---

## 🔄 Fluxo de Desenvolvimento

### Como o código é executado?

#### Android:
```
Kotlin Shared (commonMain + androidMain)
    ↓ compile
Bytecode JVM (.class)
    ↓
Dalvik/ART (Android Runtime)
    ↓
App Android rodando
```

#### iOS:
```
Kotlin Shared (commonMain + iosMain)
    ↓ Kotlin/Native compiler
Framework iOS (.framework)
    ↓ Xcode
Swift/Objective-C interop
    ↓
App iOS rodando
```

**Importante**: No Windows, você pode compilar Android normalmente, mas iOS precisa de macOS + Xcode.

---

## 🎯 O que é possível fazer no Windows?

### ✅ Possível (100% funcional):

1. **Escrever TODO código Kotlin** (commonMain, androidMain, iosMain)
2. **Compilar módulo `:shared`** para Android
3. **Desenvolver e testar app Android** (emulador ou device)
4. **Gradle sync do projeto completo** (incluindo iosApp)
5. **Compartilhar código** via Git para compilar iOS em Mac

### ❌ Não possível (precisa macOS):

1. **Compilar `:shared` para iOS** (Kotlin/Native → framework)
2. **Rodar app iOS** em simulador ou device
3. **Testar código iosMain** diretamente
4. **Gerar IPA** (instalador iOS)

### 🔄 Workflow híbrido:

**Desenvolvimento no Windows**:
- Escreve código shared (commonMain)
- Escreve código androidMain
- Testa tudo em Android

**Build iOS** (quando necessário):
- Usa Mac físico OU
- Usa GitHub Actions (CI/CD com macOS runner) OU
- Usa MacStadium/MacinCloud (Mac remoto)

---

## 🛠️ Ferramentas de Build

### Gradle

Todo projeto KMM usa **Gradle** como sistema de build:

```kotlin
// build.gradle.kts (Kotlin DSL)
plugins {
    kotlin("multiplatform") version "1.9.20"
    id("com.android.application")
}

kotlin {
    android()  // Target Android
    ios()      // Target iOS

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:2.3.7")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:2.3.7")
            }
        }
        val iosMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.3.7")
            }
        }
    }
}
```

**Comandos úteis**:
```bash
# Compilar tudo
./gradlew build

# Compilar apenas Android
./gradlew :androidApp:assembleDebug

# Rodar testes shared
./gradlew :shared:test

# Instalar app no emulador
./gradlew :androidApp:installDebug
```

---

## 🎓 Conceitos de Kotlin para Mobile

### 1. Coroutines (Programação Assíncrona)

**Problema**: Requisições HTTP bloqueiam a UI.

**Solução**: `suspend fun` (como async/await do JavaScript)

```kotlin
// ❌ Bloqueante (trava a UI)
fun getJetskis(): List<Jetski> {
    return httpClient.get("https://api.com/jetskis")  // Espera resposta
}

// ✅ Assíncrono (não bloqueia)
suspend fun getJetskis(): List<Jetski> {
    return httpClient.get("https://api.com/jetskis")  // Suspende, não bloqueia
}

// Chamar de uma coroutine
viewModelScope.launch {
    val jetskis = repository.getJetskis()  // Executa em background
    _uiState.value = jetskis  // Atualiza UI
}
```

**Por quê?** App responsivo, sem ANR (Application Not Responding).

### 2. Flow (Streams reativos)

**Conceito**: Observar mudanças ao longo do tempo.

```kotlin
// Repositório emite lista atualizada
fun observeJetskis(): Flow<List<Jetski>> = flow {
    while (true) {
        val jetskis = database.getAllJetskis()
        emit(jetskis)  // Emitir nova lista
        delay(5000)  // Atualizar a cada 5s
    }
}

// UI observa e atualiza automaticamente
val jetskis: StateFlow<List<Jetski>> = repository.observeJetskis()
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
```

**Analogia**: Flow = Netflix stream (dados chegam continuamente)

### 3. Null Safety

**Kotlin é null-safe** (evita `NullPointerException`):

```kotlin
var serie: String = "ABC123"      // Não pode ser null
var serie: String? = null         // Pode ser null

// Safe call
val length = serie?.length        // null se serie for null

// Elvis operator
val length = serie?.length ?: 0   // 0 se serie for null

// Not-null assertion (cuidado!)
val length = serie!!.length       // Crash se serie for null
```

---

## 🚀 Vantagens do KMM

### Para o Projeto Jetski:

1. **Redução de código duplicado**: Lógica de negócio escrita uma vez
2. **Consistência**: Validações e regras iguais em ambas plataformas
3. **Manutenção mais fácil**: Bug fix no shared = fix em Android + iOS
4. **Performance nativa**: Não é WebView (como Ionic), é código nativo
5. **Gradual adoption**: Pode começar com Android e adicionar iOS depois

### Comparação com outras tecnologias:

| Tecnologia | UI | Lógica | Performance | Curva de Aprendizado |
|------------|-----|--------|-------------|---------------------|
| **KMM** | Nativa (Compose/SwiftUI) | Compartilhada (Kotlin) | ⭐⭐⭐⭐⭐ Excelente | 🟡 Média |
| **Flutter** | Compartilhada (Dart) | Compartilhada (Dart) | ⭐⭐⭐⭐ Boa | 🟢 Fácil |
| **React Native** | Compartilhada (JS/TS) | Compartilhada (JS/TS) | ⭐⭐⭐ OK | 🟢 Fácil |
| **Nativo puro** | Nativa | Duplicada | ⭐⭐⭐⭐⭐ Excelente | 🔴 Difícil |

**Por que escolhemos KMM?**
- Performance nativa (crítico para câmera/fotos)
- UI 100% nativa (melhor UX)
- Backend já é Kotlin (mesmo ecossistema)

---

## 📖 Próximos Passos

Agora que você entende KMM:

1. ✅ Prosseguir para `ARCHITECTURE.md` (arquitetura do projeto Jetski)
2. ✅ Começar a criar o projeto (seguir plano de implementação)
3. 📚 Recursos de estudo:
   - [Kotlin Multiplatform Docs](https://kotlinlang.org/docs/multiplatform.html)
   - [KMM Portal](https://kotlinlang.org/docs/multiplatform-mobile-getting-started.html)
   - [Ktor Client Tutorial](https://ktor.io/docs/getting-started-ktor-client.html)

---

**Pronto para começar? 🚀 Vamos criar o projeto!**
