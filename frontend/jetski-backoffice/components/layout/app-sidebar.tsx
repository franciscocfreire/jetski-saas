'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import type { LucideIcon } from 'lucide-react'
import {
  BadgeCheck,
  Anchor,
  Calendar,
  CalendarCheck,
  ListOrdered,
  ChartBar,
  ClipboardList,
  FileText,
  Home,
  Ship,
  Users,
  UserCircle,
  Wrench,
  LogOut,
  Building2,
  ChevronDown,
  PieChart,
  Receipt,
  Settings,
  Wallet,
  LifeBuoy,
  Percent,
  Store,
  FileClock,
  CalendarSearch,
  GraduationCap,
  Landmark,
  Handshake,
  ShieldCheck,
} from 'lucide-react'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSkeleton,
  SidebarSeparator,
} from '@/components/ui/sidebar'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Logo } from '@/components/logo'
import { useTenantStore } from '@/lib/store/tenant-store'
import { useSidebarStore } from '@/lib/store/sidebar-store'
import { usePermissions } from '@/lib/hooks/use-permissions'
import { useQuery } from '@tanstack/react-query'
import { useSession } from 'next-auth/react'
import { configuracoesService, perfilService } from '@/lib/api/services'

type NavItem = {
  title: string
  href: string
  icon: LucideIcon
  /**
   * Chave(s) do ModuloPlano quando o item é gateável por plano; ausente = core.
   * Array = basta UM dos módulos no plano (ex.: Documentos/GRUs pertencem à
   * emissão própria E à delegada — split V047).
   */
  modulo?: string | string[]
  /**
   * Action(s) OPA exigidas para ver o item (fonte: rbac.rego via
   * GET /v1/user/permissions). Array = basta UMA (any-of). Ausente = sempre
   * visível. Filtro de UX — o enforcement real é o 403 do backend.
   */
  permissao?: string | string[]
  /** Prefixo de rota para marcar ativo em sub-páginas; ausente = match exato. */
  activePrefix?: string
}

type NavGroup = {
  id: string
  label: string
  items: NavItem[]
  /** Grupo visível apenas para superadmin de plataforma (accessType UNRESTRICTED). */
  superAdminOnly?: boolean
}

const NAV_GROUPS: NavGroup[] = [
  {
    id: 'principal',
    label: 'Principal',
    items: [
      { title: 'Dashboard', href: '/dashboard', icon: Home },
      { title: 'Agenda', href: '/dashboard/agenda', icon: Calendar, permissao: 'reserva:list' },
      { title: 'Pendências', href: '/dashboard/pendencias', icon: CalendarCheck, permissao: 'reserva:pagamentos-pendentes' },
      { title: 'Fila de espera', href: '/dashboard/fila', icon: ListOrdered, permissao: 'reserva:list' },
      { title: 'Controle do dia', href: '/dashboard/controle-do-dia', icon: ClipboardList, permissao: 'locacao:list' },
      { title: 'Locações', href: '/dashboard/locacoes', icon: Anchor, permissao: 'locacao:list' },
      { title: 'Balcão', href: '/dashboard/balcao', icon: Store, permissao: 'reserva:create' },
      { title: 'Atendimentos em aberto', href: '/dashboard/atendimentos', icon: FileClock, permissao: 'reserva:list' },
    ],
  },
  {
    id: 'cadastros',
    label: 'Cadastros',
    items: [
      { title: 'Jetskis', href: '/dashboard/jetskis', icon: Ship, permissao: 'jetski:list' },
      { title: 'Modelos', href: '/dashboard/modelos', icon: FileText, permissao: 'modelo:list' },
      { title: 'Clientes', href: '/dashboard/clientes', icon: Users, permissao: 'cliente:list' },
      { title: 'Vendedores', href: '/dashboard/vendedores', icon: UserCircle, modulo: 'COMISSOES', permissao: 'vendedor:list' },
      {
        title: 'Instrutores',
        href: '/dashboard/instrutores',
        icon: GraduationCap,
        // Emissão própria: CRUD normal. Delegada: a página vira visão informativa
        // (instrutores da EAMA em destaque; os próprios aparecem desativados).
        modulo: ['EMISSAO_PROPRIA', 'EMISSAO_DELEGADA'],
        permissao: 'instrutor:list',
      },
      { title: 'Reservas', href: '/dashboard/reservas', icon: CalendarSearch, permissao: 'reserva:list' },
      { title: 'Documentos', href: '/dashboard/documentos', icon: FileText, modulo: ['EMISSAO_PROPRIA', 'EMISSAO_DELEGADA'], permissao: 'documento:list' },
      { title: 'GRUs', href: '/dashboard/grus', icon: Landmark, modulo: ['EMISSAO_PROPRIA', 'EMISSAO_DELEGADA'], permissao: 'gru:list' },
      {
        title: 'Emissão delegada',
        href: '/dashboard/emissao-delegada',
        icon: Handshake,
        // sem gate de módulo: a EAMA emissora precisa do painel/kill switch mesmo
        // que o plano dela não inclua EMISSAO_DELEGADA
        permissao: 'vinculo-emissao:list',
      },
    ],
  },
  {
    id: 'operacoes',
    label: 'Operações',
    items: [
      { title: 'Manutenção', href: '/dashboard/manutencao', icon: Wrench, modulo: 'MANUTENCAO', permissao: 'manutencao:list' },
      {
        title: 'Fechamentos',
        href: '/dashboard/fechamentos/diario',
        icon: CalendarCheck,
        modulo: 'FECHAMENTOS',
        permissao: 'fechamento:list',
        activePrefix: '/dashboard/fechamentos',
      },
      {
        title: 'Relatórios',
        href: '/dashboard/relatorios',
        icon: ChartBar,
        modulo: 'RELATORIOS',
        permissao: ['relatorio:operacional', 'relatorio:financeiro', 'relatorio:comissoes'],
      },
      { title: 'Auditoria', href: '/dashboard/auditoria', icon: ClipboardList, permissao: 'auditoria:list' },
    ],
  },
  {
    id: 'financeiro',
    label: 'Financeiro',
    items: [
      { title: 'Dashboard Financeiro', href: '/dashboard/financeiro', icon: PieChart, modulo: 'RELATORIOS', permissao: 'dashboard:list' },
      {
        title: 'Comissões',
        href: '/dashboard/comissoes',
        icon: Percent,
        modulo: 'COMISSOES',
        permissao: 'comissao:list',
        activePrefix: '/dashboard/comissoes',
      },
      {
        title: 'Pagamentos',
        href: '/dashboard/financeiro/pagamentos',
        icon: Wallet,
        modulo: 'COMISSOES',
        permissao: 'pagamento:list',
        activePrefix: '/dashboard/financeiro/pagamentos',
      },
      { title: 'Validar pagamentos', href: '/dashboard/financeiro/sinais', icon: BadgeCheck, permissao: 'reserva:pagamentos-pendentes' },
      {
        title: 'Despesas Operacionais',
        href: '/dashboard/despesas-operacionais',
        icon: Receipt,
        modulo: 'DESPESAS',
        permissao: 'despesa-operacional:list',
        activePrefix: '/dashboard/despesas-operacionais',
      },
    ],
  },
  {
    id: 'sistema',
    label: 'Sistema',
    items: [
      { title: 'Plano e faturas', href: '/dashboard/plano', icon: Wallet, permissao: 'fatura:list' },
      { title: 'Configurações', href: '/dashboard/configuracoes', icon: Settings, permissao: 'config:list' },
      { title: 'Ajuda', href: '/ajuda', icon: LifeBuoy },
    ],
  },
  {
    id: 'plataforma',
    label: 'Plataforma',
    superAdminOnly: true,
    items: [
      { title: 'Empresas', href: '/dashboard/plataforma', icon: ShieldCheck, activePrefix: '/dashboard/plataforma' },
    ],
  },
]

export function AppSidebar() {
  const pathname = usePathname()
  const { currentTenant, tenants, setCurrentTenant, accessType } = useTenantStore()
  const { collapsed, toggleGroup } = useSidebarStore()
  const { data: session } = useSession()
  const { canAny, isLoading: permsLoading, isError: permsError } = usePermissions()

  // Logo white-label do tenant (mesma query do TenantThemeProvider — deduplicada)
  const { data: branding } = useQuery({
    queryKey: ['branding-config'],
    queryFn: () => configuracoesService.getBrandingConfig(),
    enabled: !!currentTenant,
    staleTime: 5 * 60 * 1000,
    retry: false,
  })

  // Nome/avatar reais do usuário (mesma query da página de perfil — invalidada
  // ao salvar lá; staleTime evita bater no Keycloak a cada navegação)
  const { data: perfil } = useQuery({
    queryKey: ['user-profile'],
    queryFn: () => perfilService.getMe(),
    staleTime: 5 * 60 * 1000,
    retry: false,
  })

  const displayName = perfil?.nome || session?.user?.name || 'Usuário'
  const iniciais =
    displayName
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0]!.toUpperCase())
      .join('') || 'U'

  // Gating de oferta (V046): item com `modulo` fora do plano some do menu.
  // null/ausente = todos os módulos; superadmin sempre vê tudo; array = basta
  // um dos módulos (split emissão própria × delegada, V047). A API tem o
  // enforcement de verdade (ModuloPlanoInterceptor) — aqui é só UX.
  const moduloHabilitado = (modulo?: string | string[]) =>
    !modulo ||
    accessType === 'UNRESTRICTED' ||
    !currentTenant?.modulos ||
    (Array.isArray(modulo)
      ? modulo.some((m) => currentTenant.modulos!.includes(m))
      : currentTenant.modulos.includes(modulo))

  // Gating por permissão OPA: item com `permissao` some quando o papel do
  // usuário não cobre nenhuma das actions. Erro na query = fail-open (mostra
  // e deixa o 403 do backend falar) — um soluço do OPA não pode travar o menu.
  const permissaoHabilitada = (permissao?: string | string[]) =>
    !permissao ||
    permsError ||
    canAny(Array.isArray(permissao) ? permissao : [permissao])

  const isItemActive = (item: NavItem) =>
    item.activePrefix ? pathname.startsWith(item.activePrefix) : pathname === item.href

  const handleSignOut = () => {
    // Navegar para a página de logout que:
    // 1. Limpa o tenant do localStorage (client-side)
    // 2. Redireciona para /api/logout que:
    //    - Limpa cookies de autenticação
    //    - Faz logout do Keycloak (via redirect)
    //    - Redireciona para /login
    window.location.href = '/logout'
  }

  const renderGroup = (group: NavGroup) => {
    if (group.superAdminOnly && accessType !== 'UNRESTRICTED') return null

    const itensDoPlano = group.items.filter((item) => moduloHabilitado(item.modulo))
    const itensVisiveis = itensDoPlano.filter((item) => permissaoHabilitada(item.permissao))

    // Anti-flash: enquanto as permissões carregam, itens gateados viram
    // skeleton (mesma altura) em vez de aparecer/sumir depois da resposta.
    const carregando = permsLoading && itensDoPlano.some((item) => item.permissao)

    if (!carregando && itensVisiveis.length === 0) return null

    return (
      <Collapsible
        key={group.id}
        open={!collapsed[group.id]}
        onOpenChange={() => toggleGroup(group.id)}
        className="group/collapsible"
      >
        <SidebarGroup>
          <SidebarGroupLabel asChild>
            <CollapsibleTrigger className="w-full uppercase tracking-wider">
              {group.label}
              <ChevronDown className="ml-auto size-4 transition-transform group-data-[state=open]/collapsible:rotate-180" />
            </CollapsibleTrigger>
          </SidebarGroupLabel>
          <CollapsibleContent>
            <SidebarGroupContent>
              <SidebarMenu>
                {carregando
                  ? itensDoPlano.map((item) =>
                      item.permissao ? (
                        <SidebarMenuItem key={item.href}>
                          <SidebarMenuSkeleton showIcon />
                        </SidebarMenuItem>
                      ) : (
                        renderItem(item)
                      )
                    )
                  : itensVisiveis.map(renderItem)}
              </SidebarMenu>
            </SidebarGroupContent>
          </CollapsibleContent>
        </SidebarGroup>
      </Collapsible>
    )
  }

  const renderItem = (item: NavItem) => (
    <SidebarMenuItem key={item.href}>
      <SidebarMenuButton asChild isActive={isItemActive(item)}>
        <Link href={item.href}>
          <item.icon className="size-4" />
          <span>{item.title}</span>
        </Link>
      </SidebarMenuButton>
    </SidebarMenuItem>
  )

  return (
    <Sidebar>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <SidebarMenuButton
                  size="lg"
                  className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
                >
                  <div className="flex aspect-square size-8 items-center justify-center overflow-hidden rounded-lg bg-sidebar-accent">
                    {branding?.logoDataUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={branding.logoDataUrl} alt="" className="size-8 object-contain" />
                    ) : (
                      <Logo variant="icon" theme="dark" size={14} />
                    )}
                  </div>
                  <div className="flex flex-col gap-0.5 leading-none">
                    <span className="font-semibold">
                      {currentTenant?.razaoSocial || 'Meu Jet'}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {currentTenant?.slug || 'Selecione um tenant'}
                    </span>
                  </div>
                  <ChevronDown className="ml-auto size-4" />
                </SidebarMenuButton>
              </DropdownMenuTrigger>
              <DropdownMenuContent
                className="w-[--radix-dropdown-menu-trigger-width]"
                align="start"
              >
                {tenants.map((tenant) => (
                  <DropdownMenuItem
                    key={tenant.id}
                    onClick={() => setCurrentTenant(tenant)}
                    className="cursor-pointer"
                  >
                    <Building2 className="mr-2 size-4" />
                    <div className="flex flex-col">
                      <span>{tenant.razaoSocial}</span>
                      <span className="text-xs text-muted-foreground">{tenant.slug}</span>
                    </div>
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarSeparator />

      <SidebarContent>{NAV_GROUPS.map(renderGroup)}</SidebarContent>

      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <SidebarMenuButton
                  size="lg"
                  className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
                >
                  <Avatar className="size-8">
                    {perfil?.avatarDataUrl && <AvatarImage src={perfil.avatarDataUrl} alt="" />}
                    <AvatarFallback>{iniciais}</AvatarFallback>
                  </Avatar>
                  <div className="flex flex-col gap-0.5 leading-none">
                    <span className="max-w-[140px] truncate font-medium">{displayName}</span>
                    <span className="text-xs text-muted-foreground">
                      {currentTenant?.roles?.join(', ') || 'Carregando...'}
                    </span>
                  </div>
                  <ChevronDown className="ml-auto size-4" />
                </SidebarMenuButton>
              </DropdownMenuTrigger>
              <DropdownMenuContent
                className="w-[--radix-dropdown-menu-trigger-width]"
                align="start"
              >
                <DropdownMenuItem asChild>
                  <Link href="/dashboard/perfil">
                    <UserCircle className="mr-2 size-4" />
                    Perfil
                  </Link>
                </DropdownMenuItem>
                {permissaoHabilitada('config:list') && (
                  <DropdownMenuItem asChild>
                    <Link href="/dashboard/configuracoes">
                      <Settings className="mr-2 size-4" />
                      Configurações
                    </Link>
                  </DropdownMenuItem>
                )}
                {permissaoHabilitada('member:list') && (
                  <DropdownMenuItem asChild>
                    <Link href="/dashboard/usuarios">
                      <Users className="mr-2 size-4" />
                      Gerenciar Usuários
                    </Link>
                  </DropdownMenuItem>
                )}
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={handleSignOut} className="text-destructive">
                  <LogOut className="mr-2 size-4" />
                  Sair
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
    </Sidebar>
  )
}
