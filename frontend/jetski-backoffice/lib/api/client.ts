import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8090/api'

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor for adding auth token and tenant header
apiClient.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    // Token will be set by the provider/hook
    const token = typeof window !== 'undefined'
      ? sessionStorage.getItem('accessToken')
      : null

    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // Tenant ID will be set by the provider/hook
    const tenantId = typeof window !== 'undefined'
      ? sessionStorage.getItem('tenantId')
      : null

    if (tenantId) {
      config.headers['X-Tenant-Id'] = tenantId
    }

    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor for handling errors
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    if (error.response?.status === 401) {
      // Token expired or invalid - redirect to login
      if (typeof window !== 'undefined') {
        window.location.href = '/login'
      }
    }

    if (error.response?.status === 403) {
      // Forbidden - user doesn't have permission
      console.error('Access forbidden:', error.response.data)
    }

    return Promise.reject(error)
  }
)

// Helper to set auth token
export function setAuthToken(token: string | null) {
  if (typeof window !== 'undefined') {
    if (token) {
      sessionStorage.setItem('accessToken', token)
    } else {
      sessionStorage.removeItem('accessToken')
    }
  }
}

// Helper to set tenant ID
export function setTenantId(tenantId: string | null) {
  if (typeof window !== 'undefined') {
    if (tenantId) {
      sessionStorage.setItem('tenantId', tenantId)
    } else {
      sessionStorage.removeItem('tenantId')
    }
  }
}

// Helper to get current tenant ID
export function getTenantId(): string | null {
  if (typeof window !== 'undefined') {
    return sessionStorage.getItem('tenantId')
  }
  return null
}
