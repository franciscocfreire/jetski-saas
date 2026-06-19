import { apiClient, getTenantId } from '../client'
import type { DocumentoEmitido } from '../types'

const getBasePath = () => `/v1/tenants/${getTenantId()}/documentos`

export const documentosService = {
  /** Lista documentos emitidos; filtra por cliente quando clienteId é informado. */
  async list(clienteId?: string): Promise<DocumentoEmitido[]> {
    const { data } = await apiClient.get<DocumentoEmitido[]>(getBasePath(), {
      params: clienteId ? { clienteId } : undefined,
    })
    return data
  },

  /**
   * Baixa o PDF do documento emitido (streaming autenticado via Bearer).
   * Retorna o Blob + nome de arquivo extraído do Content-Disposition.
   */
  async download(id: string): Promise<{ blob: Blob; filename: string }> {
    const res = await apiClient.get(`${getBasePath()}/${id}/download`, {
      responseType: 'blob',
    })
    const disp = (res.headers['content-disposition'] as string | undefined) ?? ''
    const match = /filename="?([^"]+)"?/.exec(disp)
    const filename = match?.[1] ?? `documento-${id.slice(0, 8)}.pdf`
    return { blob: res.data as Blob, filename }
  },

  /** Reenvia por e-mail um documento já emitido (Marinha + cliente). */
  async reenviar(id: string): Promise<{ enviadoMarinha: boolean; enviadoCliente: boolean }> {
    const { data } = await apiClient.post(`${getBasePath()}/${id}/reenviar`)
    return data
  },
}
