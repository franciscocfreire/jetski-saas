'use client'

import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { configuracoesService } from '@/lib/api/services'
import { useTenantStore } from '@/lib/store/tenant-store'

/** Tokens sobrescritos quando o tenant tem cor própria (fallback = Meu Jet no CSS). */
const PRIMARY_TOKENS = ['--primary', '--ring', '--sidebar-ring'] as const
const ACCENT_TOKENS = ['--sidebar-primary', '--gold'] as const

/**
 * White-label em runtime: aplica as cores do branding do tenant como CSS variables
 * no <html>. Sem branding (ou ao trocar de tenant/logout), remove as propriedades
 * inline e a identidade padrão Meu Jet do globals.css volta a valer.
 */
export function TenantThemeProvider({ children }: { children: React.ReactNode }) {
  const currentTenant = useTenantStore((s) => s.currentTenant)

  const { data: branding } = useQuery({
    queryKey: ['branding-config'],
    queryFn: () => configuracoesService.getBrandingConfig(),
    enabled: !!currentTenant,
    staleTime: 5 * 60 * 1000,
    retry: false,
  })

  useEffect(() => {
    const style = document.documentElement.style
    const clear = () => [...PRIMARY_TOKENS, ...ACCENT_TOKENS].forEach((t) => style.removeProperty(t))

    if (!currentTenant || !branding) {
      clear()
      return clear
    }
    if (branding.corPrimaria) {
      PRIMARY_TOKENS.forEach((t) => style.setProperty(t, branding.corPrimaria!))
    } else {
      PRIMARY_TOKENS.forEach((t) => style.removeProperty(t))
    }
    if (branding.corSecundaria) {
      ACCENT_TOKENS.forEach((t) => style.setProperty(t, branding.corSecundaria!))
    } else {
      ACCENT_TOKENS.forEach((t) => style.removeProperty(t))
    }
    return clear
  }, [branding, currentTenant])

  return <>{children}</>
}
