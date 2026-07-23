'use client'

import { Fragment } from 'react'
import { useQuery } from '@tanstack/react-query'
import { permissoesService } from '@/lib/api/services'
import { AVAILABLE_ROLES } from '@/lib/api/types'
import { matchesPermission } from '@/lib/hooks/use-permissions'
import { useTenantStore } from '@/lib/store/tenant-store'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { AlertCircle, Check, Loader2, ShieldCheck } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * Aba Configurações › Permissões: matriz papel × ações (read-only).
 * Fonte: rbac.rego via OPA (GET /v1/tenants/{id}/config/permissions-matrix) —
 * exatamente o que o backend usa no enforcement. Papéis são atribuídos por
 * usuário em Gerenciar Usuários; edição de permissões por tenant é escopo futuro.
 */

const RESOURCE_LABEL: Record<string, string> = {
  locacao: 'Locações',
  reserva: 'Reservas',
  cliente: 'Clientes',
  jetski: 'Jetskis',
  modelo: 'Modelos',
  abastecimento: 'Abastecimento',
  foto: 'Fotos',
  os: 'Ordens de serviço',
  manutencao: 'Manutenção',
  fechamento: 'Fechamentos',
  comissao: 'Comissões',
  pagamento: 'Pagamentos',
  'politica-comissao': 'Políticas de comissão',
  'politicas-comissao': 'Políticas de comissão (alias)',
  desconto: 'Descontos',
  config: 'Configurações',
  frota: 'Frota',
  relatorio: 'Relatórios',
  vendedor: 'Vendedores',
  instrutor: 'Instrutores',
  documento: 'Documentos',
  gru: 'GRUs',
  'vinculo-emissao': 'Emissão delegada (vínculo)',
  'emissao-delegada': 'Emissão delegada',
  'item-opcional': 'Itens opcionais',
  member: 'Membros da equipe',
  invitation: 'Convites',
}

const VERB_LABEL: Record<string, string> = {
  list: 'Listar',
  view: 'Ver detalhes',
  create: 'Criar',
  update: 'Editar',
  delete: 'Excluir',
  '*': 'Todas as ações',
}

const resourceLabel = (r: string) => RESOURCE_LABEL[r] ?? r
const verbLabel = (v: string) => VERB_LABEL[v] ?? v

export function PermissoesTab() {
  const { currentTenant } = useTenantStore()

  const { data, isLoading, error } = useQuery({
    queryKey: ['permissions-matrix', currentTenant?.id],
    queryFn: () => permissoesService.getMatriz(),
    enabled: !!currentTenant,
    staleTime: 5 * 60 * 1000,
  })

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-48">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (error || !data) {
    return (
      <Alert variant="destructive">
        <AlertCircle className="h-4 w-4" />
        <AlertDescription>Não foi possível carregar a matriz de permissões.</AlertDescription>
      </Alert>
    )
  }

  // Papéis nas colunas na ordem canônica; ADMIN_TENANT fica de fora da matriz
  // (coluna seria toda marcada) e é representado pelo badge "Acesso total".
  const rolesMatriz = AVAILABLE_ROLES.filter((r) => r.value !== 'ADMIN_TENANT')

  // Linhas: união das ações concretas de todos os papéis; recurso com apenas
  // wildcard ("emissao-delegada:*") vira a linha sintética "Todas as ações".
  const todasPermissoes = Object.values(data.roles).flat()
  const acoesConcretas = new Set(
    todasPermissoes.filter((p) => p.includes(':') && !p.endsWith(':*'))
  )
  for (const p of todasPermissoes) {
    if (p.endsWith(':*')) {
      const recurso = p.slice(0, -2)
      const temConcreta = [...acoesConcretas].some((a) => a.startsWith(`${recurso}:`))
      if (!temConcreta) acoesConcretas.add(p)
    }
  }

  const porRecurso = new Map<string, string[]>()
  for (const acao of [...acoesConcretas].sort()) {
    const recurso = acao.split(':')[0]!
    porRecurso.set(recurso, [...(porRecurso.get(recurso) ?? []), acao])
  }
  const recursos = [...porRecurso.keys()].sort((a, b) =>
    resourceLabel(a).localeCompare(resourceLabel(b), 'pt-BR')
  )

  const papelTem = (papel: string, acao: string) =>
    (data.roles[papel] ?? []).some((p) => matchesPermission(acao, p))

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5" />
            Matriz de permissões por papel
          </CardTitle>
          <CardDescription>
            Visualização do que cada papel pode fazer — é a mesma regra que o servidor aplica em
            cada requisição. Os papéis de cada pessoa são definidos em Gerenciar Usuários.{' '}
            <Badge variant="secondary" className="align-middle">
              Administrador: acesso total
            </Badge>
          </CardDescription>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="min-w-[220px]">Ação</TableHead>
                {rolesMatriz.map((role) => (
                  <TableHead key={role.value} className="text-center" title={role.description}>
                    {role.label}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {recursos.map((recurso) => (
                <Fragment key={recurso}>
                  <TableRow className="bg-muted/50 hover:bg-muted/50">
                    <TableCell
                      colSpan={rolesMatriz.length + 1}
                      className="py-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                    >
                      {resourceLabel(recurso)}
                    </TableCell>
                  </TableRow>
                  {porRecurso.get(recurso)!.map((acao) => (
                    <TableRow key={acao}>
                      <TableCell className="py-1.5">
                        <span className="text-sm">{verbLabel(acao.split(':')[1]!)}</span>{' '}
                        <code className="text-xs text-muted-foreground">{acao}</code>
                      </TableCell>
                      {rolesMatriz.map((role) => (
                        <TableCell key={role.value} className="py-1.5 text-center">
                          <Check
                            aria-label={papelTem(role.value, acao) ? 'permitido' : undefined}
                            className={cn(
                              'mx-auto h-4 w-4',
                              papelTem(role.value, acao)
                                ? 'text-primary'
                                : 'invisible'
                            )}
                          />
                        </TableCell>
                      ))}
                    </TableRow>
                  ))}
                </Fragment>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  )
}
