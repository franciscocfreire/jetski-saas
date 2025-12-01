import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { TenantSummary } from '../api/types'
import { setTenantId } from '../api/client'

interface TenantState {
  currentTenant: TenantSummary | null
  tenants: TenantSummary[]
  setCurrentTenant: (tenant: TenantSummary | null) => void
  setTenants: (tenants: TenantSummary[]) => void
  clearTenant: () => void
}

export const useTenantStore = create<TenantState>()(
  persist(
    (set) => ({
      currentTenant: null,
      tenants: [],
      setCurrentTenant: (tenant) => {
        setTenantId(tenant?.id ?? null)
        set({ currentTenant: tenant })
      },
      setTenants: (tenants) => set({ tenants }),
      clearTenant: () => {
        setTenantId(null)
        set({ currentTenant: null, tenants: [] })
      },
    }),
    {
      name: 'tenant-storage',
      partialize: (state) => ({
        currentTenant: state.currentTenant,
        tenants: state.tenants,
      }),
    }
  )
)
