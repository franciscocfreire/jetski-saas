'use client'

import { useState } from 'react'
import { toast } from 'sonner'
import { Anchor, Eye, Loader2, User } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { reservasService } from '@/lib/api/services'

type Destino = 'MARINHA' | 'CLIENTE'

/**
 * Abre a prévia (sem enviar) do PDF que cada destino receberá, respeitando a
 * parametrização do tenant. Útil antes de emitir os documentos definitivos —
 * o PDF sai com marca d'água RASCUNHO enquanto houver pendências.
 */
export function DocumentoPreviewButtons({
  reservaId,
  className,
}: {
  reservaId: string
  className?: string
}) {
  const [carregando, setCarregando] = useState<Destino | null>(null)

  async function abrir(destino: Destino) {
    try {
      setCarregando(destino)
      const blob = await reservasService.previewDocumento(reservaId, destino)
      const url = URL.createObjectURL(blob)
      window.open(url, '_blank', 'noopener,noreferrer')
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
    } catch (e) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Não foi possível gerar a prévia.')
    } finally {
      setCarregando(null)
    }
  }

  return (
    <div className={className}>
      <p className="mb-2 flex items-center gap-1.5 text-xs text-muted-foreground">
        <Eye className="h-3.5 w-3.5" /> Prévia do documento (não envia; RASCUNHO enquanto há pendências)
      </p>
      <div className="grid gap-2 sm:grid-cols-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={carregando !== null}
          onClick={() => abrir('MARINHA')}
        >
          {carregando === 'MARINHA' ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          ) : (
            <Anchor className="mr-2 h-4 w-4" />
          )}
          Prévia Marinha
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={carregando !== null}
          onClick={() => abrir('CLIENTE')}
        >
          {carregando === 'CLIENTE' ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          ) : (
            <User className="mr-2 h-4 w-4" />
          )}
          Prévia Cliente
        </Button>
      </div>
    </div>
  )
}
