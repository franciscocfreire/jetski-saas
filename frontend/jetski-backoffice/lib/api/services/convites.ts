import { apiClient, getTenantId } from '../client'
import type { ConviteSummary } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/invitations`

/**
 * Service: Invitations Management
 *
 * Handles pending invitation operations:
 * - List pending invitations
 * - Resend invitation emails
 * - Cancel invitations
 */
export const convitesService = {
  /**
   * List all pending invitations for the current tenant.
   *
   * @returns List of invitation summaries
   */
  async listPending(): Promise<ConviteSummary[]> {
    const { data } = await apiClient.get<ConviteSummary[]>(getBasePath())
    return data
  },

  /**
   * Resend an invitation email.
   * Generates new token and extends expiration to 48 hours.
   *
   * @param conviteId Invitation UUID
   * @returns Updated invitation summary
   */
  async resend(conviteId: string): Promise<ConviteSummary> {
    const { data } = await apiClient.post<ConviteSummary>(`${getBasePath()}/${conviteId}/resend`)
    return data
  },

  /**
   * Cancel a pending invitation.
   *
   * @param conviteId Invitation UUID
   */
  async cancel(conviteId: string): Promise<void> {
    await apiClient.delete(`${getBasePath()}/${conviteId}`)
  },
}
