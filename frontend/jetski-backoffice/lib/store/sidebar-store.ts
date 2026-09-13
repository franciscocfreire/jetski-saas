import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface SidebarState {
  /**
   * Estado por grupo. A ausência da chave significa RECOLHIDO: a barra abre
   * fechada e a pessoa escolhe o que quer ver, em vez de encarar a lista
   * inteira — com o texto em 150%/200% a lista aberta não cabia na tela.
   *
   * Só `false` explícito (grupo que a pessoa abriu) mantém aberto. Quem já
   * usava o sistema continua com as escolhas que fez; quem nunca mexeu passa
   * a ver tudo fechado.
   */
  collapsed: Record<string, boolean>
  /** Um grupo está aberto apenas se foi explicitamente aberto. */
  grupoAberto: (groupId: string) => boolean
  toggleGroup: (groupId: string) => void
}

export const useSidebarStore = create<SidebarState>()(
  persist(
    (set, get) => ({
      collapsed: {},
      grupoAberto: (groupId) => get().collapsed[groupId] === false,
      toggleGroup: (groupId) =>
        set((state) => ({
          // `?? true` fecha o buraco do primeiro clique: sem ele, o grupo nasce
          // sem chave, `!undefined` daria `true` (recolher) e o clique de abrir
          // não faria nada visível.
          collapsed: { ...state.collapsed, [groupId]: !(state.collapsed[groupId] ?? true) },
        })),
    }),
    {
      name: 'sidebar-groups-storage',
    }
  )
)
