'use client'

import { useEffect, useState } from 'react'
import { ShieldAlert } from 'lucide-react'
import { apiClient } from '@/lib/api/client'

interface SessaoSuporte {
  id: string
  tenantId: string
  somenteLeitura: boolean
}

/**
 * Faixa permanente do modo suporte.
 *
 * Existe porque acesso de plataforma a uma empresa não pode ser invisível: quem está
 * operando precisa lembrar, a cada tela, que está dentro de outra empresa e sob trilha.
 * Era exatamente o que faltava no god mode do switcher — nada distinguia "minha empresa"
 * de "empresa de um cliente".
 *
 * Renderiza nada quando não há sessão: o custo é uma chamada por carga de layout.
 */
export function SuporteBanner() {
  const [sessao, setSessao] = useState<SessaoSuporte | null>(null)
  const [saindo, setSaindo] = useState(false)

  useEffect(() => {
    apiClient
      .get<SessaoSuporte>('/v1/suporte/atual')
      .then((r) => setSessao(r.status === 204 ? null : r.data))
      .catch(() => setSessao(null))
  }, [])

  if (!sessao) return null

  const sair = async () => {
    setSaindo(true)
    try {
      await apiClient.post('/v1/suporte/sair')
    } finally {
      // Recarrega na raiz: sem o cookie, o backoffice volta a ser o backoffice.
      window.location.href = '/dashboard'
    }
  }

  return (
    <div
      className={`flex flex-wrap items-center justify-between gap-2 px-4 py-2 text-sm text-white ${
        sessao.somenteLeitura ? 'bg-amber-600' : 'bg-red-700'
      }`}
      role="status"
    >
      <span className="flex items-center gap-2">
        <ShieldAlert className="h-4 w-4 shrink-0" />
        <strong>MODO SUPORTE</strong>
        <span className="opacity-90">
          {sessao.somenteLeitura
            ? 'somente leitura — alterações estão bloqueadas'
            : 'COM ESCRITA — tudo que você fizer fica registrado em nome da empresa'}
        </span>
      </span>
      <button
        onClick={sair}
        disabled={saindo}
        className="rounded bg-white/20 px-3 py-1 font-medium hover:bg-white/30 disabled:opacity-60"
      >
        {saindo ? 'Saindo…' : 'Sair do modo suporte'}
      </button>
    </div>
  )
}
