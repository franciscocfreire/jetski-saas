import { apiClient, getTenantId } from '../client'
import type {
  ListMembersResponse,
  MemberSummary,
  InviteUserRequest,
  UpdateRolesRequest,
} from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}`

/**
 * Service: Members Management
 *
 * Handles tenant member operations:
 * - List members with plan limit info
 * - Invite new users
 * - Update member roles
 * - Deactivate/reactivate members
 */
export const membrosService = {
  /**
   * List all members of the current tenant.
   *
   * @param includeInactive Include inactive members (default: false)
   * @returns List of members with plan limit info
   */
  async list(includeInactive = false): Promise<ListMembersResponse> {
    const { data } = await apiClient.get<ListMembersResponse>(
      `${getBasePath()}/members`,
      { params: { includeInactive } }
    )
    return data
  },

  /**
   * Invite a new user to the tenant.
   *
   * @param request Invitation details (email, nome, papeis)
   */
  async invite(request: InviteUserRequest): Promise<void> {
    await apiClient.post(`${getBasePath()}/users/invite`, request)
  },

  /**
   * Update a member's roles.
   *
   * @param usuarioId User UUID
   * @param request New roles to assign
   * @returns Updated member summary
   */
  async updateRoles(usuarioId: string, request: UpdateRolesRequest): Promise<MemberSummary> {
    const { data } = await apiClient.patch<MemberSummary>(
      `${getBasePath()}/members/${usuarioId}/roles`,
      request
    )
    return data
  },

  /**
   * Deactivate a member (soft delete).
   *
   * @param usuarioId User UUID to deactivate
   */
  async deactivate(usuarioId: string): Promise<void> {
    await apiClient.delete(`${getBasePath()}/members/${usuarioId}`)
  },

  /**
   * Reactivate a deactivated member.
   *
   * @param usuarioId User UUID to reactivate
   * @returns Reactivated member summary
   */
  async reactivate(usuarioId: string): Promise<MemberSummary> {
    const { data } = await apiClient.post<MemberSummary>(
      `${getBasePath()}/members/${usuarioId}/reactivate`
    )
    return data
  },
}
