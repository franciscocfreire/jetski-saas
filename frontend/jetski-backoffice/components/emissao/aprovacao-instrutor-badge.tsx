import { Badge } from '@/components/ui/badge'
import type { StatusAprovacaoInstrutor } from '@/lib/api/services/emissao-delegada'

const ROTULO: Record<StatusAprovacaoInstrutor, { label: string; className: string }> = {
  PENDENTE: { label: 'Aguardando a EAMA', className: 'bg-amber-100 text-amber-900' },
  APROVADO: { label: 'Aprovado pela EAMA', className: 'bg-emerald-100 text-emerald-900' },
  REJEITADO: { label: 'Rejeitado pela EAMA', className: 'bg-red-100 text-red-900' },
  REMOVIDO: { label: 'Removido pela EAMA', className: 'bg-muted text-muted-foreground' },
}

/** Status da aprovação de um instrutor da operadora pela EAMA emissora (V070). */
export function AprovacaoInstrutorBadge({ status }: { status: StatusAprovacaoInstrutor }) {
  const r = ROTULO[status]
  return (
    <Badge data-testid="aprovacao-instrutor" data-status={status} className={r.className}>
      {r.label}
    </Badge>
  )
}
