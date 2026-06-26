import { apiClient, getTenantId } from '../client'
import type { Cliente, ClienteCreateRequest, ClientePreContaRequest } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/clientes`

export const clientesService = {
  // Backend returns a simple list, not a paginated response
  async list(params?: { includeInactive?: boolean }): Promise<Cliente[]> {
    const { data } = await apiClient.get<Cliente[]>(getBasePath(), { params })
    return data
  },

  async getById(id: string): Promise<Cliente> {
    const { data } = await apiClient.get<Cliente>(`${getBasePath()}/${id}`)
    return data
  },

  async create(request: ClienteCreateRequest): Promise<Cliente> {
    const { data } = await apiClient.post<Cliente>(getBasePath(), request)
    return data
  },

  async update(id: string, request: Partial<ClienteCreateRequest>): Promise<Cliente> {
    const { data } = await apiClient.put<Cliente>(`${getBasePath()}/${id}`, request)
    return data
  },

  /** Cria (ou reutiliza) a pré-conta de balcão; dedupe por CPF no backend. */
  async criarPreConta(request: ClientePreContaRequest): Promise<Cliente> {
    const { data } = await apiClient.post<Cliente>(`${getBasePath()}/pre-conta`, request)
    return data
  },

  /** Busca cliente por CPF (dedupe). Retorna o primeiro match ou null. */
  async buscarPorCpf(cpf: string): Promise<Cliente | null> {
    const { data } = await apiClient.get<Cliente[]>(getBasePath(), { params: { cpf } })
    return data.length > 0 ? data[0] : null
  },

  /** Consulta o nome do contribuinte por CPF na base da Marinha (pré-preenchimento). */
  async consultarNomeMarinha(cpf: string): Promise<string | null> {
    const { data } = await apiClient.get<{ nome: string | null }>(`${getBasePath()}/consulta-marinha`, {
      params: { cpf },
    })
    return data.nome ?? null
  },

  /** Envia um anexo do cliente (imagem em dataURL/base64) para incluir no PDF. */
  async uploadAnexo(
    clienteId: string,
    tipo: 'IDENTIDADE' | 'COMPROVANTE_RESIDENCIA' | 'SELFIE',
    conteudoBase64: string
  ): Promise<void> {
    await apiClient.put(`${getBasePath()}/${clienteId}/anexos/${tipo}`, { conteudoBase64 })
  },
}
