import { apiClient, getTenantId } from '../client'
import type { Aceite, AceiteRequest } from '../types'

const path = (reservaId: string) =>
  `/v1/tenants/${getTenantId()}/reservas/${reservaId}/aceite`

export const aceiteService = {
  /** Grava o aceite/assinatura (a imagem PNG vai em base64 no corpo). */
  async registrar(reservaId: string, req: AceiteRequest): Promise<Aceite> {
    const { data } = await apiClient.post<Aceite>(path(reservaId), req)
    return data
  },

  /** Aceite atual (o mais recente) ou null. */
  async get(reservaId: string): Promise<Aceite | null> {
    try {
      const { data } = await apiClient.get<Aceite>(path(reservaId))
      return data
    } catch {
      return null
    }
  },

  /** OTP do aceite: está ativo p/ o tenant? qual canal? já verificado? */
  async otpStatus(reservaId: string): Promise<OtpStatus> {
    const { data } = await apiClient.get<OtpStatus>(`${path(reservaId)}/otp`)
    return data
  },

  /** Envia o código (e-mail) ou gera o link WhatsApp. */
  async otpEnviar(reservaId: string): Promise<OtpEnvio> {
    const { data } = await apiClient.post<OtpEnvio>(`${path(reservaId)}/otp/enviar`, {})
    return data
  },

  /** Verifica o código informado. */
  async otpVerificar(reservaId: string, codigo: string): Promise<boolean> {
    const { data } = await apiClient.post<{ verificado: boolean }>(
      `${path(reservaId)}/otp/verificar`,
      { codigo }
    )
    return !!data.verificado
  },
}

export interface OtpStatus {
  ativo: boolean
  canal: 'EMAIL' | 'WHATSAPP'
  verificado: boolean
}

export interface OtpEnvio {
  ativo: boolean
  canal: 'EMAIL' | 'WHATSAPP' | null
  destinoMascarado: string | null
  whatsappUrl: string | null
  mensagem: string | null
}
