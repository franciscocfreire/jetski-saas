import { apiClient } from '../client'
import type { CreateTenantRequest, TenantSignupResponse } from '../types'

/**
 * Service for tenant management operations (authentication required).
 * Used for existing users to create additional companies.
 */
export const tenantService = {
  /**
   * Create a new tenant for the authenticated user.
   * User is immediately added as ADMIN_TENANT of the new company.
   *
   * @param data Tenant details (razaoSocial, slug, cnpj)
   * @returns Signup response with tenant ID
   */
  async createTenant(data: CreateTenantRequest): Promise<TenantSignupResponse> {
    const response = await apiClient.post<TenantSignupResponse>(
      '/v1/tenants/create',
      data
    )
    return response.data
  },

  /**
   * Check if a slug is available for use.
   * This endpoint is public but can be used by authenticated users too.
   *
   * @param slug The slug to check
   * @returns Whether the slug is available
   */
  async checkSlugAvailability(slug: string): Promise<boolean> {
    const response = await apiClient.get<{ available: boolean }>(
      '/v1/signup/check-slug',
      { params: { slug } }
    )
    return response.data.available
  },
}
