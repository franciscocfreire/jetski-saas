# Backoffice Frontend API Integration - Executive Summary

## Quick Facts

| Aspect | Details |
|--------|---------|
| **Frontend Framework** | Next.js 14 (React, TypeScript) |
| **HTTP Client** | Axios with interceptors |
| **Authentication** | NextAuth + Keycloak (PKCE flow) |
| **Backend URL** | `http://localhost:8090/api` |
| **Tenant Isolation** | `X-Tenant-Id` header + PostgreSQL RLS |
| **Token Storage** | sessionStorage (not httpOnly in browser) |
| **Session Management** | NextAuth JWT strategy |

---

## API Request Flow (Simple Version)

```
1. User logs in → Keycloak OAuth2/PKCE
   ↓
2. NextAuth creates session with accessToken
   ↓
3. Frontend stores token in sessionStorage
   ↓
4. Frontend fetches /v1/user/tenants endpoint
   ↓
5. User selects tenant → stored in sessionStorage
   ↓
6. ALL API requests automatically include:
   • Authorization: Bearer {token}
   • X-Tenant-Id: {selectedTenant}
   ↓
7. Backend validates both headers
   ↓
8. PostgreSQL RLS filters data by tenant_id
```

---

## Key Files

### Frontend Configuration
| File | Purpose |
|------|---------|
| `lib/api/client.ts` | Axios client setup + interceptors |
| `lib/auth.ts` | NextAuth + Keycloak config |
| `lib/store/tenant-store.ts` | Zustand tenant state |
| `lib/api/services/*.ts` | API service methods |
| `.env.local` | Environment variables |

### Backend Configuration
| File | Purpose |
|------|---------|
| `src/main/java/com/jetski/shared/internal/TenantFilter.java` | Tenant extraction & validation |
| `src/main/java/com/jetski/shared/security/TenantContext.java` | Thread-local tenant storage |
| `src/main/java/com/jetski/locacoes/api/JetskiController.java` | Example endpoint |

---

## How Headers Are Added

**Automatically by Axios Interceptor:**

```typescript
// Every request gets these headers automatically
Authorization: Bearer {token from sessionStorage}
X-Tenant-Id: {tenantId from sessionStorage}
Content-Type: application/json
```

**No manual header adding needed** - the interceptor in `lib/api/client.ts` handles it.

---

## Example Request Lifecycle

**Frontend Code:**
```typescript
const jetskis = await jetskisService.list()
```

**What actually happens:**
```
1. getTenantId() → "550e8400-e29b-41d4-a716-446655440000"
2. getBasePath() → "/v1/tenants/550e8400-e29b-41d4-a716-446655440000/jetskis"
3. apiClient.get("/v1/tenants/550e8400-e29b-41d4-a716-446655440000/jetskis")
4. Interceptor runs:
   - Adds Authorization header from sessionStorage
   - Adds X-Tenant-Id header from sessionStorage
   - Adds Content-Type header
5. HTTP GET sent to:
   http://localhost:8090/api/v1/tenants/550e8400-e29b-41d4-a716-446655440000/jetskis
6. Backend:
   - Validates JWT signature
   - Checks tenant context matches
   - Verifies user is member of tenant
   - Returns data filtered by tenant_id
7. Frontend receives Jetski[]
```

---

## Environment Configuration

**Current Local Setup (.env.local):**
```
KEYCLOAK_ISSUER=http://localhost:8081/realms/jetski-saas
NEXT_PUBLIC_API_URL=http://localhost:8090/api
NEXTAUTH_URL=http://localhost:3002
```

**What each does:**
- `KEYCLOAK_ISSUER` - Where to get OAuth tokens from
- `NEXT_PUBLIC_API_URL` - Base URL for all API calls
- `NEXTAUTH_URL` - Frontend URL for auth callbacks

---

## Multi-Tenant Architecture

### How Tenant Isolation Works

1. **Frontend identifies tenant:**
   - User sees list of available tenants
   - User selects one
   - Tenant ID stored in sessionStorage

2. **Every API request includes tenant:**
   - Via `X-Tenant-Id` header
   - Via URL path `/v1/tenants/{tenantId}/`

3. **Backend validates tenant access:**
   ```
   TenantFilter checks:
   ├─ Header X-Tenant-Id present ✓
   ├─ Valid UUID format ✓
   ├─ User is member of tenant ✓
   ├─ User has required role (ADMIN_TENANT, GERENTE, etc.) ✓
   └─ Store in TenantContext for request processing
   ```

4. **Database enforces isolation:**
   - PostgreSQL RLS policies
   - Every query filters by tenant_id
   - Even if code has bug, database prevents cross-tenant data leaks

---

## Common API Endpoints

All endpoints follow the pattern: `/v1/tenants/{tenantId}/{resource}`

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/v1/user/tenants` | GET | List user's tenants (public) |
| `/v1/tenants/{id}/jetskis` | GET | List jetskis |
| `/v1/tenants/{id}/jetskis` | POST | Create jetski |
| `/v1/tenants/{id}/jetskis/{id}` | PUT | Update jetski |
| `/v1/tenants/{id}/reservas` | GET | List reservations |
| `/v1/tenants/{id}/locacoes/{id}/check-out` | POST | Checkout rental |
| `/v1/tenants/{id}/modelos` | GET | List jetski models |

---

## Comparison: Mobile vs Backoffice

| Aspect | Mobile App | Backoffice |
|--------|-----------|-----------|
| **HTTP Client** | Ktor | Axios |
| **Header Management** | Manual per request | Axios interceptor |
| **Tenant Selection** | User selects in app | Dashboard auto-selects |
| **Token Storage** | Keychain/Keystore | sessionStorage |
| **API URLs** | `/api/v1/tenants/...` | `/v1/tenants/...` |
| **Headers Sent** | Explicitly added | Interceptor adds |

**Key Difference:** Backoffice uses interceptors (DRY), mobile adds headers per-request (explicit).

Both successfully call the same backend endpoints.

---

## What Could Go Wrong

### Common Issues

| Issue | Check | Fix |
|-------|-------|-----|
| **401 Unauthorized** | Is token in sessionStorage? | User needs to sign in again |
| **403 Forbidden** | Is X-Tenant-Id sent? Is user member? | Add user to tenant in DB |
| **404 Not Found** | Does resource exist? Is URL correct? | Verify resource ID or check if deleted |
| **CORS Error** | Does backend allow origin? | Update CORS config in backend |
| **No data returned** | Correct tenant selected? | Verify currentTenant in Zustand |

### How to Debug

1. Open browser DevTools → Network tab
2. Make API request
3. Click on request to see:
   - **Headers tab:** Verify Authorization and X-Tenant-Id
   - **Response tab:** Check error message
4. Open Console tab and check:
   - `sessionStorage.getItem('accessToken')`
   - `sessionStorage.getItem('tenantId')`

---

## Security Highlights

✅ **What's Secure:**
- JWT signature validation (backend checks with Keycloak public key)
- HTTPS in production (TLS encryption)
- PKCE flow for mobile/SPA (prevents auth code interception)
- PostgreSQL RLS (database-level isolation)
- Role-based access control (Spring @PreAuthorize)
- Thread-local tenant context (prevents cross-thread leaks)

⚠️ **What to Monitor:**
- sessionStorage is not httpOnly (XSS vulnerable)
- Token refresh logic (ensure tokens auto-refresh)
- CORS headers (don't allow `*` origins in production)
- Audit logs (track who accessed what)

---

## Documentation Files Created

1. **BACKOFFICE-API-REQUEST-ANALYSIS.md**
   - Detailed analysis of client configuration
   - Complete code samples
   - All services and interceptors documented
   - Environment variables explained

2. **BACKOFFICE-REQUEST-ARCHITECTURE.md**
   - High-level architecture diagrams
   - Request/response flow examples
   - Security boundaries explained
   - Common request patterns

3. **BACKOFFICE-API-TROUBLESHOOTING.md**
   - Solutions for common issues
   - Debugging steps for each problem
   - Browser DevTools tips
   - Backend log patterns
   - Best practices checklist

---

## For Backend Developers

**What you need to know:**

1. **Every request includes X-Tenant-Id header**
   - Extract it in TenantFilter
   - Validate user is member of tenant
   - Store in TenantContext

2. **Use @PathVariable UUID tenantId**
   ```java
   @RequestMapping("/v1/tenants/{tenantId}/jetskis")
   public ResponseEntity<List<JetskiResponse>> list(
     @PathVariable UUID tenantId,
     ...
   ) {
     validateTenantContext(tenantId);
     // Now safe to query database
   }
   ```

3. **All queries filtered by tenant_id**
   - Via PostgreSQL RLS (recommended)
   - Or via WHERE clause in JPA
   - Never trust client to filter correctly

4. **Role-based access control**
   - Use `@PreAuthorize("hasAnyRole(...)")`
   - Roles come from membro.papeis in database
   - TenantContext.getUserRoles() returns tenant-scoped roles

---

## For Frontend Developers

**What you need to know:**

1. **Tenant must be selected before making requests**
   ```typescript
   useEffect(() => {
     if (!currentTenant) return  // Wait for tenant
     // Safe to make requests now
   }, [currentTenant])
   ```

2. **All service methods use getTenantId() automatically**
   - Don't manually construct URLs
   - Use service methods like `jetskisService.list()`

3. **Headers are added automatically**
   - Don't manually add Authorization header
   - Axios interceptor handles it
   - Just use apiClient.get/post/put/delete

4. **Handle errors properly**
   ```typescript
   try {
     await jetskisService.list()
   } catch (error) {
     // 401: user redirected to login automatically
     // 403: show "Access denied" message
     // 404: show "Resource not found" message
   }
   ```

---

## Next Steps

1. **Review the detailed documentation:**
   - Read BACKOFFICE-API-REQUEST-ANALYSIS.md for complete details
   - Study BACKOFFICE-REQUEST-ARCHITECTURE.md for flow diagrams
   - Keep BACKOFFICE-API-TROUBLESHOOTING.md handy for debugging

2. **Test the integration:**
   - Open browser DevTools
   - Make an API request
   - Verify headers in Network tab
   - Check response in Response tab

3. **Add error handling to all screens:**
   - Show 401 message (handle manually or use interceptor)
   - Show 403 message ("Access Denied")
   - Show 404 message ("Not Found")
   - Show generic errors

4. **Monitor in production:**
   - Track 401/403/404 rates
   - Log tenant context for debugging
   - Set up alerts for unusual patterns

---

## Quick Reference

### Make an API request:
```typescript
import { jetskisService } from '@/lib/api/services'

// In a component
useEffect(() => {
  if (!currentTenant) return

  jetskisService.list()
    .then(setJetskis)
    .catch(setError)
}, [currentTenant])
```

### Access current tenant:
```typescript
import { useTenantStore } from '@/lib/store/tenant-store'

const { currentTenant } = useTenantStore()
console.log(currentTenant.id)
```

### Check if user has role:
```typescript
// Roles are stored in TenantContext on backend
// Frontend can't directly check roles
// Just make the request, backend will return 403 if denied
```

### Debug headers in browser:
```javascript
// Console tab:
sessionStorage.getItem('accessToken')  // Should return token
sessionStorage.getItem('tenantId')     // Should return UUID

// Network tab:
// Click any API request
// Headers tab → Request Headers
// Should see:
// Authorization: Bearer ...
// X-Tenant-Id: ...
```

---

## Support

- **Backend Issues:** Check TenantFilter logs, verify X-Tenant-Id header
- **Frontend Issues:** Check Network tab, verify sessionStorage contents
- **Database Issues:** Check RLS policies, verify tenant_id column exists
- **Auth Issues:** Check Keycloak logs, verify PKCE configuration

