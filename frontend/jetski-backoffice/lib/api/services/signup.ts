import axios from 'axios'
import type {
  TenantSignupRequest,
  TenantSignupResponse,
  SlugAvailabilityResponse,
  SignupActivationRequest,
} from '../types'

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8090/api'

/**
 * Service for public tenant signup (no authentication required).
 * Used for new users creating their first company.
 */
export const signupService = {
  /**
   * Create a new tenant and admin user account.
   * User will receive an activation email to complete registration.
   *
   * @param data Tenant and admin user details
   * @returns Signup response with tenant ID and next steps
   */
  async signupTenant(data: TenantSignupRequest): Promise<TenantSignupResponse> {
    const response = await axios.post<TenantSignupResponse>(
      `${API_URL}/v1/signup/tenant`,
      data
    )
    return response.data
  },

  /**
   * Check if a slug is available for use.
   *
   * @param slug The slug to check
   * @returns Whether the slug is available
   */
  async checkSlugAvailability(slug: string): Promise<boolean> {
    const response = await axios.get<SlugAvailabilityResponse>(
      `${API_URL}/v1/signup/check-slug`,
      { params: { slug } }
    )
    return response.data.available
  },

  /**
   * Activate a signup by providing the token and temporary password.
   *
   * @param token Activation token from email
   * @param temporaryPassword Temporary password from email
   * @returns Success message
   */
  async activateSignup(token: string, temporaryPassword: string): Promise<{ message: string }> {
    const response = await axios.post<{ message: string }>(
      `${API_URL}/v1/signup/activate`,
      null,
      { params: { token, temporaryPassword } }
    )
    return response.data
  },
}
