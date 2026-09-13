'use client'

import { useQuery } from '@tanstack/react-query'
import { configuracoesService } from '@/lib/api/services'
import type { OficioMarinhaAviso, OficioMarinhaPreviewRequest } from '@/lib/api/types'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import { AlertCircle, AlertTriangle, CheckCircle2, FileText, Paperclip } from 'lucide-react'
import { cn } from '@/lib/utils'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** O que está digitado na tela (mesmo sem salvar) — o backend aplica por cima do gravado. */
  dados: OficioMarinhaPreviewRequest
}

/**
 * Pré-visualização do e-mail (ofício NORMAM-212 5.4.2) que a Capitania recebe na emissão.
 *
 * Desenho: um "cliente de e-mail" — envelope (De/Para/Responder-para/Assunto/Anexo) em cima,
 * corpo em folha branca embaixo (e-mail é renderizado em fundo claro pelo destinatário,
 * independentemente do tema do backoffice). As pendências ficam ANTES do envelope porque
 * são o motivo de o operador abrir a prévia: ver o que falta antes da primeira emissão real.
 */
export function OficioMarinhaPreviewDialog({ open, onOpenChange, dados }: Props) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['oficio-marinha-preview', dados],
    queryFn: () => configuracoesService.previewOficioMarinha(dados),
    enabled: open,
    staleTime: 0,
    gcTime: 0,
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[90vh] max-w-3xl flex-col gap-0 p-0">
        <DialogHeader className="border-b px-6 py-4">
          <DialogTitle className="flex items-center gap-2">
            <FileText className="h-5 w-5" />
            Pré-visualização do e-mail à Capitania
          </DialogTitle>
          <DialogDescription>
            É assim que o ofício de solicitação da CHA-MTA-E sai na emissão. Exemplo com um
            locatário fictício; reflete o que está digitado na tela, mesmo antes de salvar.
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 space-y-4 overflow-y-auto px-6 py-4">
          {isLoading && (
            <div className="space-y-3">
              <Skeleton className="h-24 w-full" />
              <Skeleton className="h-32 w-full" />
              <Skeleton className="h-64 w-full" />
            </div>
          )}

          {error && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>Não foi possível montar a prévia</AlertTitle>
              <AlertDescription>
                {error instanceof Error ? error.message : 'Erro inesperado. Tente novamente.'}
              </AlertDescription>
            </Alert>
          )}

          {data && (
            <>
              <Pendencias avisos={data.avisos} bloqueado={data.bloqueado} />

              {/* Envelope */}
              <div className="rounded-lg border bg-muted/30 text-sm">
                <Linha rotulo="De">
                  <span className="break-all">{data.de}</span>
                  <Badge variant={data.deOrigem === 'SMTP_PROPRIO' ? 'secondary' : 'outline'}>
                    {data.deOrigem === 'SMTP_PROPRIO' ? 'SMTP próprio' : 'remetente da plataforma'}
                  </Badge>
                </Linha>
                <Linha rotulo="Para">
                  {data.para ? (
                    <span className="break-all">{data.para}</span>
                  ) : (
                    <Badge variant="destructive">não configurado</Badge>
                  )}
                </Linha>
                <Linha rotulo="Responder para">
                  {data.responderPara ? (
                    <span className="break-all">{data.responderPara}</span>
                  ) : (
                    <span className="text-muted-foreground">— (sem e-mail oficial)</span>
                  )}
                </Linha>
                <Linha rotulo="Assunto">
                  <span className="font-medium">{data.assunto}</span>
                </Linha>
                <Linha rotulo="Anexo" ultima>
                  <span className="inline-flex max-w-full items-center gap-1.5 rounded-md border bg-background px-2 py-1 text-xs">
                    <Paperclip className="h-3.5 w-3.5 shrink-0" />
                    <span className="truncate">{data.nomeAnexo}</span>
                    <span className="shrink-0 text-muted-foreground">· PDF consolidado</span>
                  </span>
                </Linha>
              </div>

              {/* Corpo — folha branca, como o destinatário lê */}
              <div
                className={cn(
                  'rounded-lg border bg-white px-5 py-4 font-sans text-[14px] leading-relaxed text-neutral-900 shadow-sm',
                  '[&_p]:my-3 [&_ul]:my-2 [&_ul]:list-disc [&_ul]:pl-6 [&_li]:my-1',
                  '[&_code]:break-all [&_code]:rounded [&_code]:bg-neutral-100 [&_code]:px-1 [&_code]:text-[11px]'
                )}
                // HTML gerado pelo backend a partir do template (valores escapados no servidor).
                dangerouslySetInnerHTML={{ __html: data.corpoHtml }}
              />
            </>
          )}
        </div>

        <DialogFooter className="border-t px-6 py-3 sm:justify-between">
          <p className="text-xs text-muted-foreground">
            Nada é enviado nem gravado por esta prévia. O e-mail real sai na emissão dos documentos.
          </p>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function Linha({
  rotulo,
  ultima,
  children,
}: {
  rotulo: string
  ultima?: boolean
  children: React.ReactNode
}) {
  return (
    <div
      className={cn(
        'grid grid-cols-[7.5rem_1fr] items-start gap-x-3 px-4 py-2',
        !ultima && 'border-b'
      )}
    >
      <span className="pt-0.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {rotulo}
      </span>
      <div className="flex min-w-0 flex-wrap items-center gap-2">{children}</div>
    </div>
  )
}

function Pendencias({ avisos, bloqueado }: { avisos: OficioMarinhaAviso[]; bloqueado: boolean }) {
  if (avisos.length === 0) {
    return (
      <Alert className="border-emerald-500/40 text-emerald-700 dark:text-emerald-400 [&>svg]:text-emerald-600">
        <CheckCircle2 className="h-4 w-4" />
        <AlertTitle>Pronto para a Capitania</AlertTitle>
        <AlertDescription>
          Destinatário, remetente e assinatura do EAMA completos. Nenhuma pendência.
        </AlertDescription>
      </Alert>
    )
  }
  return (
    <Alert variant={bloqueado ? 'destructive' : 'default'}>
      {bloqueado ? <AlertCircle className="h-4 w-4" /> : <AlertTriangle className="h-4 w-4" />}
      <AlertTitle>
        {bloqueado
          ? 'Com estes dados o e-mail NÃO seria enviado'
          : `${avisos.length === 1 ? '1 pendência' : `${avisos.length} pendências`} na assinatura/remetente`}
      </AlertTitle>
      <AlertDescription>
        <ul className="mt-1 list-disc space-y-1 pl-4">
          {avisos.map((a) => (
            <li key={a.campo + a.mensagem}>
              {a.nivel === 'ERRO' && (
                <Badge variant="destructive" className="mr-1.5 align-middle">
                  bloqueia
                </Badge>
              )}
              {a.mensagem}
            </li>
          ))}
        </ul>
      </AlertDescription>
    </Alert>
  )
}
