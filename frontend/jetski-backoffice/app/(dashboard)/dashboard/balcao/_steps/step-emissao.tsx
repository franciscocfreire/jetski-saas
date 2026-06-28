'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { FileDown, CheckCircle2, Anchor, Mail, Printer, AlertTriangle, Ship } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { DocumentoPreviewButtons } from '@/components/documento-preview-buttons'
import { reservasService, documentosService } from '@/lib/api/services'
import type { Atendimento } from '../types'
import type { ResultadoEmissao } from '@/lib/api/types'

export function StepEmissao({
  atendimento,
  onBack,
  onReset,
}: {
  atendimento: Atendimento
  onBack: () => void
  onReset: () => void
}) {
  const [resultado, setResultado] = useState<ResultadoEmissao | null>(null)
  const [baixando, setBaixando] = useState(false)

  async function abrirPdf(documentoId: string) {
    try {
      setBaixando(true)
      const { blob, filename } = await documentosService.download(documentoId)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.target = '_blank'
      a.rel = 'noopener noreferrer'
      a.download = filename
      document.body.appendChild(a)
      a.click()
      a.remove()
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
    } catch (e) {
      console.error('[emissao] download falhou', e)
      toast.error('Não foi possível abrir o PDF.')
    } finally {
      setBaixando(false)
    }
  }

  const emitir = useMutation({
    mutationFn: () => reservasService.emitirDocumentos(atendimento.reserva!.id),
    onSuccess: (r) => {
      setResultado(r)
      toast.success('Documentos emitidos.')
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha ao emitir documentos.')
    },
  })

  if (resultado) {
    return (
      <div className="space-y-5">
        <div className="flex items-center gap-2 text-emerald-600">
          <CheckCircle2 className="h-6 w-6" />
          <span className="text-lg font-semibold">Documentos emitidos</span>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <Button
            type="button"
            className="w-full"
            disabled={baixando}
            onClick={() => abrirPdf(resultado.documentoId)}
          >
            <FileDown size={16} className="mr-2" /> {baixando ? 'Abrindo…' : 'Abrir / Baixar PDF'}
          </Button>
          {resultado.gruNumero && (
            <Button type="button" variant="outline" onClick={() => window.print()}>
              <Printer size={16} className="mr-2" /> Imprimir GRU {resultado.gruNumero}
            </Button>
          )}
        </div>

        <div className="space-y-2 rounded-lg border p-4 text-sm">
          {resultado.docCompleta ? (
            <p className="flex items-center gap-2">
              <Anchor size={15} className={resultado.enviadoMarinha ? 'text-emerald-600' : 'text-muted-foreground'} />
              {resultado.enviadoMarinha ? '✓ Enviado à Marinha' : 'Não enviado à Marinha (sem e-mail configurado)'}
            </p>
          ) : (
            <div className="rounded-md border border-amber-300 bg-amber-50 p-2 text-amber-800 dark:bg-amber-950/30">
              <p className="flex items-center gap-2 font-medium">
                <Anchor size={15} /> Marinha não notificada — documentação incompleta
              </p>
              <ul className="ml-6 mt-1 list-disc text-xs">
                {resultado.pendencias.map((p) => (
                  <li key={p}>{p}</li>
                ))}
              </ul>
              <p className="mt-1 text-xs">
                A reserva fica pendente e na fila; complete os itens e reenvie à Marinha.
              </p>
            </div>
          )}
          <p className="flex items-center gap-2">
            <Mail size={15} className={resultado.enviadoCliente ? 'text-emerald-600' : 'text-muted-foreground'} />
            {resultado.enviadoCliente ? '✓ E-mail ao cliente' : 'Não enviado ao cliente (sem e-mail)'}
          </p>
          {resultado.gruValor && (
            <p className="text-muted-foreground">GRU {resultado.gruNumero} — R$ {resultado.gruValor}</p>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-4">
          <p className="flex items-center gap-2 text-sm text-muted-foreground">
            <Ship size={16} className="text-primary" />
            A reserva entrou na <strong className="text-foreground">fila de espera</strong>. O embarque
            (check-in) é feito na Fila quando o jetski do modelo estiver livre.
          </p>
          <Button type="button" onClick={onReset}>
            Concluir atendimento
          </Button>
        </div>
      </div>
    )
  }

  // Sem termos assinados não chega aqui no fluxo; guarda mesmo assim.
  if (!atendimento.aceiteFeito) {
    return (
      <div className="space-y-5">
        <div className="flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800 dark:bg-amber-950/30">
          <AlertTriangle className="mt-0.5 h-4 w-4" /> Assine os termos antes de concluir.
        </div>
        <Button type="button" variant="outline" onClick={onBack}>Voltar</Button>
      </div>
    )
  }

  // Termos OK + GRU NÃO paga → a reserva já vale (está na fila); a emissão fica para depois.
  if (!atendimento.habilitacaoResolvida) {
    return (
      <div className="space-y-5">
        <div className="space-y-1 rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-800 dark:bg-amber-950/30">
          <p className="flex items-center gap-2 font-medium">
            <Ship className="h-4 w-4" /> Reserva confirmada — entrou na fila de espera
          </p>
          <p>
            A <strong>GRU ainda não está paga</strong>, então os documentos NÃO podem ser emitidos
            agora. A reserva já vale e pode embarcar normalmente. Pague a GRU (PIX/boleto ou
            comprovante) e <strong>emita os documentos depois</strong> em Pendências → Retomar.
          </p>
        </div>
        {atendimento.reserva && (
          <DocumentoPreviewButtons reservaId={atendimento.reserva.id} className="rounded-lg border p-4" />
        )}
        <div className="flex justify-between">
          <Button type="button" variant="outline" onClick={onBack}>Voltar</Button>
          <Button type="button" onClick={onReset}>Concluir atendimento</Button>
        </div>
      </div>
    )
  }

  // GRU paga + termos → pode emitir agora.
  return (
    <div className="space-y-5">
      <p className="text-sm text-muted-foreground">
        Gera o PDF consolidado (anexos + termo + assinatura), arquiva, envia à Marinha e ao
        cliente, e disponibiliza a GRU.
      </p>
      {atendimento.reserva && (
        <DocumentoPreviewButtons reservaId={atendimento.reserva.id} className="rounded-lg border p-4" />
      )}
      <div className="flex justify-between">
        <Button type="button" variant="outline" onClick={onBack}>
          Voltar
        </Button>
        <Button type="button" disabled={emitir.isPending} onClick={() => emitir.mutate()}>
          {emitir.isPending ? 'Emitindo…' : 'Emitir documentos'}
        </Button>
      </div>
    </div>
  )
}
