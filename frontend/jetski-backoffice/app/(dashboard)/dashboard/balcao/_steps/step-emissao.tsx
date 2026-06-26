'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { FileDown, CheckCircle2, Anchor, Mail, Printer, AlertTriangle } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { reservasService, documentosService } from '@/lib/api/services'
import { EmbarqueSection } from './embarque'
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
  const bloqueado = !atendimento.habilitacaoResolvida || !atendimento.aceiteFeito

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

        <EmbarqueSection atendimento={atendimento} onReset={onReset} />
      </div>
    )
  }

  return (
    <div className="space-y-5">
      {bloqueado && (
        <div className="flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800 dark:bg-amber-950/30">
          <AlertTriangle className="mt-0.5 h-4 w-4" />
          <div>
            Para emitir é necessário:
            <ul className="ml-4 list-disc">
              <li className={atendimento.habilitacaoResolvida ? 'line-through' : ''}>habilitação resolvida</li>
              <li className={atendimento.aceiteFeito ? 'line-through' : ''}>termos assinados</li>
            </ul>
          </div>
        </div>
      )}

      <p className="text-sm text-muted-foreground">
        Gera o PDF consolidado (anexos + termo + assinatura), arquiva, envia à Marinha e ao
        cliente, e disponibiliza a GRU.
      </p>

      <div className="flex justify-between">
        <Button type="button" variant="outline" onClick={onBack}>
          Voltar
        </Button>
        <Button type="button" disabled={bloqueado || emitir.isPending} onClick={() => emitir.mutate()}>
          {emitir.isPending ? 'Emitindo…' : 'Emitir documentos'}
        </Button>
      </div>
    </div>
  )
}
