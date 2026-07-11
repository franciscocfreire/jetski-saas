'use client'

import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2, Blocks } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { useToast } from '@/hooks/use-toast'
import { platformService, type ModuloCatalogo, type PlanoInfo } from '@/lib/api/services/platform'

/**
 * Grade módulos × planos (controle de oferta, V046). Coluna = plano;
 * linha = módulo gateável. plano.modulos null = todos incluídos.
 * Salvamento por plano (botão aparece só na coluna alterada).
 */
export function ModulosPorPlanoCard({ enabled }: { enabled: boolean }) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  const { data: catalogo } = useQuery({
    queryKey: ['platform', 'modulos-catalogo'],
    queryFn: () => platformService.modulos(),
    enabled,
    staleTime: Infinity,
  })
  const { data: planos } = useQuery({
    queryKey: ['platform', 'planos'],
    queryFn: () => platformService.planos(),
    enabled,
  })

  // Estado editável: planoId → Set de módulos marcados
  const [marcados, setMarcados] = useState<Record<string, Set<string>>>({})
  const [alterados, setAlterados] = useState<Set<string>>(new Set())

  useEffect(() => {
    if (!planos || !catalogo) return
    const inicial: Record<string, Set<string>> = {}
    for (const p of planos) {
      inicial[p.id] = new Set(parseModulos(p, catalogo))
    }
    setMarcados(inicial)
    setAlterados(new Set())
  }, [planos, catalogo])

  const salvar = useMutation({
    mutationFn: (planoId: string) =>
      platformService.salvarModulosDoPlano(planoId, [...(marcados[planoId] ?? [])]),
    onSuccess: (_d, planoId) => {
      toast({ title: 'Módulos do plano salvos' })
      setAlterados((prev) => {
        const next = new Set(prev)
        next.delete(planoId)
        return next
      })
      queryClient.invalidateQueries({ queryKey: ['platform', 'planos'] })
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast({ title: 'Falha ao salvar', description: msg ?? 'Erro inesperado', variant: 'destructive' })
    },
  })

  if (!catalogo || !planos || planos.length === 0) return null

  const toggle = (planoId: string, modulo: string, checked: boolean) => {
    setMarcados((prev) => {
      const set = new Set(prev[planoId] ?? [])
      if (checked) set.add(modulo)
      else set.delete(modulo)
      return { ...prev, [planoId]: set }
    })
    setAlterados((prev) => new Set(prev).add(planoId))
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Blocks className="size-5" /> Módulos por plano
        </CardTitle>
        <CardDescription>
          Defina a oferta: o que ficar desmarcado some do menu da empresa e a API nega
          com pedido de upgrade. O core operacional (agenda, balcão, locações, reservas,
          frota, clientes) está sempre incluído.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Módulo</TableHead>
                {planos.map((p) => (
                  <TableHead key={p.id} className="text-center">{p.nome}</TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {catalogo.map((m) => (
                <TableRow key={m.key}>
                  <TableCell>
                    <div className="font-medium">{m.rotulo}</div>
                    <div className="text-xs text-muted-foreground">{m.descricao}</div>
                  </TableCell>
                  {planos.map((p) => (
                    <TableCell key={p.id} className="text-center">
                      <Checkbox
                        checked={marcados[p.id]?.has(m.key) ?? false}
                        onCheckedChange={(c) => toggle(p.id, m.key, c === true)}
                        aria-label={`${m.rotulo} no plano ${p.nome}`}
                      />
                    </TableCell>
                  ))}
                </TableRow>
              ))}
              <TableRow>
                <TableCell />
                {planos.map((p) => (
                  <TableCell key={p.id} className="text-center">
                    {alterados.has(p.id) && (
                      <Button
                        size="sm"
                        disabled={salvar.isPending || (marcados[p.id]?.size ?? 0) === 0}
                        onClick={() => salvar.mutate(p.id)}
                      >
                        {salvar.isPending && salvar.variables === p.id ? (
                          <Loader2 className="size-4 animate-spin" />
                        ) : (
                          'Salvar'
                        )}
                      </Button>
                    )}
                  </TableCell>
                ))}
              </TableRow>
            </TableBody>
          </Table>
        </div>
      </CardContent>
    </Card>
  )
}

/** plano.modulos vem como texto JSON ou null (= todos os módulos do catálogo). */
function parseModulos(plano: PlanoInfo, catalogo: ModuloCatalogo[]): string[] {
  if (!plano.modulos) return catalogo.map((m) => m.key)
  try {
    return JSON.parse(plano.modulos) as string[]
  } catch {
    return catalogo.map((m) => m.key)
  }
}
