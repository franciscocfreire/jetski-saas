'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { CheckCircle2, PlayCircle, ShieldAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { VideoaulaPlayer } from '@/components/videoaula-player'
import { habilitacaoService } from '@/lib/api/services'
import type { Habilitacao, VideoaulaIdioma, VideoaulaModo } from '@/lib/api/types'
import { VIDEOAULAS, rotuloIdioma, videoaulaUrl } from '@/lib/videoaulas'
import { formatDateTime } from '@/lib/utils'
import { useVideoaulaObrigatoria } from '@/lib/hooks/use-videoaula-obrigatoria'
import type { Atendimento } from '../types'

export type VideoaulaPatch = Pick<Atendimento, 'videoaulaEm' | 'videoaulaModo' | 'videoaulaIdioma'>

/**
 * Passo "Orientações" (só via EMA): a videoaula oficial da Marinha toca num player
 * integrado — sempre. Com a obrigatoriedade ligada (padrão; desligável só com o
 * módulo VIDEO_ORIENTACAO), "Continuar" só libera depois do término (evento do
 * player) E do checkbox do operador; desligada, o checkbox libera na hora e o
 * registro sai como DECLARACAO (ou PLAYER, se o vídeo chegou ao fim).
 *
 * O registro (PUT habilitação, modo PLAYER) dispara no término — o fato auditável
 * fica gravado mesmo que o operador dê F5 antes de clicar. Se o YouTube não
 * carregar, cai num fallback auditável (links + declaração manual = DECLARACAO).
 */
export function StepOrientacoes({
  atendimento,
  onBack,
  onDone,
}: {
  atendimento: Atendimento
  onBack: () => void
  onDone: (patch: VideoaulaPatch) => void
}) {
  const reservaId = atendimento.reserva!.id
  const queryClient = useQueryClient()
  const { obrigatoria } = useVideoaulaObrigatoria()
  const [concluido, setConcluido] = useState(false)
  const [confirmado, setConfirmado] = useState(false)
  const [idioma, setIdioma] = useState<VideoaulaIdioma>('pt')
  const [playerErro, setPlayerErro] = useState<string | null>(null)
  const [reassistir, setReassistir] = useState(false)
  const [salvo, setSalvo] = useState<Habilitacao | null>(null)

  // Habilitação salva (retomada / voltar pelo breadcrumb) — mesma chave do Termos.
  const { data: hab } = useQuery({
    queryKey: ['habilitacao', reservaId],
    queryFn: () => habilitacaoService.get(reservaId),
  })
  const videoaulaEm = salvo?.videoaulaEm ?? atendimento.videoaulaEm ?? hab?.videoaulaEm
  const videoaulaModo = salvo?.videoaulaModo ?? atendimento.videoaulaModo ?? hab?.videoaulaModo
  const videoaulaIdioma = salvo?.videoaulaIdioma ?? atendimento.videoaulaIdioma ?? hab?.videoaulaIdioma
  // "Já assistida" = estado PRÉVIO (retomada / voltar pelo breadcrumb). O término
  // nesta sessão (`concluido`) fica no player + checkbox: o registro que volta do
  // servidor não pode pular a confirmação do operador.
  const jaAssistida = !!videoaulaEm && !reassistir && !concluido && !playerErro

  const registrar = useMutation({
    mutationFn: (modo: VideoaulaModo) =>
      habilitacaoService.registrar(reservaId, {
        via: 'EMA',
        videoaulaAssistida: true,
        videoaulaModo: modo,
        videoaulaIdioma: idioma,
      }),
    onSuccess: (h) => {
      setSalvo(h)
      queryClient.invalidateQueries({ queryKey: ['habilitacao', reservaId] })
    },
    onError: () => toast.error('Falha ao registrar a videoaula. Tente novamente.'),
  })

  const patch = (h: Habilitacao | null): VideoaulaPatch => ({
    videoaulaEm: h?.videoaulaEm ?? videoaulaEm,
    videoaulaModo: h?.videoaulaModo ?? videoaulaModo,
    videoaulaIdioma: h?.videoaulaIdioma ?? videoaulaIdioma,
  })

  const modoFallback = !!playerErro
  // Checkbox libera com o término; sem obrigatoriedade (ou no fallback) libera na hora.
  const podeConfirmar = concluido || modoFallback || !obrigatoria
  const podeContinuar = jaAssistida || (podeConfirmar && confirmado)

  function continuar() {
    if (jaAssistida || salvo) {
      onDone(patch(salvo))
      return
    }
    // Player concluiu mas o registro falhou/ainda não voltou, fallback manual ou
    // videoaula não obrigatória até o fim: registra (ou re-tenta) e só então avança.
    registrar.mutate(concluido ? 'PLAYER' : 'DECLARACAO', {
      onSuccess: (h) => onDone(patch(h)),
    })
  }

  return (
    <div className="space-y-5">
      <div>
        <h3 className="flex items-center gap-2 text-base font-semibold">
          <PlayCircle className="h-5 w-5 text-primary" /> Videoaula de orientação — Marinha do Brasil
        </h3>
        <p className="text-sm text-muted-foreground">
          Videoaula oficial para a emissão da CHA-MTA-E (NORMAM-212).{' '}
          {obrigatoria ? (
            <>
              O botão <strong>Continuar</strong> libera ao final do vídeo, após a confirmação do operador.
            </>
          ) : (
            <>
              Nesta empresa não é obrigatório assistir até o final — confirme com o locatário para
              continuar.
            </>
          )}
        </p>
      </div>

      {jaAssistida ? (
        <div className="flex items-start justify-between gap-3 rounded-lg border border-emerald-500/40 bg-emerald-500/5 p-4">
          <div className="flex items-start gap-2 text-sm">
            <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-emerald-600" />
            <div>
              <p className="font-medium">Videoaula assistida em {formatDateTime(videoaulaEm!)}</p>
              <p className="text-xs text-muted-foreground">
                {videoaulaModo === 'PLAYER' ? 'Player integrado' : 'Declaração manual'}
                {videoaulaIdioma && <> · {rotuloIdioma(videoaulaIdioma)}</>}
              </p>
            </div>
          </div>
          <Button type="button" variant="ghost" size="sm" onClick={() => setReassistir(true)}>
            Assistir novamente
          </Button>
        </div>
      ) : (
        <>
          <VideoaulaPlayer
            idiomaInicial={idioma}
            onConcluido={(id) => {
              setIdioma(id)
              setConcluido(true)
              registrar.mutate('PLAYER')
            }}
            onErro={(motivo) => setPlayerErro(motivo)}
          />

          {modoFallback && (
            <Alert>
              <ShieldAlert className="h-4 w-4" />
              <AlertDescription className="space-y-2">
                <p>
                  Não foi possível carregar o player ({playerErro}). Abra a videoaula pelo link e
                  registre manualmente — o registro fica auditado como <strong>declaração</strong>.
                </p>
                <p className="text-xs">
                  {VIDEOAULAS.map((v, i) => (
                    <span key={v.idioma}>
                      {i > 0 && ' · '}
                      <a
                        className="text-primary hover:underline"
                        href={videoaulaUrl(v)}
                        target="_blank"
                        rel="noreferrer"
                        onClick={() => setIdioma(v.idioma)}
                      >
                        {v.rotulo}
                      </a>
                    </span>
                  ))}
                </p>
              </AlertDescription>
            </Alert>
          )}

          <div className="space-y-2 rounded-lg border p-4">
            <Label className="text-sm font-medium">Confirmação do operador</Label>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox
                checked={confirmado}
                disabled={!podeConfirmar}
                onCheckedChange={(v) => setConfirmado(!!v)}
              />
              {modoFallback
                ? 'Declaro que o locatário assistiu à videoaula pelo link acima (registro manual)'
                : concluido
                  ? 'O locatário assistiu à videoaula na íntegra'
                  : obrigatoria
                    ? 'O locatário assistiu à videoaula na íntegra'
                    : 'O locatário assistiu à videoaula (registro por declaração)'}
            </label>
            {!podeConfirmar && (
              <p className="text-xs text-amber-700 dark:text-amber-400">
                A confirmação libera quando o vídeo terminar.
              </p>
            )}
            {!obrigatoria && !concluido && !modoFallback && (
              <p className="text-xs text-muted-foreground">
                Se o vídeo chegar ao fim, o registro passa a ser do player (auditável).
              </p>
            )}
          </div>
        </>
      )}

      <div className="flex justify-between">
        <Button type="button" variant="outline" onClick={onBack}>
          Voltar
        </Button>
        <Button type="button" disabled={!podeContinuar || registrar.isPending} onClick={continuar}>
          {registrar.isPending ? 'Registrando…' : 'Continuar'}
        </Button>
      </div>
    </div>
  )
}
