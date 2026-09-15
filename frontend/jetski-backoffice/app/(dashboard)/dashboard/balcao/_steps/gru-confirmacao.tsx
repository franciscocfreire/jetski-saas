'use client'

import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { CheckCircle2, Landmark, Mail } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { FileUpload } from '@/components/file-upload'
import { PixQrCode } from '@/components/pix-qrcode'
import { habilitacaoService } from '@/lib/api/services'

/**
 * Confirmação da GRU dentro do passo Emissão: quem chegou aqui com a GRU em aberto
 * (ex.: "Prosseguir sem confirmar a GRU" ou reserva retomada do portal) confere o
 * PIX, verifica o pagamento ou anexa o comprovante sem voltar à Habilitação.
 */
export function GruConfirmacao({ reservaId, onPago }: { reservaId: string; onPago: () => void }) {
  const qc = useQueryClient()
  const { data: hab, isLoading } = useQuery({
    queryKey: ['habilitacao', reservaId],
    queryFn: () => habilitacaoService.get(reservaId),
  })
  const [modoComprovante, setModoComprovante] = useState(false)
  const [comprovante, setComprovante] = useState<string | undefined>(undefined)

  // Estado do wizard pode estar defasado (GRU paga por outra tela): destrava a emissão.
  useEffect(() => {
    if (hab?.resolvida) onPago()
  }, [hab?.resolvida, onPago])

  function confirmado() {
    qc.invalidateQueries({ queryKey: ['habilitacao', reservaId] })
    onPago()
  }

  const verificar = useMutation({
    mutationFn: () => habilitacaoService.verificarPagamento(reservaId),
    onSuccess: (r) => {
      if (r.pago) {
        toast.success('Pagamento confirmado — GRU paga.')
        confirmado()
      } else if (r.situacao === 'EXPIRADO') {
        toast.warning('A sessão do PIX expirou. Gere um novo PIX na Habilitação ou anexe o comprovante.')
      } else {
        toast.info('Pagamento ainda não identificado. Tente de novo em instantes ou anexe o comprovante.')
      }
    },
    onError: () => toast.error('Falha ao verificar o pagamento.'),
  })

  const enviarComprovante = useMutation({
    mutationFn: () => habilitacaoService.registrarComprovante(reservaId, comprovante!),
    onSuccess: () => {
      toast.success('Comprovante registrado — GRU marcada como paga.')
      confirmado()
    },
    onError: () => toast.error('Falha ao registrar o comprovante.'),
  })

  const enviarEmail = useMutation({
    mutationFn: () => habilitacaoService.enviarEmailGru(reservaId),
    onSuccess: (enviado) =>
      enviado
        ? toast.success('GRU enviada ao e-mail do cliente.')
        : toast.warning('Não enviado: cliente sem e-mail ou GRU ainda não gerada.'),
    onError: () => toast.error('Falha ao enviar o e-mail da GRU.'),
  })

  if (isLoading) {
    return <p className="rounded-lg border p-4 text-sm text-muted-foreground">Carregando a GRU…</p>
  }

  if (!hab?.gruNumero) {
    return (
      <div className="rounded-lg border p-4 text-sm text-muted-foreground">
        A GRU ainda não foi gerada. Volte ao passo <strong>Habilitação</strong> para gerar o PIX ou o boleto.
      </div>
    )
  }

  if (hab.gruPago) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200">
        <CheckCircle2 className="h-4 w-4 shrink-0" /> GRU {hab.gruNumero} paga.
      </div>
    )
  }

  return (
    <div className="space-y-3 rounded-lg border p-4" data-testid="balcao-emissao-gru">
      <Label className="flex items-center gap-2 text-sm font-medium">
        <Landmark className="h-4 w-4" /> Confirmar o pagamento da GRU
      </Label>
      <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
        <span>
          GRU nº <strong>{hab.gruNumero}</strong>
        </span>
        {hab.gruValor != null && (
          <span>
            Valor: <strong>R$ {hab.gruValor.toFixed(2).replace('.', ',')}</strong>
          </span>
        )}
        {hab.gruPixExpiracao && (
          <span className="text-muted-foreground">
            PIX vence {new Date(hab.gruPixExpiracao).toLocaleString('pt-BR')}
          </span>
        )}
      </div>

      {hab.gruPixCopiaECola && (
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
          <PixQrCode payload={hab.gruPixCopiaECola} size={160} />
          <div className="flex min-w-0 flex-1 flex-col gap-2">
            <Input readOnly value={hab.gruPixCopiaECola} className="min-w-0 font-mono text-xs" />
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => {
                  navigator.clipboard.writeText(hab.gruPixCopiaECola!)
                  toast.success('PIX copia-e-cola copiado.')
                }}
              >
                Copiar
              </Button>
              <Button
                type="button"
                size="sm"
                variant="outline"
                disabled={enviarEmail.isPending}
                onClick={() => enviarEmail.mutate()}
              >
                <Mail className="mr-1 h-4 w-4" />
                {enviarEmail.isPending ? 'Enviando…' : 'Enviar ao cliente por e-mail'}
              </Button>
            </div>
          </div>
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        {hab.gruPixCopiaECola && (
          <Button
            data-testid="balcao-emissao-verificar-pix"
            type="button"
            variant="secondary"
            size="sm"
            disabled={verificar.isPending}
            onClick={() => verificar.mutate()}
          >
            {verificar.isPending ? 'Verificando…' : 'Verificar pagamento do PIX'}
          </Button>
        )}
        {!modoComprovante && (
          <Button type="button" variant="outline" size="sm" onClick={() => setModoComprovante(true)}>
            Paguei por outro meio / anexar comprovante
          </Button>
        )}
      </div>

      {modoComprovante && (
        <div className="space-y-2 rounded-md border border-dashed p-3">
          <Label className="text-xs">Comprovante de pagamento</Label>
          <FileUpload
            label="Enviar/tirar foto do comprovante"
            accept="image/*,application/pdf"
            tipoDocumento="GRU_COMPROVANTE"
            onChange={(f) => setComprovante(f?.dataUrl)}
          />
          <div className="flex gap-2">
            <Button
              type="button"
              size="sm"
              disabled={!comprovante || enviarComprovante.isPending}
              onClick={() => enviarComprovante.mutate()}
            >
              {enviarComprovante.isPending ? 'Enviando…' : 'Confirmar pagamento com comprovante'}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="ghost"
              onClick={() => {
                setModoComprovante(false)
                setComprovante(undefined)
              }}
            >
              Cancelar
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
