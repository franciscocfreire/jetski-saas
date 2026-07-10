import type { ClienteOrigem, ClienteStatusConta } from '@/lib/api/types'

type BadgeInfo = { label: string; variant: 'default' | 'secondary' | 'outline' }

/** Badge do estado da conta do cliente — fonte única (lista + detail sheet). */
export const CONTA_BADGE: Record<ClienteStatusConta, BadgeInfo> = {
  ATIVA: { label: 'Conta ativa', variant: 'default' },
  CONVIDADA: { label: 'Convidada', variant: 'secondary' },
  PRE_CONTA: { label: 'Pré-conta', variant: 'outline' },
  SEM_LOGIN: { label: 'Sem login', variant: 'outline' },
}

/** Badge da origem do cadastro. */
export const ORIGEM_BADGE: Record<ClienteOrigem, BadgeInfo> = {
  PORTAL: { label: 'Online (portal)', variant: 'default' },
  BALCAO: { label: 'Balcão', variant: 'secondary' },
  LEAD: { label: 'Lead', variant: 'outline' },
}
