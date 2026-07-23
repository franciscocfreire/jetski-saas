import { apiClient } from '../client'
import type { UserProfile } from '../types'

/**
 * Service do perfil self-service do usuário staff (/v1/user/me).
 * Sem tenant: o escopo é sempre o próprio usuário do JWT.
 */
export const perfilService = {
  async getMe(): Promise<UserProfile> {
    const { data } = await apiClient.get<UserProfile>('/v1/user/me')
    return data
  },

  async updateMe(request: { nome: string; telefone?: string | null }): Promise<UserProfile> {
    const { data } = await apiClient.put<UserProfile>('/v1/user/me', request)
    return data
  },

  async updateSenha(request: { senhaAtual: string; novaSenha: string }): Promise<void> {
    await apiClient.put('/v1/user/me/senha', request)
  },

  async uploadAvatar(file: File): Promise<UserProfile> {
    const form = new FormData()
    form.append('file', file)
    const { data } = await apiClient.post<UserProfile>('/v1/user/me/avatar', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  async deleteAvatar(): Promise<UserProfile> {
    const { data } = await apiClient.delete<UserProfile>('/v1/user/me/avatar')
    return data
  },

  /** Fatores 2FA (TOTP/WebAuthn) cadastrados no Keycloak — somente leitura;
   *  cadastro/remoção via AIA (kc_action) no próprio Keycloak. */
  async getCredentials(): Promise<SecondFactorCredential[]> {
    const { data } = await apiClient.get<SecondFactorCredential[]>('/v1/user/me/credentials')
    return data
  },
}

export interface SecondFactorCredential {
  id: string
  type: 'otp' | 'webauthn' | 'webauthn-passwordless'
  userLabel?: string | null
  createdDate?: number | null
}
