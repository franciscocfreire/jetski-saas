'use client'

import { useState } from 'react'
import { Eye, GraduationCap } from 'lucide-react'
import type { InstrutorParceiro } from '@/lib/api/services/emissao-delegada'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

/** yyyy-MM-dd → dd/MM/yyyy sem passar por Date (evita virar o dia pelo fuso). */
function formatarData(iso?: string | null): string | null {
  if (!iso) return null
  const [a, m, d] = iso.split('-')
  return a && m && d ? `${d}/${m}/${a}` : iso
}

function Campo({ rotulo, valor }: { rotulo: string; valor?: string | null }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{rotulo}</dt>
      <dd className="truncate font-medium">{valor || '—'}</dd>
    </div>
  )
}

/**
 * Card de instrutor disponível para a emissão delegada (visão da operadora). Clicar abre
 * os dados cadastrais e a assinatura em somente leitura: quem responde pelo instrutor é a
 * EAMA (NORMAM-212), então só ela altera.
 */
export function InstrutorParceiroCard({
  instrutor: p,
  eama,
}: {
  instrutor: InstrutorParceiro
  eama: string | null | undefined
}) {
  const [open, setOpen] = useState(false)
  const [imagemFalhou, setImagemFalhou] = useState(false)
  const origem =
    p.origem === 'OPERADORA' ? 'Seu instrutor — aprovado pela EAMA' : `Instrutor de ${eama ?? 'EAMA parceira'}`

  return (
    <>
      <button
        type="button"
        onClick={() => {
          setImagemFalhou(false)
          setOpen(true)
        }}
        data-testid="instrutores-disponivel"
        data-instrutor-id={p.id}
        data-origem={p.origem ?? 'EAMA'}
        className="flex w-full items-center gap-3 rounded-lg border p-3 text-left transition-colors hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        title="Ver dados do instrutor"
      >
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/10">
          <GraduationCap className="h-4 w-4 text-primary" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{p.nome}</p>
          <Badge variant="outline" className="mt-0.5 text-[10px]">
            {origem}
          </Badge>
        </div>
        <Eye className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
      </button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-md" data-testid="instrutor-parceiro-detalhes">
          <DialogHeader>
            <DialogTitle>{p.nome}</DialogTitle>
            <DialogDescription>
              {origem}. Dados usados no Atestado de Demonstração (Anexo 5-B-1)
              {p.origem === 'OPERADORA' ? '.' : ' — só a EAMA altera este cadastro.'}
            </DialogDescription>
          </DialogHeader>

          <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
            <Campo rotulo="RG (identidade)" valor={p.rg} />
            <Campo rotulo="Órgão emissor" valor={p.orgaoEmissor} />
            <Campo rotulo="CPF" valor={p.cpf} />
            <Campo rotulo="Nº da CHA" valor={p.cha} />
            <Campo rotulo="Data de emissão (identidade)" valor={formatarData(p.dataEmissao)} />
          </dl>

          <div>
            <p className="mb-1 text-xs text-muted-foreground">Assinatura</p>
            <div className="flex h-28 items-center justify-center rounded-md border bg-white p-2">
              {p.temAssinatura && p.assinaturaUrl && !imagemFalhou ? (
                // eslint-disable-next-line @next/next/no-img-element -- URL pré-assinada do storage (15 min), fora do otimizador do Next
                <img
                  src={p.assinaturaUrl}
                  alt={`Assinatura de ${p.nome}`}
                  className="max-h-full max-w-full object-contain"
                  onError={() => setImagemFalhou(true)}
                />
              ) : p.temAssinatura ? (
                <span className="px-2 text-center text-xs text-muted-foreground">
                  Não foi possível carregar a imagem agora. Recarregue a página e tente de novo.
                </span>
              ) : (
                <span className="px-2 text-center text-xs text-muted-foreground">
                  Sem assinatura cadastrada — o Anexo 5-B-1 sai com a linha em branco.
                </span>
              )}
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}
