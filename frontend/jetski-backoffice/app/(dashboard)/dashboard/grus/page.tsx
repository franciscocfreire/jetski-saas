'use client'

import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Landmark, Search, Loader2, Mail, Copy, Check, Eye } from 'lucide-react'
import { toast } from 'sonner'
import { useTenantStore } from '@/lib/store/tenant-store'
import { documentosService, grusService } from '@/lib/api/services'
import { formatCurrency } from '@/lib/utils'
import { GruReservaSheet } from '@/components/grus/gru-reserva-sheet'
import type { Gru } from '@/lib/api/types'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * Módulo GRUs: ciclo das GRUs emitidas via EMA — geração, pagamento, emissão,
 * ENVIO À MARINHA (a lacuna que este módulo fecha) e confirmação (devolutiva).
 */
export default function GrusPage() {
  const { currentTenant } = useTenantStore()
  const queryClient = useQueryClient()
  const [busca, setBusca] = useState('')
  const [soNaoEnviadas, setSoNaoEnviadas] = useState(false)
  const [copiada, setCopiada] = useState<string | null>(null)
  const [reenviandoId, setReenviandoId] = useState<string | null>(null)
  const [detalhe, setDetalhe] = useState<Gru | null>(null)
  const [sheetAberto, setSheetAberto] = useState(false)

  function abrirDetalhe(g: Gru) {
    setDetalhe(g)
    setSheetAberto(true)
  }

  const { data: grus, isLoading } = useQuery({
    queryKey: ['grus', currentTenant?.id],
    queryFn: () => grusService.list(),
    enabled: !!currentTenant,
  })

  async function reenviar(documentoId: string) {
    try {
      setReenviandoId(documentoId)
      const r = await documentosService.reenviar(documentoId)
      if (r.enviadoMarinha || r.enviadoCliente) {
        const destinos = [r.enviadoMarinha && 'Marinha', r.enviadoCliente && 'cliente']
          .filter(Boolean)
          .join(' e ')
        toast.success(`E-mail enviado para ${destinos}.`)
      } else {
        toast.error('Nenhum e-mail enviado. Verifique SMTP e os e-mails de destino.')
      }
      queryClient.invalidateQueries({ queryKey: ['grus'] })
    } catch (e) {
      console.error('[grus] reenviar falhou', e)
      toast.error('Falha ao reenviar.')
    } finally {
      setReenviandoId(null)
    }
  }

  function copiarGru(numero: string) {
    navigator.clipboard.writeText(numero)
    setCopiada(numero)
    setTimeout(() => setCopiada(null), 1500)
  }

  function fmt(d?: string) {
    if (!d) return '-'
    return new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
  }

  function fmtData(d?: string) {
    if (!d) return '-'
    return new Date(d).toLocaleDateString('pt-BR')
  }

  const linhas = (grus ?? [])
    .filter((g) => !busca || g.clienteNome?.toLowerCase().includes(busca.toLowerCase()))
    .filter((g) => !soNaoEnviadas || (!g.marinhaEnviadaEm && !!g.documentoId))

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Landmark className="h-7 w-7 text-primary" />
        <div>
          <h1 className="text-2xl font-bold">GRUs emitidas</h1>
          <p className="text-sm text-muted-foreground">
            Ciclo de cada GRU (CHA-MTA-E): pagamento, emissão, envio à Marinha e devolutiva.
          </p>
        </div>
      </div>

      <div className="flex flex-wrap items-end gap-4 rounded-lg border p-4">
        <div className="min-w-[220px]">
          <Label className="text-xs">Cliente</Label>
          <div className="relative">
            <Search className="absolute left-2 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-8"
              placeholder="Nome do cliente…"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
            />
          </div>
        </div>
        <label className="flex items-center gap-2 pb-2 text-sm">
          <Checkbox
            checked={soNaoEnviadas}
            onCheckedChange={(v) => setSoNaoEnviadas(v === true)}
          />
          Só não enviadas à Marinha
        </label>
      </div>

      <div className="overflow-x-auto rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Cliente</TableHead>
              <TableHead className="hidden lg:table-cell">Reserva</TableHead>
              <TableHead>GRU</TableHead>
              <TableHead className="hidden md:table-cell">Valor</TableHead>
              <TableHead className="hidden md:table-cell">Gerada em</TableHead>
              <TableHead>Paga</TableHead>
              <TableHead className="hidden sm:table-cell">Emissão</TableHead>
              <TableHead>Marinha</TableHead>
              <TableHead className="hidden sm:table-cell">Confirmada</TableHead>
              <TableHead className="w-[170px]">Ações</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={10}>
                  <Skeleton className="h-8 w-full" />
                </TableCell>
              </TableRow>
            ) : linhas.length === 0 ? (
              <TableRow>
                <TableCell colSpan={10} className="h-24 text-center text-muted-foreground">
                  {soNaoEnviadas
                    ? 'Nenhuma GRU emitida aguardando envio à Marinha. 🎉'
                    : 'Nenhuma GRU emitida ainda.'}
                </TableCell>
              </TableRow>
            ) : (
              linhas.map((g) => (
                <TableRow
                  key={g.reservaId}
                  className="cursor-pointer"
                  onClick={() => abrirDetalhe(g)}
                >
                  <TableCell className="font-medium">{g.clienteNome ?? '-'}</TableCell>
                  <TableCell className="hidden lg:table-cell">
                    <span className="font-mono text-xs uppercase text-muted-foreground">
                      #{g.reservaId.slice(0, 8)}
                    </span>
                  </TableCell>
                  <TableCell onClick={(e) => e.stopPropagation()}>
                    <button
                      type="button"
                      className="flex items-center gap-1 font-mono text-xs hover:text-primary"
                      title="Copiar número da GRU"
                      onClick={() => copiarGru(g.gruNumero)}
                    >
                      {copiada === g.gruNumero ? (
                        <Check size={12} className="text-emerald-600" />
                      ) : (
                        <Copy size={12} />
                      )}
                      {g.gruNumero}
                    </button>
                  </TableCell>
                  <TableCell className="hidden md:table-cell">
                    {g.gruValor != null ? formatCurrency(g.gruValor) : '-'}
                  </TableCell>
                  <TableCell className="hidden md:table-cell">{fmt(g.gruGeradaEm)}</TableCell>
                  <TableCell>
                    {g.gruPago ? (
                      <Badge className="bg-emerald-100 text-emerald-800 hover:bg-emerald-100">
                        Paga{g.gruPagoEm ? ` ${fmtData(g.gruPagoEm)}` : ''}
                      </Badge>
                    ) : (
                      <Badge variant="secondary">Não paga</Badge>
                    )}
                  </TableCell>
                  <TableCell className="hidden sm:table-cell">
                    {g.documentoEmitidoEm ? (
                      <Badge className="bg-emerald-100 text-emerald-800 hover:bg-emerald-100">
                        {fmtData(g.documentoEmitidoEm)}
                      </Badge>
                    ) : (
                      <Badge variant="secondary">Pendente</Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    {g.marinhaEnviadaEm ? (
                      <Badge className="bg-emerald-100 text-emerald-800 hover:bg-emerald-100">
                        Enviado {fmt(g.marinhaEnviadaEm)}
                      </Badge>
                    ) : (
                      <Badge
                        className="bg-amber-100 text-amber-800 hover:bg-amber-100"
                        title="Sem registro de envio — emissões antigas não registravam; use Reenviar."
                      >
                        Não enviado
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell className="hidden sm:table-cell">
                    {g.marinhaConfirmadaEm ? (
                      <Badge className="bg-emerald-100 text-emerald-800 hover:bg-emerald-100">
                        {fmtData(g.marinhaConfirmadaEm)}
                      </Badge>
                    ) : (
                      <Badge variant="secondary">Aguardando</Badge>
                    )}
                  </TableCell>
                  <TableCell onClick={(e) => e.stopPropagation()}>
                    <div className="flex items-center">
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        title="Ver a reserva (consulta)"
                        onClick={() => abrirDetalhe(g)}
                      >
                        <Eye size={14} className="mr-1" />
                        Ver
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        title={
                          g.documentoId
                            ? 'Reenviar por e-mail (Marinha + cliente)'
                            : 'Documentação ainda não emitida'
                        }
                        disabled={!g.documentoId || reenviandoId === g.documentoId}
                        onClick={() => g.documentoId && reenviar(g.documentoId)}
                      >
                        {reenviandoId === g.documentoId ? (
                          <Loader2 size={14} className="mr-1 animate-spin" />
                        ) : (
                          <Mail size={14} className="mr-1" />
                        )}
                        Reenviar
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      <GruReservaSheet gru={detalhe} open={sheetAberto} onOpenChange={setSheetAberto} />
    </div>
  )
}
