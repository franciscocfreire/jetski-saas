import { apiClient, getTenantId } from '../client'
import type { DashboardMetrics, OnboardingChecklist } from '../types'

/**
 * Dashboard Service
 *
 * Provides cached revenue metrics for the dashboard.
 * Uses hybrid caching strategy on backend:
 * - Redis cache with 5-minute TTL
 * - Event-driven invalidation on rental completion
 */
export const dashboardService = {
  /**
   * Get dashboard revenue metrics (cached)
   *
   * Returns:
   * - receitaHoje: Today's revenue (calendar day)
   * - receitaMes: Month's revenue (calendar month: day 1 to today)
   * - locacoesHoje: Number of rentals finalized today
   * - locacoesMes: Number of rentals finalized this month
   */
  async getMetrics(): Promise<DashboardMetrics> {
    const { data } = await apiClient.get<DashboardMetrics>('/v1/frota/metrics')
    return data
  },

  /**
   * Force cache invalidation (admin only)
   */
  async invalidateCache(): Promise<void> {
    await apiClient.delete('/v1/frota/metrics/cache')
  },

  /** Checklist "primeiros passos" — cada flag auto-detectada dos dados reais do tenant. */
  async getOnboarding(): Promise<OnboardingChecklist> {
    const { data } = await apiClient.get<OnboardingChecklist>(
      `/v1/tenants/${getTenantId()}/dashboard/onboarding`
    )
    return data
  },
}
