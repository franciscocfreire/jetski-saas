'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
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
  Percent,
  Store,
  FileClock,
  CalendarSearch,
  GraduationCap,
  Landmark,
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
  SidebarSeparator,
} from '@/components/ui/sidebar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Logo } from '@/components/logo'
import { useTenantStore } from '@/lib/store/tenant-store'
import { useQuery } from '@tanstack/react-query'
import { configuracoesService } from '@/lib/api/services'

const mainNavItems = [
  {
    title: 'Dashboard',
    href: '/dashboard',
    icon: Home,
  },
  {
    title: 'Agenda',
    href: '/dashboard/agenda',
    icon: Calendar,
  },
  {
    title: 'Pendências',
    href: '/dashboard/pendencias',
    icon: CalendarCheck,
  },
  {
    title: 'Fila de espera',
    href: '/dashboard/fila',
    icon: ListOrdered,
  },
  {
    title: 'Controle do dia',
    href: '/dashboard/controle-do-dia',
    icon: ClipboardList,
  },
  {
    title: 'Locações',
    href: '/dashboard/locacoes',
    icon: Anchor,
  },
  {
    title: 'Balcão',
    href: '/dashboard/balcao',
    icon: Store,
  },
  {
    title: 'Atendimentos em aberto',
    href: '/dashboard/atendimentos',
    icon: FileClock,
  },
]

const managementItems = [
  {
    title: 'Jetskis',
    href: '/dashboard/jetskis',
    icon: Ship,
  },
  {
    title: 'Modelos',
    href: '/dashboard/modelos',
    icon: FileText,
  },
  {
    title: 'Clientes',
    href: '/dashboard/clientes',
    icon: Users,
  },
  {
    title: 'Vendedores',
    href: '/dashboard/vendedores',
    icon: UserCircle,
  },
  {
    title: 'Instrutores',
    href: '/dashboard/instrutores',
    icon: GraduationCap,
  },
  {
    title: 'Reservas',
    href: '/dashboard/reservas',
    icon: CalendarSearch,
  },
  {
    title: 'Documentos',
    href: '/dashboard/documentos',
    icon: FileText,
  },
  {
    title: 'GRUs',
    href: '/dashboard/grus',
    icon: Landmark,
  },
]

const operationsItems = [
  {
    title: 'Manutenção',
    href: '/dashboard/manutencao',
    icon: Wrench,
  },
  {
    title: 'Fechamentos',
    href: '/dashboard/fechamentos/diario',
    icon: CalendarCheck,
  },
  {
    title: 'Relatórios',
    href: '/dashboard/relatorios',
    icon: ChartBar,
  },
  {
    title: 'Auditoria',
    href: '/dashboard/auditoria',
    icon: ClipboardList,
  },
]

const financeiroItems = [
  {
    title: 'Dashboard Financeiro',
    href: '/dashboard/financeiro',
    icon: PieChart,
  },
  {
    title: 'Comissões',
    href: '/dashboard/comissoes',
    icon: Percent,
  },
  {
    title: 'Pagamentos',
    href: '/dashboard/financeiro/pagamentos',
    icon: Wallet,
  },
  {
    title: 'Validar pagamentos',
    href: '/dashboard/financeiro/sinais',
    icon: BadgeCheck,
  },
  {
    title: 'Despesas Operacionais',
    href: '/dashboard/despesas-operacionais',
    icon: Receipt,
  },
]

const sistemaItems = [
  {
    title: 'Configurações',
    href: '/dashboard/configuracoes',
    icon: Settings,
  },
]

const platformItems = [
  {
    title: 'Empresas',
    href: '/dashboard/plataforma',
    icon: ShieldCheck,
  },
]

export function AppSidebar() {
  const pathname = usePathname()
  const { currentTenant, tenants, setCurrentTenant, accessType } = useTenantStore()

  // Logo white-label do tenant (mesma query do TenantThemeProvider — deduplicada)
  const { data: branding } = useQuery({
    queryKey: ['branding-config'],
    queryFn: () => configuracoesService.getBrandingConfig(),
    enabled: !!currentTenant,
    staleTime: 5 * 60 * 1000,
    retry: false,
  })

  const handleSignOut = () => {
    // Navegar para a página de logout que:
    // 1. Limpa o tenant do localStorage (client-side)
    // 2. Redireciona para /api/logout que:
    //    - Limpa cookies de autenticação
    //    - Faz logout do Keycloak (via redirect)
    //    - Redireciona para /login
    window.location.href = '/logout'
  }

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

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>Principal</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {mainNavItems.map((item) => (
                <SidebarMenuItem key={item.href}>
                  <SidebarMenuButton asChild isActive={pathname === item.href}>
                    <Link href={item.href}>
                      <item.icon className="size-4" />
                      <span>{item.title}</span>
                    </Link>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <SidebarGroup>
          <SidebarGroupLabel>Cadastros</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {managementItems.map((item) => (
                <SidebarMenuItem key={item.href}>
                  <SidebarMenuButton asChild isActive={pathname === item.href}>
                    <Link href={item.href}>
                      <item.icon className="size-4" />
                      <span>{item.title}</span>
                    </Link>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <SidebarGroup>
          <SidebarGroupLabel>Operações</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {operationsItems.map((item) => {
                // Para Fechamentos, destacar se estiver em qualquer sub-página
                const isActive = item.href.includes('/fechamentos')
                  ? pathname.startsWith('/dashboard/fechamentos')
                  : pathname === item.href
                return (
                  <SidebarMenuItem key={item.href}>
                    <SidebarMenuButton asChild isActive={isActive}>
                      <Link href={item.href}>
                        <item.icon className="size-4" />
                        <span>{item.title}</span>
                      </Link>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                )
              })}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <SidebarGroup>
          <SidebarGroupLabel>Financeiro</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {financeiroItems.map((item) => {
                // Para Dashboard Financeiro, destacar se estiver em qualquer sub-página
                const isActive = item.href === '/dashboard/financeiro'
                  ? pathname === '/dashboard/financeiro'
                  : item.href.includes('/financeiro/pagamentos')
                  ? pathname.startsWith('/dashboard/financeiro/pagamentos')
                  : item.href.includes('/comissoes')
                  ? pathname.startsWith('/dashboard/comissoes')
                  : item.href.includes('/despesas-operacionais')
                  ? pathname.startsWith('/dashboard/despesas-operacionais')
                  : pathname === item.href
                return (
                  <SidebarMenuItem key={item.href}>
                    <SidebarMenuButton asChild isActive={isActive}>
                      <Link href={item.href}>
                        <item.icon className="size-4" />
                        <span>{item.title}</span>
                      </Link>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                )
              })}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <SidebarGroup>
          <SidebarGroupLabel>Sistema</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {sistemaItems.map((item) => (
                <SidebarMenuItem key={item.href}>
                  <SidebarMenuButton asChild isActive={pathname === item.href}>
                    <Link href={item.href}>
                      <item.icon className="size-4" />
                      <span>{item.title}</span>
                    </Link>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        {accessType === 'UNRESTRICTED' && (
          <SidebarGroup>
            <SidebarGroupLabel>Plataforma</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {platformItems.map((item) => (
                  <SidebarMenuItem key={item.href}>
                    <SidebarMenuButton asChild isActive={pathname.startsWith(item.href)}>
                      <Link href={item.href}>
                        <item.icon className="size-4" />
                        <span>{item.title}</span>
                      </Link>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        )}
      </SidebarContent>

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
                    <AvatarFallback>U</AvatarFallback>
                  </Avatar>
                  <div className="flex flex-col gap-0.5 leading-none">
                    <span className="font-medium">Usuário</span>
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
                <DropdownMenuItem>Perfil</DropdownMenuItem>
                <DropdownMenuItem>Configurações</DropdownMenuItem>
                <DropdownMenuItem asChild>
                  <Link href="/dashboard/usuarios">
                    <Users className="mr-2 size-4" />
                    Gerenciar Usuários
                  </Link>
                </DropdownMenuItem>
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
