'use client'

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Pencil, StickyNote, Check, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { reservasService } from '@/lib/api/services'

/** Nota livre da reserva (observações), editável inline na fila. */
export function NotaInline({ reservaId, nota }: { reservaId: string; nota?: string }) {
  const qc = useQueryClient()
  const [editando, setEditando] = useState(false)
  const [texto, setTexto] = useState(nota ?? '')

  const salvar = useMutation({
    mutationFn: () => reservasService.atualizar(reservaId, { observacoes: texto }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reservas'] })
      setEditando(false)
      toast.success('Nota salva.')
    },
    onError: () => toast.error('Falha ao salvar a nota.'),
  })

  if (editando) {
    return (
      <span className="inline-flex items-center gap-1">
        <Input
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          placeholder="Ex.: prefere jet 03, leva criança"
          className="h-7 w-56 text-xs"
          autoFocus
        />
        <Button
          type="button"
          size="icon"
          variant="ghost"
          className="h-7 w-7"
          disabled={salvar.isPending}
          onClick={() => salvar.mutate()}
        >
          <Check className="h-3.5 w-3.5" />
        </Button>
        <Button
          type="button"
          size="icon"
          variant="ghost"
          className="h-7 w-7"
          onClick={() => {
            setTexto(nota ?? '')
            setEditando(false)
          }}
        >
          <X className="h-3.5 w-3.5" />
        </Button>
      </span>
    )
  }

  return nota ? (
    <button
      type="button"
      onClick={() => setEditando(true)}
      className="inline-flex items-center gap-1 text-xs text-amber-700 hover:underline dark:text-amber-400"
      title="Editar nota"
    >
      <StickyNote className="h-3 w-3" /> {nota}
    </button>
  ) : (
    <button
      type="button"
      onClick={() => setEditando(true)}
      className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
    >
      <Pencil className="h-3 w-3" /> nota
    </button>
  )
}
