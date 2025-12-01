# Arquitetura do Jetski Mobile

Este documento descreve as decisões arquiteturais do aplicativo mobile.

## 🏗️ Visão Geral

O Jetski Mobile segue uma arquitetura **Clean Architecture + MVVM** com separação clara entre camadas:

```
┌─────────────────────────────────────────┐
│         UI Layer (Jetpack Compose)      │
│  ┌─────────────┐      ┌──────────────┐ │
│  │   Screens   │ ───▶ │  ViewModels  │ │
│  └─────────────┘      └──────────────┘ │
└─────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│      Domain Layer (Use Cases)           │
│  ┌─────────────────────────────────┐   │
│  │  Business Logic & Validation    │   │
│  └─────────────────────────────────┘   │
└─────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│        Data Layer (Repositories)        │
│  ┌──────────┐            ┌───────────┐ │
│  │   API    │            │  Database │ │
│  │ (Remote) │            │  (Local)  │ │
│  └──────────┘            └───────────┘ │
└─────────────────────────────────────────┘
```

## 📦 Módulos

### `:shared` (Kotlin Multiplatform)

**Responsabilidade**: Lógica compartilhada entre Android e iOS

**Estrutura**:
```
shared/src/
├── commonMain/kotlin/com/jetski/shared/
│   ├── data/
│   │   ├── models/              # DTOs (Jetski, Locacao, Reserva)
│   │   ├── repositories/        # Repositórios (cache + sync)
│   │   └── local/               # Database (SQLDelight)
│   │
│   ├── domain/
│   │   ├── usecases/            # Use Cases (CheckInUseCase, etc)
│   │   └── validators/          # Validações de negócio
│   │
│   ├── network/
│   │   ├── KtorClient.kt        # Cliente HTTP configurado
│   │   ├── api/                 # API Services
│   │   └── interceptors/        # Auth, Headers, Logging
│   │
│   └── utils/
│       ├── Result.kt            # Wrapper de sucesso/erro
│       └── Constants.kt         # Constantes
│
├── androidMain/kotlin/com/jetski/shared/
│   └── platform/
│       ├── SecureStore.android.kt    # EncryptedSharedPreferences
│       └── Logger.android.kt         # Logcat
│
└── iosMain/kotlin/com/jetski/shared/
    └── platform/
        ├── SecureStore.ios.kt        # Keychain
        └── Logger.ios.kt             # NSLog
```

**Dependências principais**:
- Ktor Client (networking)
- SQLDelight (database)
- kotlinx.serialization (JSON)
- kotlinx-datetime (dates)
- Napier (logging)

---

### `:androidApp` (Android Native)

**Responsabilidade**: UI Android e integrações específicas da plataforma

**Estrutura**:
```
androidApp/src/main/java/com/jetski/mobile/
├── ui/
│   ├── theme/                   # Material 3 Theme
│   ├── components/              # Componentes reutilizáveis
│   │   ├── PhotoCard.kt
│   │   ├── JetskiCard.kt
│   │   └── LoadingIndicator.kt
│   │
│   ├── screens/
│   │   ├── login/
│   │   │   ├── LoginScreen.kt
│   │   │   └── LoginViewModel.kt
│   │   │
│   │   ├── tenant/
│   │   │   ├── TenantSelectorScreen.kt
│   │   │   └── TenantSelectorViewModel.kt
│   │   │
│   │   ├── jetski/
│   │   │   ├── JetskiListScreen.kt
│   │   │   └── JetskiListViewModel.kt
│   │   │
│   │   ├── checkin/
│   │   │   ├── CheckInScreen.kt
│   │   │   ├── CheckInViewModel.kt
│   │   │   └── CameraScreen.kt
│   │   │
│   │   └── checkout/
│   │       ├── CheckOutScreen.kt
│   │       └── CheckOutViewModel.kt
│   │
│   └── navigation/
│       └── NavGraph.kt          # Compose Navigation
│
├── workers/
│   └── PhotoSyncWorker.kt       # WorkManager (background sync)
│
├── auth/
│   ├── AuthManager.kt           # AppAuth wrapper
│   └── TokenManager.kt          # Token refresh logic
│
└── MainActivity.kt              # Entry point
```

**Dependências principais**:
- Jetpack Compose (UI)
- Navigation Compose (navegação)
- CameraX (câmera)
- AppAuth (OAuth2/PKCE)
- WorkManager (background jobs)
- Security Crypto (secure storage)

---

## 🔄 Fluxo de Dados

### Exemplo: Carregar lista de jetskis

```
┌───────────────┐
│ JetskiList    │  1. Usuário abre tela
│ Screen        │
└───────┬───────┘
        │ observes StateFlow
        ▼
┌───────────────┐
│ JetskiList    │  2. ViewModel pede dados
│ ViewModel     │
└───────┬───────┘
        │ calls suspend fun
        ▼
┌───────────────┐
│ Jetski        │  3. Repository decide: cache ou API?
│ Repository    │
└───┬───────┬───┘
    │       │
    ▼       ▼
┌────────┐ ┌────────┐
│ Local  │ │ Remote │  4a. Cache local (SQLDelight)
│ DB     │ │ API    │  4b. API remota (Ktor)
└────────┘ └────────┘
    │       │
    └───┬───┘
        ▼
   ┌─────────┐
   │ Result  │  5. Retorna Result<List<Jetski>>
   └────┬────┘
        │
        ▼
   ┌─────────┐
   │ UI      │  6. UI atualiza automaticamente
   │ Updates │
   └─────────┘
```

---

## 🎨 Padrões de Design

### 1. Repository Pattern

**Problema**: UI não deve saber de onde vêm os dados (API, cache, database).

**Solução**: Repository abstrai a origem dos dados.

```kotlin
class JetskiRepository(
    private val apiService: JetskiApiService,
    private val localDatabase: JetskiDatabase
) {
    suspend fun getJetskis(tenantId: String): Result<List<Jetski>> {
        return try {
            // 1. Tentar cache primeiro
            val cached = localDatabase.getJetskis(tenantId)
            if (cached.isNotEmpty() && !isCacheExpired()) {
                return Result.Success(cached)
            }

            // 2. Buscar da API
            val remote = apiService.getJetskis(tenantId)

            // 3. Salvar no cache
            localDatabase.saveJetskis(remote)

            Result.Success(remote)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
```

**Benefícios**:
- ✅ UI não conhece Ktor ou SQLDelight
- ✅ Fácil trocar implementação (ex: mudar de SQLDelight para Room)
- ✅ Testável (mock do Repository)

---

### 2. MVVM (Model-View-ViewModel)

**Problema**: Lógica de UI misturada com apresentação.

**Solução**: ViewModel mantém estado e lógica.

```kotlin
class JetskiListViewModel(
    private val repository: JetskiRepository
) : ViewModel() {

    // Estado da UI (imutável para Compose)
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadJetskis()
    }

    fun loadJetskis() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            when (val result = repository.getJetskis(tenantId)) {
                is Result.Success -> {
                    _uiState.value = UiState.Success(result.data)
                }
                is Result.Error -> {
                    _uiState.value = UiState.Error(result.exception.message)
                }
            }
        }
    }

    sealed class UiState {
        object Loading : UiState()
        data class Success(val jetskis: List<Jetski>) : UiState()
        data class Error(val message: String?) : UiState()
    }
}
```

**Tela Compose observa o estado**:

```kotlin
@Composable
fun JetskiListScreen(viewModel: JetskiListViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    when (uiState) {
        is UiState.Loading -> LoadingIndicator()
        is UiState.Success -> JetskiList(uiState.jetskis)
        is UiState.Error -> ErrorMessage(uiState.message)
    }
}
```

**Benefícios**:
- ✅ Estado sobrevive a rotações de tela
- ✅ Lógica separada da UI (testável)
- ✅ Reativo (UI atualiza automaticamente)

---

### 3. Use Cases (Opcional, para lógica complexa)

**Problema**: Validações e lógica de negócio no ViewModel.

**Solução**: Extrair para Use Cases.

```kotlin
class CheckInUseCase(
    private val locacaoRepository: LocacaoRepository,
    private val photoRepository: PhotoRepository,
    private val validator: CheckInValidator
) {
    suspend operator fun invoke(
        jetskiId: String,
        horimetroInicio: Double,
        photos: List<PhotoFile>
    ): Result<Locacao> {
        // 1. Validar dados
        validator.validate(horimetroInicio, photos).onFailure {
            return Result.Error(it)
        }

        // 2. Criar locação
        val locacao = locacaoRepository.createCheckIn(jetskiId, horimetroInicio)

        // 3. Fazer upload das fotos
        photos.forEach { photo ->
            photoRepository.uploadPhoto(locacao.id, photo)
        }

        return Result.Success(locacao)
    }
}
```

**Benefícios**:
- ✅ ViewModel mais limpo
- ✅ Lógica reutilizável
- ✅ Fácil de testar isoladamente

---

## 🔐 Segurança

### Token Storage (expect/actual)

```kotlin
// commonMain
expect class SecureStore {
    fun saveToken(key: String, value: String)
    fun getToken(key: String): String?
}

// androidMain
actual class SecureStore(context: Context) {
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        "jetski_secure",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    actual fun saveToken(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }

    actual fun getToken(key: String): String? {
        return encryptedPrefs.getString(key, null)
    }
}
```

**Tokens armazenados**:
- `access_token` (JWT)
- `refresh_token`
- `tenant_id`

---

## 📡 Networking

### Ktor Client (configurado no shared)

```kotlin
val httpClient = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }

    install(Logging) {
        logger = Logger.SIMPLE
        level = LogLevel.BODY
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
    }

    defaultRequest {
        url(ApiConfig.BASE_URL)

        // Adicionar headers em toda requisição
        header("Authorization", "Bearer ${secureStore.getToken("access_token")}")
        header("X-Tenant-Id", secureStore.getToken("tenant_id"))
        header("Content-Type", "application/json")
    }
}
```

### Interceptors

**AuthInterceptor**: Refresh automático do token se expirado

```kotlin
install(HttpSend) {
    maxSendCount = 2  // Retry 1 vez

    intercept { request ->
        val originalCall = execute(request)

        // Se 401, tentar refresh
        if (originalCall.response.status == HttpStatusCode.Unauthorized) {
            val newToken = authService.refreshToken()
            secureStore.saveToken("access_token", newToken)

            // Retry com novo token
            execute(request.apply {
                header("Authorization", "Bearer $newToken")
            })
        } else {
            originalCall
        }
    }
}
```

---

## 💾 Offline-First Strategy

### SQLDelight Schema

```sql
-- FotoUploadQueue.sq
CREATE TABLE FotoUploadQueue (
  id TEXT PRIMARY KEY,
  locacaoId TEXT NOT NULL,
  tipoFoto TEXT NOT NULL,
  localFilePath TEXT NOT NULL,
  hashSha256 TEXT NOT NULL,
  tamanhoBytes INTEGER NOT NULL,
  createdAt INTEGER NOT NULL,
  syncStatus TEXT NOT NULL,  -- PENDING, UPLOADING, COMPLETED, FAILED
  retryCount INTEGER DEFAULT 0,
  lastError TEXT
);

-- Query para pegar pendentes
getPendingUploads:
SELECT * FROM FotoUploadQueue
WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'
ORDER BY createdAt ASC;

-- Inserir foto na fila
insertPhoto:
INSERT INTO FotoUploadQueue(id, locacaoId, tipoFoto, localFilePath, hashSha256, tamanhoBytes, createdAt, syncStatus)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

-- Atualizar status
updateStatus:
UPDATE FotoUploadQueue
SET syncStatus = ?, lastError = ?
WHERE id = ?;
```

### Repository com Sync

```kotlin
class PhotoRepository(
    private val apiService: PhotoApiService,
    private val database: PhotoDatabase
) {
    suspend fun queuePhotoUpload(photo: PhotoFile) {
        // Salvar localmente primeiro
        database.insertPhoto(
            id = UUID.randomUUID().toString(),
            locacaoId = photo.locacaoId,
            tipoFoto = photo.tipo,
            localFilePath = photo.path,
            hashSha256 = photo.hash,
            tamanhoBytes = photo.size,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            syncStatus = "PENDING"
        )

        // Tentar upload imediato (se online)
        if (isOnline()) {
            processPendingUploads()
        }
    }

    suspend fun processPendingUploads() {
        val pending = database.getPendingUploads()

        pending.forEach { photo ->
            try {
                // 1. Solicitar presigned URL
                val uploadUrl = apiService.requestUploadUrl(
                    tenantId = currentTenantId,
                    locacaoId = photo.locacaoId,
                    tipoFoto = photo.tipoFoto
                )

                // 2. Upload direto ao S3
                val bytes = File(photo.localFilePath).readBytes()
                apiService.uploadToS3(uploadUrl.uploadUrl, bytes)

                // 3. Confirmar ao backend
                apiService.confirmUpload(currentTenantId, uploadUrl.fotoId)

                // 4. Marcar como completo
                database.updateStatus(photo.id, "COMPLETED", null)

            } catch (e: Exception) {
                // Marcar como falhou, será retentado
                database.updateStatus(photo.id, "FAILED", e.message)
            }
        }
    }
}
```

### WorkManager (Android Background Sync)

```kotlin
class PhotoSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = PhotoRepository(/* DI */)

        return try {
            repository.processPendingUploads()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()  // Exponential backoff
            } else {
                Result.failure()
            }
        }
    }
}

// Agendar trabalho periódico
val workRequest = PeriodicWorkRequestBuilder<PhotoSyncWorker>(
    repeatInterval = 15,
    repeatIntervalTimeUnit = TimeUnit.MINUTES
)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    )
    .setBackoffCriteria(
        BackoffPolicy.EXPONENTIAL,
        WorkRequest.MIN_BACKOFF_MILLIS,
        TimeUnit.MILLISECONDS
    )
    .build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "PhotoSync",
    ExistingPeriodicWorkPolicy.KEEP,
    workRequest
)
```

---

## 🧪 Testabilidade

### Unit Test (Shared)

```kotlin
class JetskiRepositoryTest {
    private lateinit var repository: JetskiRepository
    private lateinit var mockApi: JetskiApiService
    private lateinit var mockDatabase: JetskiDatabase

    @Before
    fun setup() {
        mockApi = mockk()
        mockDatabase = mockk()
        repository = JetskiRepository(mockApi, mockDatabase)
    }

    @Test
    fun `should return cached jetskis if not expired`() = runTest {
        // Given
        val cached = listOf(Jetski("1", "ABC", "DISPONIVEL"))
        coEvery { mockDatabase.getJetskis(any()) } returns cached
        coEvery { mockDatabase.isCacheExpired() } returns false

        // When
        val result = repository.getJetskis("tenant-123")

        // Then
        assertTrue(result is Result.Success)
        assertEquals(cached, (result as Result.Success).data)
        coVerify(exactly = 0) { mockApi.getJetskis(any()) }  // Não chamou API
    }
}
```

---

## 📚 Decisões Técnicas

### Por que Ktor (não Retrofit)?
- ✅ Multiplataforma (Android + iOS)
- ✅ Coroutines nativo
- ✅ Leve e moderno

### Por que SQLDelight (não Room)?
- ✅ Multiplataforma (Room é só Android)
- ✅ Type-safe SQL puro
- ✅ Performance excelente

### Por que Jetpack Compose (não XML)?
- ✅ Declarativo (menos código)
- ✅ Reativo (UI atualiza sozinha)
- ✅ Futuro do Android

### Por que AppAuth (não implementação manual OAuth)?
- ✅ Certificado pelo Google
- ✅ PKCE built-in
- ✅ Seguro e testado

---

**Dúvidas sobre a arquitetura? Pergunte!** 😊
