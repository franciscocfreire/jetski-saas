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
  /**
   * Sem escolha registrada, quem manda é a página atual: o grupo que contém a
   * rota aberta se expande sozinho e os outros ficam fechados. Uma escolha
   * explícita (clique no título) vence a automação, nos dois sentidos.
   */
  grupoAberto: (groupId: string, contemPaginaAtual: boolean) => boolean
  toggleGroup: (groupId: string) => void
}

export const useSidebarStore = create<SidebarState>()(
  persist(
    (set, get) => ({
      collapsed: {},
      grupoAberto: (groupId, contemPaginaAtual) => {
        const escolha = get().collapsed[groupId]
        return escolha === undefined ? contemPaginaAtual : escolha === false
      },
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
      // O mapa gravado antes desta versão foi produzido com a regra antiga
      // ("ausente = aberto"). Mantê-lo deixaria grupos marcados como fechados
      // por um clique de outra época impedindo a abertura automática. Zerar uma
      // única vez é mais previsível que adivinhar a intenção de cada entrada.
      version: 1,
      migrate: () => ({ collapsed: {} }),
    }
  )
)
