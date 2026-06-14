import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { TenantSummary } from '../api/types'
import { setTenantId } from '../api/client'

interface TenantState {
  currentTenant: TenantSummary | null
  tenants: TenantSummary[]
  _hasHydrated: boolean
  setCurrentTenant: (tenant: TenantSummary | null) => void
  setTenants: (tenants: TenantSummary[]) => void
  clearTenant: () => void
  setHasHydrated: (state: boolean) => void
}

export const useTenantStore = create<TenantState>()(
  persist(
    (set) => ({
      currentTenant: null,
      tenants: [],
      _hasHydrated: false,
      setCurrentTenant: (tenant) => {
        setTenantId(tenant?.id ?? null)
        set({ currentTenant: tenant })
      },
      setTenants: (tenants) => set({ tenants }),
      clearTenant: () => {
        setTenantId(null)
        set({ currentTenant: null, tenants: [] })
      },
      setHasHydrated: (state) => set({ _hasHydrated: state }),
    }),
    {
      name: 'tenant-storage',
      partialize: (state) => ({
        currentTenant: state.currentTenant,
        tenants: state.tenants,
      }),
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true)
      },
    }
  )
)
