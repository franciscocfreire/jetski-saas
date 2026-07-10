'use client'

import { useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import Link from 'next/link'
import {
  CalendarClock,
  CreditCard,
  FileText,
  IdCard,
  Image as ImageIcon,
  Loader2,
  Mail,
  MapPin,
  Phone,
  Ship,
  Trash2,
  User,
} from 'lucide-react'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '@/components/ui/sheet'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { clientesService, locacoesService } from '@/lib/api/services'
import { WhatsAppLink } from '@/components/whatsapp-link'
import { formatCurrency, formatDate } from '@/lib/utils'
import type { Cliente, ClienteStatusConta, LocacaoStatus } from '@/lib/api/types'

import { CONTA_BADGE, ORIGEM_BADGE } from '@/components/clientes/badges'

const LOCACAO_BADGE: Record<LocacaoStatus, { label: string; variant: 'success' | 'warning' | 'secondary' }> = {
  EM_CURSO: { label: 'Em curso', variant: 'warning' },
  FINALIZADA: { label: 'Finalizada', variant: 'success' },
  CANCELADA: { label: 'Cancelada', variant: 'secondary' },
}

/** Anexos do cliente, na ordem de exibição. */
const ANEXOS: { tipo: 'IDENTIDADE' | 'SELFIE' | 'COMPROVANTE_RESIDENCIA' | 'CHA'; label: string }[] = [
  { tipo: 'IDENTIDADE', label: 'Documento (RG/CNH)' },
  { tipo: 'SELFIE', label: 'Selfie / foto' },
  { tipo: 'COMPROVANTE_RESIDENCIA', label: 'Comprovante de residência' },
  { tipo: 'CHA', label: 'CHA/CHV' },
]

type Endereco = {
  cep?: string
  logradouro?: string
  numero?: string
  complemento?: string
  bairro?: string
  cidade?: string
  uf?: string
}

function parseEndereco(json?: string): Endereco | null {
  if (!json) return null
  try {
    const e = JSON.parse(json) as Endereco
    return e && (e.logradouro || e.cidade || e.cep) ? e : null
  } catch {
    return null
  }
}

function enderecoLinha(e: Endereco): string {
  const l1 = [e.logradouro, e.numero].filter(Boolean).join(', ')
  const l2 = [e.bairro, [e.cidade, e.uf].filter(Boolean).join('/')].filter(Boolean).join(' · ')
  const cep = e.cep ? `CEP ${e.cep}` : ''
  return [l1, e.complemento, l2, cep].filter(Boolean).join(' — ')
}

function Campo({ icon, label, value }: { icon: React.ReactNode; label: string; value?: React.ReactNode }) {
  if (!value) return null
  return (
    <div className="flex items-start gap-2">
      <span className="mt-0.5 text-muted-foreground">{icon}</span>
      <div className="min-w-0 text-sm">
        <span className="text-muted-foreground">{label}: </span>
        <span className="font-medium">{value}</span>
      </div>
    </div>
  )
}

export function ClienteDetailSheet({
  cliente: clienteRow,
  open,
  onOpenChange,
}: {
  cliente: Cliente | null
  open: boolean
  onOpenChange: (v: boolean) => void
}) {
  const clienteId = clienteRow?.id

  // A lista pode ser uma projeção enxuta — busca o cadastro completo (endereço, RG…).
  const { data: clienteFull } = useQuery({
    queryKey: ['cliente', clienteId],
    queryFn: () => clientesService.getById(clienteId!),
    enabled: open && !!clienteId,
  })
  const cliente = clienteFull ?? clienteRow

  // Fotos/comprovantes — baixa cada anexo presente como blob e gera object URL.
  const { data: anexos, isLoading: anexosLoading } = useQuery({
    queryKey: ['cliente-anexos-full', clienteId],
    queryFn: async () => {
      const lista = await clientesService.listarAnexos(clienteId!)
      const presentes = new Set(lista.map((a) => a.tipo))
      const out: Record<string, string> = {}
      for (const { tipo } of ANEXOS) {
        if (!presentes.has(tipo)) continue
        const blob = await clientesService.baixarAnexo(clienteId!, tipo).catch(() => null)
        if (blob) out[tipo] = URL.createObjectURL(blob)
      }
      return out
    },
    enabled: open && !!clienteId,
  })

  // Libera os object URLs ao trocar de cliente / desmontar.
  useEffect(() => {
    return () => {
      if (anexos) Object.values(anexos).forEach((u) => URL.revokeObjectURL(u))
    }
  }, [anexos])

  const qc = useQueryClient()
  const apagarAnexo = useMutation({
    mutationFn: (tipo: string) => clientesService.deletarAnexo(clienteId!, tipo),
    onSuccess: () => {
      // Recarrega aqui e nos lugares que checam presença (drawer/pendências).
      qc.invalidateQueries({ queryKey: ['cliente-anexos-full', clienteId] })
      qc.invalidateQueries({ queryKey: ['cliente-anexos-tipos', clienteId] })
      qc.invalidateQueries({ queryKey: ['cliente-anexos', clienteId] })
      toast.success('Anexo apagado.')
    },
    onError: () => toast.error('Falha ao apagar o anexo.'),
  })

  // Histórico de passeios (locações reais do cliente).
  const { data: locacoes, isLoading: locacoesLoading } = useQuery({
    queryKey: ['locacoes-cliente', clienteId],
    queryFn: () => locacoesService.list({ clienteId: clienteId! }),
    enabled: open && !!clienteId,
  })

  if (!cliente) return null

  const endereco = parseEndereco(cliente.enderecoJson)
  const conta = cliente.statusConta ? CONTA_BADGE[cliente.statusConta] : null
  const origem = cliente.origem ? ORIGEM_BADGE[cliente.origem] : null
  const telefone = cliente.telefone || cliente.whatsapp
  const passeios = (locacoes ?? []).slice().sort((a, b) => b.dataCheckIn.localeCompare(a.dataCheckIn))
  const totalGasto = passeios
    .filter((p) => p.status === 'FINALIZADA')
    .reduce((s, p) => s + (p.valorTotal ?? 0), 0)

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="flex w-full flex-col overflow-y-auto sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>{cliente.nome}</SheetTitle>
          <SheetDescription>
            {cliente.documento ? `CPF ${cliente.documento}` : 'Sem CPF cadastrado'}
          </SheetDescription>
        </SheetHeader>

        <div className="mt-3 flex flex-wrap gap-2">
          {conta && <Badge variant={conta.variant}>{conta.label}</Badge>}
          {origem && <Badge variant={origem.variant}>{origem.label}</Badge>}
          {passeios.length > 0 && (
            <Badge variant="outline">
              {passeios.length} {passeios.length === 1 ? 'passeio' : 'passeios'}
            </Badge>
          )}
        </div>

        <Separator className="my-4" />

        {/* Dados cadastrais */}
        <div className="space-y-2">
          <h4 className="text-sm font-semibold">Dados cadastrais</h4>
          <Campo icon={<Mail className="h-4 w-4" />} label="E-mail" value={cliente.email} />
          <Campo
            icon={<Phone className="h-4 w-4" />}
            label="Telefone"
            value={
              telefone ? (
                <span className="inline-flex items-center gap-1.5">
                  {telefone}
                  <WhatsAppLink phone={telefone} nome={cliente.nome} />
                </span>
              ) : undefined
            }
          />
          <Campo
            icon={<IdCard className="h-4 w-4" />}
            label="RG"
            value={cliente.rg ? `${cliente.rg}${cliente.orgaoEmissor ? ` (${cliente.orgaoEmissor})` : ''}` : undefined}
          />
          <Campo
            icon={<CalendarClock className="h-4 w-4" />}
            label="Nascimento"
            value={cliente.dataNascimento ? formatDate(cliente.dataNascimento) : undefined}
          />
          <Campo icon={<User className="h-4 w-4" />} label="Nacionalidade" value={cliente.nacionalidade} />
          <Campo
            icon={<MapPin className="h-4 w-4" />}
            label="Endereço"
            value={endereco ? enderecoLinha(endereco) : undefined}
          />
          {cliente.observacoes && (
            <Campo icon={<FileText className="h-4 w-4" />} label="Obs." value={cliente.observacoes} />
          )}
          {cliente.capturadoPorNome && (
            <Campo
              icon={<User className="h-4 w-4" />}
              label="Capturado por"
              value={cliente.capturadoPorNome}
            />
          )}
        </div>

        <Separator className="my-4" />

        {/* Fotos & comprovantes */}
        <div className="space-y-3">
          <h4 className="flex items-center gap-2 text-sm font-semibold">
            <ImageIcon className="h-4 w-4" /> Fotos &amp; comprovantes
          </h4>
          {anexosLoading ? (
            <p className="text-sm text-muted-foreground">Carregando anexos…</p>
          ) : (
            <div className="grid grid-cols-2 gap-3">
              {ANEXOS.map(({ tipo, label }) => {
                const url = anexos?.[tipo]
                const apagando = apagarAnexo.isPending && apagarAnexo.variables === tipo
                return (
                  <div key={tipo} className="space-y-1">
                    {url ? (
                      <a href={url} target="_blank" rel="noreferrer" className="block">
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img
                          src={url}
                          alt={label}
                          className="aspect-[4/3] w-full rounded-md border object-cover transition hover:opacity-90"
                        />
                      </a>
                    ) : (
                      <div className="flex aspect-[4/3] w-full items-center justify-center rounded-md border border-dashed text-xs text-muted-foreground">
                        não enviado
                      </div>
                    )}
                    <div className="flex items-center justify-between gap-2">
                      <p className="truncate text-xs text-muted-foreground">{label}</p>
                      {url && (
                        <button
                          type="button"
                          title="Apagar anexo"
                          disabled={apagando}
                          onClick={() => {
                            if (window.confirm(`Apagar "${label}" deste cliente? Esta ação não pode ser desfeita.`))
                              apagarAnexo.mutate(tipo)
                          }}
                          className="flex shrink-0 items-center gap-1 rounded p-1.5 text-xs text-red-600 transition hover:bg-red-50 disabled:opacity-50 dark:hover:bg-red-950/40"
                        >
                          {apagando ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            <Trash2 className="h-4 w-4" />
                          )}
                          Apagar
                        </button>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        <Separator className="my-4" />

        {/* Histórico de passeios */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h4 className="flex items-center gap-2 text-sm font-semibold">
              <Ship className="h-4 w-4" /> Histórico de passeios
            </h4>
            {totalGasto > 0 && (
              <span className="flex items-center gap-1 text-xs text-muted-foreground">
                <CreditCard className="h-3.5 w-3.5" /> {formatCurrency(totalGasto)} no total
              </span>
            )}
          </div>

          {locacoesLoading ? (
            <p className="text-sm text-muted-foreground">Carregando histórico…</p>
          ) : passeios.length === 0 ? (
            <p className="rounded-md border border-dashed py-6 text-center text-sm text-muted-foreground">
              Nenhum passeio realizado ainda.
            </p>
          ) : (
            <div className="divide-y rounded-md border">
              {passeios.map((p) => {
                const badge = LOCACAO_BADGE[p.status]
                return (
                  <div key={p.id} className="flex items-center gap-3 px-3 py-2 text-sm">
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium">
                        {p.jetskiModeloNome || 'Jetski'}
                        {p.jetskiSerie && (
                          <span className="ml-1 font-normal text-muted-foreground">· {p.jetskiSerie}</span>
                        )}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {formatDate(p.dataCheckIn)}
                        {p.minutosUsados != null && ` · ${p.minutosUsados} min`}
                      </p>
                    </div>
                    <div className="shrink-0 text-right">
                      {p.valorTotal != null && (
                        <p className="font-medium tabular-nums">{formatCurrency(p.valorTotal)}</p>
                      )}
                      <Badge variant={badge.variant} className="mt-0.5">
                        {badge.label}
                      </Badge>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        <Separator className="my-4" />

        <Button asChild variant="outline" className="w-full">
          <Link href={`/dashboard/documentos?clienteId=${cliente.id}`}>
            <FileText className="mr-2 h-4 w-4" /> Ver documentos emitidos
          </Link>
        </Button>
      </SheetContent>
    </Sheet>
  )
}
