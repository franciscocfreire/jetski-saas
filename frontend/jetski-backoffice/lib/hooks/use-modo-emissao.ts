'use client'

import { useQuery } from '@tanstack/react-query'
import { emissaoDelegadaService } from '@/lib/api/services'
import { useTenantStore } from '@/lib/store/tenant-store'

/**
 * Modo de emissão da empresa (EMISSAO_DELEGADA_SPEC §8.M), decidido pelo backend:
 * operadora de parceria em vigor emite pela EAMA parceira qualquer que seja o plano
 * (um Trial com todos os módulos não cai na emissão própria).
 *
 * Enquanto a resposta não chega (ou se falhar), vale a regra do plano — a mesma que o
 * backend aplica a quem não tem parceria.
 */
export function useModoEmissao() {
  const { currentTenant } = useTenantStore()
  const query = useQuery({
    queryKey: ['modo-emissao', currentTenant?.id],
    queryFn: () => emissaoDelegadaService.modo(),
    enabled: !!currentTenant,
    staleTime: 60 * 1000,
    retry: 1,
  })
  const delegadaPeloPlano =
    !!currentTenant?.modulos && !currentTenant.modulos.includes('EMISSAO_PROPRIA')
  return {
    info: query.data,
    delegada: query.data ? query.data.modo === 'DELEGADA' : delegadaPeloPlano,
    carregando: !!currentTenant && query.isLoading,
  }
}
