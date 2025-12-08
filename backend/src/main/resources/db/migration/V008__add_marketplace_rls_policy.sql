-- V008: Add RLS policy for public marketplace reads
-- Allows reading modelo records that are marked for public marketplace display
-- without requiring a tenant context

-- First, fix the existing tenant_isolation_modelo policy to handle empty strings
-- This is needed because HikariCP connection pooling reuses connections
-- and the app.tenant_id may be reset to empty string for public endpoints
DROP POLICY IF EXISTS tenant_isolation_modelo ON modelo;

CREATE POLICY tenant_isolation_modelo ON modelo
    FOR ALL
    USING (
        -- NULLIF converts '' to NULL, NULL::uuid is NULL, tenant_id = NULL returns false
        -- This safely handles empty string without throwing UUID parse errors
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    );

COMMENT ON POLICY tenant_isolation_modelo ON modelo IS 'Tenant isolation policy using NULLIF for safe UUID handling';

-- Policy for public marketplace reads on modelo table
-- This allows SELECT when:
-- 1. The model is active and marked for marketplace display
-- 2. The tenant is active and marked for marketplace display
-- 3. No tenant context is required (app.tenant_id can be null/empty)

CREATE POLICY marketplace_public_read ON modelo
    FOR SELECT
    USING (
        exibir_no_marketplace = true
        AND ativo = true
        AND EXISTS (
            SELECT 1 FROM tenant t
            WHERE t.id = modelo.tenant_id
            AND t.status = 'ATIVO'
            AND t.exibir_no_marketplace = true
        )
    );

-- Also need to allow reading tenant data for the JOIN
-- Add marketplace policy for tenant table

CREATE POLICY marketplace_public_read ON tenant
    FOR SELECT
    USING (
        exibir_no_marketplace = true
        AND status = 'ATIVO'
    );

COMMENT ON POLICY marketplace_public_read ON modelo IS 'Allows public read access to marketplace-visible models';
COMMENT ON POLICY marketplace_public_read ON tenant IS 'Allows public read access to marketplace-visible tenants';
