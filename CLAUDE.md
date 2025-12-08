# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Multi-tenant SaaS B2B jetski rental management system** for fleet control, scheduling, daily operations, maintenance, fuel management, commissions, and daily/monthly closures, including photo capture (check-in/check-out/incidents).

## Current Status

This repository is in the **specification phase**. The main specification document is `inicial.md` (in Portuguese).

## Multi-tenant Architecture

**SaaS B2B model** serving multiple companies (tenants) with logical data isolation:

- **Isolation model**: `tenant_id` column + PostgreSQL RLS (Row Level Security) - recommended for MVP
- **Identity**: Keycloak 26 (OSS) with single realm + `tenant_id` claim in JWT tokens
- **Routing**: Subdomain `https://{tenant}.system.com` or path prefix `/t/{tenant}`
- **Storage**: Single S3 bucket with tenant prefix: `tenant_id/locacao/{id}/foto_{n}.jpg`
- **Billing**: Plans (Basic/Pro/Enterprise) with usage metering (fleet size, users, storage, transaction fees)

## Domain Model (Core Entities)

**All operational entities include `tenant_id` for multi-tenant isolation:**

- **Tenant**: company/client (slug, legal name, CNPJ, timezone, currency, branding, status)
- **Plano** (Plan): subscription tiers with limits (fleet, users, storage)
- **Assinatura** (Subscription): tenant subscription with billing cycle and payment config
- **Usuario** (User): user account (email, name, active status)
- **Membro** (Member): user-tenant relationship with roles array (ADMIN_TENANT, GERENTE, OPERADOR, etc.)
- **Modelo** (Model): jetski models with pricing per hour/package (tenant-scoped)
- **Jetski**: individual units with odometer, status (tenant-scoped)
- **Vendedor** (Seller): sales partners with commission rules (tenant-scoped)
- **Cliente** (Customer): rental customers (tenant-scoped)
- **Reserva** (Reservation): booking with predicted start/end (tenant-scoped)
- **Locacao** (Rental): actual rental operation with check-in/check-out, odometer readings, photos (tenant-scoped)
- **Foto** (Photo): check-in/out images stored in cloud with metadata (tenant-scoped)
- **Abastecimento** (Refueling): fuel logs per rental or jetski (tenant-scoped)
- **OS_Manutencao** (Maintenance Order): preventive/corrective maintenance tracking (tenant-scoped)
- **FechamentoDiario/Mensal** (Daily/Monthly Closure): financial consolidation (tenant-scoped)
- **Auditoria** (Audit): who did what, when, IP/device (tenant-scoped)

## Key Business Rules

### Billing Calculations
- **Billable time** = round((used_minutes - tolerance_minutes), base=15, min=0)
- **Tolerance** (RN01): configurable grace period (e.g., 5 min) before charging
- **Rounding**: to nearest 15-minute block
- **Value** = (billable_time/60) × base_price_per_hour + extra_hour_fee + fees - discounts

### Fuel Policies (RN03)
Three modes configurable per model/jetski/global:
1. **Incluso** (Included): no charge to customer, cost tracked for operations
2. **Medido** (Measured): charge = liters_consumed × day_fuel_price
3. **Taxa fixa** (Fixed rate): fixed_fee_per_hour × billable_hours

Fuel is **non-commissionable** by default.

### Commission Calculation (RN04, RF08)
- **Hierarchy** (first match wins): 1) Active campaign → 2) Model-specific → 3) Duration range → 4) Seller default
- **Commissionable revenue** = rental_value - non_commissionable_items (fuel, cleaning fees, damages, fines)
- **Types**: percentage (%), fixed value, or tiered (e.g., 10% up to 120min, 12% above)
- Commission calculated on **monthly closure**, adjustable by manager approval

### Status & Availability (RN06)
- Jetski in "maintenance" status cannot be reserved
- Maintenance order (OS) automatically blocks scheduling until closed

## Database Schema

PostgreSQL 16 with RLS (Row Level Security) enabled:

**Multi-tenant tables:**
- `tenant` (id UUID, slug, razao_social, cnpj, timezone, moeda, status, branding_json)
- `plano` (id, nome, limites_json, preco_mensal)
- `assinatura` (id, tenant_id, plano_id, ciclo, dt_inicio, dt_fim, status)
- `usuario` (id UUID, email, nome, ativo)
- `membro` (id, tenant_id, usuario_id, papeis[])

**Operational tables (all include `tenant_id`):**
- `modelo`, `jetski`, `vendedor`, `cliente`, `reserva`, `locacao`, `foto`, `abastecimento`, `os_manutencao`
- `commission_policy`: hierarchical commission rules (campaign/model/duration/seller)
- `fuel_policy`: fuel pricing modes (global/per_model/per_jetski)
- `fuel_price_day`: daily average fuel prices
- `fechamento_diario`, `fechamento_mensal`: financial closures with locking
- `auditoria`: audit trail

**RLS example:**
```sql
ALTER TABLE locacao ENABLE ROW LEVEL SECURITY;
CREATE POLICY locacao_tenant_isolation ON locacao
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
```

Each request must set: `SELECT set_config('app.tenant_id', '<uuid>', true);`

**Indexes:** Composite indexes on `(tenant_id, fk)` for performance.

See `inicial.md` lines 158-229 for complete SQL schema with RLS.

## MVP Scope

1. Register jetski models and pricing (time/hourly ranges)
2. Simple scheduling and reservations
3. Check-in/check-out with odometer readings and mandatory photos
4. Auto-calculate hours used and amount due (with tolerance and overtime rules)
5. Fuel log (liters, cost, pre/post rental)
6. Basic preventive/corrective maintenance and unavailability tracking
7. Commission per rental (fixed % per seller/partner or per model)
8. Daily and monthly closures with exportable reports
9. Roles and permissions (Operator, Seller, Manager, Mechanic, Finance, Admin)
10. Cloud image storage linked to events

## Technology Stack

### Backend (API SaaS)
- **Language**: Java 21
- **Framework**: Spring Boot 3.3+ (Web, Validation, Security, Data JPA, Actuator)
- **Database**: PostgreSQL 16 with RLS for multi-tenant isolation
- **Migrations**: Flyway
- **Cache/Queue**: Redis (TTL for upload sessions, rate-limit per tenant)
- **Auth**: Keycloak 26 (OSS) - OIDC, PKCE, RBAC per tenant
- **Image Storage**: Amazon S3 with presigned URLs, SSE-S3/KMS encryption
- **Messaging**: SQS (MVP) → Kafka (future for events/streams)
- **Build**: Maven, MapStruct (mapping), Testcontainers (integration tests)
- **Observability**: OpenTelemetry + Prometheus/Grafana + ELK/CloudWatch Logs
- **API Docs**: Springdoc OpenAPI

### Multi-tenant Request Flow
1. Subdomain `{tenant}` → Gateway → injects `X-Tenant-Id` header
2. Spring Filter validates `tenant_id` JWT claim matches header/domain
3. Set PostgreSQL session: `SET LOCAL app.tenant_id = '<uuid>'`
4. RLS policies automatically filter all queries by tenant

### Backend API Endpoints
**All endpoints require resolved `tenant_id` via gateway + RLS:**
- `/modelos`, `/jetskis`, `/reservas`, `/locacoes/{id}/checkin`, `/locacoes/{id}/checkout`
- `/abastecimentos`, `/manutencoes`, `/fechamento/diario`, `/fechamento/mensal`, `/comissoes`
- `/midia` for photo uploads (returns presigned S3 URL)

**Routing:** Subdomain `{tenant}.system.com` OR path prefix `/t/{tenant}` + `X-Tenant-Id` header validation

### Mobile (KMM - Kotlin Multiplatform Mobile)
- **Shared module** (`:shared`): business rules, validation, repositories, sync logic
  - **Libraries**: Ktor, SQLDelight, kotlinx.serialization, kotlinx-datetime, Napier
- **Android** (`:androidApp`): Jetpack Compose, CameraX, WorkManager, AppAuth (Keycloak PKCE)
- **iOS** (`:iosApp`): SwiftUI, AVFoundation, BGTaskScheduler, AppAuth (Keycloak PKCE)
- **Offline-first**: SQLDelight queue + background sync with exponential backoff
- **Security**: Keystore/Keychain via `SecureStore` expect/actual, PKCE, TLS
- **Photos**: Capture with EXIF, SHA-256 hash, compression (WebP/JPEG/HEIF), presigned S3 URLs for chunked upload
- **Tenant handling**: Store selected `tenant_id` and include in all requests as header

### Backoffice (Reports/Commissions/Closures)
- **Stack**: Next.js 14+ (React, TypeScript) + shadcn/ui
- **Auth**: OIDC (PKCE) via Keycloak
- **Charts/KPIs**: Recharts or ECharts

### Infrastructure & Deployment
- **Cloud**: AWS
- **Orchestration**: EKS (Kubernetes) for APIs, web, and Keycloak via Helm chart
- **Keycloak DB**: Dedicated RDS PostgreSQL
- **Ingress**: AWS ALB Ingress + Nginx Ingress (rate-limit per tenant)
- **CDN**: CloudFront for serving images (private with signed URLs)
- **Security**: AWS WAF, Secrets Manager, Security Hub
- **Backups**: RDS snapshots + Keycloak realm exports (kcadm) + retention rules

### CI/CD
- **Backend/Web**: GitHub Actions (build, tests, SCA, deploy k8s via ArgoCD/Helm)
- **Mobile**: fastlane + TestFlight/Internal Testing
- **Quality**: SonarQube, OWASP Dependency Check, SAST/DAST in pipeline

See `inicial.md` lines 670-859 for complete stack details.

## Personas & Permissions (RBAC per Tenant)

- **ADMIN_TENANT**: Tenant administrator, manages users/roles, subscription settings
- **GERENTE** (Manager): Operations manager, monitors fleet, authorizes discounts, closes day
- **OPERADOR** (Pier Operator): check-in/out, fuel logs, photos, open/close rentals
- **VENDEDOR** (Seller/Partner): creates reservations, earns commission
- **MECANICO** (Mechanic): receives maintenance orders
- **FINANCEIRO** (Finance): reconciles closures, billing, commission payouts
- **Cliente** (Customer): books, signs terms, pays, reviews

**Implementation:** Roles stored in `membro.papeis[]` array, mapped to Keycloak groups/roles per tenant.

## Key Workflows

1. **Reservation → Check-in → Rental in progress → Check-out → Payment/Receipt → Commission**
2. **Refueling** linked to rental (before/after) → daily cost aggregation
3. **Maintenance** → OS → status = unavailable → close OS → status = available
4. **Daily closure** → consolidate rentals, costs, cash → lock retroactive edits

## Testing Strategy (BDD)

Gherkin scenarios in `inicial.md` lines 415-600 covering:
- Tolerance and rounding (1.1-1.3)
- Fuel policies: fixed rate, measured, included (2.1-2.3)
- Commission hierarchy and types (3.1-3.5)
- Mandatory photos and checklist (4.1-4.2)
- Maintenance blocking reservations (5.1-5.2)
- Daily/monthly closure locking (6.1-6.2)
- Schedule conflicts (7.1)

## Non-Functional Requirements

- **Availability**: 99.5% (MVP), scalable for high season
- **API latency**: <300ms (P95)
- **Full audit trail**: per user/tenant with `traceId` correlation
- **Mobile-friendly**: Native apps with offline-first sync
- **LGPD compliance**: consent, configurable retention per tenant, data minimization in logs, DPA (Data Processing Agreement) per client
- **Observability**: Labels by `tenant_id` in logs/metrics/traces, rate-limit per tenant, storage quotas
- **Security**: Logical isolation via RLS, optional envelope encryption per tenant with AWS KMS

## Development Notes

- **Language**: Primary documentation and domain terms are in Portuguese
- **Multi-tenant**: ALL entities must include `tenant_id`; ALL queries filtered by RLS
- **Tenant resolution**: Extract from subdomain/path → validate JWT claim → set PostgreSQL session variable
- **Database**: PostgreSQL 16 with RLS + JSONB for flexible schema evolution
- **Image storage**: S3 with tenant prefix `tenant_id/`, integrity hashing (SHA-256), configurable retention per tenant
- **Authentication**: Keycloak 26 (OSS) with single realm + `tenant_id` claim, PKCE for mobile
- **API patterns**: OpenAPI-first, versioned DTOs, idempotency keys (per tenant + operation)
- **Feature flags**: Commission/fuel policies configurable per tenant
- **Integrations**: Payment gateway (card/PIX with pre-authorization for deposits), e-signature for liability terms, optional GPS/IoT telemetry

## Keycloak 26 (OSS) Configuration

- **Deployment**: Helm chart on EKS, 3 replicas, `proxy=edge`, `hostname-strict=true`
- **Realm strategy**: Single realm with `tenant_id` claim in tokens (Option A)
- **RBAC**: Groups/roles per tenant (e.g., `tenant-acme-GERENTE`, `tenant-acme-OPERADOR`)
- **Security**: PKCE mandatory for public clients, key rotation enabled, CORS/Origins per tenant
- **Metrics**: Prometheus enabled, health/readiness probes
- **Admin delegation**: Fine-Grained Admin for limited per-tenant administration

## Infrastructure Roadmap

- **MVP**: EKS + RDS Postgres + S3 + Redis + Keycloak (Helm), WAF basic rules
- **Scale**: Partition storage for large tenants, Kafka for events, dedicated KMS keys per tenant
- **Enterprise**: Realm per tenant (if required), dedicated databases/schemas for premium clients

## Next Steps (Proposed)

1. Spring Boot 3.3 backend scaffold with multi-tenant filter + RLS
2. Flyway migrations: `tenant`, `plano`, `assinatura`, `usuario`, `membro` tables
3. Keycloak 26 Helm deployment with single realm + `tenant_id` custom mapper
4. Automated tests (unit + API with Testcontainers) covering BDD scenarios
5. KMM mobile app setup: AppAuth + tenant selection + offline queue
6. Photo capture POC: 4 mandatory photos → SHA-256 → presigned S3 upload
7. CI/CD pipeline: GitHub Actions (backend), fastlane (mobile)
- Toda alteração de tabela ou inclusao devem ser inseridas no arquito reset-ambiente-dev.sh