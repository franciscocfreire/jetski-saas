# Backoffice API Request Architecture

## High-Level Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        FRONTEND (Next.js 14)                             │
│                     jetski-backoffice:3002                               │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌───────────────┐  ┌──────────────────┐  ┌────────────────────┐         │
│  │   Login Page  │→ │  NextAuth Callback│→│  Dashboard Layout  │         │
│  └───────────────┘  └──────────────────┘  └────────────────────┘         │
│                            ↓                         ↓                    │
│                    ┌──────────────┐         ┌────────────────┐            │
│                    │  Keycloak    │         │ setAuthToken() │            │
│                    │ 8081/realms  │         │ setTenantId()  │            │
│                    └──────────────┘         └────────────────┘            │
│                            ↓                         ↓                    │
│                    ┌──────────────┐         ┌──────────────────┐          │
│                    │ OAuth2/PKCE  │         │ sessionStorage   │          │
│                    │  Token Flow  │         │ .accessToken     │          │
│                    └──────────────┘         │ .tenantId        │          │
│                            ↓                └──────────────────┘          │
│                    ┌──────────────┐                                       │
│                    │  JWT Token   │                                       │
│                    │+ tenant_id   │                                       │
│                    │  claim       │                                       │
│                    └──────────────┘                                       │
│                                                                            │
│  ┌────────────────────────────────────────────────────────────────┐      │
│  │              AXIOS HTTP CLIENT (lib/api/client.ts)             │      │
│  │                                                                │      │
│  │  Base URL: process.env.NEXT_PUBLIC_API_URL                   │      │
│  │  Default: http://localhost:8090/api                          │      │
│  └────────────────────────────────────────────────────────────────┘      │
│                                                                            │
│  ┌────────────────────────────────────────────────────────────────┐      │
│  │           REQUEST INTERCEPTOR (Automatic)                     │      │
│  │                                                                │      │
│  │  For each request:                                            │      │
│  │  1. Get 'accessToken' from sessionStorage                    │      │
│  │  2. Add: Authorization: Bearer {token}                        │      │
│  │  3. Get 'tenantId' from sessionStorage                       │      │
│  │  4. Add: X-Tenant-Id: {tenantId}                             │      │
│  │  5. Content-Type: application/json (pre-configured)          │      │
│  └────────────────────────────────────────────────────────────────┘      │
│                                                                            │
│  ┌────────────────────────────────────────────────────────────────┐      │
│  │         API SERVICE LAYER (lib/api/services/*.ts)             │      │
│  │                                                                │      │
│  │  ✓ jetskisService.list()                                     │      │
│  │  ✓ locacoesService.checkOut()                                │      │
│  │  ✓ reservasService.confirmar()                               │      │
│  │  ✓ modelosService.create()                                   │      │
│  │  ✓ userTenantsService.getMyTenants()                         │      │
│  │                                                                │      │
│  │  All use getTenantId() for dynamic URL:                       │      │
│  │  /v1/tenants/{tenantId}/{resource}                           │      │
│  └────────────────────────────────────────────────────────────────┘      │
│                                                                            │
│  ┌────────────────────────────────────────────────────────────────┐      │
│  │        RESPONSE INTERCEPTOR (Error Handling)                  │      │
│  │                                                                │      │
│  │  • 401: Redirect to /login (token expired)                   │      │
│  │  • 403: Log "Access forbidden"                                │      │
│  │  • Other: Reject promise                                      │      │
│  └────────────────────────────────────────────────────────────────┘      │
│                                                                            │
└──────────────────────────────────────────────────────────────────────────┘
                                  ↓
                    HTTP NETWORK (localhost:8090)
                                  ↓
┌──────────────────────────────────────────────────────────────────────────┐
│                      BACKEND (Spring Boot)                               │
│                    jetski-api:8090/api                                    │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────┐        │
│  │              SECURITY FILTER CHAIN                           │        │
│  │                                                              │        │
│  │  1. OAuth2 Authentication Filter                            │        │
│  │     └→ Extract JWT from Authorization header                │        │
│  │     └→ Validate signature with Keycloak public key          │        │
│  │                                                              │        │
│  │  2. TenantFilter (OncePerRequestFilter)                    │        │
│  │     └→ Extract tenantId from X-Tenant-Id header             │        │
│  │     └→ Validate format (must be UUID)                       │        │
│  │     └→ Extract provider + providerUserId from JWT           │        │
│  │     └→ Validate access via TenantAccessValidator            │        │
│  │     └→ Store in TenantContext.setTenantId()                │        │
│  │     └→ Record metrics                                        │        │
│  │                                                              │        │
│  └──────────────────────────────────────────────────────────────┘        │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────┐        │
│  │              TENANT CONTEXT (Thread-Local)                   │        │
│  │                                                              │        │
│  │  static ThreadLocal<UUID> TENANT_ID                         │        │
│  │  static ThreadLocal<List<String>> USER_ROLES                │        │
│  │  static ThreadLocal<UUID> USUARIO_ID                        │        │
│  │                                                              │        │
│  │  Used by:                                                    │        │
│  │  • @PreAuthorize("hasAnyRole(...)")                        │        │
│  │  • PostgreSQL RLS queries                                   │        │
│  │  • Audit logging                                            │        │
│  │                                                              │        │
│  └──────────────────────────────────────────────────────────────┘        │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────┐        │
│  │         CONTROLLER LAYER (@RestController)                  │        │
│  │    with @PathVariable UUID tenantId                         │        │
│  │                                                              │        │
│  │  @RequestMapping("/v1/tenants/{tenantId}/jetskis")         │        │
│  │  @GetMapping                                                 │        │
│  │  public ResponseEntity<List<JetskiResponse>>                │        │
│  │    listJetskis(@PathVariable UUID tenantId, ...)            │        │
│  │    {                                                         │        │
│  │      validateTenantContext(tenantId);                        │        │
│  │      return jetskiService.listActiveJetskis();               │        │
│  │    }                                                         │        │
│  │                                                              │        │
│  └──────────────────────────────────────────────────────────────┘        │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────┐        │
│  │          SERVICE LAYER (Business Logic)                     │        │
│  │                                                              │        │
│  │  All queries automatically filtered by PostgreSQL RLS:      │        │
│  │                                                              │        │
│  │  SELECT * FROM jetski                                        │        │
│  │  WHERE tenant_id = current_setting('app.tenant_id')::uuid   │        │
│  │                                                              │        │
│  └──────────────────────────────────────────────────────────────┘        │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────┐        │
│  │              DATABASE (PostgreSQL 16)                        │        │
│  │        with Row-Level Security (RLS)                        │        │
│  │                                                              │        │
│  │  Tables: jetski, modelo, locacao, reserva, ...             │        │
│  │  All have: tenant_id UUID (for isolation)                   │        │
│  │  All have: RLS policy for automatic filtering               │        │
│  │                                                              │        │
│  │  Indexes: (tenant_id, field) for performance                │        │
│  │                                                              │        │
│  └──────────────────────────────────────────────────────────────┘        │
│                                                                            │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## Request/Response Flow Example

### Scenario: User fetches available jetskis after login

```
STEP 1: USER LOGS IN
═══════════════════════════════════════════════════════════════════════════
Frontend                              Backend
─────────────────────────────────────────────────────────────────────────
GET /api/auth/signin
  ↓
Redirect to Keycloak
  ↓
Keycloak PKCE flow
  ↓
Keycloak returns code
  ↓
NextAuth /api/auth/callback/keycloak
  ↓
Exchange code for token (server-side)
  ↓
Session callback extracts:
  • access_token → session.accessToken
  • refresh_token → session.refreshToken
  • tenant_id (if in claims) → session.tenantId
  ↓
Create JWT session
  ↓
Redirect to /dashboard


STEP 2: DASHBOARD LAYOUT INITIALIZES
═══════════════════════════════════════════════════════════════════════════
Frontend                              Backend
─────────────────────────────────────────────────────────────────────────
useEffect runs:
  ↓
session.accessToken available
  ↓
setAuthToken(session.accessToken)
  → sessionStorage.setItem('accessToken', token)
  ↓
userTenantsService.getMyTenants()
  ↓
Axios makes GET request:
  URL: http://localhost:8090/api/v1/user/tenants

  Interceptor adds headers:
  Authorization: Bearer eyJhbGciOiJSUzI1NiI...
  X-Tenant-Id: (not set, not required for this endpoint)
  Content-Type: application/json
  ↓                                    ↓
  Request sent ──────────────────→ TenantFilter receives
                                    (skips validation for /v1/user/tenants)
                                    ↓
                                    UserTenantsController
                                    extracts userId from JWT
                                    ↓
                                    SELECT tenant, membro
                                    FROM membro
                                    WHERE usuario_id = ?
                                    ↓
                                    Returns:
                                    {
                                      accessType: "LIMITED",
                                      totalTenants: 2,
                                      tenants: [
                                        {
                                          id: "550e8400-e29b-41d4-a716-446655440000",
                                          slug: "acme",
                                          razaoSocial: "ACME Corp",
                                          roles: ["ADMIN_TENANT"],
                                          status: "ATIVO"
                                        },
                                        {
                                          id: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
                                          slug: "oceanride",
                                          razaoSocial: "OceanRide Inc",
                                          roles: ["GERENTE"],
                                          status: "ATIVO"
                                        }
                                      ]
                                    }
                                    ↓
                                    Response sent ←───────────

Frontend receives response:
  ↓
setTenants(response.tenants)
  → Zustand state updated
  ↓
Auto-select first tenant:
  setCurrentTenant(tenants[0])
  ↓
setTenantId(tenants[0].id)
  → sessionStorage.setItem('tenantId', '550e8400-e29b-41d4-a716-446655440000')
  ↓
Now ready to fetch tenant-scoped data


STEP 3: USER VIEWS JETSKIS
═══════════════════════════════════════════════════════════════════════════
Frontend                              Backend
─────────────────────────────────────────────────────────────────────────
jetskisService.list()
  ↓
getTenantId()
  → Returns: '550e8400-e29b-41d4-a716-446655440000'
  ↓
getBasePath()
  → Returns: '/v1/tenants/550e8400-e29b-41d4-a716-446655440000/jetskis'
  ↓
Axios makes GET request:

  URL: http://localhost:8090/api/v1/tenants/550e8400-e29b-41d4-a716-446655440000/jetskis

  Request interceptor runs:
  ├─ token = sessionStorage.getItem('accessToken')
  │  → 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...'
  ├─ config.headers.Authorization = 'Bearer ' + token
  ├─ tenantId = sessionStorage.getItem('tenantId')
  │  → '550e8400-e29b-41d4-a716-446655440000'
  ├─ config.headers['X-Tenant-Id'] = tenantId
  └─ config.headers['Content-Type'] = 'application/json'

  ↓
  HTTP Request sent with headers:
  ┌─────────────────────────────────────────────────────────┐
  │ GET /api/v1/tenants/550e8400-.../jetskis HTTP/1.1      │
  │ Host: localhost:8090                                    │
  │ Authorization: Bearer eyJhbGciOiJSUzI1NiI...           │
  │ X-Tenant-Id: 550e8400-e29b-41d4-a716-446655440000     │
  │ Content-Type: application/json                          │
  └─────────────────────────────────────────────────────────┘
  ↓                                    ↓
  ───────────────────────────→ Spring Security Filter Chain
                               ├─ OAuth2AuthenticationFilter
                               │  ├─ Extract JWT from header
                               │  ├─ Validate with Keycloak public key
                               │  ├─ Create Authentication object
                               │  └─ Store in SecurityContext
                               │
                               ├─ TenantFilter
                               │  ├─ Not public endpoint, continue
                               │  ├─ Extract X-Tenant-Id header
                               │  │  → '550e8400-e29b-41d4-a716-446655440000'
                               │  ├─ Parse as UUID ✓
                               │  ├─ Get JWT from SecurityContext
                               │  ├─ Extract provider: 'keycloak'
                               │  ├─ Extract providerUserId: 'user-uuid'
                               │  ├─ Call tenantAccessValidator
                               │  │  ├─ Query identity_provider_mapping
                               │  │  ├─ Find usuario_id for (keycloak, user-uuid)
                               │  │  ├─ Query membro
                               │  │  ├─ Check if usuario is member of tenant
                               │  │  ├─ Return roles: ['ADMIN_TENANT']
                               │  │  └─ Return access: true
                               │  ├─ TenantContext.setTenantId(tenantId)
                               │  ├─ TenantContext.setUserRoles(['ADMIN_TENANT'])
                               │  └─ Continue to controller
                               │
                               └─ JetskiController.listJetskis()
                                  ├─ @PreAuthorize("hasAnyRole(...)")
                                  │  └─ Check: 'ADMIN_TENANT' in ['ADMIN_TENANT', 'GERENTE', 'OPERADOR'] ✓
                                  ├─ validateTenantContext(tenantId)
                                  │  └─ Assert TenantContext.getTenantId() == pathVariable
                                  ├─ Call jetskiService.listActiveJetskis()
                                  │  └─ Execute JPA query with RLS filter
                                  │     Query executed:
                                  │     SELECT j FROM Jetski j
                                  │     WHERE j.tenantId = ?1 AND j.ativo = true
                                  │     WITH RLS POLICY (auto-filter by tenant_id)
                                  │
                                  ├─ Convert to JetskiResponse DTO
                                  │  Results:
                                  │  [
                                  │    { id, modeloId, serie, ano, status, ... },
                                  │    { id, modeloId, serie, ano, status, ... }
                                  │  ]
                                  │
                                  └─ Return ResponseEntity.ok(response)

                               ↓
  Finally block:
  TenantContext.clear()
  (Remove ThreadLocal to prevent memory leak)

                               ↓
  HTTP Response sent:
  ┌──────────────────────────────────────────────────┐
  │ HTTP/1.1 200 OK                                  │
  │ Content-Type: application/json                   │
  │ Content-Length: 1234                             │
  │                                                  │
  │ [{                                               │
  │   "id": "...",                                   │
  │   "modeloId": "...",                             │
  │   "serie": "JC300-2024",                         │
  │   "status": "DISPONIVEL",                        │
  │   "horimetroAtual": 245                          │
  │ }, ...]                                          │
  └──────────────────────────────────────────────────┘
  ↓
Frontend receives response:
  ↓
Response interceptor checks status:
  ├─ 401? Redirect to login
  ├─ 403? Log error
  └─ 200? Return response ✓
  ↓
Service returns: Jetski[]
  ↓
Component renders list:
  jetskis.map(j => <JetskiRow key={j.id} jetski={j} />)
  ↓
User sees:
┌─────────────────────────────────┐
│ Jetskis                         │
├─────────────────────────────────┤
│ ✓ JC300-2024  DISPONIVEL       │
│ ✓ JC400-2023  LOCADO           │
│ ✓ JC500-2024  DISPONIVEL       │
└─────────────────────────────────┘
```

---

## Data Flow Diagram - State Management

```
┌─────────────────────────────────────────────────────────────────────┐
│                      NEXT.JS SESSION (JWT)                         │
│                                                                     │
│  NextAuth session object:                                          │
│  {                                                                  │
│    user: { email, name, ... },                                     │
│    accessToken: "eyJhbGc...",                                      │
│    refreshToken: "eyJhbGc...",                                     │
│    expiresAt: 1234567890,                                          │
│    idToken: "eyJhbGc..."                                           │
│  }                                                                  │
│                                                                     │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ↓
        ┌────────────────────────────────────┐
        │ setAuthToken(session.accessToken) │
        └────────────┬───────────────────────┘
                     ↓
    ┌──────────────────────────────────┐
    │  sessionStorage (Tab-scoped)      │
    │                                  │
    │  accessToken: "eyJhbGc..."       │
    │  tenantId: (initially empty)     │
    └────────────┬─────────────────────┘
                 ↓
        ┌────────────────────────────────────┐
        │ userTenantsService.getMyTenants()  │
        │  (calls GET /v1/user/tenants)     │
        └────────────┬───────────────────────┘
                     ↓
    ┌──────────────────────────────────────┐
    │    Zustand TenantStore (Memory)       │
    │                                      │
    │    tenants: [                        │
    │      { id: "uuid1", slug: "acme", ..}, │
    │      { id: "uuid2", slug: "ocean".. }  │
    │    ]                                 │
    │    currentTenant: null               │
    └────────────┬─────────────────────────┘
                 ↓
       ┌──────────────────────────────┐
       │ setCurrentTenant(tenant[0])  │
       └──────────┬───────────────────┘
                  ↓
    ┌────────────────────────────────────────────┐
    │  setTenantId(currentTenant.id)             │
    │  → sessionStorage.setItem('tenantId', ...) │
    └────────────┬───────────────────────────────┘
                 ↓
    ┌──────────────────────────────────┐
    │  sessionStorage (Updated)         │
    │                                  │
    │  accessToken: "eyJhbGc..."       │
    │  tenantId: "550e8400-..."        │
    └────────────┬─────────────────────┘
                 ↓
    ┌──────────────────────────────────┐
    │  localStorage (Zustand persist)   │
    │                                  │
    │  tenant-storage: {               │
    │    currentTenant: { id, ... },   │
    │    tenants: [...]                │
    │  }                               │
    └──────────────────────────────────┘
```

---

## Security Boundaries

```
┌────────────────────────────────────────────────────────────────────┐
│                     FRONTEND (Browser Sandbox)                     │
│                                                                    │
│  ┌─────────────────────────────────────────────────────────┐      │
│  │  Trust Boundary 1: XSS Prevention                       │      │
│  │  • Sanitize all user inputs                             │      │
│  │  • Use React's default XSS protection                   │      │
│  │  • Avoid dangerouslySetInnerHTML                        │      │
│  │                                                         │      │
│  │  sessionStorage (httpOnly NOT possible in browser)      │      │
│  │  • accessToken: Vulnerable to XSS                       │      │
│  │  • tenantId: Public identifier (acceptable)             │      │
│  └─────────────────────────────────────────────────────────┘      │
│                                                                    │
│  NextAuth Session (JWT in httpOnly cookie)                         │
│  • SAFER: httpOnly cookies prevent JavaScript access              │
│  • Handled by NextAuth library automatically                       │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
                           ↓ HTTPS
┌────────────────────────────────────────────────────────────────────┐
│                     NETWORK (SSL/TLS Encrypted)                    │
│                                                                    │
│  Trust Boundary 2: CORS & CSRF                                    │
│  • Browser enforces CORS (Origin header check)                    │
│  • Only requests from localhost:3002 allowed to 8090              │
│  • Axios automatically includes Origin header                      │
│                                                                    │
│  Headers sent:                                                     │
│  • Authorization: Bearer ... (JWT in Authorization header)         │
│  • X-Tenant-Id: ... (Tenant identifier)                           │
│  • Content-Type: application/json                                 │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
                           ↓
┌────────────────────────────────────────────────────────────────────┐
│                    BACKEND (Spring Security)                       │
│                                                                    │
│  ┌─────────────────────────────────────────────────────────┐      │
│  │  Trust Boundary 3: OAuth2/OIDC Validation              │      │
│  │  • Verify JWT signature with Keycloak public key       │      │
│  │  • Check token expiration                               │      │
│  │  • Validate issuer claim                                │      │
│  │                                                         │      │
│  │  Result: Authentication object with:                    │      │
│  │  • Principal (user identity)                            │      │
│  │  • Authorities (global roles)                           │      │
│  │  • JWT claims (including provider, providerUserId)      │      │
│  └─────────────────────────────────────────────────────────┘      │
│                                                                    │
│  ┌─────────────────────────────────────────────────────────┐      │
│  │  Trust Boundary 4: Tenant Isolation                     │      │
│  │  • Extract tenant ID from X-Tenant-Id header            │      │
│  │  • Validate format (UUID)                               │      │
│  │  • Query database for tenant access:                    │      │
│  │    - Check identity_provider_mapping                    │      │
│  │    - Resolve internal usuario_id                        │      │
│  │    - Check membro table for membership                  │      │
│  │    - Retrieve tenant-specific roles                     │      │
│  │  • Store in TenantContext (thread-local)                │      │
│  │  • Prevent cross-tenant data access                      │      │
│  └─────────────────────────────────────────────────────────┘      │
│                                                                    │
│  ┌─────────────────────────────────────────────────────────┐      │
│  │  Trust Boundary 5: Authorization (@PreAuthorize)        │      │
│  │  • Spring SpEL evaluates role expressions               │      │
│  │  • Tenant-scoped roles from TenantContext               │      │
│  │  • Examples:                                             │      │
│  │    - hasAnyRole('ADMIN_TENANT', 'GERENTE')             │      │
│  │    - hasAuthority('SCOPE_openid')                       │      │
│  └─────────────────────────────────────────────────────────┘      │
│                                                                    │
│  ┌─────────────────────────────────────────────────────────┐      │
│  │  Trust Boundary 6: Row-Level Security (Database)        │      │
│  │  • PostgreSQL RLS policies on all tables                │      │
│  │  • Automatic filtering by tenant_id                     │      │
│  │  • Query: WHERE tenant_id = app.tenant_id               │      │
│  │  • Even if code has bug, RLS prevents data leak         │      │
│  └─────────────────────────────────────────────────────────┘      │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

---

## Common Request Patterns

### 1. **List Resources (GET with optional filters)**
```typescript
// Frontend
jetskisService.list({ status: 'DISPONIVEL' })

// Generated Request
GET http://localhost:8090/api/v1/tenants/550e8400-.../jetskis?status=DISPONIVEL
Authorization: Bearer eyJhbGc...
X-Tenant-Id: 550e8400-...
Content-Type: application/json

// Backend Response
HTTP 200 OK
[
  { id: "...", serie: "...", status: "DISPONIVEL", ... },
  { id: "...", serie: "...", status: "DISPONIVEL", ... }
]
```

### 2. **Get Single Resource (GET/{id})**
```typescript
// Frontend
jetskisService.getById("123e4567-e89b-12d3-a456-426614174000")

// Generated Request
GET http://localhost:8090/api/v1/tenants/550e8400-.../jetskis/123e4567-e89b-12d3-a456-426614174000
Authorization: Bearer eyJhbGc...
X-Tenant-Id: 550e8400-...
Content-Type: application/json

// Backend Response
HTTP 200 OK
{ id: "123e4567...", serie: "JC300", status: "DISPONIVEL", ... }
```

### 3. **Create Resource (POST)**
```typescript
// Frontend
jetskisService.create({
  modeloId: "...",
  serie: "JC500-2024",
  ano: 2024,
  horimetroAtual: 0
})

// Generated Request
POST http://localhost:8090/api/v1/tenants/550e8400-.../jetskis
Authorization: Bearer eyJhbGc...
X-Tenant-Id: 550e8400-...
Content-Type: application/json

{
  "modeloId": "...",
  "serie": "JC500-2024",
  "ano": 2024,
  "horimetroAtual": 0
}

// Backend Response
HTTP 201 Created
{ id: "...", modeloId: "...", serie: "JC500-2024", status: "DISPONIVEL", ... }
```

### 4. **Update Resource (PUT/{id})**
```typescript
// Frontend
jetskisService.update("123e4567...", {
  ano: 2025,
  horimetroAtual: 250
})

// Generated Request
PUT http://localhost:8090/api/v1/tenants/550e8400-.../jetskis/123e4567...
Authorization: Bearer eyJhbGc...
X-Tenant-Id: 550e8400-...
Content-Type: application/json

{
  "ano": 2025,
  "horimetroAtual": 250
}

// Backend Response
HTTP 200 OK
{ id: "...", modeloId: "...", serie: "JC500-2024", ano: 2025, ... }
```

### 5. **Custom Action (POST/{id}/action)**
```typescript
// Frontend
locacoesService.checkOut("...", {
  horimetroFim: 150,
  checklistEntradaJson: "...",
  observacoes: "..."
})

// Generated Request
POST http://localhost:8090/api/v1/tenants/550e8400-.../locacoes/{id}/check-out
Authorization: Bearer eyJhbGc...
X-Tenant-Id: 550e8400-...
Content-Type: application/json

{
  "horimetroFim": 150,
  "checklistEntradaJson": "...",
  "observacoes": "..."
}

// Backend Response
HTTP 200 OK
{ id: "...", status: "FINALIZADA", valorTotal: 450.00, ... }
```

### 6. **Public Endpoint (No Tenant Required)**
```typescript
// Frontend
userTenantsService.getMyTenants()

// Generated Request
GET http://localhost:8090/api/v1/user/tenants
Authorization: Bearer eyJhbGc...
X-Tenant-Id: (not set - endpoint doesn't require it)
Content-Type: application/json

// Backend Response
HTTP 200 OK
{
  "accessType": "LIMITED",
  "totalTenants": 2,
  "tenants": [
    { id: "550e8400...", slug: "acme", razaoSocial: "ACME", ... },
    { id: "6ba7b810...", slug: "ocean", razaoSocial: "OceanRide", ... }
  ]
}
```

