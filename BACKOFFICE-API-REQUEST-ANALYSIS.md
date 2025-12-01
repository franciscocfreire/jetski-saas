# Backoffice Frontend API Request Analysis

## Overview

The jetski-backoffice (Next.js frontend) makes API requests to the backend using **Axios** with a sophisticated interceptor-based system for handling authentication and multi-tenant context.

---

## 1. API Client Configuration

**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/client.ts`

### Base URL Setup
```typescript
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8090/api'

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})
```

- **Environment Variable:** `NEXT_PUBLIC_API_URL` (from `.env.local`)
- **Default Value:** `http://localhost:8090/api`
- **Current Local Config:** `http://localhost:8090/api`

---

## 2. Request Interceptor - Authentication & Tenant Headers

**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/client.ts` (lines 13-36)

```typescript
apiClient.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    // Get authorization token from sessionStorage
    const token = typeof window !== 'undefined'
      ? sessionStorage.getItem('accessToken')
      : null

    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // Get tenant ID from sessionStorage
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

### Headers Added to Every Request:
1. **`Authorization: Bearer {token}`** - JWT access token from Keycloak
2. **`X-Tenant-Id: {tenantId}`** - Tenant UUID for multi-tenant isolation
3. **`Content-Type: application/json`** - Set by axios client config

---

## 3. Response Interceptor - Error Handling

**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/client.ts` (lines 38-56)

```typescript
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    if (error.response?.status === 401) {
      // Token expired or invalid - redirect to login
      if (typeof window !== 'undefined') {
        window.location.href = '/login'
      }
    }

    if (error.response?.status === 403) {
      // Forbidden - user doesn't have permission
      console.error('Access forbidden:', error.response.data)
    }

    return Promise.reject(error)
  }
)
```

### Error Handling:
- **401 Unauthorized:** Redirects to `/login` (token expired/invalid)
- **403 Forbidden:** Logs error (user lacks permissions)
- **Other errors:** Rejects promise for caller to handle

---

## 4. Token & Tenant ID Storage

**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/client.ts` (lines 58-86)

### Helper Functions:
```typescript
export function setAuthToken(token: string | null) {
  if (typeof window !== 'undefined') {
    if (token) {
      sessionStorage.setItem('accessToken', token)
    } else {
      sessionStorage.removeItem('accessToken')
    }
  }
}

export function setTenantId(tenantId: string | null) {
  if (typeof window !== 'undefined') {
    if (tenantId) {
      sessionStorage.setItem('tenantId', tenantId)
    } else {
      sessionStorage.removeItem('tenantId')
    }
  }
}

export function getTenantId(): string | null {
  if (typeof window !== 'undefined') {
    return sessionStorage.getItem('tenantId')
  }
  return null
}
```

### Storage Details:
- **Storage Type:** Browser `sessionStorage` (cleared on tab close)
- **Keys:**
  - `accessToken` - JWT from Keycloak
  - `tenantId` - Current tenant UUID

---

## 5. Authentication Flow with NextAuth + Keycloak

**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/auth.ts`

```typescript
import NextAuth from "next-auth"
import Keycloak from "next-auth/providers/keycloak"

declare module "next-auth" {
  interface Session {
    accessToken: string
    refreshToken: string
    idToken?: string
    tenantId?: string
    error?: string
  }
}

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [
    Keycloak({
      clientId: process.env.KEYCLOAK_CLIENT_ID!,
      clientSecret: process.env.KEYCLOAK_CLIENT_SECRET || "",
      issuer: process.env.KEYCLOAK_ISSUER,
      allowDangerousEmailAccountLinking: false,
      authorization: {
        params: {
          scope: "openid profile email",
        },
      },
    }),
  ],
  callbacks: {
    async jwt({ token, account, profile }) {
      if (account) {
        token.accessToken = account.access_token
        token.refreshToken = account.refresh_token
        token.expiresAt = account.expires_at
        token.idToken = account.id_token
      }

      // Extract tenant_id from token if available
      if (profile && typeof profile === 'object' && 'tenant_id' in profile) {
        token.tenantId = profile.tenant_id as string
      }

      return token
    },
    async session({ session, token }) {
      return {
        ...session,
        accessToken: token.accessToken as string,
        refreshToken: token.refreshToken as string,
        idToken: token.idToken as string | undefined,
        tenantId: token.tenantId as string | undefined,
        error: token.error as string | undefined,
      }
    },
  },
  pages: {
    signIn: "/login",
    error: "/login",
    signOut: "/login",
  },
  session: {
    strategy: "jwt",
  },
})
```

### Keycloak Configuration:
- **Client ID:** `jetski-backoffice`
- **Client Secret:** (empty for PKCE public client)
- **Issuer:** `http://localhost:8081/realms/jetski-saas`
- **PKCE:** Enabled (for public clients/SPAs)
- **Scopes:** `openid profile email`
- **Session Strategy:** JWT (server-side)

---

## 6. Dashboard Layout - Token & Tenant Initialization

**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/app/(dashboard)/layout.tsx`

### Token Setup Flow:
```typescript
useEffect(() => {
  if (session?.accessToken && !tenantsLoaded) {
    console.log('🔐 Setting auth token...')
    setAuthToken(session.accessToken)  // <-- Stores in sessionStorage

    // Load user tenants
    console.log('📡 Fetching user tenants...')
    userTenantsService.getMyTenants()
      .then((response) => {
        console.log('✅ Tenants response:', response)
        setTenants(response.tenants || [])
        setTenantsLoaded(true)

        // Auto-select first tenant if none selected
        if (!currentTenant && response.tenants && response.tenants.length > 0) {
          console.log('🏢 Auto-selecting first tenant:', response.tenants[0])
          setCurrentTenant(response.tenants[0])
        }
      })
      .catch((error) => {
        console.error('❌ Error fetching tenants:', error)
      })
  }
}, [session?.accessToken, tenantsLoaded, currentTenant, setTenants, setCurrentTenant])

// Set tenant ID in sessionStorage when tenant changes
useEffect(() => {
  if (currentTenant) {
    console.log('🏢 Setting tenant ID:', currentTenant.id)
    setTenantId(currentTenant.id)  // <-- Stores in sessionStorage
  }
}, [currentTenant])
```

### Initialization Steps:
1. **Extract token from NextAuth session**
2. **Store in sessionStorage** via `setAuthToken()`
3. **Fetch user's available tenants** (no tenant needed for `/v1/user/tenants`)
4. **Auto-select first tenant** if available
5. **Store tenant ID in sessionStorage** via `setTenantId()`

---

## 7. Tenant Store - Persistent Tenant Selection

**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/store/tenant-store.ts`

```typescript
import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { TenantSummary } from '../api/types'
import { setTenantId } from '../api/client'

interface TenantState {
  currentTenant: TenantSummary | null
  tenants: TenantSummary[]
  setCurrentTenant: (tenant: TenantSummary | null) => void
  setTenants: (tenants: TenantSummary[]) => void
  clearTenant: () => void
}

export const useTenantStore = create<TenantState>()(
  persist(
    (set) => ({
      currentTenant: null,
      tenants: [],
      setCurrentTenant: (tenant) => {
        setTenantId(tenant?.id ?? null)  // <-- Also updates sessionStorage
        set({ currentTenant: tenant })
      },
      setTenants: (tenants) => set({ tenants }),
      clearTenant: () => {
        setTenantId(null)
        set({ currentTenant: null, tenants: [] })
      },
    }),
    {
      name: 'tenant-storage',
      partialize: (state) => ({
        currentTenant: state.currentTenant,
        tenants: state.tenants,
      }),
    }
  )
)
```

### Storage Layers:
1. **Zustand state** - In-memory state management
2. **sessionStorage** - Persisted for axios interceptor access
3. **localStorage** - Persisted across browser sessions (via Zustand persist middleware)

---

## 8. API Service Pattern - URL Construction

All API services follow the same pattern: **dynamically constructing tenant-scoped URLs**

### Example: Jetskis Service
**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/services/jetskis.ts`

```typescript
import { apiClient, getTenantId } from '../client'
import type { Jetski, JetskiCreateRequest, JetskiUpdateRequest, JetskiStatus } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/jetskis`

export const jetskisService = {
  async list(params?: { status?: JetskiStatus; includeInactive?: boolean }): Promise<Jetski[]> {
    const { data } = await apiClient.get<Jetski[]>(getBasePath(), { params })
    return data
  },

  async getById(id: string): Promise<Jetski> {
    const { data } = await apiClient.get<Jetski>(`${getBasePath()}/${id}`)
    return data
  },

  async create(request: JetskiCreateRequest): Promise<Jetski> {
    const { data } = await apiClient.post<Jetski>(getBasePath(), request)
    return data
  },

  async update(id: string, request: JetskiUpdateRequest): Promise<Jetski> {
    const { data } = await apiClient.put<Jetski>(`${getBasePath()}/${id}`, request)
    return data
  },

  async reactivate(id: string): Promise<Jetski> {
    const { data } = await apiClient.post<Jetski>(`${getBasePath()}/${id}/reactivate`)
    return data
  },
}
```

### URL Construction:
```
Base URL: http://localhost:8090/api
Path: /v1/tenants/{tenantId}/jetskis
Full URL: http://localhost:8090/api/v1/tenants/{tenantId}/jetskis
```

### All Services Follow Same Pattern:
- **Locacoes:** `/v1/tenants/{tenantId}/locacoes`
- **Modelos:** `/v1/tenants/{tenantId}/modelos`
- **Reservas:** `/v1/tenants/{tenantId}/reservas`
- **Manutencoes:** `/v1/tenants/{tenantId}/manutencoes`
- **Clientes:** `/v1/tenants/{tenantId}/clientes`
- **Vendedores:** `/v1/tenants/{tenantId}/vendedores`

### Exceptions (No tenant in URL):
- **User Tenants:** `GET /v1/user/tenants` (to fetch available tenants)
- **Public endpoints:** `/v1/auth/*`, `/v3/api-docs`, etc.

---

## 9. Environment Configuration

**File:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/.env.local`

```bash
# Keycloak Configuration
KEYCLOAK_CLIENT_ID=jetski-backoffice
KEYCLOAK_CLIENT_SECRET=
KEYCLOAK_ISSUER=http://localhost:8081/realms/jetski-saas

# NextAuth Configuration
NEXTAUTH_URL=http://localhost:3002
NEXTAUTH_SECRET=g5jryuCyY/xKuutyKGwZ/FAqkSkbjPjbSYvefwkN0lY=
AUTH_TRUST_HOST=true

# API Configuration
NEXT_PUBLIC_API_URL=http://localhost:8090/api
```

### Environment Variables Explained:
- **`NEXTAUTH_URL`** - Frontend URL for NextAuth callback
- **`NEXTAUTH_SECRET`** - Used to encrypt JWT session token
- **`NEXT_PUBLIC_API_URL`** - Public variable exposed to frontend for API calls
- **`KEYCLOAK_ISSUER`** - OAuth2/OIDC token endpoint

---

## 10. Request Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Frontend (Next.js - jetski-backoffice)                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  1. User logs in                                                         │
│     ↓                                                                    │
│  2. NextAuth + Keycloak PKCE flow                                       │
│     ↓                                                                    │
│  3. Session created with accessToken                                    │
│     ↓                                                                    │
│  4. setAuthToken(session.accessToken)                                   │
│     → sessionStorage.setItem('accessToken', token)                      │
│     ↓                                                                    │
│  5. Fetch user tenants: GET /v1/user/tenants                            │
│     Headers:                                                             │
│     - Authorization: Bearer {token}                                      │
│     - X-Tenant-Id: (empty, not required for this endpoint)              │
│     ↓                                                                    │
│  6. User selects tenant                                                 │
│     ↓                                                                    │
│  7. setTenantId(tenantId)                                               │
│     → sessionStorage.setItem('tenantId', tenantId)                      │
│     ↓                                                                    │
│  8. API request example: GET /v1/tenants/{tenantId}/jetskis             │
│     Axios interceptor adds:                                              │
│     - Authorization: Bearer {token}                                      │
│     - X-Tenant-Id: {tenantId}                                           │
│     - Content-Type: application/json                                    │
│     ↓                                                                    │
│  9. Backend validates headers and returns data                          │
│                                                                           │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 11. Backend Tenant Validation

**File:** `/home/franciscocfreire/repos/jetski/backend/src/main/java/com/jetski/shared/internal/TenantFilter.java`

### Tenant Extraction Priority:
```java
private String extractTenantId(HttpServletRequest request) {
  // Priority 1: X-Tenant-Id header
  String tenantId = request.getHeader(TENANT_HEADER_NAME);
  if (tenantId != null && !tenantId.isBlank()) {
    log.debug("Tenant ID extracted from header: {}", tenantId);
    return tenantId.trim();
  }

  // Priority 2: Subdomain (e.g., acme.jetski.com → acme)
  String host = request.getServerName();
  if (host != null && host.contains(".")) {
    String subdomain = host.split("\\.")[0];
    // If subdomain is not "www" or "api", use it as tenant slug
    if (!subdomain.equalsIgnoreCase("www") &&
        !subdomain.equalsIgnoreCase("api") &&
        !subdomain.equalsIgnoreCase("localhost")) {
      log.debug("Tenant slug extracted from subdomain: {}", subdomain);
    }
  }

  throw InvalidTenantException.missingTenantId();
}
```

### Backend Endpoint Example:
**File:** `/home/franciscocfreire/repos/jetski/backend/src/main/java/com/jetski/locacoes/api/JetskiController.java`

```java
@RestController
@RequestMapping("/v1/tenants/{tenantId}/jetskis")
@Tag(name = "Jetskis", description = "Gerenciamento da frota de jetskis")
@RequiredArgsConstructor
@Slf4j
public class JetskiController {

    private final JetskiService jetskiService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Listar jetskis da frota")
    public ResponseEntity<List<JetskiResponse>> listJetskis(
        @Parameter(description = "UUID do tenant")
        @PathVariable UUID tenantId,
        @RequestParam(required = false) JetskiStatus status,
        @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        // Validate tenant context matches path parameter
        validateTenantContext(tenantId);

        List<Jetski> jetskis;
        if (status != null) {
            jetskis = jetskiService.listByStatus(status);
        } else if (includeInactive) {
            jetskis = jetskiService.listAllJetskis();
        } else {
            jetskis = jetskiService.listActiveJetskis();
        }

        List<JetskiResponse> response = jetskis.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}
```

### Backend Filter Logic:
1. **Extract tenant ID** from `X-Tenant-Id` header (priority 1)
2. **Validate format** - must be valid UUID
3. **Extract JWT** from Authorization header
4. **Validate access** via database using identity provider mapping
5. **Store in TenantContext** (thread-local) for request processing
6. **All queries automatically filtered** by PostgreSQL RLS

---

## 12. Comparison: Mobile vs Backoffice

### How Mobile Makes Requests (for reference):
Mobile app uses Ktor HTTP client with similar pattern:
```kotlin
headers {
    append("Authorization", "Bearer $token")
    append("X-Tenant-Id", tenantId)
    append("Content-Type", "application/json")
}
// Request: GET /api/v1/tenants/{tenantId}/jetskis
```

### How Backoffice Makes Requests:
```typescript
// axios interceptor adds:
config.headers.Authorization = `Bearer ${token}`
config.headers['X-Tenant-Id'] = tenantId
// Request: GET http://localhost:8090/api/v1/tenants/{tenantId}/jetskis
```

### Key Difference:
- **Mobile:** Explicitly builds headers in each request
- **Backoffice:** Uses axios interceptor to automatically add headers to all requests

Both approaches achieve the same result but backoffice is more DRY (Don't Repeat Yourself).

---

## 13. Summary: Request Construction

### Complete Example Request

**API Call:** `jetskisService.list({ status: 'DISPONIVEL' })`

```typescript
// 1. Service gets tenant ID
const getBasePath = () => `/v1/tenants/${getTenantId()}/jetskis`
// Result: /v1/tenants/550e8400-e29b-41d4-a716-446655440000/jetskis

// 2. Axios makes GET request
apiClient.get(getBasePath(), { params: { status: 'DISPONIVEL' } })

// 3. Interceptor adds headers
// Request URL: http://localhost:8090/api/v1/tenants/550e8400-e29b-41d4-a716-446655440000/jetskis?status=DISPONIVEL
// Headers:
//   Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cC...
//   X-Tenant-Id: 550e8400-e29b-41d4-a716-446655440000
//   Content-Type: application/json

// 4. Backend receives request
// TenantFilter extracts X-Tenant-Id header
// Validates user has access to this tenant
// Sets TenantContext.setTenantId(UUID)
// Spring @PreAuthorize checks role
// JetskiController.listJetskis() executes with RLS filtering
// PostgreSQL automatically filters by tenant_id

// 5. Response returned
// [{ id: '...', serie: '...', status: 'DISPONIVEL', ... }]
```

---

## 14. Key Findings & Recommendations

### Current Implementation Strengths:
1. ✅ **Proper header construction** - `X-Tenant-Id` sent on every tenant-scoped request
2. ✅ **Axios interceptor pattern** - DRY, maintainable, centralized
3. ✅ **Keycloak integration** - PKCE flow for security
4. ✅ **Token management** - Proper sessionStorage usage
5. ✅ **Backend validation** - Double-checks tenant context on server
6. ✅ **Role-based access control** - @PreAuthorize annotations

### Potential Issues to Verify:
1. ⚠️ **Token refresh** - Verify NextAuth refresh token flow works with backend
2. ⚠️ **CORS** - Ensure backend allows `X-Tenant-Id` header in CORS policy
3. ⚠️ **Error messages** - Current 403 handling just logs to console
4. ⚠️ **Public endpoint access** - `/v1/user/tenants` doesn't require tenant header (correct by design)

### Files to Monitor for Issues:
- `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/client.ts` - Central point for API config
- `/home/franciscocfreire/repos/jetski/backend/src/main/java/com/jetski/shared/internal/TenantFilter.java` - Tenant validation
- `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/app/(dashboard)/layout.tsx` - Auth initialization

---

## Configuration Reference

### Backend API Endpoints Pattern
```
GET    /v1/tenants/{tenantId}/{resource}
POST   /v1/tenants/{tenantId}/{resource}
PUT    /v1/tenants/{tenantId}/{resource}/{id}
DELETE /v1/tenants/{tenantId}/{resource}/{id}

Public endpoints (no X-Tenant-Id required):
GET    /v1/user/tenants
POST   /v1/auth/login
GET    /v3/api-docs
```

### Required Headers for All Tenant-Scoped Requests
```
Authorization: Bearer {accessToken}
X-Tenant-Id: {tenantId}
Content-Type: application/json
```

### Environment Variables
| Variable | Default | Used By |
|----------|---------|---------|
| `NEXT_PUBLIC_API_URL` | `http://localhost:8090/api` | axios baseURL |
| `KEYCLOAK_ISSUER` | `http://localhost:8081/realms/jetski-saas` | NextAuth token endpoint |
| `KEYCLOAK_CLIENT_ID` | `jetski-backoffice` | NextAuth client config |
| `NEXTAUTH_URL` | `http://localhost:3002` | NextAuth callback URL |
| `NEXTAUTH_SECRET` | - | JWT encryption key |

