import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { signOut } from 'next-auth/react'

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8090/api'

// Flag to prevent multiple simultaneous refresh attempts
let isRefreshing = false
// Queue of failed requests to retry after token refresh
let failedQueue: Array<{
  resolve: (token: string) => void
  reject: (error: Error) => void
}> = []

const processQueue = (error: Error | null, token: string | null = null) => {
  failedQueue.forEach((promise) => {
    if (error) {
      promise.reject(error)
    } else if (token) {
      promise.resolve(token)
    }
  })
  failedQueue = []
}

// Prevents duplicate signOut() calls when multiple pollers hit a dead
// refresh token around the same time (each would otherwise redirect once).
let signingOut = false

/**
 * Terminate the session for real when the refresh token is dead.
 * Clearing sessionStorage alone leaves the NextAuth httpOnly cookie (and its
 * dead refresh token) in place, so every subsequent poller keeps retrying
 * the refresh against Keycloak forever. signOut() clears that cookie too.
 */
async function forceSignOut() {
  if (typeof window === 'undefined' || signingOut) return
  signingOut = true
  sessionStorage.removeItem('accessToken')
  sessionStorage.removeItem('tenantId')
  await signOut({ callbackUrl: '/login?error=SessionExpired' })
}

/**
 * Attempt to refresh the session and get a new access token.
 * This triggers NextAuth to use the refresh token.
 */
async function refreshSession(): Promise<string | null> {
  try {
    // Fetch updated session from NextAuth - this triggers the jwt callback
    // which will use the refresh token if the access token is expired
    const response = await fetch('/api/auth/session', {
      method: 'GET',
      credentials: 'include',
    })

    if (!response.ok) {
      console.error('[API Client] Session refresh failed:', response.status)
      return null
    }

    const session = await response.json()

    if (session?.error === 'RefreshAccessTokenError') {
      console.error('[API Client] Refresh token is invalid or expired')
      return null
    }

    if (session?.accessToken) {
      console.log('[API Client] Session refreshed successfully')
      // Update the stored token
      if (typeof window !== 'undefined') {
        sessionStorage.setItem('accessToken', session.accessToken)
      }
      return session.accessToken
    }

    return null
  } catch (error) {
    console.error('[API Client] Error refreshing session:', error)
    return null
  }
}

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  // Rede fraca (praia) que trava não pode deixar o botão "enviando…" para sempre:
  // sem timeout, a promise nunca rejeita e o onError das mutations nunca dispara.
  // 120s é generoso p/ upload legítimo lento; ainda assim mata o request pendurado.
  timeout: 120_000,
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

    // Tenant ID will be set by the provider/hook.
    // Não sobrescreve um X-Tenant-Id explícito da requisição (ações de plataforma
    // do super admin operam no tenant alvo, não no tenant atual da sessão).
    const tenantId = typeof window !== 'undefined'
      ? sessionStorage.getItem('tenantId')
      : null

    if (tenantId && !config.headers['X-Tenant-Id']) {
      config.headers['X-Tenant-Id'] = tenantId
    }

    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor for handling errors with token refresh
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    // Handle 401 Unauthorized - attempt token refresh
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // If already refreshing, queue this request
        return new Promise<string>((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            return apiClient(originalRequest)
          })
          .catch((err) => Promise.reject(err))
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const newToken = await refreshSession()

        if (newToken) {
          // Token refreshed successfully - retry all queued requests
          processQueue(null, newToken)
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return apiClient(originalRequest)
        } else {
          // Refresh failed - session is dead, terminate it for real
          processQueue(new Error('Token refresh failed'), null)
          await forceSignOut()
          return Promise.reject(error)
        }
      } catch (refreshError) {
        processQueue(refreshError as Error, null)
        await forceSignOut()
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
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
