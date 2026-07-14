'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Anchor, Loader2, Plus, Save } from 'lucide-react'
import { platformService } from '@/lib/api/services'
import type { PlatformCapitania } from '@/lib/api/services/platform'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { useToast } from '@/hooks/use-toast'

/**
 * Catálogo de Capitanias da Marinha (V047): o seed traz código/nome/UF das
 * litorâneas SEM e-mail — o super admin preenche aqui os endereços oficiais
 * (o e-mail só pré-preenche o marinha_email dos tenants; a EAMA pode editar).
 */
export function CapitaniasCard({ enabled }: { enabled: boolean }) {
  const queryClient = useQueryClient()
  const { toast } = useToast()
  const [edits, setEdits] = useState<Record<string, { emailOficial: string; ativa: boolean }>>({})
  const [novo, setNovo] = useState({ codigo: '', nome: '', uf: '', emailOficial: '' })

  const { data: capitanias, isLoading } = useQuery({
    queryKey: ['platform', 'capitanias'],
    queryFn: () => platformService.listCapitanias(),
    enabled,
  })

  const errMsg = (e: unknown) => {
    const r = e as { response?: { data?: { message?: string } } }
    return r?.response?.data?.message ?? 'Erro inesperado'
  }
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['platform', 'capitanias'] })

  const salvar = useMutation({
    mutationFn: (c: PlatformCapitania) => {
      const edit = edits[c.id]
      return platformService.atualizarCapitania(c.id, {
        codigo: c.codigo,
        nome: c.nome,
        uf: c.uf,
        emailOficial: edit?.emailOficial ?? c.emailOficial,
        ativa: edit?.ativa ?? c.ativa,
      })
    },
    onSuccess: (c) => {
      toast({ title: 'Capitania atualizada', description: c.codigo })
      setEdits((e) => {
        const n = { ...e }
        delete n[c.id]
        return n
      })
      refresh()
    },
    onError: (e) => toast({ title: 'Falha ao salvar capitania', description: errMsg(e), variant: 'destructive' }),
  })

  const criar = useMutation({
    mutationFn: () =>
      platformService.criarCapitania({
        codigo: novo.codigo,
        nome: novo.nome,
        uf: novo.uf || null,
        emailOficial: novo.emailOficial || null,
      }),
    onSuccess: (c) => {
      toast({ title: 'Capitania criada', description: c.codigo })
      setNovo({ codigo: '', nome: '', uf: '', emailOficial: '' })
      refresh()
    },
    onError: (e) => toast({ title: 'Falha ao criar capitania', description: errMsg(e), variant: 'destructive' }),
  })

  if (!enabled) return null

  const valorEdit = (c: PlatformCapitania) =>
    edits[c.id] ?? { emailOficial: c.emailOficial ?? '', ativa: c.ativa }
  const alterado = (c: PlatformCapitania) => {
    const e = edits[c.id]
    return !!e && (e.emailOficial !== (c.emailOficial ?? '') || e.ativa !== c.ativa)
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Anchor className="size-5" /> Capitanias da Marinha
        </CardTitle>
        <CardDescription>
          Catálogo usado no perfil de emissão das empresas (parceria exige mesma capitania).
          Preencha os e-mails oficiais — eles pré-preenchem o destino da documentação das
          empresas dessa capitania.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {isLoading ? (
          <p className="text-sm text-muted-foreground">Carregando…</p>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Código</TableHead>
                  <TableHead>Nome</TableHead>
                  <TableHead>UF</TableHead>
                  <TableHead>E-mail oficial</TableHead>
                  <TableHead>Ativa</TableHead>
                  <TableHead className="text-right">Ações</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(capitanias ?? []).map((c) => {
                  const v = valorEdit(c)
                  return (
                    <TableRow key={c.id}>
                      <TableCell className="font-medium">{c.codigo}</TableCell>
                      <TableCell>{c.nome}</TableCell>
                      <TableCell>{c.uf ?? '—'}</TableCell>
                      <TableCell>
                        <Input
                          value={v.emailOficial}
                          placeholder="email@marinha.mil.br"
                          className="h-8 w-64"
                          onChange={(e) =>
                            setEdits((s) => ({ ...s, [c.id]: { ...v, emailOficial: e.target.value } }))
                          }
                        />
                      </TableCell>
                      <TableCell>
                        <Switch
                          checked={v.ativa}
                          onCheckedChange={(checked) =>
                            setEdits((s) => ({ ...s, [c.id]: { ...v, ativa: checked } }))
                          }
                        />
                      </TableCell>
                      <TableCell className="text-right">
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={!alterado(c) || salvar.isPending}
                          onClick={() => salvar.mutate(c)}
                        >
                          <Save className="mr-1 size-4" /> Salvar
                        </Button>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </div>
        )}

        <div className="flex flex-wrap items-end gap-2 rounded-lg border p-3">
          <div className="text-sm font-medium">Nova capitania/delegacia:</div>
          <Input
            placeholder="Código (ex.: DLPGA)"
            value={novo.codigo}
            onChange={(e) => setNovo({ ...novo, codigo: e.target.value })}
            className="h-8 w-36"
          />
          <Input
            placeholder="Nome"
            value={novo.nome}
            onChange={(e) => setNovo({ ...novo, nome: e.target.value })}
            className="h-8 w-72"
          />
          <Input
            placeholder="UF"
            value={novo.uf}
            onChange={(e) => setNovo({ ...novo, uf: e.target.value })}
            className="h-8 w-16"
          />
          <Input
            placeholder="E-mail oficial (opcional)"
            value={novo.emailOficial}
            onChange={(e) => setNovo({ ...novo, emailOficial: e.target.value })}
            className="h-8 w-64"
          />
          <Button
            size="sm"
            disabled={!novo.codigo.trim() || !novo.nome.trim() || criar.isPending}
            onClick={() => criar.mutate()}
          >
            {criar.isPending ? (
              <Loader2 className="mr-1 size-4 animate-spin" />
            ) : (
              <Plus className="mr-1 size-4" />
            )}
            Criar
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
