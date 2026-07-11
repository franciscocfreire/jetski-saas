'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2, Receipt } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { useToast } from '@/hooks/use-toast'
import { platformService } from '@/lib/api/services/platform'

const brl = (v: number) => v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
const mesAno = (iso: string) => { const [y, m] = iso.split('-'); return `${m}/${y}` }

/** Fila de faturas EM_CONFERENCIA — conferir o txid no extrato antes de confirmar. */
export function FaturasPendentesCard({ enabled }: { enabled: boolean }) {
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const [cancelando, setCancelando] = useState<string | null>(null)
  const [observacao, setObservacao] = useState('')

  const { data: pendentes } = useQuery({
    queryKey: ['platform', 'faturas-pendentes'],
    queryFn: () => platformService.faturasPendentes(),
    enabled,
  })

  const decidido = (msg: string) => {
    queryClient.invalidateQueries({ queryKey: ['platform', 'faturas-pendentes'] })
    toast({ title: msg })
    setCancelando(null)
    setObservacao('')
  }
  const falhou = (e: unknown) => {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    toast({ title: 'Falha na operação', description: msg ?? 'Erro inesperado', variant: 'destructive' })
  }

  const confirmar = useMutation({
    mutationFn: (p: { tenantId: string; id: string }) =>
      platformService.confirmarFatura(p.tenantId, p.id),
    onSuccess: () => decidido('Fatura confirmada — PAGA'),
    onError: falhou,
  })
  const cancelar = useMutation({
    mutationFn: (p: { tenantId: string; id: string }) =>
      platformService.cancelarFatura(p.tenantId, p.id, observacao),
    onSuccess: () => decidido('Fatura cancelada'),
    onError: falhou,
  })

  if (!pendentes || pendentes.length === 0) return null

  return (
    <Card className="border-warning/40">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Receipt className="size-5" /> Faturas aguardando conferência
        </CardTitle>
        <CardDescription>
          Confira o PIX no extrato pelo número da transação antes de confirmar.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Empresa</TableHead>
              <TableHead>Competência</TableHead>
              <TableHead className="text-right">Valor</TableHead>
              <TableHead>Transação informada</TableHead>
              <TableHead className="text-right">Ações</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {pendentes.map((p) => (
              <TableRow key={p.fatura.id}>
                <TableCell className="font-medium">{p.razaoSocial}</TableCell>
                <TableCell>{mesAno(p.fatura.competencia)}</TableCell>
                <TableCell className="text-right tabular-nums">{brl(p.fatura.valor)}</TableCell>
                <TableCell className="font-mono text-xs">{p.fatura.txidInformado ?? '—'}</TableCell>
                <TableCell className="text-right">
                  {cancelando === p.fatura.id ? (
                    <div className="flex items-center justify-end gap-2">
                      <Input
                        className="h-8 w-48"
                        placeholder="Motivo do cancelamento"
                        value={observacao}
                        onChange={(e) => setObservacao(e.target.value)}
                      />
                      <Button
                        variant="destructive" size="sm"
                        disabled={!observacao.trim() || cancelar.isPending}
                        onClick={() => cancelar.mutate({ tenantId: p.tenantId, id: p.fatura.id })}
                      >
                        Cancelar fatura
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setCancelando(null)}>Voltar</Button>
                    </div>
                  ) : (
                    <div className="flex justify-end gap-2">
                      <Button
                        size="sm"
                        disabled={confirmar.isPending}
                        onClick={() => confirmar.mutate({ tenantId: p.tenantId, id: p.fatura.id })}
                      >
                        {confirmar.isPending && <Loader2 className="mr-1 size-4 animate-spin" />}
                        Confirmar pagamento
                      </Button>
                      <Button variant="outline" size="sm" onClick={() => setCancelando(p.fatura.id)}>
                        Cancelar…
                      </Button>
                    </div>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}
