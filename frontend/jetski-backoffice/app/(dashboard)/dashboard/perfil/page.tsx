'use client'

import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BadgeCheck, Camera, KeyRound, Loader2, ShieldCheck, Smartphone, Trash2, UserCircle } from 'lucide-react'
import { signIn } from 'next-auth/react'
import { toast } from 'sonner'
import { useTenantStore } from '@/lib/store/tenant-store'
import { perfilService } from '@/lib/api/services'
import { comprimirImagem } from '@/lib/image-compress'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'

/** Mensagem do backend (BusinessException) quando houver; senão a genérica do axios. */
const msgErro = (e: unknown): string => {
  const err = e as { response?: { data?: { message?: string } }; message?: string }
  return err?.response?.data?.message ?? err?.message ?? 'Erro inesperado'
}

// Avatar é miniatura de exibição — 512px já cobre o dropdown e a página
const PRESET_AVATAR = { maxDimensao: 512, qualidade: 0.8 }

export default function PerfilPage() {
  const queryClient = useQueryClient()
  const { tenants } = useTenantStore()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const { data: perfil, isLoading } = useQuery({
    queryKey: ['user-profile'],
    queryFn: () => perfilService.getMe(),
    staleTime: 5 * 60 * 1000,
    retry: false,
  })

  const [nome, setNome] = useState('')
  const [telefone, setTelefone] = useState('')
  const [senhaAtual, setSenhaAtual] = useState('')
  const [novaSenha, setNovaSenha] = useState('')
  const [confirmacao, setConfirmacao] = useState('')

  useEffect(() => {
    if (perfil) {
      setNome(perfil.nome ?? '')
      setTelefone(perfil.telefone ?? '')
    }
  }, [perfil])

  const invalidar = () => queryClient.invalidateQueries({ queryKey: ['user-profile'] })

  const salvarDados = useMutation({
    mutationFn: () => perfilService.updateMe({ nome, telefone: telefone || null }),
    onSuccess: () => {
      invalidar()
      toast.success('Dados salvos', { description: 'Seu perfil foi atualizado.' })
    },
    onError: (e) => toast.error('Erro ao salvar', { description: msgErro(e) }),
  })

  const enviarAvatar = useMutation({
    mutationFn: async (original: File) => {
      const file = await comprimirImagem(original, PRESET_AVATAR).catch(() => original)
      return perfilService.uploadAvatar(file)
    },
    onSuccess: () => {
      invalidar()
      toast.success('Foto atualizada')
    },
    onError: (e) => toast.error('Erro ao enviar foto', { description: msgErro(e) }),
  })

  const removerAvatar = useMutation({
    mutationFn: () => perfilService.deleteAvatar(),
    onSuccess: () => {
      invalidar()
      toast.success('Foto removida')
    },
    onError: (e) => toast.error('Erro ao remover foto', { description: msgErro(e) }),
  })

  const trocarSenha = useMutation({
    mutationFn: () => perfilService.updateSenha({ senhaAtual, novaSenha }),
    onSuccess: () => {
      setSenhaAtual('')
      setNovaSenha('')
      setConfirmacao('')
      toast.success('Senha alterada', {
        description: 'Use a nova senha no próximo login.',
      })
    },
    onError: (e) => toast.error('Erro ao trocar a senha', { description: msgErro(e) }),
  })

  const iniciais =
    (perfil?.nome ?? '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0]!.toUpperCase())
      .join('') || 'U'

  const senhaValida =
    senhaAtual.length > 0 && novaSenha.length >= 8 && novaSenha === confirmacao

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Meu Perfil</h1>
        <p className="text-muted-foreground">
          Seus dados pessoais valem para todas as lojas em que você atua.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <UserCircle className="size-5" />
            Identidade
          </CardTitle>
          <CardDescription>Nome, contato e foto exibidos no sistema.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="flex items-center gap-4">
            <Avatar className="size-20">
              {perfil?.avatarDataUrl && <AvatarImage src={perfil.avatarDataUrl} alt="" />}
              <AvatarFallback className="text-xl">{iniciais}</AvatarFallback>
            </Avatar>
            <div className="flex flex-col gap-2 sm:flex-row">
              <input
                ref={fileInputRef}
                type="file"
                accept="image/png,image/jpeg,image/webp"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0]
                  if (file) enviarAvatar.mutate(file)
                  e.target.value = ''
                }}
              />
              <Button
                variant="outline"
                size="sm"
                disabled={enviarAvatar.isPending}
                onClick={() => fileInputRef.current?.click()}
              >
                {enviarAvatar.isPending ? (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                ) : (
                  <Camera className="mr-2 size-4" />
                )}
                {perfil?.avatarDataUrl ? 'Trocar foto' : 'Enviar foto'}
              </Button>
              {perfil?.avatarDataUrl && (
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={removerAvatar.isPending}
                  onClick={() => removerAvatar.mutate()}
                >
                  <Trash2 className="mr-2 size-4" />
                  Remover
                </Button>
              )}
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="perfil-nome">Nome completo</Label>
              <Input
                id="perfil-nome"
                value={nome}
                onChange={(e) => setNome(e.target.value)}
                maxLength={120}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="perfil-telefone">Telefone</Label>
              <Input
                id="perfil-telefone"
                value={telefone}
                onChange={(e) => setTelefone(e.target.value)}
                placeholder="(11) 99999-9999"
                maxLength={30}
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="perfil-email">E-mail</Label>
              <div className="flex items-center gap-2">
                <Input id="perfil-email" value={perfil?.email ?? ''} disabled />
                {perfil?.emailVerified && (
                  <Badge variant="secondary" className="shrink-0 gap-1">
                    <BadgeCheck className="size-3.5" />
                    Verificado
                  </Badge>
                )}
              </div>
              <p className="text-xs text-muted-foreground">
                O e-mail é o seu login e não pode ser alterado por aqui.
              </p>
            </div>
          </div>

          <Button
            onClick={() => salvarDados.mutate()}
            disabled={salvarDados.isPending || nome.trim().length < 3}
          >
            {salvarDados.isPending && <Loader2 className="mr-2 size-4 animate-spin" />}
            Salvar alterações
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Papéis por loja</CardTitle>
          <CardDescription>Onde você atua e com quais permissões.</CardDescription>
        </CardHeader>
        <CardContent>
          {tenants.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nenhum vínculo com loja.</p>
          ) : (
            <ul className="space-y-3">
              {tenants.map((t) => (
                <li
                  key={t.id}
                  className="flex flex-wrap items-center justify-between gap-2 rounded-md border p-3"
                >
                  <span className="font-medium">{t.razaoSocial ?? t.slug}</span>
                  <span className="flex flex-wrap gap-1">
                    {(t.roles ?? []).map((papel) => (
                      <Badge key={papel} variant="outline">
                        {papel}
                      </Badge>
                    ))}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <KeyRound className="size-5" />
            Segurança
          </CardTitle>
          <CardDescription>Troque a senha de acesso ao backoffice.</CardDescription>
        </CardHeader>
        <CardContent>
          {perfil?.senhaGerenciavel ? (
            <div className="max-w-md space-y-4">
              <div className="space-y-2">
                <Label htmlFor="senha-atual">Senha atual</Label>
                <Input
                  id="senha-atual"
                  type="password"
                  autoComplete="current-password"
                  value={senhaAtual}
                  onChange={(e) => setSenhaAtual(e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="nova-senha">Nova senha</Label>
                <Input
                  id="nova-senha"
                  type="password"
                  autoComplete="new-password"
                  value={novaSenha}
                  onChange={(e) => setNovaSenha(e.target.value)}
                />
                <p className="text-xs text-muted-foreground">Mínimo de 8 caracteres.</p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="confirmacao-senha">Confirmar nova senha</Label>
                <Input
                  id="confirmacao-senha"
                  type="password"
                  autoComplete="new-password"
                  value={confirmacao}
                  onChange={(e) => setConfirmacao(e.target.value)}
                />
                {confirmacao.length > 0 && novaSenha !== confirmacao && (
                  <p className="text-xs text-destructive">As senhas não conferem.</p>
                )}
              </div>
              <Button
                onClick={() => trocarSenha.mutate()}
                disabled={trocarSenha.isPending || !senhaValida}
              >
                {trocarSenha.isPending && <Loader2 className="mr-2 size-4 animate-spin" />}
                Alterar senha
              </Button>
            </div>
          ) : perfil?.idpFederado ? (
            <Alert>
              <AlertTitle>Login pelo Google</AlertTitle>
              <AlertDescription>
                Sua conta entra com o Google — a senha é gerenciada na sua Conta Google, não
                por aqui.
              </AlertDescription>
            </Alert>
          ) : (
            <Alert>
              <AlertTitle>Troca de senha indisponível</AlertTitle>
              <AlertDescription>
                Não foi possível consultar o provedor de login agora. Tente novamente mais
                tarde.
              </AlertDescription>
            </Alert>
          )}
        </CardContent>
      </Card>

      <TwoFactorCard />
    </div>
  )
}

/**
 * Verificação em duas etapas (identidade única no Keycloak). Toggle explícito:
 * cadastrar um fator ativa; "Desativar" remove todos de uma vez (RA custom
 * mj-2fa-disable). Ações que reduzem segurança (remover fator, desativar)
 * levam max_age=0 → o Keycloak reautentica e desafia o próprio fator
 * (step-up). As cerimônias rodam nas telas temadas do Keycloak.
 */
function TwoFactorCard() {
  const { data: fatores, isLoading } = useQuery({
    queryKey: ['user-credentials'],
    queryFn: () => perfilService.getCredentials(),
    staleTime: 60 * 1000,
    retry: false,
  })

  // max_age=0 força reautenticação (step-up); omitido = usa a sessão atual.
  const acao = (kcAction: string, stepUp = false) =>
    signIn(
      'keycloak',
      { callbackUrl: '/dashboard/perfil' },
      stepUp ? { kc_action: kcAction, max_age: '0' } : { kc_action: kcAction },
    )

  const desativar = () => {
    if (
      window.confirm(
        'Desativar a verificação em duas etapas? Todos os fatores serão removidos e ' +
          'você precisará confirmar sua identidade agora.',
      )
    ) {
      acao('mj-2fa-disable', true)
    }
  }

  const rotulo = (tipo: string) =>
    tipo === 'otp' ? 'Aplicativo autenticador (TOTP)' : 'Passkey / chave de segurança'

  const ativo = !!fatores && fatores.length > 0

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <CardTitle className="flex items-center gap-2">
            <ShieldCheck className="size-5" />
            Verificação em duas etapas
          </CardTitle>
          {!isLoading &&
            (ativo ? (
              <Badge className="bg-emerald-600 hover:bg-emerald-600">Ativado</Badge>
            ) : (
              <Badge variant="outline">Desativado</Badge>
            ))}
        </div>
        <CardDescription>
          Vale para todos os acessos da sua conta (código por e-mail, senha e Google).
          Opcional — mas recomendado para quem administra a operação.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {isLoading ? (
          <Skeleton className="h-16 w-full" />
        ) : ativo ? (
          <>
            <ul className="space-y-3">
              {fatores!.map((f) => (
                <li key={f.id} className="flex items-center justify-between rounded-md border p-3">
                  <div className="flex items-center gap-3">
                    {f.type === 'otp' ? (
                      <Smartphone className="size-5 text-muted-foreground" />
                    ) : (
                      <KeyRound className="size-5 text-muted-foreground" />
                    )}
                    <div>
                      <p className="text-sm font-medium">{f.userLabel || rotulo(f.type)}</p>
                      <p className="text-xs text-muted-foreground">
                        {rotulo(f.type)}
                        {f.createdDate
                          ? ` · desde ${new Date(f.createdDate).toLocaleDateString('pt-BR')}`
                          : ''}
                      </p>
                    </div>
                  </div>
                  {/* remover fator = downgrade → step-up */}
                  <Button variant="outline" size="sm" onClick={() => acao(`delete_credential:${f.id}`, true)}>
                    Remover
                  </Button>
                </li>
              ))}
            </ul>
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => acao('CONFIGURE_TOTP')}>
                <Smartphone className="mr-2 size-4" /> Adicionar app autenticador
              </Button>
              <Button variant="outline" onClick={() => acao('webauthn-register')}>
                <KeyRound className="mr-2 size-4" /> Adicionar passkey
              </Button>
              <Button variant="destructive" onClick={desativar}>
                Desativar verificação em duas etapas
              </Button>
            </div>
          </>
        ) : (
          <>
            <p className="text-sm text-muted-foreground">
              Desativada — seu login usa só a primeira etapa. Ative para exigir também um
              aplicativo autenticador ou uma passkey.
            </p>
            <div className="flex flex-wrap gap-2">
              <Button onClick={() => acao('CONFIGURE_TOTP')}>
                <Smartphone className="mr-2 size-4" /> Ativar com app autenticador
              </Button>
              <Button variant="outline" onClick={() => acao('webauthn-register')}>
                <KeyRound className="mr-2 size-4" /> Ativar com passkey
              </Button>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  )
}
