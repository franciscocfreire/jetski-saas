# Backoffice API Integration - File Reference Guide

## Frontend Files (Next.js)

### API Client Configuration
**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/client.ts`
- Axios client setup
- Request interceptor (adds Authorization & X-Tenant-Id headers)
- Response interceptor (handles 401/403 errors)
- Helper functions: setAuthToken(), setTenantId(), getTenantId()

### Authentication Configuration
**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/auth.ts`
- NextAuth configuration
- Keycloak provider setup
- JWT callback (extracts accessToken, refreshToken, tenant_id)
- Session callback (returns session with tokens and tenantId)
- PKCE configuration

### Tenant State Management
**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/store/tenant-store.ts`
- Zustand store for tenant selection
- Persists tenant data to localStorage via middleware
- Calls setTenantId() when tenant changes
- Actions: setCurrentTenant(), setTenants(), clearTenant()

### Dashboard Layout (Auth Initialization)
**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/app/(dashboard)/layout.tsx`
- Initializes auth after login
- Calls setAuthToken() with session.accessToken
- Fetches user tenants from /v1/user/tenants
- Auto-selects first tenant
- Calls setTenantId() when tenant selected

### API Services

#### User Tenants Service
**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/services/user-tenants.ts`
```typescript
userTenantsService.getMyTenants() → GET /v1/user/tenants
```

#### Jetskis Service
**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/services/jetskis.ts`
```typescript
jetskisService.list() → GET /v1/tenants/{tenantId}/jetskis
jetskisService.getById(id) → GET /v1/tenants/{tenantId}/jetskis/{id}
jetskisService.create(req) → POST /v1/tenants/{tenantId}/jetskis
jetskisService.update(id, req) → PUT /v1/tenants/{tenantId}/jetskis/{id}
jetskisService.reactivate(id) → POST /v1/tenants/{tenantId}/jetskis/{id}/reactivate
```

#### Locacoes Service
**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/services/locacoes.ts`
```typescript
locacoesService.list(params) → GET /v1/tenants/{tenantId}/locacoes
locacoesService.getById(id) → GET /v1/tenants/{tenantId}/locacoes/{id}
locacoesService.checkInFromReserva(req) → POST /v1/tenants/{tenantId}/locacoes/check-in/reserva
locacoesService.checkInWalkIn(req) → POST /v1/tenants/{tenantId}/locacoes/check-in/walk-in
locacoesService.checkOut(id, req) → POST /v1/tenants/{tenantId}/locacoes/{id}/check-out
```

#### Reservas Service
**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/services/reservas.ts`
```typescript
reservasService.list(params) → GET /v1/tenants/{tenantId}/reservas
reservasService.getById(id) → GET /v1/tenants/{tenantId}/reservas/{id}
reservasService.create(req) → POST /v1/tenants/{tenantId}/reservas
reservasService.confirmar(id) → POST /v1/tenants/{tenantId}/reservas/{id}/confirmar
reservasService.alocarJetski(id, jetskiId) → POST /v1/tenants/{tenantId}/reservas/{id}/alocar-jetski
```

#### Modelos Service
**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/services/modelos.ts`
```typescript
modelosService.list(params) → GET /v1/tenants/{tenantId}/modelos
modelosService.getById(id) → GET /v1/tenants/{tenantId}/modelos/{id}
modelosService.create(req) → POST /v1/tenants/{tenantId}/modelos
modelosService.update(id, req) → PUT /v1/tenants/{tenantId}/modelos/{id}
```

#### Clientes Service
**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/services/clientes.ts`
```typescript
clientesService.list() → GET /v1/tenants/{tenantId}/clientes
clientesService.getById(id) → GET /v1/tenants/{tenantId}/clientes/{id}
clientesService.create(req) → POST /v1/tenants/{tenantId}/clientes
clientesService.update(id, req) → PUT /v1/tenants/{tenantId}/clientes/{id}
```

#### Vendedores Service
**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/services/vendedores.ts`
```typescript
vendedoresService.list() → GET /v1/tenants/{tenantId}/vendedores
vendedoresService.getById(id) → GET /v1/tenants/{tenantId}/vendedores/{id}
vendedoresService.create(req) → POST /v1/tenants/{tenantId}/vendedores
vendedoresService.update(id, req) → PUT /v1/tenants/{tenantId}/vendedores/{id}
```

#### Manutencoes Service
**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/services/manutencoes.ts`
```typescript
manutencaoesService.list() → GET /v1/tenants/{tenantId}/manutencoes
manutencaoesService.getById(id) → GET /v1/tenants/{tenantId}/manutencoes/{id}
manutencaoesService.create(req) → POST /v1/tenants/{tenantId}/manutencoes
```

### Type Definitions
**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/types.ts`
- All request/response DTOs
- Entity interfaces (Jetski, Locacao, Reserva, Modelo, etc.)
- Enum types (JetskiStatus, LocacaoStatus, etc.)
- Pagination types

### Configuration Files
**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/.env.local`
```
KEYCLOAK_CLIENT_ID=jetski-backoffice
KEYCLOAK_CLIENT_SECRET=
KEYCLOAK_ISSUER=http://localhost:8081/realms/jetski-saas
NEXTAUTH_URL=http://localhost:3002
NEXTAUTH_SECRET=g5jryuCyY/xKuutyKGwZ/FAqkSkbjPjbSYvefwkN0lY=
AUTH_TRUST_HOST=true
NEXT_PUBLIC_API_URL=http://localhost:8090/api
```

**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/.env.local.example`
- Template for environment variables

---

## Backend Files (Spring Boot)

### Tenant Filtering & Context

#### Tenant Filter
**File:** `/home/franciscocfreire/repos/jetski/backend/src/main/java/com/jetski/shared/internal/TenantFilter.java`
- OncePerRequestFilter implementation
- Extracts tenant ID from X-Tenant-Id header
- Validates tenant format (UUID)
- Validates user access via TenantAccessValidator
- Stores tenant_id and user roles in TenantContext
- Skips validation for public endpoints

#### Tenant Context
**File:** `/home/franciscocfreire/repos/jetski/backend/src/main/java/com/jetski/shared/security/TenantContext.java`
- Thread-local storage for tenant ID
- Thread-local storage for user roles
- Thread-local storage for usuario ID
- Static methods: setTenantId(), getTenantId(), setUserRoles(), getUserRoles()
- MUST be cleared in finally block to prevent memory leaks

### API Controllers

#### Jetski Controller
**File:** `/home/franciscocfreire/repos/jetski/backend/src/main/java/com/jetski/locacoes/api/JetskiController.java`
- @RequestMapping("/v1/tenants/{tenantId}/jetskis")
- Methods: listJetskis(), getById(), create(), update(), reactivate()
- All methods validate tenant context matches path parameter
- All methods use @PreAuthorize for role-based access control

#### Locacao Controller
**File:** `/home/franciscocfreire/repos/jetski/backend/src/main/java/com/jetski/locacoes/api/LocacaoController.java`
- @RequestMapping("/v1/tenants/{tenantId}/locacoes")
- Methods for check-in, check-out, listing
- Complex business logic for rental calculation

#### Reserva Controller
**File:** `/home/franciscocfreire/repos/jetski/backend/src/main/java/com/jetski/locacoes/api/ReservaController.java`
- @RequestMapping("/v1/tenants/{tenantId}/reservas")
- Reservation management endpoints

#### User Tenants Controller
**File:** `/home/franciscocfreire/repos/jetski/backend/src/main/java/com/jetski/usuarios/api/UserTenantsController.java`
- @RequestMapping("/v1/user/tenants")
- No @PathVariable tenantId (public endpoint)
- Returns list of tenants user is member of
- Endpoint pattern: GET /v1/user/tenants (no tenant in path)

### Security Configuration

#### Security Config
**File:** `/home/franciscocfreire/repos/jetski/backend/src/main/java/com/jetski/shared/security/SecurityConfig.java`
- Spring Security filter chain configuration
- OAuth2/OIDC with Keycloak
- CORS configuration
- TenantFilter added to chain
- Public endpoint configuration

#### JWT Authentication Converter
**File:** `/home/franciscocfreire/repos/jetski/backend/src/main/java/com/jetski/shared/security/JwtAuthenticationConverter.java`
- Extracts provider (keycloak, google, etc.) from JWT claims
- Extracts providerUserId from JWT sub claim
- Converts JWT to Spring Authentication

---

## Database Configuration

### Migration Files
**Directory:** `/home/franciscocfreire/repos/jetski/backend/src/main/resources/db/migration/`

- `V1_*.sql` - Initial schema with tenant tables
- All operational tables include `tenant_id UUID` column
- All tables have RLS policies enabled
- Composite indexes on (tenant_id, foreign_key)

---

## Key Code Snippets

### Request Interceptor (adds headers automatically)
**Location:** `lib/api/client.ts` (lines 13-36)
```typescript
apiClient.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    const token = typeof window !== 'undefined'
      ? sessionStorage.getItem('accessToken')
      : null
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    const tenantId = typeof window !== 'undefined'
      ? sessionStorage.getItem('tenantId')
      : null
    if (tenantId) {
      config.headers['X-Tenant-Id'] = tenantId
    }

    return config
  },
  (error) => Promise.reject(error)
)
```

### Service Method Pattern (URL construction with getTenantId)
**Location:** `lib/api/services/jetskis.ts` (line 4)
```typescript
const getBasePath = () => `/v1/tenants/${getTenantId()}/jetskis`

export const jetskisService = {
  async list(): Promise<Jetski[]> {
    const { data } = await apiClient.get<Jetski[]>(getBasePath())
    return data
  }
}
```

### Backend Endpoint Pattern (validates tenant context)
**Location:** `src/main/java/com/jetski/locacoes/api/JetskiController.java` (lines 55-91)
```java
@GetMapping
@PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
public ResponseEntity<List<JetskiResponse>> listJetskis(
    @PathVariable UUID tenantId,
    @RequestParam(required = false) JetskiStatus status,
    @RequestParam(defaultValue = "false") boolean includeInactive
) {
    validateTenantContext(tenantId);
    // ... implementation
}
```

### Tenant Filter (validates X-Tenant-Id header)
**Location:** `src/main/java/com/jetski/shared/internal/TenantFilter.java` (lines 72-93)
```java
String tenantIdStr = extractTenantId(request);
UUID tenantId = parseTenantId(tenantIdStr);
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
if (auth != null && auth.isAuthenticated()) {
    String provider = JwtAuthenticationConverter.extractProvider(jwt);
    String providerUserId = JwtAuthenticationConverter.extractProviderUserId(jwt);
    validateAccessViaDatabase(provider, providerUserId, tenantId);
}
TenantContext.setTenantId(tenantId);
```

---

## Configuration Constants

### Backend Constants
```java
// Header name (TenantFilter.java)
TENANT_HEADER_NAME = "X-Tenant-Id"

// Public endpoints that skip tenant validation (TenantFilter.java)
- /actuator/*
- /v3/api-docs
- /swagger-ui/*
- /v1/user/tenants  ← User can get their tenants without specifying one
- /v1/auth/complete-activation
- /v1/auth/magic-activate
- /v1/storage/local/*
```

### Frontend Constants
```typescript
// API base URL
API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8090/api'

// Storage keys
'accessToken' - JWT from Keycloak
'tenantId' - UUID of selected tenant

// Zustand store name
'tenant-storage' - Persisted to localStorage
```

### Keycloak Constants
```
Realm: jetski-saas
Client ID: jetski-backoffice
Issuer: http://localhost:8081/realms/jetski-saas
Grant type: authorization_code (PKCE)
Scopes: openid profile email
```

---

## Testing References

### Integration Test Pattern
**Location:** Tests use Testcontainers with Spring Boot Test

Key test files to reference:
- `LocacaoControllerTest.java` - Controller testing pattern
- `TenantFilterTest.java` - Tenant context testing
- Uses @WithMockUser or @WithJwtAuthentication

---

## Related Documentation

These analysis documents provide complete details:

1. **BACKOFFICE-API-REQUEST-ANALYSIS.md** (70KB)
   - Complete code samples
   - Configuration details
   - All API services documented

2. **BACKOFFICE-REQUEST-ARCHITECTURE.md** (60KB)
   - Flow diagrams
   - Architecture visualization
   - Request/response examples
   - Security boundaries

3. **BACKOFFICE-API-TROUBLESHOOTING.md** (50KB)
   - Common issues and solutions
   - Debugging techniques
   - Best practices
   - Quick checklist

4. **BACKOFFICE-API-SUMMARY.md** (30KB)
   - Executive summary
   - Quick facts
   - Quick reference

This file: **BACKOFFICE-API-FILE-REFERENCE.md**
   - File locations and purposes
   - Code snippet references

