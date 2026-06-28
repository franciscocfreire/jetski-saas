'use client'

import { useEffect, useState } from 'react'
import QRCode from 'qrcode'
import { cn } from '@/lib/utils'

/**
 * QR Code do PIX renderizado no cliente a partir do "copia-e-cola" (o BR Code EMV
 * já é o próprio payload do QR). Assim o QR persiste ao reabrir o atendimento —
 * a API só devolve o PNG no momento da geração, não no GET da habilitação.
 * Renderizar localmente também evita mandar o payload de pagamento a um serviço externo.
 */
export function PixQrCode({
  payload,
  size = 168,
  className,
}: {
  payload?: string
  size?: number
  className?: string
}) {
  const [dataUrl, setDataUrl] = useState<string | null>(null)

  useEffect(() => {
    if (!payload) {
      setDataUrl(null)
      return
    }
    let vivo = true
    QRCode.toDataURL(payload, { width: size, margin: 1 })
      .then((url) => vivo && setDataUrl(url))
      .catch(() => vivo && setDataUrl(null))
    return () => {
      vivo = false
    }
  }, [payload, size])

  if (!dataUrl) return null
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={dataUrl}
      alt="QR Code do PIX da GRU"
      width={size}
      height={size}
      className={cn('rounded bg-white p-1', className)}
    />
  )
}
