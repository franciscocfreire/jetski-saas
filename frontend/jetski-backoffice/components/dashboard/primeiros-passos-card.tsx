'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import {
  Anchor,
  CheckCircle2,
  ChevronRight,
  CircleDollarSign,
  Mail,
  Rocket,
  Ship,
  Store,
  Users,
  X,
} from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { dashboardService } from '@/lib/api/services'
import type { OnboardingChecklist } from '@/lib/api/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { cn } from '@/lib/utils'

type Passo = {
  key: keyof Omit<OnboardingChecklist, 'completo'>
  titulo: string
  descricao: string
  href: string
  icon: React.ComponentType<{ className?: string }>
}

/** Ordem operacional: sem modelo não há reserva; sem jetski não há check-in. */
const PASSOS: Passo[] = [
  {
    key: 'temModelo',
    titulo: 'Cadastre seu primeiro modelo',
    descricao: 'O modelo define o preço por hora e aparece nas reservas',
    href: '/dashboard/modelos',
    icon: Ship,
  },
  {
    key: 'temJetski',
    titulo: 'Cadastre seu primeiro jetski',
    descricao: 'Cada jetski pertence a um modelo e entra na agenda',
    href: '/dashboard/jetskis',
    icon: Anchor,
  },
  {
    key: 'marinhaEmailConfigurado',
    titulo: 'Informe o e-mail da Capitania',
    descricao: 'Sem ele, o documento EMA não é enviado à Marinha',
    href: '/dashboard/configuracoes?tab=empresa',
    icon: Mail,
  },
  {
    key: 'pixConfigurado',
    titulo: 'Cadastre sua chave PIX',
    descricao: 'O cliente do portal paga o sinal da reserva direto para você',
    href: '/dashboard/configuracoes?tab=empresa',
    icon: CircleDollarSign,
  },
  {
    key: 'equipeConvidada',
    titulo: 'Convide sua equipe',
    descricao: 'Operadores e vendedores, cada um com o papel certo',
    href: '/dashboard/usuarios',
    icon: Users,
  },
  {
    key: 'primeiraLocacaoFeita',
    titulo: 'Faça sua primeira locação',
    descricao: 'Atendimento completo pelo balcão, do cadastro ao check-in',
    href: '/dashboard/balcao',
    icon: Store,
  },
]

/**
 * Checklist "Primeiros passos" — caminho inicial da empresa recém-aprovada.
 * Cada passo é auto-detectado dos dados reais (nada é marcado à mão); o card
 * some sozinho quando tudo estiver completo e pode ser ocultado (localStorage).
 * Visível só para ADMIN_TENANT (quem configura a empresa).
 */
export function PrimeirosPassosCard() {
  const { currentTenant } = useTenantStore()
  const isAdmin = currentTenant?.roles?.includes('ADMIN_TENANT') ?? false
  const storageKey = `primeirosPassos.oculto.${currentTenant?.id}`
  const [oculto, setOculto] = useState(true) // evita flash: só mostra após ler o localStorage

  useEffect(() => {
    if (currentTenant?.id) {
      setOculto(localStorage.getItem(storageKey) === '1')
    }
  }, [currentTenant?.id, storageKey])

  const { data: checklist } = useQuery({
    queryKey: ['onboarding-checklist', currentTenant?.id],
    queryFn: () => dashboardService.getOnboarding(),
    enabled: isAdmin && !!currentTenant && !oculto,
    refetchOnWindowFocus: true,
  })

  if (!isAdmin || oculto || !checklist || checklist.completo) return null

  const feitos = PASSOS.filter((p) => checklist[p.key]).length
  const proximo = PASSOS.find((p) => !checklist[p.key])

  return (
    <Card className="border-primary/30 bg-gradient-to-br from-primary/5 to-transparent">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-2">
          <div>
            <CardTitle className="flex items-center gap-2">
              <Rocket className="size-5 text-primary" />
              Primeiros passos
            </CardTitle>
            <CardDescription>
              Bem-vindo ao Meu Jet! Complete o caminho abaixo e comece a operar.
            </CardDescription>
          </div>
          <Button
            variant="ghost"
            size="icon"
            className="shrink-0 text-muted-foreground"
            title="Ocultar este guia"
            onClick={() => {
              localStorage.setItem(storageKey, '1')
              setOculto(true)
            }}
          >
            <X className="size-4" />
          </Button>
        </div>
        <div className="flex items-center gap-3 pt-1">
          <Progress value={(feitos / PASSOS.length) * 100} className="h-2" />
          <span className="shrink-0 text-sm font-medium text-muted-foreground">
            {feitos} de {PASSOS.length}
          </span>
        </div>
      </CardHeader>
      <CardContent>
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {PASSOS.map((p) => {
            const feito = checklist[p.key]
            const ehProximo = proximo?.key === p.key
            const Icon = feito ? CheckCircle2 : p.icon
            const conteudo = (
              <div
                className={cn(
                  'flex h-full items-start gap-3 rounded-lg border p-3 transition-colors',
                  feito
                    ? 'border-transparent bg-muted/40 opacity-70'
                    : 'hover:bg-muted/60',
                  ehProximo && 'border-primary/50 bg-primary/5'
                )}
              >
                <Icon
                  className={cn('mt-0.5 size-5 shrink-0', feito ? 'text-emerald-600' : 'text-primary')}
                />
                <div className="min-w-0 flex-1">
                  <p className={cn('text-sm font-medium', feito && 'line-through')}>{p.titulo}</p>
                  <p className="text-xs text-muted-foreground">{p.descricao}</p>
                </div>
                {!feito && <ChevronRight className="mt-1 size-4 shrink-0 text-muted-foreground" />}
              </div>
            )
            return feito ? (
              <div key={p.key}>{conteudo}</div>
            ) : (
              <Link key={p.key} href={p.href} className="block">
                {conteudo}
              </Link>
            )
          })}
        </div>
      </CardContent>
    </Card>
  )
}
