'use client'

import { useEffect, useState } from 'react'
import { Minus, Plus, Type } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

/**
 * Ajuste do tamanho do texto — acessibilidade real de quem opera no balcão ou
 * na praia, muitas vezes no celular e no sol.
 *
 * Mexe no font-size da RAIZ: como o Tailwind dimensiona em rem, o layout inteiro
 * acompanha (texto, espaçamentos, alvos de toque), em vez de só a letra crescer
 * e estourar as caixas.
 *
 * A escolha fica no localStorage e é reaplicada por um script inline no layout
 * raiz, ANTES da primeira pintura: sem isso a página apareceria no tamanho padrão
 * e saltaria para o escolhido — pior que não ter o recurso.
 *
 * Vive num dropdown (e não solto no header) porque o header do celular já disputa
 * espaço com título, tema e notificações. Os botões são <button> comuns, não
 * DropdownMenuItem, de propósito: item fecha o menu a cada clique, e aqui a pessoa
 * precisa tocar em + várias vezes seguidas vendo a página mudar atrás.
 */
export const CHAVE_FONTE = 'mj-app-fonte'
const NIVEIS = [14, 16, 18, 20, 22, 24, 28, 32] as const
const PADRAO = 16

export function TamanhoFonte() {
  const [px, setPx] = useState<number>(PADRAO)

  useEffect(() => {
    try {
      const salvo = Number(localStorage.getItem(CHAVE_FONTE))
      if ((NIVEIS as readonly number[]).includes(salvo)) setPx(salvo)
    } catch {
      // localStorage bloqueado (janela privada): segue no padrão
    }
  }, [])

  function aplicar(novo: number) {
    setPx(novo)
    document.documentElement.style.fontSize = `${novo}px`
    try {
      localStorage.setItem(CHAVE_FONTE, String(novo))
    } catch {
      // sem persistência, mas o ajuste vale para esta sessão
    }
  }

  const i = NIVEIS.indexOf(px as (typeof NIVEIS)[number])
  const posicao = i < 0 ? NIVEIS.indexOf(PADRAO) : i
  const percentual = Math.round((px / PADRAO) * 100)

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="h-10 w-10 sm:h-9 sm:w-9"
          aria-label="Ajustar o tamanho do texto"
          title="Tamanho do texto"
        >
          <Type className="h-5 w-5" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56 max-w-[calc(100vw-2rem)] p-3">
        <p className="mb-2 text-xs font-medium text-muted-foreground">Tamanho do texto</p>
        <div className="flex items-center justify-between gap-2">
          <Button
            variant="outline"
            size="icon"
            className="h-9 w-9"
            onClick={() => aplicar(NIVEIS[posicao - 1])}
            disabled={posicao <= 0}
            aria-label="Diminuir o tamanho do texto"
          >
            <Minus className="h-4 w-4" />
          </Button>

          <button
            type="button"
            onClick={() => aplicar(PADRAO)}
            title="Voltar ao padrão"
            aria-label={`Tamanho do texto em ${percentual}% — voltar ao padrão`}
            className="min-w-[4rem] rounded px-2 py-1 text-sm tabular-nums text-foreground transition hover:bg-accent"
          >
            {percentual}%
          </button>

          <Button
            variant="outline"
            size="icon"
            className="h-9 w-9"
            onClick={() => aplicar(NIVEIS[posicao + 1])}
            disabled={posicao >= NIVEIS.length - 1}
            aria-label="Aumentar o tamanho do texto"
          >
            <Plus className="h-4 w-4" />
          </Button>
        </div>
        <p className="mt-2 text-[11px] leading-snug text-muted-foreground">
          Vale para este aparelho. Toque na porcentagem para voltar ao padrão.
        </p>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
