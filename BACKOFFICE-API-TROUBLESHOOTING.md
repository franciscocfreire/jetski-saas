# Backoffice API Integration - Troubleshooting Guide

## Common Issues & Solutions

### Issue 1: 401 Unauthorized - Invalid Token

**Symptom:**
```
Error: 401 Unauthorized
response.status = 401
```

**Causes:**
1. Token expired
2. Token not being sent
3. Token signature invalid
4. Keycloak public key outdated

**Debugging Steps:**

1. **Check if token is in sessionStorage:**
```typescript
// In browser console
sessionStorage.getItem('accessToken')
// Should return: "eyJhbGciOiJSUzI1NiI..." (not null/undefined)
```

2. **Check if Authorization header is being sent:**
```typescript
// In Network tab
Request Headers:
Authorization: Bearer eyJhbGciOiJSUzI1NiI...
```

3. **Verify token expiration:**
```typescript
// Decode JWT (use jwt.io or:)
const parts = token.split('.')
const payload = JSON.parse(atob(parts[1]))
console.log('Token expires at:', new Date(payload.exp * 1000))
console.log('Current time:', new Date())
```

4. **Check NextAuth session:**
```typescript
// In browser console
const session = await fetch('/api/auth/session').then(r => r.json())
console.log('Session:', session)
// Should show: { accessToken: "...", expiresAt: ..., ... }
```

**Solutions:**

- **Token expired:** Session expires, user needs to refresh
  ```typescript
  // NextAuth auto-refreshes with refreshToken
  // If not working, user must sign out and sign in again
  ```

- **Token not sent:** Check axios interceptor
  ```typescript
  // File: lib/api/client.ts
  const token = sessionStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  ```

- **CORS issue preventing token transmission:**
  ```typescript
  // Add to backend SecurityConfig:
  http.cors(cors -> cors
    .allowedOrigins("http://localhost:3002")
    .allowCredentials(true)
    .allowedHeaders("*")
    .allowedMethods("*")
  )
  ```

---

### Issue 2: 403 Forbidden - Access Denied

**Symptom:**
```
Error: Access denied
response.status = 403
response.data.message = "No access to tenant: 550e8400-..."
```

**Causes:**
1. User is not a member of the selected tenant
2. Identity provider mapping not found
3. Tenant-scoped role missing
4. X-Tenant-Id header not sent

**Debugging Steps:**

1. **Check if X-Tenant-Id header is sent:**
```typescript
// In Network tab, check Request Headers
X-Tenant-Id: 550e8400-e29b-41d4-a716-446655440000
```

2. **Verify tenant is stored in sessionStorage:**
```typescript
// In browser console
sessionStorage.getItem('tenantId')
// Should return: "550e8400-e29b-41d4-a716-446655440000"
```

3. **Check available tenants:**
```typescript
// In browser console
const session = await fetch('/api/auth/session').then(r => r.json())
const response = await fetch('http://localhost:8090/api/v1/user/tenants', {
  headers: {
    'Authorization': `Bearer ${session.accessToken}`
  }
})
const tenants = await response.json()
console.log('Available tenants:', tenants)
```

4. **Check identity provider mapping in database:**
```sql
-- Backend database query
SELECT * FROM identity_provider_mapping
WHERE provider = 'keycloak'
AND provider_user_id = '{user_sub_from_jwt}'
```

5. **Check user membership in tenant:**
```sql
-- Backend database query
SELECT * FROM membro
WHERE usuario_id = '{usuario_id}'
AND tenant_id = '550e8400-e29b-41d4-a716-446655440000'
```

**Solutions:**

- **User not member of tenant:** Add user to tenant
  ```sql
  INSERT INTO membro (tenant_id, usuario_id, papeis, ativo, created_at)
  VALUES (
    '550e8400-...',
    '{usuario_id}',
    '["GERENTE"]',
    true,
    now()
  )
  ```

- **Identity provider mapping missing:**
  ```sql
  INSERT INTO identity_provider_mapping (usuario_id, provider, provider_user_id)
  VALUES (
    '{usuario_id}',
    'keycloak',
    '{sub_claim_from_jwt}'
  )
  ```

- **X-Tenant-Id header not sent:** Check tenant initialization
  ```typescript
  // File: app/(dashboard)/layout.tsx
  useEffect(() => {
    if (currentTenant) {
      setTenantId(currentTenant.id)  // Must be called
    }
  }, [currentTenant])
  ```

---

### Issue 3: 422 Unprocessable Entity - Validation Error

**Symptom:**
```
Error: 422 Unprocessable Entity
response.data = {
  "status": 422,
  "message": "Validation failed",
  "errors": {
    "serie": ["Serie must not be blank"],
    "ano": ["Ano must be valid"]
  }
}
```

**Causes:**
1. Required fields missing in request body
2. Field format invalid
3. Field size exceeds limit
4. Business rule violation

**Debugging Steps:**

1. **Check request body in Network tab:**
   - Look at Request Payload
   - Verify all required fields present
   - Verify field formats match expected types

2. **Check type definitions:**
```typescript
// File: lib/api/types.ts
export interface JetskiCreateRequest {
  modeloId: string      // required
  serie: string         // required
  ano?: number          // optional
  horimetroAtual?: number  // optional
}
```

3. **Validate before sending:**
```typescript
const isValid = validateRequest({
  modeloId: '...',  // UUID format
  serie: 'JC500',   // non-empty string
  ano: 2024         // valid year
})
```

**Solutions:**

- **Add missing field:**
  ```typescript
  const request: JetskiCreateRequest = {
    modeloId: '550e8400-...',  // Add UUID
    serie: 'JC500-2024',        // Add required field
    ano: 2024                   // Add optional field
  }
  jetskisService.create(request)
  ```

- **Fix field format:**
  ```typescript
  // Wrong:
  { ano: "2024" }  // string

  // Correct:
  { ano: 2024 }    // number
  ```

- **Check backend validation:**
```java
// File: JetskiCreateRequest.java
@NotBlank(message = "Serie must not be blank")
private String serie;

@Min(value = 2000, message = "Year must be 2000 or later")
private Integer ano;
```

---

### Issue 4: 404 Not Found

**Symptom:**
```
Error: 404 Not Found
response.status = 404
```

**Causes:**
1. Resource ID doesn't exist
2. Tenant ID doesn't exist
3. URL path typo
4. Resource deleted

**Debugging Steps:**

1. **Check if resource exists:**
```typescript
// Verify with list call first
const jetskis = await jetskisService.list()
const exists = jetskis.some(j => j.id === resourceId)
console.log('Resource exists:', exists)
```

2. **Check URL construction:**
```typescript
// In service file
const getBasePath = () => `/v1/tenants/${getTenantId()}/jetskis`
console.log('Base path:', getBasePath())  // Should be: /v1/tenants/550e8400-.../jetskis
```

3. **Check tenant exists:**
```typescript
const tenants = await userTenantsService.getMyTenants()
const currentTenant = tenants.tenants.find(t => t.id === getTenantId())
console.log('Tenant exists:', !!currentTenant)
```

4. **Check backend logs:**
```bash
docker logs jetski-api | grep "404\|Not Found\|path"
```

**Solutions:**

- **Resource deleted:** Handle gracefully
  ```typescript
  try {
    const jetski = await jetskisService.getById(id)
  } catch (error) {
    if (error.response?.status === 404) {
      router.push('/jetskis')  // Redirect to list
    }
  }
  ```

- **Tenant doesn't exist:** Validate tenant selection
  ```typescript
  if (!currentTenant) {
    console.error('No tenant selected')
    // Force tenant selection
    router.push('/tenants')
  }
  ```

---

### Issue 5: Network Request Fails Before Reaching Backend

**Symptom:**
```
Error: Network Error
Failed to fetch (no response from backend)
```

**Causes:**
1. Backend server not running
2. Port 8090 not accessible
3. Firewall blocking connection
4. Backend crashed

**Debugging Steps:**

1. **Check if backend is running:**
```bash
# Test backend health endpoint
curl http://localhost:8090/api/actuator/health
# Should return: {"status":"UP"}
```

2. **Check network connectivity:**
```bash
# From your computer
ping localhost
# Or
curl -v http://localhost:8090/api/actuator/health
```

3. **Check port is open:**
```bash
# List open ports
netstat -tuln | grep 8090
# Or on Windows:
netstat -ano | findstr :8090
```

4. **Check API_BASE_URL configuration:**
```typescript
// In lib/api/client.ts
console.log('API_BASE_URL:', API_BASE_URL)
// Should be: http://localhost:8090/api
```

5. **Check browser Network tab:**
   - Look for CORS errors
   - Check Request URL is correct
   - Check Response tab for error details

**Solutions:**

- **Start backend:**
```bash
# In backend directory
./mvnw spring-boot:run
```

- **Check CORS configuration:**
```java
// In SecurityConfig.java
http.cors(cors -> cors
  .allowedOrigins("http://localhost:3002")
  .allowedMethods("*")
  .allowedHeaders("*")
  .allowCredentials(true)
)
```

- **Update environment variable:**
```bash
# In .env.local
NEXT_PUBLIC_API_URL=http://localhost:8090/api
```

---

### Issue 6: CORS Error

**Symptom:**
```
Error: CORS policy: No 'Access-Control-Allow-Origin' header
XMLHttpRequest cannot load 'http://localhost:8090/api/v1/tenants/.../jetskis'
```

**Causes:**
1. Backend CORS policy too restrictive
2. Request origin not in allowed origins list
3. Request headers not allowed by CORS policy
4. Credentials mode mismatch

**Debugging Steps:**

1. **Check Origin header in request:**
   - Open Network tab
   - Look for Origin header in Request Headers
   - Should be: `http://localhost:3002`

2. **Check CORS response headers:**
   - Look at Response Headers
   - Should include: `Access-Control-Allow-Origin: http://localhost:3002`
   - Should include: `Access-Control-Allow-Headers: Content-Type, Authorization, X-Tenant-Id`

3. **Check preflight request (OPTIONS):**
   - Some requests trigger preflight
   - Check if OPTIONS request returns 200 OK
   - Should include proper Access-Control headers

**Solutions:**

- **Update backend CORS configuration:**
```java
// SecurityConfig.java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
  http.cors(cors -> cors.configurationSource(request -> {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:3002"));
    config.setAllowedMethods(List.of("*"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    return config;
  }))
  // ... rest of config
}
```

- **Allow X-Tenant-Id header:**
```java
config.setAllowedHeaders(List.of(
  "Content-Type",
  "Authorization",
  "X-Tenant-Id",
  "Accept"
))
```

---

### Issue 7: Token Not in Correct Format

**Symptom:**
```
Error: 401 Unauthorized
Backend log: Invalid JWT token
```

**Debugging Steps:**

1. **Inspect token in sessionStorage:**
```typescript
const token = sessionStorage.getItem('accessToken')
console.log('Token:', token)
console.log('Starts with "eyJ":', token?.startsWith('eyJ'))
console.log('Has 3 parts:', token?.split('.').length === 3)
```

2. **Check JWT structure:**
```typescript
// Should be: header.payload.signature
// Each part should be base64url encoded
const parts = token.split('.')
console.log('Header:', JSON.parse(atob(parts[0])))
console.log('Payload:', JSON.parse(atob(parts[1])))
```

3. **Verify token is from Keycloak:**
```typescript
const payload = JSON.parse(atob(token.split('.')[1]))
console.log('Issuer:', payload.iss)  // Should be: http://localhost:8081/realms/jetski-saas
console.log('Client ID:', payload.aud)  // Should be: jetski-backoffice
```

**Solutions:**

- **Ensure token is complete:**
  ```typescript
  // Wrong: "eyJ..." (truncated)
  // Correct: Full token with all 3 parts
  const token = sessionStorage.getItem('accessToken')
  if (!token || token.split('.').length !== 3) {
    // Re-authenticate
    window.location.href = '/api/auth/signin'
  }
  ```

---

### Issue 8: Tenant ID Mismatch

**Symptom:**
```
Error: 400 Bad Request
Backend log: Tenant context mismatch - expected 550e8400... but got 6ba7b810...
```

**Causes:**
1. URL path tenant_id doesn't match X-Tenant-Id header
2. Tenant ID changed after request started
3. Multiple requests with different tenants simultaneously

**Debugging Steps:**

1. **Check URL path tenant ID:**
```typescript
const getBasePath = () => `/v1/tenants/${getTenantId()}/jetskis`
const path = getBasePath()
console.log('Tenant in URL:', path.split('/')[3])  // Extract tenant ID
```

2. **Check header tenant ID:**
```typescript
const tenantId = sessionStorage.getItem('tenantId')
console.log('Tenant in header:', tenantId)
```

3. **Verify they match:**
```typescript
const urlTenant = getBasePath().split('/')[3]
const headerTenant = sessionStorage.getItem('tenantId')
console.assert(urlTenant === headerTenant, 'Tenant mismatch!')
```

**Solutions:**

- **Ensure tenant ID is set before making request:**
  ```typescript
  useEffect(() => {
    if (!currentTenant) {
      return  // Don't make requests until tenant is selected
    }

    jetskisService.list()  // Now safe to call
  }, [currentTenant])
  ```

- **Use consistent tenant ID:**
  ```typescript
  // DON'T do this:
  const tenantId1 = sessionStorage.getItem('tenantId')
  // ... later
  const tenantId2 = sessionStorage.getItem('tenantId')  // Might be different!

  // DO this:
  const tenantId = getTenantId()  // Use helper function
  ```

---

## Monitoring & Debugging

### Browser DevTools

1. **Network Tab:**
   - See all API requests
   - Check headers (Authorization, X-Tenant-Id)
   - Check response status and body
   - Check CORS headers

2. **Console Tab:**
   - Axios error logs
   - Check sessionStorage contents
   - Verify token structure

3. **Storage Tab:**
   - sessionStorage contents
   - localStorage contents (tenant-storage)
   - Cookies (NextAuth session)

### Backend Logs

```bash
# Watch backend logs
docker logs -f jetski-api

# Or if running locally:
./mvnw spring-boot:run 2>&1 | grep -E "TenantFilter|Authorization|401|403"
```

### Test with cURL

```bash
# 1. Get user tenants (no tenant required)
curl -v http://localhost:8090/api/v1/user/tenants \
  -H "Authorization: Bearer {token}"

# 2. Get jetskis for specific tenant
curl -v http://localhost:8090/api/v1/tenants/{tenantId}/jetskis \
  -H "Authorization: Bearer {token}" \
  -H "X-Tenant-Id: {tenantId}"

# 3. Create jetski
curl -X POST http://localhost:8090/api/v1/tenants/{tenantId}/jetskis \
  -H "Authorization: Bearer {token}" \
  -H "X-Tenant-Id: {tenantId}" \
  -H "Content-Type: application/json" \
  -d '{
    "modeloId": "...",
    "serie": "JC500",
    "ano": 2024,
    "horimetroAtual": 0
  }'
```

### Common Log Patterns

**Successful request:**
```
DEBUG TenantFilter - Tenant context set successfully: tenantId=550e8400..., path=/v1/tenants/550e8400.../jetskis, method=GET
DEBUG TenantFilter - Access validated: provider=keycloak, providerUserId=..., tenant=550e8400..., roles=[ADMIN_TENANT], unrestricted=false
INFO JetskiController - GET /v1/tenants/550e8400.../jetskis?includeInactive=false
```

**Missing tenant header:**
```
ERROR TenantFilter - Invalid tenant ID format: null
```

**Access denied:**
```
ERROR TenantFilter - Access denied: provider=keycloak, providerUserId=..., tenant=550e8400..., reason=User is not a member of tenant
```

**Token invalid:**
```
ERROR JwtAuthenticationConverter - Invalid JWT token
```

---

## Best Practices

### 1. Always Check currentTenant Before Making Requests

```typescript
export function useJetskis() {
  const { currentTenant } = useTenantStore()
  const [jetskis, setJetskis] = useState<Jetski[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!currentTenant) {
      return  // Don't make request until tenant is selected
    }

    jetskisService.list()
      .then(setJetskis)
      .catch(err => setError(err.message))
  }, [currentTenant])

  return { jetskis, error }
}
```

### 2. Handle 401 Errors Properly

```typescript
// The interceptor already handles this, but in UI:
try {
  await jetskisService.list()
} catch (error) {
  if (error.response?.status === 401) {
    // Token expired, user will be redirected to /login automatically
    console.log('Session expired')
  }
}
```

### 3. Show Error Messages to Users

```typescript
const [error, setError] = useState<string | null>(null)

useEffect(() => {
  jetskisService.list()
    .catch(err => {
      const message = err.response?.data?.message || err.message
      setError(message)
    })
}, [])

if (error) {
  return <div className="error">{error}</div>
}
```

### 4. Log Request/Response for Debugging

```typescript
// In lib/api/client.ts
apiClient.interceptors.request.use(config => {
  console.log('Request:', {
    url: config.url,
    method: config.method,
    headers: {
      Authorization: config.headers.Authorization?.substring(0, 20) + '...',
      'X-Tenant-Id': config.headers['X-Tenant-Id']
    }
  })
  return config
})

apiClient.interceptors.response.use(
  response => {
    console.log('Response:', {
      status: response.status,
      url: response.config.url
    })
    return response
  },
  error => {
    console.error('Error:', {
      status: error.response?.status,
      url: error.response?.config?.url,
      message: error.response?.data?.message
    })
    return Promise.reject(error)
  }
)
```

---

## Quick Checklist for New Endpoint

When adding a new API endpoint:

- [ ] Service method created in `lib/api/services/{module}.ts`
- [ ] Uses `getTenantId()` for URL construction
- [ ] Uses axios client (interceptors will add headers automatically)
- [ ] Types defined in `lib/api/types.ts`
- [ ] Error handling in UI component
- [ ] Backend controller validates tenant context
- [ ] Backend uses @PreAuthorize for roles
- [ ] Backend endpoint documented with OpenAPI annotations
- [ ] Tested with both valid and invalid tenant IDs
- [ ] Tested with 401, 403, and 404 responses

