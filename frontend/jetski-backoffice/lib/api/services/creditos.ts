import { apiClient, getTenantId } from '../client'
import type {
  CompraCreditos,
  CreditoLancamento,
  CreditosConfig,
  PlatformCompraCreditos,
  PlatformSaldoTenant,
  SaldoCreditos,
} from '../types'

/**
 * Créditos de emissão pré-pagos: 1 crédito por documento emitido à Marinha.
 * Ledger append-only; lançamentos do admin são auditados.
 */
export const creditosService = {
  async getSaldo(): Promise<SaldoCreditos> {
    const { data } = await apiClient.get<SaldoCreditos>(
      `/v1/tenants/${getTenantId()}/creditos/saldo`
    )
    return data
  },

  async getExtrato(limit = 10): Promise<CreditoLancamento[]> {
    const { data } = await apiClient.get<CreditoLancamento[]>(
      `/v1/tenants/${getTenantId()}/creditos/extrato`,
      { params: { limit } }
    )
    return data
  },

  /** Super admin: saldo de todas as empresas. */
  async getPlatformSaldos(): Promise<PlatformSaldoTenant[]> {
    const { data } = await apiClient.get<PlatformSaldoTenant[]>('/v1/platform/creditos')
    return data
  },

  /** Super admin: lança créditos (±) com motivo obrigatório (auditado). */
  async lancarCreditos(tenantId: string, quantidade: number, motivo: string): Promise<CreditoLancamento> {
    const { data } = await apiClient.post<CreditoLancamento>(
      `/v1/platform/creditos/${tenantId}`,
      { quantidade, motivo }
    )
    return data
  },

  // ===== Compra de créditos (PIX manual) =====

  async getConfig(): Promise<CreditosConfig> {
    const { data } = await apiClient.get<CreditosConfig>(
      `/v1/tenants/${getTenantId()}/creditos/config`
    )
    return data
  },

  /** Compra por QUANTIDADE: o valor a transferir (qtd × preço) é calculado no backend. */
  async solicitarCompra(quantidade: number, pixTxid: string): Promise<CompraCreditos> {
    const { data } = await apiClient.post<CompraCreditos>(
      `/v1/tenants/${getTenantId()}/creditos/compras`,
      { quantidade, pixTxid }
    )
    return data
  },

  /** Super admin: preço do crédito. */
  async getPlatformConfig(): Promise<{ precoUnitario: number }> {
    const { data } = await apiClient.get<{ precoUnitario: number }>('/v1/platform/creditos/config')
    return data
  },

  async atualizarPreco(precoUnitario: number): Promise<{ precoUnitario: number }> {
    const { data } = await apiClient.put<{ precoUnitario: number }>(
      '/v1/platform/creditos/config',
      { precoUnitario }
    )
    return data
  },

  async getCompras(limit = 10): Promise<CompraCreditos[]> {
    const { data } = await apiClient.get<CompraCreditos[]>(
      `/v1/tenants/${getTenantId()}/creditos/compras`,
      { params: { limit } }
    )
    return data
  },

  /** Super admin: fila de compras pendentes de todas as empresas. */
  async getComprasPendentes(): Promise<PlatformCompraCreditos[]> {
    const { data } = await apiClient.get<PlatformCompraCreditos[]>('/v1/platform/creditos/compras')
    return data
  },

  async aprovarCompra(tenantId: string, compraId: string): Promise<CompraCreditos> {
    const { data } = await apiClient.post<CompraCreditos>(
      `/v1/platform/creditos/compras/${tenantId}/${compraId}/aprovar`
    )
    return data
  },

  async rejeitarCompra(tenantId: string, compraId: string, observacao: string): Promise<CompraCreditos> {
    const { data } = await apiClient.post<CompraCreditos>(
      `/v1/platform/creditos/compras/${tenantId}/${compraId}/rejeitar`,
      { observacao }
    )
    return data
  },
}
