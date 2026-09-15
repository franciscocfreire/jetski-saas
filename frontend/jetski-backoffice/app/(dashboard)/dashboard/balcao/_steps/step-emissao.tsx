'use client'

import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useMutation } from '@tanstack/react-query'
import { FileDown, CheckCircle2, Anchor, Mail, Printer, AlertTriangle, Ship, Loader2, Send, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { DocumentoPreviewButtons } from '@/components/documento-preview-buttons'
import { reservasService, documentosService, instrutoresService, habilitacaoService, emissaoDelegadaService } from '@/lib/api/services'
import { useModoEmissao } from '@/lib/hooks/use-modo-emissao'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Label } from '@/components/ui/label'
import Link from 'next/link'
import { abrirPdfPorLink } from '@/lib/pdf'
import type { Atendimento } from '../types'
import type { EnvioStatus, ResultadoEmissao } from '@/lib/api/types'

export function StepEmissao({
  atendimento,
  onBack,
  onReset,
  onBusyChange,
}: {
  atendimento: Atendimento
  onBack: () => void
  onReset: () => void
  /** Avisa o wizard enquanto a emissão está em curso, para congelar a navegação. */
  onBusyChange?: (busy: boolean) => void
}) {
  const [resultado, setResultado] = useState<ResultadoEmissao | null>(null)
  const [baixando, setBaixando] = useState(false)
  // Instrutor é a ÚLTIMA etapa: o cliente já chegou instruído (videoaula) e
  // a emissão não sai sem ele — aqui é onde o staff confirma quem demonstrou.
  const [instrutorId, setInstrutorId] = useState(atendimento.instrutorId ?? '')
  const [salvandoInstrutor, setSalvandoInstrutor] = useState(false)

  // Emissão delegada (§8.M): operadora de parceria em vigor emite pela EAMA, qualquer
  // que seja o plano — quem diz o modo é o backend. O instrutor que assina o 5-B-1 é
  // da EAMA (designado) ou da própria operadora com aprovação da EAMA (V070); a lista
  // expõe só id + nome.
  const { delegada: emissaoDelegada, carregando: carregandoModo } = useModoEmissao()
  const { data: instrutores } = useQuery({
    queryKey: ['instrutores-emissao', emissaoDelegada],
    queryFn: async () =>
      emissaoDelegada
        ? (await emissaoDelegadaService.instrutoresParceiro()).map((i) => ({
            id: i.id,
            nome: i.origem === 'OPERADORA' ? `${i.nome} (seu instrutor, aprovado pela EAMA)` : i.nome,
            cha: undefined as string | undefined,
          }))
        : instrutoresService.list(),
    enabled: !atendimento.temCha && !carregandoModo,
  })

  // O estado "emitido" vive na reserva (documento_emitido_em), não no wizard:
  // ao sair e voltar (sessionStorage rehidrata reserva antiga), consulta fresca
  // evita mostrar o form de novo — reemissão reenviaria à Marinha e debitaria crédito.
  const reservaId = atendimento.reserva?.id
  const { data: reservaAtual, isLoading: conferindoEmissao } = useQuery({
    queryKey: ['reserva-emissao', reservaId],
    queryFn: () => reservasService.getById(reservaId!),
    enabled: !!reservaId && !resultado,
  })
  const jaEmitida = !resultado && !!reservaAtual?.documentoEmitidoEm
  const { data: documentosCliente } = useQuery({
    queryKey: ['documentos-reserva', reservaId],
    queryFn: () => documentosService.list(atendimento.cliente!.id),
    enabled: jaEmitida && !!atendimento.cliente?.id,
  })
  const docEmitido = documentosCliente?.find((d) => d.reservaId === reservaId)
  const { data: habEmitida } = useQuery({
    queryKey: ['habilitacao', reservaId],
    queryFn: () => habilitacaoService.get(reservaId!),
    enabled: jaEmitida,
  })

  async function escolherInstrutor(id: string) {
    setInstrutorId(id)
    if (!atendimento.reserva) return
    try {
      setSalvandoInstrutor(true)
      await habilitacaoService.registrar(atendimento.reserva.id, { via: 'EMA', instrutorId: id })
      toast.success('Instrutor registrado.')
    } catch {
      toast.error('Não foi possível registrar o instrutor.')
    } finally {
      setSalvandoInstrutor(false)
    }
  }

  async function abrirPdf(documentoId: string) {
    try {
      setBaixando(true)
      await abrirPdfPorLink(() => documentosService.downloadLink(documentoId))
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
      // reaproveitado = a reserva já tinha documento (o backend devolveu o mesmo em
      // vez de emitir e cobrar outro). Não anunciar uma emissão que não aconteceu.
      toast.success(r.reaproveitado ? 'Documentos já emitidos para esta reserva.' : 'Documentos emitidos.')
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha ao emitir documentos.')
    },
  })

  // O wizard congela a navegação enquanto emitimos: sair no meio desmontaria o step.
  useEffect(() => {
    onBusyChange?.(emitir.isPending)
    return () => onBusyChange?.(false)
  }, [emitir.isPending, onBusyChange])

  // Os e-mails saem fora do request: acompanhamos até os dois destinos ficarem
  // num estado terminal. O servidor decide quando acabou (campo `concluido`) —
  // duplicar essa regra aqui em lista de strings sairia de sincronia.
  const [envioParou, setEnvioParou] = useState(false)
  const queryClient = useQueryClient()

  // A query é a fonte da verdade do envio (inclusive depois de um reenvio); quem
  // decide se ainda vale perguntar é o refetchInterval. No modo síncrono a primeira
  // resposta já vem concluída e o polling nem começa.
  const { data: envio } = useQuery({
    queryKey: ['documento-envio', resultado?.documentoId],
    queryFn: () => documentosService.envioStatus(resultado!.documentoId),
    enabled: !!resultado,
    refetchInterval: (query) => (envioParou || query.state.data?.concluido ? false : 2000),
  })

  const aguardandoEnvio = !!resultado && !envio?.concluido

  // Teto de ~60 s: se o envio não concluir, paramos de perguntar e dizemos onde olhar.
  useEffect(() => {
    if (!aguardandoEnvio || envioParou) return
    const t = setTimeout(() => setEnvioParou(true), 60_000)
    return () => clearTimeout(t)
  }, [aguardandoEnvio, envioParou])

  const marinhaStatus: EnvioStatus | undefined = envio?.marinha.status ?? resultado?.marinhaEnvioStatus
  const clienteStatus: EnvioStatus | undefined = envio?.cliente.status ?? resultado?.clienteEnvioStatus

  const reenviar = useMutation({
    mutationFn: () => documentosService.reenviar(resultado!.documentoId),
    onSuccess: () => {
      setEnvioParou(false)
      queryClient.invalidateQueries({ queryKey: ['documento-envio', resultado?.documentoId] })
      toast.success('Reenvio disparado.')
    },
    onError: () => toast.error('Não foi possível reenviar.'),
  })

  /** Uma linha de destino: ícone + texto conforme o estado do envio. */
  function LinhaEnvio({
    icone,
    status,
    rotulo,
    semDestinatario,
    testId,
  }: {
    icone: React.ReactNode
    status: EnvioStatus | undefined
    rotulo: string
    semDestinatario: string
    /** Âncora estável para o e2e; o estado vai em data-status. */
    testId: string
  }) {
    const ancora = { 'data-testid': testId, 'data-status': status ?? '' }
    if (status === 'PENDENTE') {
      return (
        <p {...ancora} className="flex items-center gap-2 text-muted-foreground">
          <Loader2 size={15} className="animate-spin" />
          {envioParou ? `${rotulo}: ainda enviando — confira no módulo GRUs` : `Enviando ${rotulo.toLowerCase()}…`}
        </p>
      )
    }
    if (status === 'ENVIADO') {
      return (
        <p {...ancora} className="flex items-center gap-2">
          <span className="text-emerald-600">{icone}</span> ✓ {rotulo}
        </p>
      )
    }
    if (status === 'SEM_DESTINATARIO') {
      return (
        <p {...ancora} className="flex items-center gap-2 text-muted-foreground">{icone} {semDestinatario}</p>
      )
    }
    const botaoReenviar = (
      <Button
        data-testid={`${testId}-reenviar`}
        type="button"
        size="sm"
        variant="outline"
        disabled={reenviar.isPending}
        onClick={() => reenviar.mutate()}
      >
        <Send size={13} className="mr-1" /> {reenviar.isPending ? 'Reenviando…' : 'Reenviar'}
      </Button>
    )
    if (status === 'SEM_SMTP') {
      // O ofício só sai pelo e-mail da EAMA emissora: depois de ela configurar o SMTP, reenviar.
      return (
        <div {...ancora} className="flex flex-wrap items-center gap-2 text-amber-700 dark:text-amber-500">
          <XCircle size={15} /> Não enviado à Marinha: a EAMA emissora não configurou o servidor de e-mail (SMTP)
          {botaoReenviar}
        </div>
      )
    }
    return (
      <div {...ancora} className="flex flex-wrap items-center gap-2 text-amber-700 dark:text-amber-500">
        <XCircle size={15} /> {rotulo}: falhou no envio
        {botaoReenviar}
      </div>
    )
  }

  // Emissão em curso: bloco amigável no lugar do formulário. Sem Dialog — o
  // DialogContent do projeto tem o X fixo e não dá para torná-lo não-dispensável.
  if (emitir.isPending) {
    return (
      <div className="flex h-[50vh] flex-col items-center justify-center gap-3 text-center">
        <div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary/10">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
        </div>
        <p className="text-lg font-semibold">Emitindo os documentos…</p>
        <p className="max-w-sm text-sm text-muted-foreground">
          Gerando o PDF, arquivando e preparando os e-mails. Leva alguns segundos — não feche
          esta tela.
        </p>
      </div>
    )
  }

  if (resultado) {
    return (
      <div className="space-y-5">
        <div
          className="flex items-center gap-2 text-emerald-600"
          data-testid="balcao-emissao-resultado"
          data-documento-id={resultado.documentoId}
          data-reaproveitado={resultado.reaproveitado ? 'true' : 'false'}
        >
          <CheckCircle2 className="h-6 w-6" />
          <span className="text-lg font-semibold">
            {resultado.reaproveitado ? 'Documentos já emitidos' : 'Documentos emitidos'}
          </span>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <Button
            data-testid="balcao-emissao-abrir-pdf"
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
          {/* CHA não exige envio à Marinha (sem documentação NORMAM). */}
          {atendimento.temCha ? (
            <p className="flex items-center gap-2 text-muted-foreground">
              <Anchor size={15} /> Habilitação por CHA — sem envio à Marinha.
            </p>
          ) : resultado.docCompleta ? (
            <LinhaEnvio
              testId="balcao-emissao-envio-marinha"
              icone={<Anchor size={15} />}
              status={marinhaStatus}
              rotulo="Enviado à Marinha"
              semDestinatario="Não enviado à Marinha (sem e-mail configurado)"
            />
          ) : (
            <div
              data-testid="balcao-emissao-marinha-pendente"
              className="rounded-md border border-amber-300 bg-amber-50 p-2 text-amber-800 dark:bg-amber-950/30"
            >
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
          <LinhaEnvio
            testId="balcao-emissao-envio-cliente"
            icone={<Mail size={15} />}
            status={clienteStatus}
            rotulo="E-mail ao cliente"
            semDestinatario="Não enviado ao cliente (sem e-mail)"
          />
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
          <Button data-testid="balcao-emissao-concluir" type="button" onClick={onReset}>
            Concluir atendimento
          </Button>
        </div>
      </div>
    )
  }

  // Reserva já emitida (retomada/volta ao wizard): mostra o estado concluído —
  // sem oferecer nova emissão.
  if (jaEmitida) {
    return (
      <div className="space-y-5">
        <div className="flex items-center gap-2 text-emerald-600">
          <CheckCircle2 className="h-6 w-6" />
          <span className="text-lg font-semibold">Documentos já emitidos</span>
        </div>

        <div className="space-y-2 rounded-lg border p-4 text-sm">
          <p className="text-muted-foreground">
            Emitidos em{' '}
            <strong className="text-foreground">
              {new Date(reservaAtual!.documentoEmitidoEm!).toLocaleString('pt-BR')}
            </strong>
            {atendimento.temCha && ' · habilitação por CHA — sem envio à Marinha'}
          </p>
          {habEmitida?.gruNumero && (
            <p className="text-muted-foreground">
              GRU {habEmitida.gruNumero}
              {habEmitida.gruValor ? ` — R$ ${habEmitida.gruValor}` : ''}
            </p>
          )}
        </div>

        {docEmitido && (
          <Button
            type="button"
            className="w-full"
            disabled={baixando}
            onClick={() => abrirPdf(docEmitido.id)}
          >
            <FileDown size={16} className="mr-2" /> {baixando ? 'Abrindo…' : 'Abrir / Baixar PDF'}
          </Button>
        )}

        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-4">
          <p className="flex items-center gap-2 text-sm text-muted-foreground">
            <Ship size={16} className="text-primary" />
            A reserva entrou na <strong className="text-foreground">fila de espera</strong>. O embarque
            (check-in) é feito na Fila quando o jetski do modelo estiver livre.
          </p>
          <Button data-testid="balcao-emissao-concluir" type="button" onClick={onReset}>
            Concluir atendimento
          </Button>
        </div>
      </div>
    )
  }

  // Evita piscar o form de emissão antes de saber se já foi emitida.
  if (conferindoEmissao) {
    return <div className="p-6 text-sm text-muted-foreground">Conferindo emissão…</div>
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

  // Habilitação ainda não resolvida. Para EMA = GRU não paga; para CHA = falta o
  // número da carteira (sem GRU e sem envio à Marinha). A reserva já vale e está na fila.
  if (!atendimento.habilitacaoResolvida) {
    const cha = atendimento.temCha
    return (
      <div className="space-y-5">
        <div className="space-y-1 rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-800 dark:bg-amber-950/30">
          <p className="flex items-center gap-2 font-medium">
            <Ship className="h-4 w-4" /> Reserva confirmada — entrou na fila de espera
          </p>
          {cha ? (
            <p>
              O cliente já é <strong>habilitado (CHA)</strong> — não há GRU a pagar nem envio à
              Marinha. O embarque já está liberado. Para emitir o comprovante, falta apenas{' '}
              <strong>informar o número da CHA</strong> na etapa de Habilitação.
            </p>
          ) : (
            <p>
              A <strong>GRU ainda não está paga</strong>, então os documentos NÃO podem ser emitidos
              agora. A reserva já vale e pode embarcar normalmente. Pague a GRU (PIX/boleto ou
              comprovante) e <strong>emita os documentos depois</strong> em Pendências → Retomar.
            </p>
          )}
        </div>
        {atendimento.reserva && (
          <DocumentoPreviewButtons
            reservaId={atendimento.reserva.id}
            marinha={!cha}
            className="rounded-lg border p-4"
          />
        )}
        <div className="flex justify-between">
          <Button type="button" variant="outline" onClick={onBack}>Voltar</Button>
          <Button data-testid="balcao-emissao-concluir" type="button" onClick={onReset}>Concluir atendimento</Button>
        </div>
      </div>
    )
  }

  // GRU paga + termos → pode emitir agora (instrutor é o último requisito).
  const precisaInstrutor = !atendimento.temCha
  const instrutorOk = !precisaInstrutor || !!instrutorId

  return (
    <div className="space-y-5">
      <p className="text-sm text-muted-foreground">
        {atendimento.temCha
          ? 'Gera o comprovante (termo + assinatura) para o cliente. Habilitação por CHA — sem envio à Marinha.'
          : 'Gera o PDF consolidado (anexos + termo + assinatura), arquiva, envia à Marinha e ao cliente, e disponibiliza a GRU.'}
      </p>

      {precisaInstrutor && (
        <div className="space-y-2 rounded-lg border p-4">
          <Label className="text-sm font-medium">
            Instrutor (Atestado de Demonstração 5-B-1) — última etapa
          </Label>
          <p className="text-xs text-muted-foreground">
            O instrutor confirma a demonstração prática realizada — obrigatório
            para emitir os documentos.
          </p>
          {(instrutores ?? []).length === 0 ? (
            <p className="text-xs text-muted-foreground">
              {emissaoDelegada
                ? 'Nenhum instrutor disponível: a EAMA parceira precisa designar um instrutor dela ou aprovar um seu.'
                : 'Nenhum instrutor cadastrado.'}{' '}
              <Link href="/dashboard/instrutores" className="text-primary underline" target="_blank">
                {emissaoDelegada ? 'Ver instrutores' : 'Cadastrar instrutor'}
              </Link>
            </p>
          ) : (
            <Select value={instrutorId} onValueChange={escolherInstrutor} disabled={salvandoInstrutor}>
              <SelectTrigger data-testid="balcao-emissao-instrutor">
                <SelectValue placeholder="Selecione quem fez a demonstração" />
              </SelectTrigger>
              <SelectContent>
                {(instrutores ?? []).map((i) => (
                  <SelectItem
                    key={i.id}
                    value={i.id}
                    data-testid="balcao-emissao-instrutor-opcao"
                    data-instrutor-id={i.id}
                  >
                    {i.nome}
                    {i.cha ? ` — CHA ${i.cha}` : ''}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </div>
      )}
      {atendimento.reserva && (
        <DocumentoPreviewButtons
          reservaId={atendimento.reserva.id}
          marinha={!atendimento.temCha}
          className="rounded-lg border p-4"
        />
      )}
      <div className="flex justify-between">
        <Button type="button" variant="outline" onClick={onBack}>
          Voltar
        </Button>
        <Button
          data-testid="balcao-emissao-emitir"
          type="button"
          disabled={emitir.isPending || !instrutorOk}
          title={!instrutorOk ? 'Selecione o instrutor da demonstração' : undefined}
          onClick={() => emitir.mutate()}
        >
          {emitir.isPending ? 'Emitindo…' : 'Emitir documentos'}
        </Button>
      </div>
    </div>
  )
}
