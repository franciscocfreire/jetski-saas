'use client'

import { useQuery } from '@tanstack/react-query'
import { useTenantStore } from '@/lib/store/tenant-store'
import { userTenantsService } from '@/lib/api/services/user-tenants'

/** Módulo de plano (V063) que LIBERA a empresa para desligar a videoaula obrigatória. */
export const MODULO_VIDEO_ORIENTACAO = 'VIDEO_ORIENTACAO'

/** Chave da query do summary fresco — invalidada ao salvar Configurações › Documentos. */
export const USER_TENANTS_QUERY_KEY = ['user-tenants-summary'] as const

/**
 * Regra efetiva da videoaula obrigatória no balcão (via EMA).
 *
 * A fórmula mora no backend (`DocumentoConfig.videoaulaExigida`) e chega pronta em
 * `TenantSummary.videoaulaObrigatoria` (GET /v1/user/tenants) — o OPERADOR, que
 * atende o balcão, não lê /config/documento.
 *
 * Lemos o summary FRESCO (react-query) e não só o store: o store é hidratado no
 * boot do layout, e quem desliga o toggle em Configurações e navega para o
 * Balcão sem recarregar ficava com o valor antigo. Sem valor (erro/carregando)
 * cai no store e, por fim, em `true`: a direção segura é exibir o vídeo.
 *
 * `moduloPermiteDesativar` é o gate do toggle em Configurações › Documentos.
 */
export function useVideoaulaObrigatoria() {
  const { currentTenant } = useTenantStore()
  const tenantId = currentTenant?.id
  const { data: fresh } = useQuery({
    queryKey: [...USER_TENANTS_QUERY_KEY, tenantId],
    queryFn: async () => {
      const r = await userTenantsService.getMyTenants()
      return r.tenants?.find((t) => t.id === tenantId) ?? null
    },
    enabled: !!tenantId,
    staleTime: 60 * 1000,
    retry: 1,
  })
  const summary = fresh ?? currentTenant
  const modulos = summary?.modulos
  const moduloPermiteDesativar = !modulos || modulos.includes(MODULO_VIDEO_ORIENTACAO)
  const obrigatoria = !moduloPermiteDesativar || (summary?.videoaulaObrigatoria ?? true)
  return { obrigatoria, moduloPermiteDesativar }
}
