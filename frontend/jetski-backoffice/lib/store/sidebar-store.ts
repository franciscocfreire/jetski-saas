import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface SidebarState {
  /** Grupos recolhidos por id (ex.: { financeiro: true }); ausente = expandido. */
  collapsed: Record<string, boolean>
  toggleGroup: (groupId: string) => void
}

export const useSidebarStore = create<SidebarState>()(
  persist(
    (set) => ({
      collapsed: {},
      toggleGroup: (groupId) =>
        set((state) => ({
          collapsed: { ...state.collapsed, [groupId]: !state.collapsed[groupId] },
        })),
    }),
    {
      name: 'sidebar-groups-storage',
    }
  )
)
