'use client'

import { useEffect } from 'react'
import { useTenantStore } from '@/lib/store/tenant-store'

export default function LogoutPage() {
  const clearTenant = useTenantStore((state) => state.clearTenant)

  useEffect(() => {
    // Clear tenant from localStorage
    clearTenant()

    // Also clear the raw localStorage key to be safe
    if (typeof window !== 'undefined') {
      localStorage.removeItem('tenant-storage')
    }

    // Redirect to API logout route (server-side cleanup)
    window.location.href = '/api/logout'
  }, [clearTenant])

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="text-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mx-auto mb-4"></div>
        <p className="text-muted-foreground">Saindo...</p>
      </div>
    </div>
  )
}
