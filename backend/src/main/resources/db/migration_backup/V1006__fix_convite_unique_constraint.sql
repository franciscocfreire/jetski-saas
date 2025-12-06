-- Migration: Fix convite unique constraint to allow re-inviting expired users
--
-- Problem: UNIQUE constraint on (tenant_id, email) prevents re-inviting users with EXPIRED invitations
-- Solution: Replace with partial unique index that only applies to PENDING invitations
--
-- This allows:
-- - Only ONE pending invitation per email/tenant (prevents duplicates)
-- - Multiple EXPIRED/CANCELLED/ACTIVATED invitations (historical record)
-- - Re-inviting users whose previous invitations expired
--
-- @author Jetski Team
-- @since 0.4.1

-- Drop the old UNIQUE constraint
ALTER TABLE convite DROP CONSTRAINT IF EXISTS convite_tenant_email_unique;

-- Create a partial unique index that only enforces uniqueness for PENDING invitations
CREATE UNIQUE INDEX convite_tenant_email_pending_unique
    ON convite(tenant_id, email)
    WHERE status = 'PENDING';

COMMENT ON INDEX convite_tenant_email_pending_unique IS
    'Ensures only one PENDING invitation per email/tenant, allows re-inviting EXPIRED users';
