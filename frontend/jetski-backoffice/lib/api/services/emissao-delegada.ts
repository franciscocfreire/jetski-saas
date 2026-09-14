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
  /** EAMA = instrutor da emissora; OPERADORA = instrutor próprio aprovado pela EAMA (V070). */
  origem?: 'EAMA' | 'OPERADORA'
  // Dados cadastrais (somente leitura na operadora — já vão impressos no Anexo 5-B-1)
  rg?: string | null
  orgaoEmissor?: string | null
  cpf?: string | null
  cha?: string | null
  /** yyyy-MM-dd */
  dataEmissao?: string | null
  temAssinatura?: boolean
  /** Link temporário (15 min) da assinatura; nulo sem assinatura. */
  assinaturaUrl?: string | null
}

/** Como a empresa emite hoje (§8.M): a parceria em vigor como operadora manda, não o plano. */
export type ModoEmissao = 'PROPRIA' | 'DELEGADA' | 'SEM_EMISSAO'

export interface ModoEmissaoInfo {
  modo: ModoEmissao
  /** Parceria viva como operadora (convite pendente, ativa ou bloqueada), se houver. */
  vinculoId: string | null
  vinculoStatus: VinculoEmissao['status'] | null
  emissoraNome: string | null
  planoPermitePropria: boolean
  planoPermiteDelegada: boolean
}

export type StatusAprovacaoInstrutor = 'PENDENTE' | 'APROVADO' | 'REJEITADO' | 'REMOVIDO'

/** Instrutor da operadora submetido à EAMA, com os dados que ela avalia (V070). */
export interface InstrutorOperadora {
  instrutorId: string
  nome: string
  rg: string | null
  orgaoEmissor: string | null
  cpf: string | null
  cha: string | null
  dataEmissao: string | null
  temAssinatura: boolean
  assinaturaUrl: string | null
  ativo: boolean
  status: StatusAprovacaoInstrutor
  solicitadoEm: string
  decididoEm: string | null
  motivo: string | null
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

  /** Modo de emissão da empresa (própria × delegada), decidido pelo backend. */
  async modo(): Promise<ModoEmissaoInfo> {
    const { data } = await apiClient.get<ModoEmissaoInfo>(`${vinculosPath()}/modo`)
    return data
  },

  /**
   * Instrutores para a emissão delegada: os da EAMA (designados) e os da operadora
   * aprovados pela EAMA — id, nome e origem.
   */
  async instrutoresParceiro(): Promise<InstrutorParceiro[]> {
    const { data } = await apiClient.get<InstrutorParceiro[]>(`${vinculosPath()}/instrutores-parceiro`)
    return data
  },

  /** Instrutores que a operadora submeteu à parceria, com o status da aprovação (V070). */
  async instrutoresOperadora(vinculoId: string): Promise<InstrutorOperadora[]> {
    const { data } = await apiClient.get<InstrutorOperadora[]>(
      `${vinculosPath()}/${vinculoId}/instrutores-operadora`)
    return data
  },

  /** A operadora pede à EAMA a aprovação de um instrutor próprio. */
  async solicitarAprovacao(instrutorId: string): Promise<{ status: StatusAprovacaoInstrutor }> {
    const { data } = await apiClient.post<{ status: StatusAprovacaoInstrutor }>(
      `${vinculosPath()}/instrutores-proprios/${instrutorId}/solicitar-aprovacao`)
    return data
  },

  /** A EAMA aprova, rejeita ou remove um instrutor da operadora. */
  async decidirInstrutor(
    vinculoId: string,
    instrutorId: string,
    decisao: 'APROVAR' | 'REJEITAR' | 'REMOVER',
    motivo?: string,
  ): Promise<{ status: StatusAprovacaoInstrutor }> {
    const { data } = await apiClient.post<{ status: StatusAprovacaoInstrutor }>(
      `${vinculosPath()}/${vinculoId}/instrutores-operadora/${instrutorId}/decisao`, { decisao, motivo })
    return data
  },

  /** Instrutores designados da parceria (V049); vazio = nenhum instrutor da EAMA. */
  async instrutoresDesignados(vinculoId: string): Promise<InstrutorParceiro[]> {
    const { data } = await apiClient.get<InstrutorParceiro[]>(
      `${vinculosPath()}/${vinculoId}/instrutores-designados`)
    return data
  },

  /** Substitui o conjunto de designados (só a EAMA; lista vazia = nenhum instrutor da EAMA). */
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
