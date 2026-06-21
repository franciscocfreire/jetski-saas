import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { TenantSummary } from '../api/types'
import { setTenantId } from '../api/client'

interface TenantState {
  currentTenant: TenantSummary | null
  tenants: TenantSummary[]
  accessType: 'LIMITED' | 'UNRESTRICTED' | null
  _hasHydrated: boolean
  setCurrentTenant: (tenant: TenantSummary | null) => void
  setTenants: (tenants: TenantSummary[]) => void
  setAccessType: (accessType: 'LIMITED' | 'UNRESTRICTED' | null) => void
  clearTenant: () => void
  setHasHydrated: (state: boolean) => void
}

export const useTenantStore = create<TenantState>()(
  persist(
    (set) => ({
      currentTenant: null,
      tenants: [],
      accessType: null,
      _hasHydrated: false,
      setCurrentTenant: (tenant) => {
        setTenantId(tenant?.id ?? null)
        set({ currentTenant: tenant })
      },
      setTenants: (tenants) => set({ tenants }),
      setAccessType: (accessType) => set({ accessType }),
      clearTenant: () => {
        setTenantId(null)
        set({ currentTenant: null, tenants: [], accessType: null })
      },
      setHasHydrated: (state) => set({ _hasHydrated: state }),
    }),
    {
      name: 'tenant-storage',
      partialize: (state) => ({
        currentTenant: state.currentTenant,
        tenants: state.tenants,
        accessType: state.accessType,
      }),
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true)
      },
    }
  )
)
