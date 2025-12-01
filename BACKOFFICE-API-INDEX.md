# Backoffice API Integration - Complete Documentation Index

## Overview

This directory contains comprehensive documentation of how the jetski-backoffice (Next.js frontend) integrates with the backend API.

### Quick Navigation

1. **Want to understand the big picture?**
   - Start with: `BACKOFFICE-API-SUMMARY.md` (Executive Summary)

2. **Want to see architecture and flow?**
   - Read: `BACKOFFICE-REQUEST-ARCHITECTURE.md` (Diagrams & Flows)

3. **Need to debug or troubleshoot?**
   - Reference: `BACKOFFICE-API-TROUBLESHOOTING.md` (Solutions & Debugging)

4. **Need complete technical details?**
   - Study: `BACKOFFICE-API-REQUEST-ANALYSIS.md` (Complete Analysis)

5. **Looking for specific file locations?**
   - Check: `BACKOFFICE-API-FILE-REFERENCE.md` (File Map)

---

## Documentation Files

### 1. BACKOFFICE-API-SUMMARY.md
**Executive Summary - START HERE**
- Quick facts table
- Simple request flow explanation
- Key files overview
- Common issues quick reference
- For managers, architects, new developers

**Length:** ~2,000 words | **Read time:** 5-10 minutes

**Key Sections:**
- Quick Facts
- API Request Flow (Simple Version)
- Key Files
- How Headers Are Added
- Multi-Tenant Architecture
- For Backend Developers
- For Frontend Developers
- Quick Reference

---

### 2. BACKOFFICE-REQUEST-ARCHITECTURE.md
**Architecture & Flow Diagrams**
- High-level system architecture diagram
- Detailed request/response flow with timing
- State management flow
- Security boundaries diagram
- Common API request patterns
- For architects, DevOps, security reviewers

**Length:** ~4,000 words | **Read time:** 15-20 minutes

**Key Sections:**
- High-Level Architecture Diagram
- Request/Response Flow Example (detailed walkthrough)
- Data Flow Diagram - State Management
- Security Boundaries
- Common Request Patterns (with HTTP examples)

---

### 3. BACKOFFICE-API-REQUEST-ANALYSIS.md
**Complete Technical Analysis**
- API client configuration code
- Axios interceptor implementation
- NextAuth & Keycloak setup
- All API services documented
- Environment variables explained
- Backend tenant validation
- For developers implementing or modifying integration

**Length:** ~6,000 words | **Read time:** 20-30 minutes

**Key Sections:**
1. API Client Configuration
2. Request Interceptor - Authentication & Tenant Headers
3. Response Interceptor - Error Handling
4. Token & Tenant ID Storage
5. Authentication Flow with NextAuth + Keycloak
6. Dashboard Layout - Token & Tenant Initialization
7. Tenant Store - Persistent Tenant Selection
8. API Service Pattern - URL Construction
9. Environment Configuration
10. Request Flow Diagram
11. Backend Tenant Validation
12. Comparison: Mobile vs Backoffice
13. Summary: Request Construction
14. Key Findings & Recommendations

---

### 4. BACKOFFICE-API-TROUBLESHOOTING.md
**Debugging Guide & Solutions**
- Common issues with solutions
- Step-by-step debugging for each problem
- Browser DevTools tips
- Backend log patterns
- Test with cURL commands
- Best practices checklist
- For support engineers, QA, developers debugging issues

**Length:** ~5,000 words | **Read time:** 15-25 minutes

**Key Sections:**
- Issue 1: 401 Unauthorized - Invalid Token
- Issue 2: 403 Forbidden - Access Denied
- Issue 3: 422 Unprocessable Entity - Validation Error
- Issue 4: 404 Not Found
- Issue 5: Network Request Fails Before Reaching Backend
- Issue 6: CORS Error
- Issue 7: Token Not in Correct Format
- Issue 8: Tenant ID Mismatch
- Monitoring & Debugging (DevTools, logs, cURL)
- Best Practices
- Quick Checklist for New Endpoint

---

### 5. BACKOFFICE-API-FILE-REFERENCE.md
**File Locations & Code References**
- Absolute paths to all relevant files
- Purpose of each file
- Code snippets from key locations
- Configuration constants
- For developers who need to locate and modify code

**Length:** ~3,000 words | **Read time:** 10-15 minutes

**Key Sections:**
- Frontend Files (complete list with URLs)
- Backend Files (complete list with URLs)
- Database Configuration
- Key Code Snippets (with line numbers)
- Configuration Constants
- Testing References

---

## At a Glance

### How API Requests Work

```
Frontend Component
  ↓
Service Method (jetskisService.list())
  ↓
URL Construction (includes getTenantId())
  ↓
Axios Request
  ↓
Interceptor Adds Headers:
  • Authorization: Bearer {token}
  • X-Tenant-Id: {tenantId}
  ↓
HTTP Request to Backend
  ↓
Backend TenantFilter:
  • Validates X-Tenant-Id header
  • Checks user is member of tenant
  • Stores in TenantContext
  ↓
Controller:
  • @PreAuthorize checks role
  • validateTenantContext() ensures match
  ↓
Service/Repository:
  • Query includes tenant_id filter
  • PostgreSQL RLS adds additional filter
  ↓
Response Sent to Frontend
  ↓
Component Receives Data
```

### Files You'll Touch Most

| File | Why | How Often |
|------|-----|-----------|
| `lib/api/client.ts` | HTTP client setup | Rarely - once at project start |
| `lib/api/services/*.ts` | Add new endpoints | Often - adding features |
| `lib/auth.ts` | Auth config | Rarely - Keycloak changes |
| `app/(dashboard)/layout.tsx` | Auth initialization | Rarely - core logic |
| Backend Controllers | API implementation | Often - adding features |
| `TenantFilter.java` | Tenant validation | Rarely - core logic |

---

## Use Cases

### Use Case 1: "I need to call a new API endpoint"
1. Read: `BACKOFFICE-API-SUMMARY.md` (sections: How Headers Are Added, Quick Reference)
2. Check: `BACKOFFICE-API-FILE-REFERENCE.md` (find service file example)
3. Copy pattern from existing service in `lib/api/services/`
4. Test using Network tab in DevTools

### Use Case 2: "I'm getting 403 Forbidden error"
1. Read: `BACKOFFICE-API-TROUBLESHOOTING.md` (Issue 2: 403 Forbidden)
2. Check: Network tab for X-Tenant-Id header
3. Verify: User is member of tenant in database
4. Check: Backend logs with grep "403\|Access"

### Use Case 3: "I'm implementing the backend endpoint"
1. Read: `BACKOFFICE-API-SUMMARY.md` (For Backend Developers section)
2. Study: `BACKOFFICE-API-REQUEST-ANALYSIS.md` (Backend Tenant Validation)
3. Reference: `BACKOFFICE-API-FILE-REFERENCE.md` (JetskiController example)
4. Remember: TenantContext must be cleared in finally block

### Use Case 4: "Understanding how multi-tenant isolation works"
1. Read: `BACKOFFICE-API-SUMMARY.md` (Multi-Tenant Architecture)
2. Study: `BACKOFFICE-REQUEST-ARCHITECTURE.md` (Security Boundaries diagram)
3. Deep dive: `BACKOFFICE-API-REQUEST-ANALYSIS.md` (sections 11-12)

### Use Case 5: "Debugging token/auth issues"
1. Read: `BACKOFFICE-API-TROUBLESHOOTING.md` (Issue 1: 401 Unauthorized)
2. Check: Browser console for sessionStorage contents
3. Inspect: Network tab Authorization header
4. Decode: JWT using jwt.io or console

### Use Case 6: "Adding CORS support for new origin"
1. Read: `BACKOFFICE-API-TROUBLESHOOTING.md` (Issue 6: CORS Error)
2. Reference: `BACKOFFICE-API-FILE-REFERENCE.md` (SecurityConfig file)
3. Update: Backend CORS configuration

---

## Key Concepts Explained

### Authentication (JWT Tokens)
- **What:** JSON Web Tokens from Keycloak
- **Storage:** sessionStorage (not httpOnly)
- **Sent in:** Authorization header as `Bearer {token}`
- **Where to learn:** BACKOFFICE-API-REQUEST-ANALYSIS.md section 5

### Multi-Tenant Isolation
- **What:** X-Tenant-Id header + PostgreSQL RLS
- **How:** Every request includes tenant, backend validates and filters
- **Why:** Prevents accidental data leaks between companies
- **Where to learn:** BACKOFFICE-REQUEST-ARCHITECTURE.md Security section

### Axios Interceptors
- **What:** Functions that run on every request/response
- **Why:** DRY principle - add headers once, apply to all requests
- **How:** Stored in lib/api/client.ts
- **Where to learn:** BACKOFFICE-API-REQUEST-ANALYSIS.md sections 2-3

### TenantContext (Backend)
- **What:** Thread-local storage for tenant ID during request
- **Why:** Make tenant available throughout request without passing as parameter
- **Important:** MUST be cleared in finally block
- **Where to learn:** BACKOFFICE-API-FILE-REFERENCE.md or source code

---

## Common Questions

**Q: Why do I need to include X-Tenant-Id header?**
A: It tells the backend which tenant you're accessing data for. This enables the system to serve multiple companies from one API.

**Q: What if I forget to set the tenant ID?**
A: The service will try to construct a URL with `undefined`, creating invalid requests. Always ensure `currentTenant` is set before making requests.

**Q: Can I call the API without authentication?**
A: Only public endpoints like `/v1/user/tenants` don't require authentication. All tenant-scoped endpoints require Authorization header.

**Q: What happens if user is not member of selected tenant?**
A: Backend returns 403 Forbidden. User must select a different tenant they have access to.

**Q: How are token refresh handled?**
A: NextAuth with Keycloak handles this automatically using refresh tokens. If access token expires, NextAuth refreshes it before sending request.

**Q: Can users access data from multiple tenants simultaneously?**
A: Users can switch between tenants, but each request is for one tenant at a time.

---

## Related Projects

- **Backend:** `/home/franciscocfreire/repos/jetski/backend/` (Spring Boot)
- **Frontend:** `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/` (Next.js)
- **Mobile:** `/mnt/c/repos/jetski-mobile/` (KMM - Kotlin Multiplatform)
- **Infrastructure:** `/home/franciscocfreire/repos/jetski/infra/` (Keycloak, Docker Compose)

---

## Document Maintenance

These documents were created on 2025-12-01 based on analysis of:
- Frontend source code (Next.js)
- Backend source code (Spring Boot)
- Configuration files (.env, application.yml)
- API service implementations

### To Keep Documentation Updated

When making changes to:
- API endpoints → Update BACKOFFICE-API-FILE-REFERENCE.md
- Authentication flow → Update BACKOFFICE-API-REQUEST-ANALYSIS.md section 5
- Header handling → Update BACKOFFICE-API-REQUEST-ANALYSIS.md section 2
- Error responses → Update BACKOFFICE-API-TROUBLESHOOTING.md
- New services → Update BACKOFFICE-API-FILE-REFERENCE.md

---

## Quick Links

### Frontend Source Files
- API Client: `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/client.ts`
- Auth: `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/auth.ts`
- Services: `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/lib/api/services/`

### Backend Source Files
- TenantFilter: `/home/franciscocfreire/repos/jetski/backend/src/main/java/com/jetski/shared/internal/TenantFilter.java`
- TenantContext: `/home/franciscocfreire/repos/jetski/backend/src/main/java/com/jetski/shared/security/TenantContext.java`
- Controllers: `/home/franciscocfreire/repos/jetski/backend/src/main/java/com/jetski/*/api/`

### Configuration Files
- Frontend: `/home/franciscocfreire/repos/jetski/frontend/jetski-backoffice/.env.local`
- Backend: `/home/franciscocfreire/repos/jetski/backend/src/main/resources/application-local.yml`

---

## Feedback & Issues

If documentation is unclear:
1. Check the referenced source files in the repository
2. Trace through the code step by step
3. Use Browser DevTools to inspect actual requests
4. Check backend logs for what server is receiving

If something is wrong:
1. Verify against actual source code (docs may become outdated)
2. Check recent git commits for changes
3. Update the relevant documentation file
4. Commit changes with message like: "docs: update backoffice API docs for new feature X"

---

## Summary

This documentation provides complete, multi-level explanation of backoffice-to-backend API integration:

- **High Level:** BACKOFFICE-API-SUMMARY.md
- **Visual:** BACKOFFICE-REQUEST-ARCHITECTURE.md
- **Detailed:** BACKOFFICE-API-REQUEST-ANALYSIS.md
- **Practical:** BACKOFFICE-API-TROUBLESHOOTING.md
- **Reference:** BACKOFFICE-API-FILE-REFERENCE.md

Pick the level of detail you need and dive in!

