'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Anchor, FileDown, GraduationCap, Handshake, Loader2, Lock, LockOpen, Mail, ShieldCheck, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { emissaoDelegadaService, instrutoresService } from '@/lib/api/services'
import type { EmissaoDelegada, VinculoEmissao } from '@/lib/api/services/emissao-delegada'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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

const STATUS_BADGE: Record<VinculoEmissao['status'], { label: string; className: string }> = {
  CONVIDADO: { label: 'Convite pendente', className: 'bg-amber-100 text-amber-900' },
  ATIVO: { label: 'Ativa', className: 'bg-emerald-100 text-emerald-900' },
  BLOQUEADO: { label: 'Bloqueada pela EAMA', className: 'bg-red-100 text-red-900' },
  REVOGADO: { label: 'Revogada', className: 'bg-muted text-muted-foreground' },
}

const dataBr = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—'

function errMsg(e: unknown): string {
  const r = e as { response?: { data?: { message?: string } }; message?: string }
  return r?.response?.data?.message ?? r?.message ?? 'Erro inesperado'
}

export default function EmissaoDelegadaPage() {
  const qc = useQueryClient()
  const [slug, setSlug] = useState('')
  const [papel, setPapel] = useState<'OPERADORA' | 'EMISSORA'>('OPERADORA')
  const [aceitando, setAceitando] = useState<VinculoEmissao | null>(null)
  const [termoOk, setTermoOk] = useState(false)
  const [designando, setDesignando] = useState<VinculoEmissao | null>(null)

  const { data: vinculos, isLoading } = useQuery({
    queryKey: ['vinculos-emissao'],
    queryFn: () => emissaoDelegadaService.listVinculos(),
  })
  const { data: termo } = useQuery({
    queryKey: ['vinculos-emissao-termo'],
    queryFn: () => emissaoDelegadaService.termo(),
  })

  const souEmissora = (vinculos ?? []).some((v) => v.papel === 'EMISSORA' && v.status !== 'REVOGADO')

  const invalidar = () => {
    qc.invalidateQueries({ queryKey: ['vinculos-emissao'] })
    qc.invalidateQueries({ queryKey: ['emissoes-delegadas'] })
  }

  const convidar = useMutation({
    mutationFn: () => emissaoDelegadaService.convidar(slug.trim(), papel),
    onSuccess: () => {
      toast.success('Convite enviado — aguarde o aceite da empresa parceira.')
      setSlug('')
      invalidar()
    },
    onError: (e) => toast.error(errMsg(e)),
  })
  const aceitar = useMutation({
    mutationFn: (id: string) => emissaoDelegadaService.aceitar(id),
    onSuccess: () => {
      toast.success('Parceria ativada.')
      setAceitando(null)
      setTermoOk(false)
      invalidar()
    },
    onError: (e) => toast.error(errMsg(e)),
  })
  const bloquear = useMutation({
    mutationFn: (id: string) => emissaoDelegadaService.bloquear(id),
    onSuccess: () => {
      toast.success('Emissão em seu nome BLOQUEADA (efeito imediato).')
      invalidar()
    },
    onError: (e) => toast.error(errMsg(e)),
  })
  const liberar = useMutation({
    mutationFn: (id: string) => emissaoDelegadaService.liberar(id),
    onSuccess: () => {
      toast.success('Parceria liberada.')
      invalidar()
    },
    onError: (e) => toast.error(errMsg(e)),
  })
  const revogar = useMutation({
    mutationFn: (id: string) => emissaoDelegadaService.revogar(id),
    onSuccess: () => {
      toast.success('Parceria revogada.')
      invalidar()
    },
    onError: (e) => toast.error(errMsg(e)),
  })

  return (
    <div className="space-y-6 p-4 md:p-6">
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-semibold">
          <Handshake className="h-6 w-6" /> Emissão delegada
        </h1>
        <p className="text-sm text-muted-foreground">
          Parceria entre uma operadora e uma EAMA licenciada da <b>mesma capitania</b>: a
          documentação NORMAM-212 sai em nome da EAMA, com instrutor dela. Os créditos de
          emissão são debitados da operadora; o acerto entre as empresas é feito por fora.
        </p>
      </div>

      <PerfilEmissao />

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Convidar empresa parceira</CardTitle>
          <CardDescription>
            O convite exige aceite do outro lado (com termo de responsabilidade). Ao ativar a
            parceria, os créditos de <b>bônus</b> da operadora são zerados (anti-fraude);
            créditos comprados são preservados.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap items-end gap-3">
          <div className="space-y-1">
            <Label htmlFor="slug-parceiro">Identificador (slug) da empresa</Label>
            <Input
              id="slug-parceiro"
              placeholder="ex.: eama-santos"
              value={slug}
              onChange={(e) => setSlug(e.target.value)}
              className="w-56"
            />
          </div>
          <div className="space-y-1">
            <Label>Meu papel na parceria</Label>
            <Select value={papel} onValueChange={(v) => setPapel(v as 'OPERADORA' | 'EMISSORA')}>
              <SelectTrigger className="w-64">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="OPERADORA">Sou a operadora (convido a EAMA)</SelectItem>
                <SelectItem value="EMISSORA">Sou a EAMA emissora (convido a operadora)</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <Button
            onClick={() => convidar.mutate()}
            disabled={!slug.trim() || convidar.isPending}
          >
            {convidar.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Convidar
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Parcerias</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {isLoading && <p className="text-sm text-muted-foreground">Carregando…</p>}
          {!isLoading && (vinculos ?? []).length === 0 && (
            <p className="text-sm text-muted-foreground">
              Nenhuma parceria ainda. Convide uma empresa acima para começar.
            </p>
          )}
          {(vinculos ?? []).map((v) => (
            <div
              key={v.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3"
            >
              <div className="space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">{v.parceiroNome ?? v.parceiroTenantId}</span>
                  <Badge variant="outline">
                    {v.papel === 'OPERADORA' ? 'parceiro é a EAMA' : 'parceiro é a operadora'}
                  </Badge>
                  <Badge className={STATUS_BADGE[v.status].className}>
                    {STATUS_BADGE[v.status].label}
                  </Badge>
                </div>
                <p className="text-xs text-muted-foreground">
                  Convidado em {dataBr(v.convidadoEm)}
                  {v.aceitoEm ? ` · ativo desde ${dataBr(v.aceitoEm)}` : ''}
                  {v.bloqueadoEm ? ` · bloqueado em ${dataBr(v.bloqueadoEm)}` : ''}
                  {v.revogadoEm ? ` · revogado em ${dataBr(v.revogadoEm)}` : ''}
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                {v.status === 'CONVIDADO' && v.aguardandoMeuAceite && (
                  <Button size="sm" onClick={() => setAceitando(v)}>
                    <ShieldCheck className="mr-1 h-4 w-4" /> Ver termo e aceitar
                  </Button>
                )}
                {v.status === 'CONVIDADO' && !v.aguardandoMeuAceite && (
                  <span className="text-xs text-muted-foreground self-center">
                    Aguardando aceite do parceiro
                  </span>
                )}
                {v.status === 'ATIVO' && v.papel === 'EMISSORA' && (
                  <Button
                    size="sm"
                    variant="destructive"
                    onClick={() => bloquear.mutate(v.id)}
                    disabled={bloquear.isPending}
                  >
                    <Lock className="mr-1 h-4 w-4" /> Bloquear emissão
                  </Button>
                )}
                {v.papel === 'EMISSORA' && (v.status === 'ATIVO' || v.status === 'BLOQUEADO') && (
                  <Button size="sm" variant="outline" onClick={() => setDesignando(v)}>
                    <GraduationCap className="mr-1 h-4 w-4" /> Instrutores designados
                  </Button>
                )}
                {v.status === 'BLOQUEADO' && v.papel === 'EMISSORA' && (
                  <Button
                    size="sm"
                    onClick={() => liberar.mutate(v.id)}
                    disabled={liberar.isPending}
                  >
                    <LockOpen className="mr-1 h-4 w-4" /> Liberar
                  </Button>
                )}
                {v.status !== 'REVOGADO' && (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      if (window.confirm('Revogar a parceria? A ação é definitiva e não devolve bônus estornado.')) {
                        revogar.mutate(v.id)
                      }
                    }}
                    disabled={revogar.isPending}
                  >
                    <XCircle className="mr-1 h-4 w-4" /> Revogar
                  </Button>
                )}
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      {souEmissora && <PainelEmissor />}

      {designando && (
        <DialogDesignacao vinculo={designando} onClose={() => setDesignando(null)} />
      )}

      <Dialog open={!!aceitando} onOpenChange={(open) => !open && setAceitando(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Termo de responsabilidade</DialogTitle>
            <DialogDescription>
              Parceria com {aceitando?.parceiroNome ?? 'a empresa parceira'} — leia antes de ativar.
            </DialogDescription>
          </DialogHeader>
          <div className="max-h-64 overflow-y-auto whitespace-pre-wrap rounded-md border bg-muted/40 p-3 text-sm">
            {aceitando?.termoTexto ?? termo ?? 'Carregando termo…'}
          </div>
          <label className="flex items-start gap-2 text-sm">
            <Checkbox checked={termoOk} onCheckedChange={(c) => setTermoOk(c === true)} />
            <span>
              Li e aceito o termo de responsabilidade. Entendo que, se minha empresa for a
              operadora, os créditos de <b>bônus</b> serão zerados na ativação.
            </span>
          </label>
          <DialogFooter>
            <Button variant="outline" onClick={() => setAceitando(null)}>
              Cancelar
            </Button>
            <Button
              disabled={!termoOk || aceitar.isPending}
              onClick={() => aceitando && aceitar.mutate(aceitando.id)}
            >
              {aceitar.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Aceitar e ativar parceria
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function PerfilEmissao() {
  const qc = useQueryClient()
  const [capitaniaId, setCapitaniaId] = useState<string | null>(null)
  const [registro, setRegistro] = useState<string | null>(null)
  const [validade, setValidade] = useState<string | null>(null)

  const { data: perfil } = useQuery({
    queryKey: ['perfil-emissora'],
    queryFn: () => emissaoDelegadaService.perfilEmissora(),
  })
  const { data: capitanias } = useQuery({
    queryKey: ['capitanias'],
    queryFn: () => emissaoDelegadaService.capitanias(),
  })

  // valores exibidos: edição local sobrepõe o que veio da API
  const capitaniaSel = capitaniaId ?? perfil?.capitaniaId ?? ''
  const registroVal = registro ?? perfil?.eamaRegistro ?? ''
  const validadeVal = validade ?? perfil?.eamaRegistroValidade ?? ''

  const salvar = useMutation({
    mutationFn: () =>
      emissaoDelegadaService.salvarPerfilEmissora({
        capitaniaId: capitaniaSel || undefined,
        eamaRegistro: registroVal || undefined,
        eamaRegistroValidade: validadeVal || undefined,
      }),
    onSuccess: (p) => {
      toast.success(
        p.emissoraHabilitada
          ? 'Perfil de emissão salvo.'
          : 'Perfil de emissão salvo. A habilitação como EAMA emissora depende da validação do Meu Jet.')
      setCapitaniaId(null)
      setRegistro(null)
      setValidade(null)
      qc.invalidateQueries({ queryKey: ['perfil-emissora'] })
    },
    onError: (e) => toast.error(errMsg(e)),
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center gap-2 text-base">
          Perfil de emissão
          {perfil?.emissoraHabilitada ? (
            <Badge className="bg-emerald-100 text-emerald-900">EAMA emissora habilitada</Badge>
          ) : (
            <Badge variant="outline">Não habilitada como emissora</Badge>
          )}
        </CardTitle>
        <CardDescription>
          A capitania é obrigatória para qualquer parceria (os dois lados precisam ser da
          mesma). O registro EAMA é só para quem emite: após preencher, o Meu Jet valida e
          habilita sua empresa como emissora — alterar capitania/registro depois derruba a
          habilitação.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <Label>Capitania</Label>
          <Select value={capitaniaSel} onValueChange={(v) => setCapitaniaId(v)}>
            <SelectTrigger className="w-80">
              <SelectValue placeholder="Selecione a capitania da sua área" />
            </SelectTrigger>
            <SelectContent>
              {(capitanias ?? []).map((c) => (
                <SelectItem key={c.id} value={c.id}>
                  {c.codigo} — {c.nome}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="eama-registro">Registro EAMA (se emissora)</Label>
          <Input
            id="eama-registro"
            placeholder="nº de inscrição na Capitania"
            value={registroVal}
            onChange={(e) => setRegistro(e.target.value)}
            className="w-56"
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="eama-validade">Validade do registro</Label>
          <Input
            id="eama-validade"
            type="date"
            value={validadeVal}
            onChange={(e) => setValidade(e.target.value)}
            className="w-44"
          />
        </div>
        <Button onClick={() => salvar.mutate()} disabled={salvar.isPending || !capitaniaSel}>
          {salvar.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Salvar perfil
        </Button>
      </CardContent>
    </Card>
  )
}

function DialogDesignacao({ vinculo, onClose }: { vinculo: VinculoEmissao; onClose: () => void }) {
  const qc = useQueryClient()
  const [selecionados, setSelecionados] = useState<Set<string> | null>(null)

  const { data: meusInstrutores } = useQuery({
    queryKey: ['instrutores-designacao'],
    queryFn: () => instrutoresService.list(),
  })
  const { data: designados } = useQuery({
    queryKey: ['instrutores-designados', vinculo.id],
    queryFn: () => emissaoDelegadaService.instrutoresDesignados(vinculo.id),
  })

  // Inicializa a seleção com os designados atuais assim que ambos carregarem
  const selecao =
    selecionados ?? new Set((designados ?? []).map((d) => d.id))

  const alternar = (id: string) => {
    const nova = new Set(selecao)
    if (nova.has(id)) {
      nova.delete(id)
    } else {
      nova.add(id)
    }
    setSelecionados(nova)
  }

  const salvar = useMutation({
    mutationFn: () => emissaoDelegadaService.designarInstrutores(vinculo.id, [...selecao]),
    onSuccess: (res) => {
      toast.success(
        res.length === 0
          ? 'Sem designação — a operadora vê todos os instrutores ativos.'
          : `${res.length} instrutor(es) designado(s) para a parceria.`)
      qc.invalidateQueries({ queryKey: ['instrutores-designados', vinculo.id] })
      onClose()
    },
    onError: (e) => toast.error(errMsg(e)),
  })

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Instrutores designados</DialogTitle>
          <DialogDescription>
            Escolha quais instrutores da sua EAMA atendem {vinculo.parceiroNome ?? 'a operadora'}.
            A operadora só enxerga (e só emite com) os designados. <b>Nenhum selecionado</b> ={' '}
            todos os instrutores ativos ficam disponíveis.
          </DialogDescription>
        </DialogHeader>
        <div className="max-h-64 space-y-2 overflow-y-auto">
          {(meusInstrutores ?? []).length === 0 && (
            <p className="text-sm text-muted-foreground">Nenhum instrutor cadastrado.</p>
          )}
          {(meusInstrutores ?? []).map((i) => (
            <label key={i.id} className="flex items-center gap-2 text-sm">
              <Checkbox checked={selecao.has(i.id)} onCheckedChange={() => alternar(i.id)} />
              <span>{i.nome}{i.cha ? ` — CHA ${i.cha}` : ''}</span>
            </label>
          ))}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => salvar.mutate()} disabled={salvar.isPending}>
            {salvar.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Salvar designação
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function PainelEmissor() {
  const qc = useQueryClient()
  const { data: emissoes, isLoading } = useQuery({
    queryKey: ['emissoes-delegadas'],
    queryFn: () => emissaoDelegadaService.listEmissoes(),
  })
  const { data: contagens } = useQuery({
    queryKey: ['emissoes-delegadas-contagens'],
    queryFn: () => emissaoDelegadaService.contagens(),
  })

  const reenviar = useMutation({
    mutationFn: ({ id, destino }: { id: string; destino?: string }) =>
      emissaoDelegadaService.reenviar(id, destino),
    onSuccess: (e: EmissaoDelegada) => {
      toast.success(`Reenviado para ${e.reenviadoPara}.`)
      qc.invalidateQueries({ queryKey: ['emissoes-delegadas'] })
    },
    onError: (e) => toast.error(errMsg(e)),
  })

  const baixar = async (id: string) => {
    try {
      const url = await emissaoDelegadaService.downloadUrl(id)
      window.open(url, '_blank')
    } catch (e) {
      toast.error(errMsg(e))
    }
  }

  const pedirReenvio = (em: EmissaoDelegada) => {
    const destino = window.prompt(
      'Reenviar para (deixe vazio para usar o e-mail da Capitania configurado):', '')
    if (destino === null) return
    reenviar.mutate({ id: em.id, destino: destino.trim() || undefined })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Anchor className="h-4 w-4" /> Emissões em meu nome
        </CardTitle>
        <CardDescription>
          Tudo que as operadoras parceiras emitiram em nome da sua EAMA — com reenvio à
          Capitania sem re-emissão e sem novo crédito. Use as contagens mensais para o
          acerto financeiro com a parceira.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {(contagens ?? []).length > 0 && (
          <div className="flex flex-wrap gap-2">
            {(contagens ?? []).map((c) => (
              <Badge key={`${c.operadoraTenantId}-${c.mes}`} variant="outline">
                {c.mes} · {c.operadoraNome ?? 'operadora'}: <b className="ml-1">{c.total}</b>
              </Badge>
            ))}
          </div>
        )}
        {isLoading && <p className="text-sm text-muted-foreground">Carregando…</p>}
        {!isLoading && (emissoes ?? []).length === 0 && (
          <p className="text-sm text-muted-foreground">Nenhuma emissão em seu nome ainda.</p>
        )}
        {(emissoes ?? []).length > 0 && (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Emitido em</TableHead>
                  <TableHead>Operadora</TableHead>
                  <TableHead>Condutor</TableHead>
                  <TableHead>Instrutor</TableHead>
                  <TableHead>GRU</TableHead>
                  <TableHead>Reenvio</TableHead>
                  <TableHead className="text-right">Ações</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(emissoes ?? []).map((e) => (
                  <TableRow key={e.id}>
                    <TableCell>{dataBr(e.emitidoEm)}</TableCell>
                    <TableCell>{e.operadoraNome ?? '—'}</TableCell>
                    <TableCell>
                      {e.condutorNome ?? '—'}
                      {e.condutorCpf ? (
                        <span className="block text-xs text-muted-foreground">{e.condutorCpf}</span>
                      ) : null}
                    </TableCell>
                    <TableCell>{e.instrutorNome ?? '—'}</TableCell>
                    <TableCell>{e.gruNumero ?? '—'}</TableCell>
                    <TableCell>
                      {e.reenviadoEm ? (
                        <span className="text-xs">
                          {dataBr(e.reenviadoEm)}
                          <span className="block text-muted-foreground">{e.reenviadoPara}</span>
                        </span>
                      ) : (
                        '—'
                      )}
                    </TableCell>
                    <TableCell className="space-x-1 text-right">
                      <Button size="sm" variant="outline" onClick={() => baixar(e.id)}>
                        <FileDown className="mr-1 h-4 w-4" /> PDF
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => pedirReenvio(e)}
                        disabled={reenviar.isPending}
                      >
                        <Mail className="mr-1 h-4 w-4" /> Reenviar
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
