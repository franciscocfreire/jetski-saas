'use client'

import { useEffect, useState } from 'react'

/**
 * URL do Portal do Cliente derivada do host atual: www.{dominio} → cliente.{dominio}.
 * Funciona em qualquer domínio (pegaojet/meujet/jetsave) sem env de build;
 * em localhost cai no portal dev (:3003).
 */
export function usePortalUrl(): string {
  const [url, setUrl] = useState('https://cliente.meujet.com.br')
  useEffect(() => {
    const host = window.location.host
    if (host.startsWith('localhost') || host.startsWith('127.')) {
      setUrl('http://localhost:3003')
      return
    }
    setUrl(`https://cliente.${host.replace(/^www\./, '')}`)
  }, [])
  return url
}

/**
 * URL do Portal da Empresa (backoffice) derivada do host atual:
 * www.{dominio} → app.{dominio}. Em localhost fica no próprio host
 * (login relativo), pois não há subdomínio local.
 */
export function useAppUrl(): string {
  const [url, setUrl] = useState('')
  useEffect(() => {
    const host = window.location.host
    if (host.startsWith('localhost') || host.startsWith('127.') || host.startsWith('app.')) {
      setUrl('') // já é o host do app (ou dev local) — usa caminho relativo
      return
    }
    setUrl(`https://app.${host.replace(/^www\./, '')}`)
  }, [])
  return url
}

/** Âncora para o portal (para uso em server components, ex.: footer). */
export function PortalLink({ className, children }: {
  className?: string
  children: React.ReactNode
}) {
  const url = usePortalUrl()
  return <a href={url} className={className}>{children}</a>
}
