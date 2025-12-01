import { apiClient } from '../client'
import type { UserTenantsResponse } from '../types'

export const userTenantsService = {
  async getMyTenants(): Promise<UserTenantsResponse> {
    const { data } = await apiClient.get<UserTenantsResponse>('/v1/user/tenants')
    return data
  },
}
