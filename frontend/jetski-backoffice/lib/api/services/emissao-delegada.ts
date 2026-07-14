import { apiClient, getTenantId } from '../client'

// Emissão delegada (V048): parceria operadora × EAMA emissora + painel do emissor

export interface VinculoEmissao {
  id: string
  /** Papel do MEU tenant no vínculo. */
  papel: 'OPERADORA' | 'EMISSORA'
  parceiroTenantId: string
  parceiroNome: string | null
  status: 'CONVIDADO' | 'ATIVO' | 'BLOQUEADO' | 'REVOGADO'
  aguardandoMeuAceite: boolean
  convidadoEm: string
  aceitoEm?: string | null
  bloqueadoEm?: string | null
  revogadoEm?: string | null
  termoTexto?: string | null
}

export interface InstrutorParceiro {
  id: string
  nome: string
}

export interface EmissaoDelegada {
  id: string
  operadoraTenantId: string
  operadoraNome: string | null
  condutorNome: string | null
  condutorCpf: string | null
  instrutorNome: string | null
  gruNumero: string | null
  documentoHash: string | null
  emitidoEm: string
  reenviadoEm?: string | null
  reenviadoPara?: string | null
}

export interface ContagemDelegada {
  operadoraTenantId: string
  operadoraNome: string | null
  mes: string
  total: number
}

export interface Capitania {
  id: string
  codigo: string
  nome: string
  uf: string | null
  emailOficial: string | null
  ativa: boolean
}

export interface PerfilEmissora {
  capitaniaId: string | null
  capitaniaCodigo: string | null
  capitaniaNome: string | null
  eamaRegistro: string | null
  eamaRegistroValidade: string | null
  emissoraHabilitada: boolean
}

const vinculosPath = () => `/v1/tenants/${getTenantId()}/vinculos-emissao`
const delegadasPath = () => `/v1/tenants/${getTenantId()}/emissoes-delegadas`

export const emissaoDelegadaService = {
  // ===== perfil de emissão (capitania + registro EAMA, V047) =====

  async capitanias(): Promise<Capitania[]> {
    const { data } = await apiClient.get<Capitania[]>('/v1/capitanias')
    return data
  },

  async perfilEmissora(): Promise<PerfilEmissora> {
    const { data } = await apiClient.get<PerfilEmissora>(
      `/v1/tenants/${getTenantId()}/config/emissora`)
    return data
  },

  /** Alterar capitania/registro com a empresa já habilitada derruba a habilitação. */
  async salvarPerfilEmissora(req: {
    capitaniaId?: string
    eamaRegistro?: string
    eamaRegistroValidade?: string
  }): Promise<PerfilEmissora> {
    const { data } = await apiClient.put<PerfilEmissora>(
      `/v1/tenants/${getTenantId()}/config/emissora`, req)
    return data
  },

  async listVinculos(): Promise<VinculoEmissao[]> {
    const { data } = await apiClient.get<VinculoEmissao[]>(vinculosPath())
    return data
  },

  async termo(): Promise<string> {
    const { data } = await apiClient.get<{ termo: string }>(`${vinculosPath()}/termo`)
    return data.termo
  },

  async convidar(parceiroSlug: string, papel: 'OPERADORA' | 'EMISSORA'): Promise<VinculoEmissao> {
    const { data } = await apiClient.post<VinculoEmissao>(vinculosPath(), { parceiroSlug, papel })
    return data
  },

  async aceitar(id: string): Promise<VinculoEmissao> {
    const { data } = await apiClient.post<VinculoEmissao>(`${vinculosPath()}/${id}/aceitar`, {
      termoAceito: true,
    })
    return data
  },

  async bloquear(id: string): Promise<VinculoEmissao> {
    const { data } = await apiClient.post<VinculoEmissao>(`${vinculosPath()}/${id}/bloquear`)
    return data
  },

  async liberar(id: string): Promise<VinculoEmissao> {
    const { data } = await apiClient.post<VinculoEmissao>(`${vinculosPath()}/${id}/liberar`)
    return data
  },

  async revogar(id: string): Promise<VinculoEmissao> {
    const { data } = await apiClient.post<VinculoEmissao>(`${vinculosPath()}/${id}/revogar`)
    return data
  },

  /** Instrutores da EAMA parceira (id + nome) para a emissão delegada. */
  async instrutoresParceiro(): Promise<InstrutorParceiro[]> {
    const { data } = await apiClient.get<InstrutorParceiro[]>(`${vinculosPath()}/instrutores-parceiro`)
    return data
  },

  /** Instrutores designados da parceria (V049); vazio = todos os ativos da EAMA. */
  async instrutoresDesignados(vinculoId: string): Promise<InstrutorParceiro[]> {
    const { data } = await apiClient.get<InstrutorParceiro[]>(
      `${vinculosPath()}/${vinculoId}/instrutores-designados`)
    return data
  },

  /** Substitui o conjunto de designados (só a EAMA; lista vazia = todos os ativos). */
  async designarInstrutores(vinculoId: string, instrutorIds: string[]): Promise<InstrutorParceiro[]> {
    const { data } = await apiClient.put<InstrutorParceiro[]>(
      `${vinculosPath()}/${vinculoId}/instrutores-designados`, { instrutorIds })
    return data
  },

  // ===== painel da EAMA emissora =====

  async listEmissoes(operadoraId?: string): Promise<EmissaoDelegada[]> {
    const { data } = await apiClient.get<EmissaoDelegada[]>(delegadasPath(), {
      params: operadoraId ? { operadoraId } : undefined,
    })
    return data
  },

  async contagens(): Promise<ContagemDelegada[]> {
    const { data } = await apiClient.get<ContagemDelegada[]>(`${delegadasPath()}/contagens`)
    return data
  },

  async downloadUrl(id: string): Promise<string> {
    const { data } = await apiClient.get<{ url: string }>(`${delegadasPath()}/${id}/download`)
    return data.url
  },

  /** Reenvia o mesmo PDF à Capitania (sem re-emissão, sem crédito novo). */
  async reenviar(id: string, destino?: string): Promise<EmissaoDelegada> {
    const { data } = await apiClient.post<EmissaoDelegada>(
      `${delegadasPath()}/${id}/reenviar`, destino ? { destino } : {})
    return data
  },
}
