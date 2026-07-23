'use client'

import { useQuery } from '@tanstack/react-query'
import { permissoesService } from '@/lib/api/services'
import { useTenantStore } from '@/lib/store/tenant-store'

/**
 * Matching de permissão crua contra uma action — espelho de
 * action_matches_permission do rbac.rego: exato, "*" e "recurso:*".
 */
export function matchesPermission(action: string, permission: string): boolean {
  if (permission === '*' || permission === action) return true
  if (permission.endsWith(':*')) {
    return action.startsWith(permission.slice(0, -1))
  }
  return false
}

/**
 * Permissões efetivas do usuário no tenant atual (GET /v1/user/permissions,
 * fonte rbac.rego via OPA).
 *
 * - `can(action)` / `canAny(actions)`: filtro de UX (menu, botões) — o
 *   enforcement real continua no backend (403 por requisição).
 * - Superadmin (accessType UNRESTRICTED) curto-circuita para true: pode não
 *   ter vínculo/papéis no tenant que está operando.
 * - Key inclui o tenant: trocar de tenant refaz a query automaticamente.
 * - `isError`: quem consome decide o fail-open (ex.: sidebar mostra o item).
 */
export function usePermissions() {
  const { currentTenant, accessType } = useTenantStore()
  const unrestricted = accessType === 'UNRESTRICTED'

  const query = useQuery({
    queryKey: ['user-permissions', currentTenant?.id],
    queryFn: () => permissoesService.getMinhasPermissoes(),
    enabled: !!currentTenant && !unrestricted,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  })

  const permissions = query.data?.permissions ?? []

  const can = (action: string): boolean =>
    unrestricted || permissions.some((p) => matchesPermission(action, p))

  const canAny = (actions: string[]): boolean => actions.some(can)

  return {
    can,
    canAny,
    permissions,
    isLoading: !unrestricted && !!currentTenant && query.isLoading,
    isError: query.isError,
  }
}
