'use client'

import { useMutation } from '@tanstack/react-query'
import { FileDown, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useToast } from '@/hooks/use-toast'
import { platformService } from '@/lib/api/services/platform'
import type { TenantSummary } from '@/lib/api/types'

/**
 * Gera o export de arquivamento (.zip: dados JSON de todas as tabelas +
 * arquivos do storage) e baixa na hora. O mesmo export roda automaticamente
 * antes de qualquer reset/expurgo — este botão é a versão sob demanda.
 */
export function ExportarEmpresaButton({ tenant }: { tenant: TenantSummary }) {
  const { toast } = useToast()

  const exportar = useMutation({
    mutationFn: async () => {
      const exp = await platformService.exportTenant(tenant.id)
      const blob = await platformService.downloadExport(tenant.id, exp.key)
      return { exp, blob }
    },
    onSuccess: ({ exp, blob }) => {
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = exp.key.split('/').pop() ?? `${tenant.slug}-export.zip`
      a.click()
      URL.revokeObjectURL(url)
      toast({
        title: 'Export gerado',
        description: `${exp.tabelas} tabelas e ${exp.arquivos} arquivos (${(exp.bytes / 1024 / 1024).toFixed(1)} MB).`,
      })
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast({ title: 'Falha no export', description: msg ?? 'Erro inesperado', variant: 'destructive' })
    },
  })

  return (
    <Button
      variant="outline"
      size="sm"
      disabled={exportar.isPending}
      onClick={() => exportar.mutate()}
      title="Gerar e baixar o arquivamento completo (.zip)"
    >
      {exportar.isPending ? (
        <Loader2 className="mr-1 size-4 animate-spin" />
      ) : (
        <FileDown className="mr-1 size-4" />
      )}
      Exportar
    </Button>
  )
}
