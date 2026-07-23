'use client'

import { useEffect, useState } from 'react'
import { Loader2, Receipt } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { useToast } from '@/hooks/use-toast'

/**
 * "Ver comprovante" de uma compra de créditos: busca o blob autenticado
 * (imagem ou PDF) e exibe — imagem abre num Dialog, PDF abre em nova aba
 * via object URL (o endpoint exige Authorization, não dá pra linkar direto).
 */
export function VerComprovanteButton({
  fetchBlob,
  size = 'sm',
}: {
  /** Busca o blob no endpoint certo (tenant ou platform). */
  fetchBlob: () => Promise<Blob>
  size?: 'sm' | 'default'
}) {
  const { toast } = useToast()
  const [loading, setLoading] = useState(false)
  const [imagemUrl, setImagemUrl] = useState<string | null>(null)

  // Revoga o object URL da imagem quando o dialog fecha / componente desmonta.
  useEffect(() => {
    return () => {
      if (imagemUrl) URL.revokeObjectURL(imagemUrl)
    }
  }, [imagemUrl])

  const abrir = async () => {
    setLoading(true)
    try {
      const blob = await fetchBlob()
      if (blob.type === 'application/pdf') {
        const url = URL.createObjectURL(blob)
        window.open(url, '_blank', 'noopener')
        // Dá tempo da aba carregar o PDF antes de revogar.
        setTimeout(() => URL.revokeObjectURL(url), 60_000)
      } else {
        setImagemUrl(URL.createObjectURL(blob))
      }
    } catch (e: unknown) {
      const err = e as { response?: { status?: number }; message?: string }
      toast({
        title: 'Erro ao abrir comprovante',
        description:
          err.response?.status === 404
            ? 'Comprovante não encontrado para esta compra.'
            : err.message || 'Não foi possível carregar o comprovante.',
        variant: 'destructive',
      })
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <Button
        type="button"
        variant="outline"
        size={size}
        onClick={abrir}
        disabled={loading}
        className="gap-1.5"
      >
        {loading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Receipt className="h-3.5 w-3.5" />}
        Ver comprovante
      </Button>

      <Dialog
        open={imagemUrl != null}
        onOpenChange={(open) => {
          if (!open && imagemUrl) {
            URL.revokeObjectURL(imagemUrl)
            setImagemUrl(null)
          }
        }}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>Comprovante de pagamento</DialogTitle>
          </DialogHeader>
          {imagemUrl && (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={imagemUrl}
              alt="Comprovante de pagamento"
              className="max-h-[75vh] w-full rounded-md object-contain"
            />
          )}
        </DialogContent>
      </Dialog>
    </>
  )
}
