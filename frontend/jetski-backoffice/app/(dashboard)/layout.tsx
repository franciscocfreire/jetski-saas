'use client'

import { useEffect, useState, useRef } from 'react'
import { useSession, signOut } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { SidebarProvider, SidebarInset } from '@/components/ui/sidebar'
import { AppSidebar } from '@/components/layout/app-sidebar'
import { Header } from '@/components/layout/header'
import { RentalNotificationProvider } from '@/components/providers/rental-notification-provider'
import { useTenantStore } from '@/lib/store/tenant-store'
import { setAuthToken, setTenantId } from '@/lib/api/client'
import { userTenantsService } from '@/lib/api/services'
import { Skeleton } from '@/components/ui/skeleton'

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const { data: session, status } = useSession()
  const router = useRouter()
  const { currentTenant, setTenants, setCurrentTenant, clearTenant } = useTenantStore()
  const [tenantsLoaded, setTenantsLoaded] = useState(false)
  const lastTokenRef = useRef<string | null>(null)

  // Handle unauthenticated or session error
  useEffect(() => {
    if (status === 'unauthenticated') {
      router.push('/login')
    }

    // Handle refresh token error - force re-login
    if (session?.error === 'RefreshAccessTokenError') {
      console.error('🔒 Refresh token error, signing out...')
      clearTenant()
      signOut({ callbackUrl: '/login' })
    }
  }, [status, session?.error, router, clearTenant])

  // ALWAYS sync accessToken when it changes (important for token refresh)
  useEffect(() => {
    if (session?.accessToken && session.accessToken !== lastTokenRef.current) {
      console.log('🔐 Syncing auth token (new or refreshed)...')
      setAuthToken(session.accessToken)
      lastTokenRef.current = session.accessToken
    }
  }, [session?.accessToken])

  // Load tenants only once on initial auth
  useEffect(() => {
    if (session?.accessToken && !tenantsLoaded) {
      // Load user tenants
      console.log('📡 Fetching user tenants...')
      userTenantsService.getMyTenants()
        .then((response) => {
          console.log('✅ Tenants response:', response)
          setTenants(response.tenants || [])
          setTenantsLoaded(true)

          // Auto-select first tenant if none selected
          if (!currentTenant && response.tenants && response.tenants.length > 0) {
            console.log('🏢 Auto-selecting first tenant:', response.tenants[0])
            setCurrentTenant(response.tenants[0])
          }
        })
        .catch((error) => {
          console.error('❌ Error fetching tenants:', error)
          console.error('Error details:', error.response?.data || error.message)
          setTenantsLoaded(true) // Mark as loaded even on error to avoid infinite retries
        })
    }
  }, [session?.accessToken, tenantsLoaded, currentTenant, setTenants, setCurrentTenant])

  useEffect(() => {
    if (currentTenant) {
      console.log('🏢 Setting tenant ID:', currentTenant.id)
      setTenantId(currentTenant.id)
    }
  }, [currentTenant])

  if (status === 'loading') {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="space-y-4">
          <Skeleton className="h-12 w-48" />
          <Skeleton className="h-4 w-32" />
        </div>
      </div>
    )
  }

  if (status === 'unauthenticated') {
    return null
  }

  return (
    <SidebarProvider>
      <RentalNotificationProvider>
        <AppSidebar />
        <SidebarInset>
          <Header />
          <main className="flex-1 overflow-auto p-6">{children}</main>
        </SidebarInset>
      </RentalNotificationProvider>
    </SidebarProvider>
  )
}
