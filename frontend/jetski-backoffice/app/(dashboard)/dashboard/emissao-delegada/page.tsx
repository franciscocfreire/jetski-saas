'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Anchor, FileDown, GraduationCap, Handshake, Loader2, Lock, LockOpen, Mail, ShieldCheck, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { emissaoDelegadaService, instrutoresService } from '@/lib/api/services'
import type { EmissaoDelegada, VinculoEmissao } from '@/lib/api/services/emissao-delegada'
import { useModoEmissao } from '@/lib/hooks/use-modo-emissao'
import { AprovacaoInstrutorBadge } from '@/components/emissao/aprovacao-instrutor-badge'
import { RedeEmissao } from '@/components/emissao/rede-emissao'
import { useTenantStore } from '@/lib/store/tenant-store'
import Link from 'next/link'
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
  // Papel exclusivo (§8.M): uma empresa é emissora OU delegada. Com parceria viva como
  // operadora não há convite a fazer; quem já é emissora só convida como emissora.
  const { info: modo } = useModoEmissao()
  const souOperadoraViva = (vinculos ?? []).some((v) => v.papel === 'OPERADORA' && v.status !== 'REVOGADO')
  const souOperadoraEmVigor = (vinculos ?? []).some(
    (v) => v.papel === 'OPERADORA' && (v.status === 'ATIVO' || v.status === 'BLOQUEADO'))
  const papelConvite: 'OPERADORA' | 'EMISSORA' = souEmissora ? 'EMISSORA' : papel
  const [instrutoresDe, setInstrutoresDe] = useState<VinculoEmissao | null>(null)
  const { currentTenant } = useTenantStore()
  // Emissões do mês por operadora, para a árvore (mesma query do painel do emissor).
  const { data: contagens } = useQuery({
    queryKey: ['emissoes-delegadas-contagens'],
    queryFn: () => emissaoDelegadaService.contagens(),
    enabled: souEmissora,
  })

  const invalidar = () => {
    qc.invalidateQueries({ queryKey: ['vinculos-emissao'] })
    qc.invalidateQueries({ queryKey: ['emissoes-delegadas'] })
  }

  const convidar = useMutation({
    mutationFn: () => emissaoDelegadaService.convidar(slug.trim(), papelConvite),
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

      <PerfilEmissao operadoraDe={souOperadoraEmVigor ? (modo?.emissoraNome ?? 'EAMA parceira') : null} />

      <RedeEmissao
        minhaEmpresa={currentTenant?.razaoSocial ?? 'Sua empresa'}
        vinculos={vinculos ?? []}
        contagens={contagens}
      />

      {souOperadoraViva ? (
        <Card data-testid="delegada-convite-bloqueado">
          <CardHeader>
            <CardTitle className="text-base">Convidar empresa parceira</CardTitle>
            <CardDescription>
              Sua empresa já é operadora (delegada) de uma parceria. Uma empresa é EAMA
              emissora <b>ou</b> delegada: para trocar de EAMA ou voltar a emitir em nome
              próprio, revogue a parceria atual.
            </CardDescription>
          </CardHeader>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Convidar empresa parceira</CardTitle>
            <CardDescription>
              O convite exige aceite do outro lado (com termo de responsabilidade). Ao ativar a
              parceria, os créditos de <b>bônus</b> da operadora são zerados (anti-fraude);
              créditos comprados são preservados, e a operadora deixa de ser emissora.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap items-end gap-3">
            <div className="space-y-1">
              <Label htmlFor="slug-parceiro">Identificador (slug) da empresa</Label>
              <Input
                id="slug-parceiro"
                data-testid="delegada-convite-slug"
                placeholder="ex.: eama-santos"
                value={slug}
                onChange={(e) => setSlug(e.target.value)}
                className="w-56"
              />
            </div>
            <div className="space-y-1">
              <Label>Meu papel na parceria</Label>
              <Select value={papelConvite} onValueChange={(v) => setPapel(v as 'OPERADORA' | 'EMISSORA')}>
                <SelectTrigger className="w-64" data-testid="delegada-convite-papel">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem
                    value="OPERADORA"
                    data-testid="delegada-convite-papel-operadora"
                    disabled={souEmissora}
                  >
                    Sou a operadora (convido a EAMA)
                  </SelectItem>
                  <SelectItem value="EMISSORA" data-testid="delegada-convite-papel-emissora">
                    Sou a EAMA emissora (convido a operadora)
                  </SelectItem>
                </SelectContent>
              </Select>
              {souEmissora && (
                <p className="text-xs text-muted-foreground">
                  Sua empresa é EAMA emissora: só convida operadoras.
                </p>
              )}
            </div>
            <Button
              data-testid="delegada-convite-enviar"
              onClick={() => convidar.mutate()}
              disabled={!slug.trim() || convidar.isPending}
            >
              {convidar.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Convidar
            </Button>
          </CardContent>
        </Card>
      )}

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
              data-testid="delegada-parceria"
              data-vinculo-id={v.id}
              data-status={v.status}
              data-papel={v.papel}
              data-parceiro={v.parceiroNome ?? ''}
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3"
            >
              <div className="space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">{v.parceiroNome ?? v.parceiroTenantId}</span>
                  <Badge variant="outline">
                    {v.papel === 'OPERADORA' ? 'parceiro é a EAMA' : 'parceiro é a operadora'}
                  </Badge>
                  <Badge data-testid="delegada-parceria-status" className={STATUS_BADGE[v.status].className}>
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
                  <Button size="sm" data-testid="delegada-parceria-aceitar" onClick={() => setAceitando(v)}>
                    <ShieldCheck className="mr-1 h-4 w-4" /> Ver termo e aceitar
                  </Button>
                )}
                {v.status === 'CONVIDADO' && !v.aguardandoMeuAceite && (
                  <span data-testid="delegada-parceria-aguardando" className="text-xs text-muted-foreground self-center">
                    Aguardando aceite do parceiro
                  </span>
                )}
                {v.status === 'ATIVO' && v.papel === 'EMISSORA' && (
                  <Button
                    size="sm"
                    variant="destructive"
                    data-testid="delegada-parceria-bloquear"
                    onClick={() => bloquear.mutate(v.id)}
                    disabled={bloquear.isPending}
                  >
                    <Lock className="mr-1 h-4 w-4" /> Bloquear emissão
                  </Button>
                )}
                {v.papel === 'EMISSORA' && (v.status === 'ATIVO' || v.status === 'BLOQUEADO') && (
                  <Button size="sm" variant="outline" data-testid="delegada-parceria-designar" onClick={() => setDesignando(v)}>
                    <GraduationCap className="mr-1 h-4 w-4" /> Instrutores designados
                  </Button>
                )}
                {v.papel === 'EMISSORA' && (v.status === 'ATIVO' || v.status === 'BLOQUEADO') && (
                  <Button
                    size="sm"
                    variant="outline"
                    data-testid="delegada-parceria-instrutores-operadora"
                    onClick={() => setInstrutoresDe(v)}
                  >
                    <ShieldCheck className="mr-1 h-4 w-4" /> Instrutores da operadora
                  </Button>
                )}
                {v.papel === 'OPERADORA' && (v.status === 'ATIVO' || v.status === 'BLOQUEADO') && (
                  <Button asChild size="sm" variant="outline">
                    <Link href="/dashboard/instrutores" data-testid="delegada-parceria-meus-instrutores">
                      <GraduationCap className="mr-1 h-4 w-4" /> Meus instrutores
                    </Link>
                  </Button>
                )}
                {v.status === 'BLOQUEADO' && v.papel === 'EMISSORA' && (
                  <Button
                    size="sm"
                    data-testid="delegada-parceria-liberar"
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
                    data-testid="delegada-parceria-revogar"
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

      {instrutoresDe && (
        <DialogInstrutoresOperadora vinculo={instrutoresDe} onClose={() => setInstrutoresDe(null)} />
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
            <Checkbox
              data-testid="delegada-termo-aceito"
              checked={termoOk}
              onCheckedChange={(c) => setTermoOk(c === true)}
            />
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
              data-testid="delegada-termo-confirmar"
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

/**
 * Perfil de emissão. {@code operadoraDe} = nome da EAMA quando a empresa é operadora de
 * parceria em vigor: aí ela não é emissora (§8.M) e só a capitania importa.
 */
function PerfilEmissao({ operadoraDe }: { operadoraDe: string | null }) {
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

  // Delegada (§8.M): a capitania é a da EAMA, herdada no aceite e travada — nada a editar.
  if (operadoraDe) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex flex-wrap items-center gap-2 text-base">
            Perfil de emissão
            <Badge data-testid="delegada-perfil-operadora" className="bg-sky-100 text-sky-900">
              Operadora delegada de {operadoraDe}
            </Badge>
          </CardTitle>
          <CardDescription>
            Sua empresa emite em nome de <b>{operadoraDe}</b> e, enquanto a parceria existir, não é
            EAMA — uma empresa é emissora <b>ou</b> delegada. A capitania é a da EAMA e não pode
            ser alterada; para mudar, revogue a parceria.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div data-testid="delegada-perfil-capitania-herdada" className="text-sm">
            <span className="text-muted-foreground">Capitania: </span>
            <b>
              {perfil?.capitaniaCodigo
                ? `${perfil.capitaniaCodigo} — ${perfil.capitaniaNome ?? ''}`
                : 'a da EAMA parceira'}
            </b>
            <span className="text-muted-foreground"> (herdada de {operadoraDe})</span>
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center gap-2 text-base">
          Perfil de emissão
          {operadoraDe ? (
            <Badge data-testid="delegada-perfil-operadora" className="bg-sky-100 text-sky-900">
              Operadora delegada de {operadoraDe}
            </Badge>
          ) : perfil?.emissoraHabilitada ? (
            <Badge data-testid="delegada-perfil-habilitada" className="bg-emerald-100 text-emerald-900">
              EAMA emissora habilitada
            </Badge>
          ) : perfil?.eamaRegistro ? (
            <Badge variant="outline" data-testid="delegada-perfil-em-validacao">
              Registro EAMA em validação
            </Badge>
          ) : (
            <Badge variant="outline" data-testid="delegada-perfil-nao-eama">
              Não é EAMA
            </Badge>
          )}
        </CardTitle>
        <CardDescription>
          {operadoraDe ? (
            <>
              Sua empresa emite em nome de <b>{operadoraDe}</b> e, enquanto a parceria existir,
              não é EAMA emissora — uma empresa é emissora <b>ou</b> delegada. Aqui só a
              capitania importa (os dois lados precisam ser da mesma).
            </>
          ) : (
            <>
              Capitania e registro EAMA são para quem emite em nome próprio: após preencher, o
              Meu Jet valida e habilita sua empresa como EAMA emissora — alterar capitania ou
              registro depois derruba a habilitação. Empresa que não é EAMA não precisa preencher:
              ao aceitar a parceria com uma EAMA, a capitania passa a ser a dela.
            </>
          )}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <Label>Capitania</Label>
          <Select value={capitaniaSel} onValueChange={(v) => setCapitaniaId(v)}>
            <SelectTrigger className="w-80" data-testid="delegada-perfil-capitania">
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
        {!operadoraDe && (
          <>
            <div className="space-y-1">
              <Label htmlFor="eama-registro">Registro EAMA (se emissora)</Label>
              <Input
                id="eama-registro"
                data-testid="delegada-perfil-registro"
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
          </>
        )}
        <Button
          data-testid="delegada-perfil-salvar"
          onClick={() => salvar.mutate()}
          disabled={salvar.isPending || !capitaniaSel}
        >
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
          ? 'Nenhum instrutor designado — a operadora fica só com os instrutores próprios que você aprovou.'
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
            nenhum instrutor da sua EAMA: a operadora fica só com os instrutores próprios que
            você aprovar.
          </DialogDescription>
        </DialogHeader>
        <div className="max-h-64 space-y-2 overflow-y-auto">
          {(meusInstrutores ?? []).length === 0 && (
            <p className="text-sm text-muted-foreground">Nenhum instrutor cadastrado.</p>
          )}
          {(meusInstrutores ?? []).map((i) => (
            <label key={i.id} className="flex items-center gap-2 text-sm">
              <Checkbox
                data-testid="delegada-designacao-instrutor"
                data-instrutor-id={i.id}
                checked={selecao.has(i.id)}
                onCheckedChange={() => alternar(i.id)}
              />
              <span>{i.nome}{i.cha ? ` — CHA ${i.cha}` : ''}</span>
            </label>
          ))}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button data-testid="delegada-designacao-salvar" onClick={() => salvar.mutate()} disabled={salvar.isPending}>
            {salvar.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Salvar designação
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/**
 * EAMA avalia os instrutores que a operadora submeteu (V070). Pela NORMAM-212 a EAMA
 * responde pelo instrutor: sem aprovação ele não assina emissões em nome dela, e ela
 * pode remover a aprovação a qualquer momento.
 */
function DialogInstrutoresOperadora({ vinculo, onClose }: { vinculo: VinculoEmissao; onClose: () => void }) {
  const qc = useQueryClient()
  const { data: pedidos, isLoading } = useQuery({
    queryKey: ['instrutores-operadora', vinculo.id],
    queryFn: () => emissaoDelegadaService.instrutoresOperadora(vinculo.id),
  })

  type Decisao = 'APROVAR' | 'REJEITAR' | 'REMOVER'
  const decidir = useMutation({
    mutationFn: ({ instrutorId, decisao, motivo }: { instrutorId: string; decisao: Decisao; motivo?: string }) =>
      emissaoDelegadaService.decidirInstrutor(vinculo.id, instrutorId, decisao, motivo),
    onSuccess: (_r, vars) => {
      toast.success(
        vars.decisao === 'APROVAR'
          ? 'Instrutor aprovado — já pode assinar emissões em seu nome.'
          : vars.decisao === 'REJEITAR'
            ? 'Pedido rejeitado.'
            : 'Instrutor removido — não assina mais emissões em seu nome.')
      qc.invalidateQueries({ queryKey: ['instrutores-operadora', vinculo.id] })
    },
    onError: (e) => toast.error(errMsg(e)),
  })

  const comMotivo = (instrutorId: string, decisao: Decisao) => {
    const motivo = window.prompt(
      decisao === 'REJEITAR' ? 'Motivo da rejeição (opcional):' : 'Motivo da remoção (opcional):', '')
    if (motivo === null) return
    decidir.mutate({ instrutorId, decisao, motivo: motivo.trim() || undefined })
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>Instrutores da operadora</DialogTitle>
          <DialogDescription>
            Pela NORMAM-212 o instrutor responde em nome da sua EAMA. Os instrutores cadastrados
            por {vinculo.parceiroNome ?? 'a operadora'} só assinam emissões em seu nome depois
            da sua aprovação — e você pode removê-los a qualquer momento.
          </DialogDescription>
        </DialogHeader>
        <div className="max-h-[60vh] space-y-3 overflow-y-auto">
          {isLoading && <p className="text-sm text-muted-foreground">Carregando…</p>}
          {!isLoading && (pedidos ?? []).length === 0 && (
            <p className="text-sm text-muted-foreground">
              A operadora ainda não enviou instrutores para a sua aprovação.
            </p>
          )}
          {(pedidos ?? []).map((p) => (
            <div
              key={p.instrutorId}
              data-testid="delegada-instrutor-operadora"
              data-instrutor-id={p.instrutorId}
              data-status={p.status}
              className="flex flex-wrap items-start justify-between gap-3 rounded-lg border p-3"
            >
              <div className="min-w-0 space-y-1 text-sm">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">{p.nome}</span>
                  <AprovacaoInstrutorBadge status={p.status} />
                  {!p.ativo && <Badge variant="outline">Inativo na operadora</Badge>}
                </div>
                <p className="text-xs text-muted-foreground">
                  CPF {p.cpf || '—'} · RG {p.rg || '—'}
                  {p.orgaoEmissor ? ` (${p.orgaoEmissor})` : ''} · CHA {p.cha || '—'}
                  {p.dataEmissao ? ` · identidade emitida em ${p.dataEmissao.split('-').reverse().join('/')}` : ''}
                </p>
                <p className="text-xs text-muted-foreground">
                  Enviado em {dataBr(p.solicitadoEm)}
                  {p.decididoEm ? ` · decidido em ${dataBr(p.decididoEm)}` : ''}
                </p>
                {p.motivo && <p className="text-xs">Motivo: {p.motivo}</p>}
                {p.assinaturaUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={p.assinaturaUrl}
                    alt={`Assinatura de ${p.nome}`}
                    className="mt-1 h-12 rounded border bg-white"
                  />
                ) : (
                  <p className="text-xs text-amber-700">Sem assinatura cadastrada</p>
                )}
              </div>
              <div className="flex flex-wrap gap-2">
                {p.status === 'PENDENTE' && (
                  <>
                    <Button
                      size="sm"
                      data-testid="delegada-instrutor-operadora-aprovar"
                      onClick={() => decidir.mutate({ instrutorId: p.instrutorId, decisao: 'APROVAR' })}
                      disabled={decidir.isPending || !p.ativo}
                    >
                      <ShieldCheck className="mr-1 h-4 w-4" /> Aprovar
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      data-testid="delegada-instrutor-operadora-rejeitar"
                      onClick={() => comMotivo(p.instrutorId, 'REJEITAR')}
                      disabled={decidir.isPending}
                    >
                      <XCircle className="mr-1 h-4 w-4" /> Rejeitar
                    </Button>
                  </>
                )}
                {p.status === 'APROVADO' && (
                  <Button
                    size="sm"
                    variant="destructive"
                    data-testid="delegada-instrutor-operadora-remover"
                    onClick={() => comMotivo(p.instrutorId, 'REMOVER')}
                    disabled={decidir.isPending}
                  >
                    <XCircle className="mr-1 h-4 w-4" /> Remover
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Fechar
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
    <Card data-testid="delegada-painel">
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
              <Badge
                key={`${c.operadoraTenantId}-${c.mes}`}
                variant="outline"
                data-testid="delegada-painel-contagem"
                data-operadora-id={c.operadoraTenantId}
                data-total={c.total}
              >
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
                  <TableRow key={e.id} data-testid="delegada-painel-emissao" data-emissao-id={e.id}>
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
                      <Button size="sm" variant="outline" data-testid="delegada-painel-pdf" onClick={() => baixar(e.id)}>
                        <FileDown className="mr-1 h-4 w-4" /> PDF
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        data-testid="delegada-painel-reenviar"
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
