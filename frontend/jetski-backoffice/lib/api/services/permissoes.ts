import { apiClient, getTenantId } from '../client'
import type { PermissionsMatrixResponse, UserPermissionsResponse } from '../types'

/**
 * Service de permissões (fonte: rbac.rego via OPA, exposto pelo backend).
 * O menu usa getMinhasPermissoes() como filtro de UX; o enforcement real
 * continua no backend (ABACAuthorizationInterceptor → 403).
 */
export const permissoesService = {
  /** Permissões efetivas do usuário logado no tenant atual (X-Tenant-Id). */
  async getMinhasPermissoes(): Promise<UserPermissionsResponse> {
    const { data } = await apiClient.get<UserPermissionsResponse>('/v1/user/permissions')
    return data
  },

  /** Matriz papel × permissões (read-only; requer ADMIN_TENANT ou GERENTE). */
  async getMatriz(): Promise<PermissionsMatrixResponse> {
    const { data } = await apiClient.get<PermissionsMatrixResponse>(
      `/v1/tenants/${getTenantId()}/config/permissions-matrix`
    )
    return data
  },
}
