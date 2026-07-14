'use client'

import { useEffect, useState, useRef } from 'react'
import { useSession, signOut } from 'next-auth/react'
import { useRouter } from 'next/navigation'
import { SidebarProvider, SidebarInset } from '@/components/ui/sidebar'
import { AppSidebar } from '@/components/layout/app-sidebar'
import { Header } from '@/components/layout/header'
import { RentalNotificationProvider } from '@/components/providers/rental-notification-provider'
import { TenantThemeProvider } from '@/components/providers/tenant-theme-provider'
import { useTenantStore } from '@/lib/store/tenant-store'
import { setAuthToken, setTenantId } from '@/lib/api/client'
import { userTenantsService, platformService } from '@/lib/api/services'
import { Skeleton } from '@/components/ui/skeleton'
import { TenantStatusGate } from '@/components/tenant-status-gate'

const OPERATIONAL_STATUSES = ['ATIVO', 'TRIAL']

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const { data: session, status } = useSession()
  const router = useRouter()
  const { currentTenant, accessType, setTenants, setCurrentTenant, setAccessType, clearTenant, _hasHydrated } = useTenantStore()
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

  // Load tenants only once on initial auth (after hydration)
  useEffect(() => {
    if (!_hasHydrated) return // Wait for Zustand hydration
    if (session?.accessToken && !tenantsLoaded) {
      // Load user tenants
      console.log('📡 Fetching user tenants...')
      userTenantsService.getMyTenants()
        .then(async (response) => {
          console.log('✅ Tenants response:', response)
          const memberships = response.tenants || []
          setTenants(memberships)
          setAccessType(response.accessType ?? null)

          // Garante um tenant atual (e X-Tenant-Id) antes de chamadas tenant-scoped
          let current = currentTenant
          if (current) {
            // Reconcilia o tenant persistido (zustand/localStorage) com a resposta
            // fresca — SEMPRE que existir: status (aprovação/suspensão) e módulos
            // do plano (V046/V047) mudam no servidor, e o snapshot local não pode
            // prender o usuário em gate/menu desatualizado até relogar.
            const fresh = memberships.find((t) => t.id === current!.id)
            if (fresh) {
              if (fresh.status !== current.status) {
                console.log('🔄 Tenant status atualizado:', current.status, '→', fresh.status)
              }
              setCurrentTenant(fresh)
              current = fresh
            }
          }
          if (!current && memberships.length > 0) {
            current = memberships[0]
            console.log('🏢 Auto-selecting first tenant:', current)
            setCurrentTenant(current)
          }
          setTenantsLoaded(true)

          // Super admin: carrega TODAS as empresas no switcher (acesso total à plataforma)
          if (response.accessType === 'UNRESTRICTED' && current) {
            try {
              const all = await platformService.listAllTenants()
              if (all && all.length > 0) {
                setTenants(all)
              }
            } catch (e) {
              console.error('⚠️ Falha ao carregar todas as empresas (platform):', e)
            }
          }
        })
        .catch((error) => {
          console.error('❌ Error fetching tenants:', error)
          console.error('Error details:', error.response?.data || error.message)
          setTenantsLoaded(true) // Mark as loaded even on error to avoid infinite retries
        })
    }
  }, [session?.accessToken, tenantsLoaded, currentTenant, setTenants, setCurrentTenant, setAccessType, _hasHydrated])

  useEffect(() => {
    if (currentTenant) {
      console.log('🏢 Setting tenant ID:', currentTenant.id)
      setTenantId(currentTenant.id)
    }
  }, [currentTenant])

  if (status === 'loading' || !_hasHydrated) {
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

  // Gate de status: empresa selecionada não operacional (pendente/suspensa/etc.)
  // mostra a tela de status no lugar do conteúdo, mantendo sidebar (switcher) e header.
  // Super admin (UNRESTRICTED) é isento do gate — pode inspecionar qualquer empresa.
  const tenantBlocked =
    accessType !== 'UNRESTRICTED' &&
    currentTenant != null &&
    !OPERATIONAL_STATUSES.includes(currentTenant.status)

  return (
    <SidebarProvider>
      <TenantThemeProvider>
        <RentalNotificationProvider>
          <AppSidebar />
          <SidebarInset>
            <Header />
            <main className="flex flex-1 flex-col overflow-auto p-4 sm:p-6">
              {tenantBlocked ? <TenantStatusGate tenant={currentTenant!} /> : children}
            </main>
          </SidebarInset>
        </RentalNotificationProvider>
      </TenantThemeProvider>
    </SidebarProvider>
  )
}
