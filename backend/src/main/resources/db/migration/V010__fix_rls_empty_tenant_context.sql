-- ==============================================================================
-- V010: Fix ALL RLS policies to handle empty tenant context gracefully
-- ==============================================================================
--
-- Problem: When there's no tenant context (scheduled jobs, public endpoints),
-- app.tenant_id is reset to empty string ''. The RLS policies try to cast
-- ''::uuid which fails with "invalid input syntax for type uuid".
--
-- Solution: Update all RLS policies to use NULLIF to handle empty strings.
-- When NULLIF(current_setting('app.tenant_id', true), '') returns NULL,
-- the comparison tenant_id = NULL will be FALSE (safe, returns no rows).
--
-- Special cases:
-- - convite: Allows SELECT without tenant (for magic-activate)
-- - modelo, modelo_midia, tenant: Have marketplace_public_read (already handle this)
-- ==============================================================================

-- Helper function to safely get tenant_id as UUID (returns NULL if empty/invalid)
CREATE OR REPLACE FUNCTION get_current_tenant_id()
RETURNS uuid AS $$
BEGIN
    RETURN NULLIF(current_setting('app.tenant_id', true), '')::uuid;
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON FUNCTION get_current_tenant_id() IS
    'Safely returns the current tenant_id as UUID, or NULL if not set/empty/invalid. '
    'Used by RLS policies to handle missing tenant context gracefully.';

-- ==============================================================================
-- Standard tenant isolation policies (block access when no tenant)
-- ==============================================================================

-- abastecimento
DROP POLICY IF EXISTS tenant_isolation_abastecimento ON abastecimento;
CREATE POLICY tenant_isolation_abastecimento ON abastecimento
    USING (tenant_id = get_current_tenant_id());

-- auditoria
DROP POLICY IF EXISTS tenant_isolation_auditoria ON auditoria;
CREATE POLICY tenant_isolation_auditoria ON auditoria
    USING (tenant_id = get_current_tenant_id());

-- cliente
DROP POLICY IF EXISTS tenant_isolation_cliente ON cliente;
CREATE POLICY tenant_isolation_cliente ON cliente
    USING (tenant_id = get_current_tenant_id());

-- comissao
DROP POLICY IF EXISTS tenant_isolation_comissao ON comissao;
CREATE POLICY tenant_isolation_comissao ON comissao
    USING (tenant_id = get_current_tenant_id());

-- fechamento_diario
DROP POLICY IF EXISTS tenant_isolation_fechamento_diario ON fechamento_diario;
CREATE POLICY tenant_isolation_fechamento_diario ON fechamento_diario
    USING (tenant_id = get_current_tenant_id());

-- fechamento_mensal
DROP POLICY IF EXISTS tenant_isolation_fechamento_mensal ON fechamento_mensal;
CREATE POLICY tenant_isolation_fechamento_mensal ON fechamento_mensal
    USING (tenant_id = get_current_tenant_id());

-- foto
DROP POLICY IF EXISTS tenant_isolation_foto ON foto;
CREATE POLICY tenant_isolation_foto ON foto
    USING (tenant_id = get_current_tenant_id());

-- fuel_price_day
DROP POLICY IF EXISTS tenant_isolation_fuel_price_day ON fuel_price_day;
CREATE POLICY tenant_isolation_fuel_price_day ON fuel_price_day
    USING (tenant_id = get_current_tenant_id());

-- item_opcional
DROP POLICY IF EXISTS tenant_isolation_item_opcional ON item_opcional;
CREATE POLICY tenant_isolation_item_opcional ON item_opcional
    USING (tenant_id = get_current_tenant_id());

-- jetski
DROP POLICY IF EXISTS tenant_isolation_jetski ON jetski;
CREATE POLICY tenant_isolation_jetski ON jetski
    USING (tenant_id = get_current_tenant_id());

-- locacao
DROP POLICY IF EXISTS tenant_isolation_locacao ON locacao;
CREATE POLICY tenant_isolation_locacao ON locacao
    USING (tenant_id = get_current_tenant_id());

-- locacao_item_opcional
DROP POLICY IF EXISTS tenant_isolation_locacao_item_opcional ON locacao_item_opcional;
CREATE POLICY tenant_isolation_locacao_item_opcional ON locacao_item_opcional
    USING (tenant_id = get_current_tenant_id());

-- modelo (keep marketplace_public_read, update tenant_isolation)
DROP POLICY IF EXISTS tenant_isolation_modelo ON modelo;
CREATE POLICY tenant_isolation_modelo ON modelo
    USING (tenant_id = get_current_tenant_id());

-- modelo_midia (keep marketplace_public_read, update tenant_isolation)
DROP POLICY IF EXISTS tenant_isolation_modelo_midia ON modelo_midia;
CREATE POLICY tenant_isolation_modelo_midia ON modelo_midia
    USING (tenant_id = get_current_tenant_id());

-- os_manutencao
DROP POLICY IF EXISTS tenant_isolation_os_manutencao ON os_manutencao;
CREATE POLICY tenant_isolation_os_manutencao ON os_manutencao
    USING (tenant_id = get_current_tenant_id());

-- politica_comissao
DROP POLICY IF EXISTS tenant_isolation_politica_comissao ON politica_comissao;
CREATE POLICY tenant_isolation_politica_comissao ON politica_comissao
    USING (tenant_id = get_current_tenant_id());

-- reserva
DROP POLICY IF EXISTS tenant_isolation_reserva ON reserva;
CREATE POLICY tenant_isolation_reserva ON reserva
    USING (tenant_id = get_current_tenant_id());

-- vendedor
DROP POLICY IF EXISTS tenant_isolation_vendedor ON vendedor;
CREATE POLICY tenant_isolation_vendedor ON vendedor
    USING (tenant_id = get_current_tenant_id());

-- ==============================================================================
-- Fuel policy (has separate policies for each command)
-- ==============================================================================

DROP POLICY IF EXISTS fuel_policy_tenant_select ON fuel_policy;
DROP POLICY IF EXISTS fuel_policy_tenant_insert ON fuel_policy;
DROP POLICY IF EXISTS fuel_policy_tenant_update ON fuel_policy;
DROP POLICY IF EXISTS fuel_policy_tenant_delete ON fuel_policy;

CREATE POLICY fuel_policy_tenant_select ON fuel_policy FOR SELECT
    USING (tenant_id = get_current_tenant_id());
CREATE POLICY fuel_policy_tenant_insert ON fuel_policy FOR INSERT
    WITH CHECK (tenant_id = get_current_tenant_id());
CREATE POLICY fuel_policy_tenant_update ON fuel_policy FOR UPDATE
    USING (tenant_id = get_current_tenant_id());
CREATE POLICY fuel_policy_tenant_delete ON fuel_policy FOR DELETE
    USING (tenant_id = get_current_tenant_id());

-- ==============================================================================
-- Assinatura (has separate policies for each command)
-- ==============================================================================

DROP POLICY IF EXISTS assinatura_tenant_select ON assinatura;
DROP POLICY IF EXISTS assinatura_tenant_insert ON assinatura;
DROP POLICY IF EXISTS assinatura_tenant_update ON assinatura;
DROP POLICY IF EXISTS assinatura_tenant_delete ON assinatura;

CREATE POLICY assinatura_tenant_select ON assinatura FOR SELECT
    USING (tenant_id = get_current_tenant_id());
CREATE POLICY assinatura_tenant_insert ON assinatura FOR INSERT
    WITH CHECK (tenant_id = get_current_tenant_id());
CREATE POLICY assinatura_tenant_update ON assinatura FOR UPDATE
    USING (tenant_id = get_current_tenant_id());
CREATE POLICY assinatura_tenant_delete ON assinatura FOR DELETE
    USING (tenant_id = get_current_tenant_id());

-- ==============================================================================
-- Convite: Special case - allows SELECT without tenant (for magic-activate)
-- ==============================================================================

DROP POLICY IF EXISTS tenant_isolation_convite ON convite;

CREATE POLICY tenant_isolation_convite ON convite
    FOR ALL
    USING (
        -- Allow SELECT/UPDATE access if:
        -- 1. Tenant context matches, OR
        -- 2. No tenant context (for public token-based queries like magic-activate)
        CASE
            WHEN get_current_tenant_id() IS NULL THEN true
            ELSE tenant_id = get_current_tenant_id()
        END
    )
    WITH CHECK (
        -- For INSERT/UPDATE: Allow without tenant context (needed for magic-activate)
        -- This is safe because:
        -- 1. Convite tokens are secure random 40-character strings
        -- 2. The activation endpoint validates token + temporary password
        -- 3. Only the specific convite row matching the token gets updated
        CASE
            WHEN get_current_tenant_id() IS NULL THEN true
            ELSE tenant_id = get_current_tenant_id()
        END
    );

COMMENT ON POLICY tenant_isolation_convite ON convite IS
    'RLS for convite: Allows SELECT/UPDATE without tenant (for magic-activate public endpoint). '
    'Safe because token-based access validates cryptographically secure token + password.';
