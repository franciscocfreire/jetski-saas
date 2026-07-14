'use client'

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Anchor, Loader2 } from 'lucide-react'
import { platformService } from '@/lib/api/services'
import type { TenantSummary } from '@/lib/api/types'
import { Button } from '@/components/ui/button'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { useToast } from '@/hooks/use-toast'

/**
 * Habilitar/desabilitar a empresa como EAMA emissora (V047) — portão
 * cadastral da emissão. Habilitar exige capitania + registro EAMA já
 * declarados pela empresa (o backend valida e explica se faltar).
 */
export function EmissoraButton({ tenant }: { tenant: TenantSummary }) {
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const habilitada = !!tenant.emissoraHabilitada

  const mutation = useMutation({
    mutationFn: () =>
      habilitada
        ? platformService.desabilitarEmissora(tenant.id)
        : platformService.habilitarEmissora(tenant.id),
    onSuccess: () => {
      toast({
        title: habilitada ? 'Habilitação de emissora removida' : 'EAMA emissora habilitada',
        description: tenant.razaoSocial,
      })
      queryClient.invalidateQueries({ queryKey: ['platform', 'tenants'] })
    },
    onError: (e) => {
      const r = e as { response?: { data?: { message?: string } } }
      toast({
        title: habilitada ? 'Falha ao desabilitar emissora' : 'Falha ao habilitar emissora',
        description: r?.response?.data?.message ?? 'Erro inesperado',
        variant: 'destructive',
      })
    },
  })

  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button variant="outline" size="sm" disabled={mutation.isPending}>
          {mutation.isPending ? (
            <Loader2 className="mr-1 size-4 animate-spin" />
          ) : (
            <Anchor className="mr-1 size-4" />
          )}
          {habilitada ? 'Desabilitar EAMA' : 'Habilitar EAMA'}
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>
            {habilitada ? 'Remover habilitação de emissora?' : 'Habilitar como EAMA emissora?'}
          </AlertDialogTitle>
          <AlertDialogDescription>
            {habilitada ? (
              <>
                {tenant.razaoSocial} deixará de poder emitir documentação NORMAM em nome
                próprio e as parcerias em que é a emissora ficarão bloqueadas na emissão.
              </>
            ) : (
              <>
                Confirme somente após validar o registro na Capitania.{' '}
                {tenant.eamaRegistro ? (
                  <>Registro declarado: <b>{tenant.eamaRegistro}</b>.</>
                ) : (
                  <>A empresa ainda não declarou o registro EAMA — o backend vai recusar até
                  ela preencher o perfil de emissão.</>
                )}
              </>
            )}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancelar</AlertDialogCancel>
          <AlertDialogAction onClick={() => mutation.mutate()}>
            {habilitada ? 'Desabilitar' : 'Habilitar'}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
