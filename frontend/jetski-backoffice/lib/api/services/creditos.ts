import { apiClient, getTenantId } from '../client'
import type { CreditoLancamento, PlatformSaldoTenant, SaldoCreditos } from '../types'

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
}
