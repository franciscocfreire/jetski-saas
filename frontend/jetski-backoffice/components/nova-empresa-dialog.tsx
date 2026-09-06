'use client'

import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { signIn } from 'next-auth/react'
import { CheckCircle2, Loader2, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { tenantService, userTenantsService } from '@/lib/api/services'
import { useTenantStore } from '@/lib/store/tenant-store'

/** Mesma normalização do /signup público: acento fora, não-alfanumérico vira hífen. */
function gerarSlug(nome: string): string {
  return nome
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .substring(0, 30)
}

/**
 * Cadastro de empresa por quem JÁ tem conta.
 *
 * O /signup público recusa e-mail que já é identidade (409) — de propósito: ele é
 * anônimo, e criar vínculo por coincidência de e-mail seria promover alguém a
 * ADMIN_TENANT sem prova de posse. Aqui a pessoa está autenticada, então o vínculo
 * é explícito e a empresa sai com ela como ADMIN_TENANT na hora, sem e-mail de
 * ativação. Nasce PENDENTE_APROVACAO: quem libera é a plataforma.
 */
export function NovaEmpresaDialog({
  trigger,
  open,
  onOpenChange,
  onCriada,
}: {
  /** Botão que abre o diálogo. Omita ao controlar por `open` — dentro de um
   *  dropdown o trigger não serve: o menu fecha e leva o diálogo junto. */
  trigger?: React.ReactNode
  open?: boolean
  onOpenChange?: (aberto: boolean) => void
  /** Chamado após a empresa entrar nas memberships (o gate/dashboard reage). */
  onCriada?: () => void
}) {
  const queryClient = useQueryClient()
  const { setTenants, setCurrentTenant } = useTenantStore()

  const [abertoInterno, setAbertoInterno] = useState(false)
  const controlado = open !== undefined
  const aberto = controlado ? open : abertoInterno
  const setAberto = (o: boolean) => {
    if (!controlado) setAbertoInterno(o)
    onOpenChange?.(o)
  }
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [razaoSocial, setRazaoSocial] = useState('')
  const [slug, setSlug] = useState('')
  const [slugEditado, setSlugEditado] = useState(false)
  const [cnpj, setCnpj] = useState('')
  const [slugLivre, setSlugLivre] = useState<boolean | null>(null)
  const [checandoSlug, setChecandoSlug] = useState(false)

  // Retomada do /signup público: lá o cadastro parou no 409 (e-mail já tem conta) e a
  // pessoa foi para o login. Volta com ?novaEmpresa=1 e o que digitou, para não redigitar.
  useEffect(() => {
    const url = new URL(window.location.href)
    if (url.searchParams.get('novaEmpresa') !== '1') return

    try {
      const rascunho = sessionStorage.getItem('meujet:nova-empresa')
      if (rascunho) {
        const d = JSON.parse(rascunho) as { razaoSocial?: string; slug?: string; cnpj?: string }
        if (d.razaoSocial) setRazaoSocial(d.razaoSocial)
        if (d.slug) {
          setSlug(d.slug)
          setSlugEditado(true) // não deixa o efeito de sugestão sobrescrever
        }
        if (d.cnpj) setCnpj(d.cnpj)
      }
      sessionStorage.removeItem('meujet:nova-empresa')
    } catch {
      /* sessionStorage indisponível: abre vazio, que é o comportamento aceitável */
    }

    setAberto(true)
    // Tira o parâmetro para o diálogo não reabrir a cada refresh/navegação.
    url.searchParams.delete('novaEmpresa')
    window.history.replaceState({}, '', url.toString())
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Domínio do exemplo de vitrine — dev (pegaojet) e prod (meujet) usam o mesmo código.
  const [baseDominio, setBaseDominio] = useState('meujet.com.br')
  useEffect(() => {
    const host = window.location.hostname.replace(/^(app|www)\./, '')
    if (host && !host.includes('localhost') && host.split('.').length >= 2) {
      setBaseDominio(host)
    }
  }, [])

  // Sugere o slug enquanto a pessoa não o editar à mão.
  useEffect(() => {
    if (!slugEditado) setSlug(gerarSlug(razaoSocial))
  }, [razaoSocial, slugEditado])

  // Disponibilidade do slug (debounce) — o backend confere de novo no POST.
  useEffect(() => {
    if (slug.length < 3) {
      setSlugLivre(null)
      return
    }
    setChecandoSlug(true)
    const timer = setTimeout(() => {
      tenantService
        .checkSlugAvailability(slug)
        .then(setSlugLivre)
        .catch(() => setSlugLivre(null))
        .finally(() => setChecandoSlug(false))
    }, 500)
    return () => {
      clearTimeout(timer)
      setChecandoSlug(false)
    }
  }, [slug])

  function limpar() {
    setRazaoSocial('')
    setSlug('')
    setSlugEditado(false)
    setCnpj('')
    setSlugLivre(null)
    setErro(null)
  }

  async function enviar(e: React.FormEvent) {
    e.preventDefault()
    setErro(null)
    setEnviando(true)
    try {
      const criada = await tenantService.createTenant({
        razaoSocial,
        slug,
        cnpj: cnpj || undefined,
      })

      // Recarrega as memberships do servidor: a nova empresa já vem com o papel
      // ADMIN_TENANT e o status real (PENDENTE_APROVACAO), sem inventar o objeto aqui.
      const { tenants } = await userTenantsService.getMyTenants()
      setTenants(tenants)
      const nova = tenants.find((t) => t.id === criada.tenantId)
      if (nova) setCurrentTenant(nova)
      queryClient.invalidateQueries({ queryKey: ['no-tenant-gate'] })

      toast.success('Empresa cadastrada! Ela entra em análise antes de operar.')
      setAberto(false)
      limpar()
      onCriada?.()

      // O papel ADMIN_TENANT acabou de ser concedido no provedor, mas o token em mãos
      // foi emitido antes disso — e o @PreAuthorize dos controllers lê a role do token,
      // não do banco. Sem renovar, a pessoa entra na empresa nova e toma 403 em quase
      // tudo até o refresh acontecer. Reautentica pelo SSO (não pede senha de novo).
      signIn('keycloak', { callbackUrl: '/dashboard' })
    } catch (err) {
      const e = err as { response?: { data?: { message?: string } } }
      setErro(e.response?.data?.message ?? 'Não foi possível cadastrar a empresa. Tente novamente.')
    } finally {
      setEnviando(false)
    }
  }

  const podeEnviar =
    razaoSocial.trim().length >= 3 && slug.length >= 3 && slugLivre !== false && !enviando

  return (
    <Dialog
      open={aberto}
      onOpenChange={(o) => {
        setAberto(o)
        if (!o) limpar()
      }}
    >
      {trigger && <DialogTrigger asChild>{trigger}</DialogTrigger>}
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Cadastrar empresa</DialogTitle>
          <DialogDescription>
            Você fica como administrador dela. A empresa passa por uma análise da
            plataforma antes de liberar a operação.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={enviar} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="razaoSocial">Razão social</Label>
            <Input
              id="razaoSocial"
              value={razaoSocial}
              onChange={(e) => setRazaoSocial(e.target.value)}
              placeholder="Locadora Praia Grande LTDA"
              maxLength={200}
              autoFocus
              required
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="slug">Identificador</Label>
            <div className="relative">
              <Input
                id="slug"
                value={slug}
                onChange={(e) => {
                  setSlugEditado(true)
                  setSlug(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))
                  setSlugLivre(null)
                }}
                placeholder="praia-grande"
                maxLength={50}
                required
              />
              <span className="absolute right-3 top-1/2 -translate-y-1/2">
                {checandoSlug && <Loader2 className="size-4 animate-spin text-muted-foreground" />}
                {!checandoSlug && slugLivre === true && (
                  <CheckCircle2 className="size-4 text-emerald-600" />
                )}
                {!checandoSlug && slugLivre === false && (
                  <XCircle className="size-4 text-destructive" />
                )}
              </span>
            </div>
            <p className="text-xs text-muted-foreground">
              {slugLivre === false
                ? 'Este identificador já está em uso. Escolha outro.'
                : `Endereço da sua vitrine: ${slug || 'sua-empresa'}.${baseDominio}`}
            </p>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="cnpj">
              CNPJ <span className="text-muted-foreground">(opcional)</span>
            </Label>
            <Input
              id="cnpj"
              value={cnpj}
              onChange={(e) => setCnpj(e.target.value)}
              placeholder="00.000.000/0001-00"
              maxLength={18}
            />
          </div>

          {erro && (
            <Alert variant="destructive">
              <AlertDescription>{erro}</AlertDescription>
            </Alert>
          )}

          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => setAberto(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={!podeEnviar}>
              {enviando && <Loader2 className="mr-2 size-4 animate-spin" />}
              Cadastrar empresa
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
