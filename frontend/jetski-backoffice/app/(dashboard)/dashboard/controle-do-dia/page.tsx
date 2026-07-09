'use client'

import { useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Anchor,
  ChevronLeft,
  ChevronRight,
  CircleDollarSign,
  Clock,
  FileSignature,
  IdCard,
  LogIn,
  MoreHorizontal,
  Pencil,
  Store,
  Timer,
  UserRound,
} from 'lucide-react'
import { toast } from 'sonner'
import { useTenantStore } from '@/lib/store/tenant-store'
import { locacoesService, reservasService, vendedoresService } from '@/lib/api/services'
import type { ControleDoDiaLinha, FormaPagamento, Locacao, Vendedor } from '@/lib/api/types'
import { formatCurrency, cn, toLocalDateTime } from '@/lib/utils'
import { RentalCountdown } from '@/components/notifications/rental-countdown'
import { CheckOutDialog } from '@/components/locacoes/check-out-dialog'
import { PagamentoDialog } from '@/components/locacoes/pagamento-dialog'
import { EmbarqueDialog } from '@/components/fila/embarque-dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip'

const ymd = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

const hora = (iso: string) =>
  new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })

/** Rótulos curtos para os badges de forma de pagamento da prancheta. */
const FORMA_LABEL: Record<string, string> = {
  PIX: 'PIX',
  DINHEIRO: 'Dinheiro',
  CARTAO_DEBITO: 'Débito',
  CARTAO_CREDITO: 'Crédito',
  OUTRO: 'Outro',
}

/** Valor sentinela do Select (shadcn não aceita item com value vazio). */
const SEM_VENDEDOR = '__sem_vendedor__'

const mensagemErro = (e: unknown, fallback: string) =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback

/** Trio de prontidão da reserva — mesmo desenho da agenda: $ / CHA / termo. */
function Prontidao({ linha }: { linha: ControleDoDiaLinha }) {
  const itens = [
    {
      ok: !!linha.pagamentoOk,
      Icon: CircleDollarSign,
      dica: linha.pagamentoOk ? 'Pagamento confirmado' : 'Pagamento pendente',
    },
    {
      ok: !!linha.habilitacaoOk,
      Icon: IdCard,
      dica: linha.habilitacaoOk ? 'Habilitação resolvida' : 'Habilitação pendente',
    },
    {
      ok: !!linha.termoOk,
      Icon: FileSignature,
      dica: linha.termoOk ? 'Termo assinado' : 'Termo pendente',
    },
  ]
  return (
    <TooltipProvider>
      <span className="inline-flex items-center gap-1 align-middle">
        {itens.map(({ ok, Icon, dica }, i) => (
          <Tooltip key={i}>
            <TooltipTrigger asChild>
              <Icon
                size={13}
                className={ok ? 'text-emerald-600' : 'text-muted-foreground/50'}
                aria-label={dica}
              />
            </TooltipTrigger>
            <TooltipContent>{dica}</TooltipContent>
          </Tooltip>
        ))}
      </span>
    </TooltipProvider>
  )
}

/** Header sutil separando os grupos da prancheta (Na água / Futuras / Encerradas). */
function LinhaSecao({ label, count }: { label: string; count: number }) {
  return (
    <TableRow className="bg-muted/40 hover:bg-muted/40">
      <TableCell
        colSpan={8}
        className="py-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground"
      >
        {label} ({count})
      </TableCell>
    </TableRow>
  )
}

/** Mudar o horário de uma reserva confirmada mantendo a duração contratada. */
function MudarHorarioDialog({
  linha,
  onOpenChange,
}: {
  linha: ControleDoDiaLinha
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [horaStr, setHoraStr] = useState(() => hora(linha.dataCheckIn))
  const [motivo, setMotivo] = useState('')
  const finalizada = linha.tipo === 'LOCACAO' && linha.status === 'FINALIZADA'

  const salvarMutation = useMutation({
    mutationFn: async (): Promise<unknown> => {
      const [h, m] = horaStr.split(':').map(Number)
      const inicio = new Date(linha.dataCheckIn)
      inicio.setHours(h, m, 0, 0)
      // Locação: a saída (dataCheckIn) é o campo editado — em curso pelo
      // endpoint próprio; finalizada pelo fluxo auditado (motivo obrigatório)
      if (linha.tipo === 'LOCACAO') {
        if (finalizada) {
          return locacoesService.editarFinalizada(linha.locacaoId!, {
            dataCheckIn: toLocalDateTime(inicio),
            motivoEdicao: motivo.trim(),
          })
        }
        return locacoesService.updateDataCheckIn(linha.locacaoId!, toLocalDateTime(inicio))
      }
      const body: { dataInicio: string; dataFimPrevista?: string } = {
        dataInicio: toLocalDateTime(inicio),
      }
      // Desloca o fim junto para manter a duração — mudar horário não é
      // encurtar/estender o passeio
      if (linha.duracaoPrevista) {
        body.dataFimPrevista = toLocalDateTime(
          new Date(inicio.getTime() + linha.duracaoPrevista * 60_000)
        )
      }
      return reservasService.atualizar(linha.reservaId!, body)
    },
    onSuccess: () => {
      toast.success(`Saída alterada para ${horaStr}`)
      queryClient.invalidateQueries({ queryKey: ['controle-do-dia'] })
      queryClient.invalidateQueries({ queryKey: ['reservas'] })
      queryClient.invalidateQueries({ queryKey: ['agenda'] })
      queryClient.invalidateQueries({ queryKey: ['locacoes'] })
      onOpenChange(false)
    },
    // Conflito de agenda etc. — a mensagem do backend explica melhor que a nossa
    onError: (e: unknown) => toast.error(mensagemErro(e, 'Não foi possível mudar o horário')),
  })

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[380px]">
        <DialogHeader>
          <DialogTitle>{linha.tipo === 'LOCACAO' ? 'Mudar saída' : 'Mudar horário'}</DialogTitle>
          <DialogDescription>
            {linha.clienteNome || 'Cliente'} — a volta prevista acompanha o novo horário.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-2 py-2">
          <Label htmlFor="novo-horario">Nova saída</Label>
          <Input
            id="novo-horario"
            type="time"
            value={horaStr}
            onChange={(e) => setHoraStr(e.target.value)}
          />
          {finalizada && (
            <>
              <Label htmlFor="motivo-saida" className="mt-2">
                Motivo da edição <span className="text-red-500">*</span>
              </Label>
              <Textarea
                id="motivo-saida"
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                placeholder="Ex.: horário anotado errado no embarque"
                rows={2}
              />
            </>
          )}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Cancelar
          </Button>
          <Button
            type="button"
            disabled={!horaStr || (finalizada && !motivo.trim()) || salvarMutation.isPending}
            onClick={() => salvarMutation.mutate()}
          >
            {salvarMutation.isPending ? 'Salvando…' : 'Salvar'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/**
 * Trocar vendedor — vale para reserva, locação em curso e finalizada.
 * Finalizada exige motivo (trilha de auditoria) e recalcula a comissão.
 */
function TrocarVendedorDialog({
  linha,
  vendedores,
  carregandoVendedores,
  onOpenChange,
}: {
  linha: ControleDoDiaLinha
  vendedores: Vendedor[]
  carregandoVendedores: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [vendedorId, setVendedorId] = useState('')
  const [motivo, setMotivo] = useState('')

  const finalizada = linha.tipo === 'LOCACAO' && linha.status === 'FINALIZADA'

  const salvarMutation = useMutation({
    mutationFn: (): Promise<unknown> => {
      const novoVendedorId = vendedorId === SEM_VENDEDOR ? null : vendedorId
      if (linha.tipo === 'RESERVA') {
        return reservasService.atualizar(linha.reservaId!, { vendedorId: novoVendedorId })
      }
      if (finalizada) {
        // motivoEdicao obrigatório: edição retroativa fica auditável
        return locacoesService.editarFinalizada(linha.locacaoId!, {
          vendedorId: novoVendedorId ?? undefined,
          motivoEdicao: motivo.trim(),
        })
      }
      return locacoesService.alterarVendedor(linha.locacaoId!, novoVendedorId)
    },
    onSuccess: () => {
      toast.success(finalizada ? 'Vendedor alterado — comissão recalculada' : 'Vendedor alterado')
      queryClient.invalidateQueries({ queryKey: ['controle-do-dia'] })
      queryClient.invalidateQueries({ queryKey: ['locacoes'] })
      queryClient.invalidateQueries({ queryKey: ['reservas'] })
      onOpenChange(false)
    },
    // Ex.: fechamento diário travado — o backend explica o porquê
    onError: (e: unknown) => toast.error(mensagemErro(e, 'Não foi possível trocar o vendedor')),
  })

  const podeSalvar =
    !!vendedorId && (!finalizada || motivo.trim().length > 0) && !salvarMutation.isPending

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[400px]">
        <DialogHeader>
          <DialogTitle>Trocar vendedor</DialogTitle>
          <DialogDescription>
            {linha.clienteNome || 'Cliente'} — vendedor atual: {linha.vendedorNome || 'nenhum'}
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-3 py-2">
          <div className="grid gap-2">
            <Label>Novo vendedor</Label>
            <Select value={vendedorId} onValueChange={setVendedorId}>
              <SelectTrigger>
                <SelectValue
                  placeholder={carregandoVendedores ? 'Carregando…' : 'Selecione o vendedor'}
                />
              </SelectTrigger>
              <SelectContent>
                {/* "Sem vendedor" só na locação EM_CURSO: é o único endpoint em
                    que null DESASSOCIA (PATCH /vendedor). No PUT de reserva e no
                    editar-finalizada, vendedorId null/omitido é ignorado — a
                    opção seria um botão que não faz nada */}
                {linha.tipo === 'LOCACAO' && linha.status === 'EM_CURSO' && (
                  <SelectItem value={SEM_VENDEDOR}>Sem vendedor</SelectItem>
                )}
                {vendedores.map((v) => (
                  <SelectItem key={v.id} value={v.id}>
                    {v.nome}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {finalizada && (
            <div className="grid gap-2">
              <Label htmlFor="motivo-edicao">Motivo da alteração *</Label>
              <Textarea
                id="motivo-edicao"
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                placeholder="Por que o vendedor está sendo trocado após a finalização?"
                rows={3}
              />
            </div>
          )}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Cancelar
          </Button>
          <Button type="button" disabled={!podeSalvar} onClick={() => salvarMutation.mutate()}>
            {salvarMutation.isPending ? 'Salvando…' : 'Salvar'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export default function ControleDoDiaPage() {
  const { currentTenant } = useTenantStore()
  const queryClient = useQueryClient()
  const router = useRouter()
  const [currentDate, setCurrentDate] = useState(new Date())
  const [checkOutLocacao, setCheckOutLocacao] = useState<Locacao | null>(null)
  const [abrindoId, setAbrindoId] = useState<string | null>(null)
  // Dialogs das ações do menu (⋯) — guardam a linha alvo
  const [horarioLinha, setHorarioLinha] = useState<ControleDoDiaLinha | null>(null)
  const [vendedorLinha, setVendedorLinha] = useState<ControleDoDiaLinha | null>(null)
  const [pagamentoLinha, setPagamentoLinha] = useState<ControleDoDiaLinha | null>(null)
  // Embarcar = check-in de verdade (aloca jet + registra saída) — mesmo dialog da Fila.
  // O balcão fica no menu ⋯ para completar cadastro/documentos quando houver pendência.
  const [embarqueLinha, setEmbarqueLinha] = useState<ControleDoDiaLinha | null>(null)

  const dataYmd = ymd(currentDate)

  const { data: controle, isLoading } = useQuery({
    queryKey: ['controle-do-dia', currentTenant?.id, dataYmd],
    queryFn: () => locacoesService.controleDoDia(dataYmd),
    enabled: !!currentTenant,
    refetchInterval: 60_000, // a prancheta fica aberta no balcão — atualiza sozinha
  })

  // Vendedores só são necessários quando o dialog de troca abre
  const { data: vendedores = [], isLoading: carregandoVendedores } = useQuery({
    queryKey: ['vendedores', currentTenant?.id],
    queryFn: () => vendedoresService.list(),
    enabled: !!currentTenant && !!vendedorLinha,
  })

  // Extrato da linha em recebimento (reserva ou locação)
  const idPagamento = pagamentoLinha?.reservaId ?? pagamentoLinha?.locacaoId
  const { data: extratoPagamento, isLoading: carregandoExtrato } = useQuery({
    queryKey: ['controle-extrato', pagamentoLinha?.tipo, idPagamento],
    queryFn: () =>
      pagamentoLinha!.tipo === 'RESERVA'
        ? reservasService.extrato(pagamentoLinha!.reservaId!)
        : locacoesService.extrato(pagamentoLinha!.locacaoId!),
    enabled: !!pagamentoLinha && !!idPagamento,
  })

  // Três grupos, na ordem da operação: quem está na água agora, quem ainda
  // vai sair (reservas confirmadas) e o que já encerrou (riscado, como no papel)
  const grupos = useMemo(() => {
    const todas = controle?.linhas ?? []
    const naAgua = todas
      .filter((l) => l.tipo === 'LOCACAO' && l.status === 'EM_CURSO')
      .sort((a, b) => a.dataCheckIn.localeCompare(b.dataCheckIn))
    const futuras = todas
      .filter((l) => l.tipo === 'RESERVA')
      .sort((a, b) => a.dataCheckIn.localeCompare(b.dataCheckIn))
    const encerradas = todas
      .filter((l) => l.tipo === 'LOCACAO' && l.status !== 'EM_CURSO')
      .sort((a, b) =>
        (b.dataCheckOut ?? b.dataCheckIn).localeCompare(a.dataCheckOut ?? a.dataCheckIn)
      )
    return { naAgua, futuras, encerradas, vazio: todas.length === 0 }
  }, [controle])

  const prorrogarMutation = useMutation({
    mutationFn: ({ id, duracaoPrevista }: { id: string; duracaoPrevista: number }) =>
      locacoesService.prorrogar(id, duracaoPrevista),
    onSuccess: (loc) => {
      const novaVolta = new Date(
        new Date(loc.dataCheckIn).getTime() + (loc.duracaoPrevista ?? 0) * 60_000
      )
      toast.success(`Volta estendida para ${hora(novaVolta.toISOString())}`)
      queryClient.invalidateQueries({ queryKey: ['controle-do-dia'] })
      queryClient.invalidateQueries({ queryKey: ['locacoes'] })
    },
    onError: () => toast.error('Não foi possível prorrogar a locação'),
  })

  // A prancheta traz só o resumo — o check-out precisa da locação completa (horímetro etc.)
  const abrirCheckOut = async (linha: ControleDoDiaLinha) => {
    if (!linha.locacaoId) return
    setAbrindoId(linha.locacaoId)
    try {
      const locacao = await locacoesService.getById(linha.locacaoId)
      setCheckOutLocacao(locacao)
    } catch {
      toast.error('Não foi possível carregar a locação')
    } finally {
      setAbrindoId(null)
    }
  }

  const registrarPagamento = async (forma: FormaPagamento, valor: number) => {
    if (!pagamentoLinha) return
    if (pagamentoLinha.tipo === 'RESERVA') {
      await reservasService.registrarPagamento(pagamentoLinha.reservaId!, { forma, valor })
      queryClient.invalidateQueries({ queryKey: ['reservas'] })
    } else {
      await locacoesService.registrarPagamento(pagamentoLinha.locacaoId!, { forma, valor })
      queryClient.invalidateQueries({ queryKey: ['locacoes'] })
    }
    queryClient.invalidateQueries({ queryKey: ['controle-do-dia'] })
    queryClient.invalidateQueries({ queryKey: ['controle-extrato'] })
  }

  const stepDay = (delta: number) =>
    setCurrentDate(
      new Date(currentDate.getFullYear(), currentDate.getMonth(), currentDate.getDate() + delta)
    )
  const goToday = () => setCurrentDate(new Date())

  if (!currentTenant) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <p className="text-muted-foreground">Selecione um tenant para continuar</p>
      </div>
    )
  }

  const dayLabel = currentDate.toLocaleDateString('pt-BR', {
    weekday: 'long',
    day: '2-digit',
    month: 'long',
  })

  const totalPorForma = Object.entries(controle?.totalPorForma ?? {})
  const totalPorVendedor = controle?.totalPorVendedor ?? []

  const renderLinha = (linha: ControleDoDiaLinha) => {
    const reserva = linha.tipo === 'RESERVA'
    const emCurso = !reserva && linha.status === 'EM_CURSO'
    const finalizada = !reserva && linha.status === 'FINALIZADA'
    const voltaPrevista =
      emCurso && linha.duracaoPrevista
        ? new Date(new Date(linha.dataCheckIn).getTime() + linha.duracaoPrevista * 60_000)
        : null
    return (
      <TableRow
        key={linha.locacaoId ?? linha.reservaId ?? linha.dataCheckIn}
        className={cn(
          emCurso && 'bg-primary/5 font-medium',
          // O gesto do papel: linha riscada = encerrada (badges não são riscados
          // porque line-through não propaga para inline-flex)
          !reserva && !emCurso && 'line-through opacity-60'
        )}
      >
        {/* Reserva ainda não tem jet alocado — mostra o modelo contratado */}
        <TableCell>{linha.jetskiSerie || linha.modeloNome || '—'}</TableCell>
        <TableCell>
          <span className="inline-flex items-center gap-2">
            {linha.clienteNome || 'Walk-in'}
            {reserva && <Prontidao linha={linha} />}
            {reserva && linha.status === 'PENDENTE' && (
              <Badge variant="outline" className="border-amber-400 px-1.5 py-0 text-xs text-amber-700 dark:text-amber-400">
                Pendente
              </Badge>
            )}
          </span>
        </TableCell>
        <TableCell className="tabular-nums">
          {/* Clique na hora = editar a saída (atalho do menu ⋯); cancelada não edita */}
          {reserva || emCurso || finalizada ? (
            <button
              type="button"
              onClick={() => setHorarioLinha(linha)}
              title="Clique para mudar a saída"
              className="group inline-flex items-center gap-1 rounded px-1 -mx-1 tabular-nums underline-offset-2 hover:bg-accent hover:underline"
            >
              {hora(linha.dataCheckIn)}
              <Pencil size={11} className="opacity-0 transition group-hover:opacity-60" />
            </button>
          ) : (
            hora(linha.dataCheckIn)
          )}
        </TableCell>
        <TableCell>
          {reserva ? (
            <span className="text-muted-foreground">—</span>
          ) : emCurso ? (
            <div className="flex items-center gap-2">
              {voltaPrevista && (
                <span className="tabular-nums text-muted-foreground">
                  {hora(voltaPrevista.toISOString())}
                </span>
              )}
              <RentalCountdown
                dataCheckIn={linha.dataCheckIn}
                duracaoPrevista={linha.duracaoPrevista}
              />
            </div>
          ) : linha.status === 'CANCELADA' ? (
            <Badge variant="destructive">Cancelada</Badge>
          ) : (
            <span className="tabular-nums">
              {linha.dataCheckOut ? hora(linha.dataCheckOut) : '—'}
            </span>
          )}
        </TableCell>
        <TableCell className="text-right tabular-nums">
          {linha.valorTotal != null ? formatCurrency(linha.valorTotal) : '—'}
        </TableCell>
        <TableCell>
          {linha.formas.length > 0 ? (
            <div className="flex flex-wrap gap-1">
              {linha.formas.map((forma) => (
                <Badge key={forma} variant="outline" className="px-1.5 py-0 text-xs">
                  {FORMA_LABEL[forma] ?? forma}
                </Badge>
              ))}
            </div>
          ) : (
            <span className="text-muted-foreground">—</span>
          )}
        </TableCell>
        <TableCell>{linha.vendedorNome || '—'}</TableCell>
        <TableCell className="text-right">
          <div className="flex items-center justify-end gap-2">
            {reserva && (
              <Button size="sm" onClick={() => setEmbarqueLinha(linha)}>
                <Anchor className="mr-1 h-4 w-4" />
                Embarcar
              </Button>
            )}
            {emCurso && (
              <>
                <Button
                  size="sm"
                  onClick={() => abrirCheckOut(linha)}
                  disabled={abrindoId === linha.locacaoId}
                >
                  <LogIn className="mr-1 h-4 w-4" />
                  {abrindoId === linha.locacaoId ? 'Abrindo…' : 'Voltou'}
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  // Sem duração prevista não há o que estender (locação "em aberto")
                  disabled={!linha.duracaoPrevista || prorrogarMutation.isPending}
                  onClick={() =>
                    prorrogarMutation.mutate({
                      id: linha.locacaoId!,
                      duracaoPrevista: (linha.duracaoPrevista ?? 0) + 30,
                    })
                  }
                >
                  <Timer className="mr-1 h-4 w-4" />
                  +30 min
                </Button>
              </>
            )}
            {reserva || emCurso || finalizada ? (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button size="sm" variant="ghost" className="h-8 w-8 p-0">
                    <span className="sr-only">Mais ações</span>
                    <MoreHorizontal className="h-4 w-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  {(reserva || emCurso || finalizada) && (
                    <DropdownMenuItem onSelect={() => setHorarioLinha(linha)}>
                      <Clock className="mr-2 h-4 w-4" />
                      {reserva ? 'Mudar horário' : 'Mudar saída'}
                    </DropdownMenuItem>
                  )}
                  {reserva && (
                    <DropdownMenuItem
                      onSelect={() => router.push(`/dashboard/balcao?reserva=${linha.reservaId}`)}
                    >
                      <Store className="mr-2 h-4 w-4" />
                      Abrir no balcão
                    </DropdownMenuItem>
                  )}
                  <DropdownMenuItem onSelect={() => setVendedorLinha(linha)}>
                    <UserRound className="mr-2 h-4 w-4" />
                    Trocar vendedor
                  </DropdownMenuItem>
                  <DropdownMenuItem onSelect={() => setPagamentoLinha(linha)}>
                    <CircleDollarSign className="mr-2 h-4 w-4" />
                    {reserva ? 'Receber pagamento' : 'Registrar pagamento'}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            ) : (
              !reserva && !emCurso && <span className="text-muted-foreground">—</span>
            )}
          </div>
        </TableCell>
      </TableRow>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Controle do dia</h1>
          <p className="text-muted-foreground">Saídas, voltas e caixa — a prancheta, ao vivo</p>
        </div>
      </div>

      {/* Toolbar de data — mesmo padrão da Agenda */}
      <div className="flex flex-wrap items-center gap-3">
        <Button variant="outline" size="icon" onClick={() => stepDay(-1)}>
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <h2 className="flex-1 truncate text-center text-base font-semibold capitalize sm:min-w-[14rem] sm:flex-none sm:text-lg">
          {dayLabel}
        </h2>
        <Button variant="outline" size="icon" onClick={() => stepDay(1)}>
          <ChevronRight className="h-4 w-4" />
        </Button>
        <Button variant="outline" onClick={goToday}>
          Hoje
        </Button>
      </div>

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Jet</TableHead>
              <TableHead>Cliente</TableHead>
              <TableHead>Saída</TableHead>
              <TableHead>Volta</TableHead>
              <TableHead className="text-right">Valor</TableHead>
              <TableHead>Pagamento</TableHead>
              <TableHead>Vendedor</TableHead>
              <TableHead className="text-right">Ações</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  {Array.from({ length: 8 }).map((_, j) => (
                    <TableCell key={j}>
                      <Skeleton className="h-5 w-full" />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : grupos.vazio ? (
              <TableRow>
                <TableCell colSpan={8} className="h-24 text-center text-muted-foreground">
                  Nenhuma saída registrada neste dia
                </TableCell>
              </TableRow>
            ) : (
              <>
                {grupos.naAgua.length > 0 && (
                  <LinhaSecao label="Na água" count={grupos.naAgua.length} />
                )}
                {grupos.naAgua.map(renderLinha)}
                {grupos.futuras.length > 0 && (
                  <LinhaSecao label="Futuras saídas" count={grupos.futuras.length} />
                )}
                {grupos.futuras.map(renderLinha)}
                {grupos.encerradas.length > 0 && (
                  <LinhaSecao label="Concluídas e canceladas" count={grupos.encerradas.length} />
                )}
                {grupos.encerradas.map(renderLinha)}
              </>
            )}
          </TableBody>
        </Table>
      </div>

      {/* Rodapé de totais — a linha de baixo da prancheta. Futuras saídas NÃO
          entram nos totais: caixa/competência são só de locações (o backend já
          calcula assim; a reserva vira dinheiro no dia em que o pagamento cai). */}
      {!isLoading && controle && (
        <div className="rounded-lg border bg-muted/30 px-4 py-3">
          <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm">
            <span className="text-base font-bold">
              Total do dia{' '}
              <span className="tabular-nums">{formatCurrency(controle.totalDia)}</span>
            </span>
            {totalPorForma.length > 0 && (
              <TooltipProvider>
                <div className="flex flex-wrap items-center gap-1.5">
                  {totalPorForma.map(([forma, total]) => (
                    <Tooltip key={forma}>
                      <TooltipTrigger asChild>
                        <Badge variant="secondary" className="tabular-nums">
                          {FORMA_LABEL[forma] ?? forma}: {formatCurrency(total)}
                        </Badge>
                      </TooltipTrigger>
                      <TooltipContent>
                        {/* Nuance caixa × competência: aqui somam os PAGAMENTOS recebidos no
                            dia (regime de caixa), não o valor das locações — é a mesma conta
                            que o fechamento diário confere. */}
                        Regime de caixa — mesma conta do fechamento diário
                      </TooltipContent>
                    </Tooltip>
                  ))}
                </div>
              </TooltipProvider>
            )}
            {totalPorVendedor.length > 0 && (
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-muted-foreground">
                <span
                  className="text-xs font-medium uppercase tracking-wide"
                  title="Estimativa pela política de comissão vigente (mesma régua do fechamento mensal, que é quem oficializa o valor)"
                >
                  Comissão prevista:
                </span>
                {/* Bruto engana (comissão varia por vendedor e por venda) — mostramos
                    a SIMULAÇÃO RN04; sem política configurada, não inventamos valor */}
                {totalPorVendedor.map((v) => (
                  <span key={v.vendedorId} title={`Produção bruta: ${formatCurrency(v.total)}`}>
                    {v.vendedorNome}:{' '}
                    <span className="tabular-nums font-medium text-foreground">
                      {v.expectativaComissao != null
                        ? `~${formatCurrency(v.expectativaComissao)}`
                        : 'sem política'}
                    </span>
                  </span>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {checkOutLocacao && (
        <CheckOutDialog
          locacao={checkOutLocacao}
          open={!!checkOutLocacao}
          onOpenChange={(open) => {
            if (!open) {
              setCheckOutLocacao(null)
              // O check-out pode ter finalizado a locação — atualiza a prancheta
              queryClient.invalidateQueries({ queryKey: ['controle-do-dia'] })
            }
          }}
        />
      )}

      {horarioLinha && (
        <MudarHorarioDialog
          linha={horarioLinha}
          onOpenChange={(open) => !open && setHorarioLinha(null)}
        />
      )}

      {vendedorLinha && (
        <TrocarVendedorDialog
          linha={vendedorLinha}
          vendedores={vendedores}
          carregandoVendedores={carregandoVendedores}
          onOpenChange={(open) => !open && setVendedorLinha(null)}
        />
      )}

      {embarqueLinha?.reservaId && (
        <EmbarqueDialog
          reservaId={embarqueLinha.reservaId}
          modeloId={embarqueLinha.modeloId ?? undefined}
          open={!!embarqueLinha}
          onOpenChange={(open) => !open && setEmbarqueLinha(null)}
          onEmbarcado={() => {
            queryClient.invalidateQueries({ queryKey: ['controle-do-dia'] })
            queryClient.invalidateQueries({ queryKey: ['jetskis'] })
          }}
        />
      )}

      {pagamentoLinha && (
        <PagamentoDialog
          open={!!pagamentoLinha}
          onOpenChange={(open) => !open && setPagamentoLinha(null)}
          titulo={pagamentoLinha.tipo === 'RESERVA' ? 'Receber pagamento' : 'Registrar pagamento'}
          descricao={`${pagamentoLinha.clienteNome || 'Cliente'} — ${
            pagamentoLinha.jetskiSerie || pagamentoLinha.modeloNome || 'sem jet'
          }`}
          extrato={extratoPagamento}
          carregando={carregandoExtrato}
          onRegistrar={registrarPagamento}
        />
      )}
    </div>
  )
}
