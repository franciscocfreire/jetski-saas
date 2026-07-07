'use client'

import { Check, Circle, Clock } from 'lucide-react'

export interface PassoCiclo {
  titulo: string
  detalhe: string
  /** ok = feito; atencao = desbloqueado mas parado; aguardando = depende do anterior */
  estado: 'ok' | 'atencao' | 'aguardando'
}

/**
 * Timeline vertical do ciclo da GRU (Marinha) — apresentacional, usada no
 * sheet da página GRUs e na página de detalhe da reserva.
 */
export function GruCicloTimeline({ passos }: { passos: PassoCiclo[] }) {
  return (
    <ol className="mt-2">
      {passos.map((p, i) => (
        <li key={p.titulo} className="relative flex gap-3 pb-4 last:pb-0">
          {i < passos.length - 1 && (
            <span className="absolute left-[11px] top-6 h-full w-px bg-border" />
          )}
          <span
            className={`z-10 mt-0.5 grid h-[22px] w-[22px] shrink-0 place-items-center rounded-full border ${
              p.estado === 'ok'
                ? 'border-emerald-500 bg-emerald-50 text-emerald-600'
                : p.estado === 'atencao'
                  ? 'border-amber-400 bg-amber-50 text-amber-600'
                  : 'border-border bg-muted text-muted-foreground'
            }`}
          >
            {p.estado === 'ok' ? (
              <Check size={13} />
            ) : p.estado === 'atencao' ? (
              <Clock size={12} />
            ) : (
              <Circle size={8} />
            )}
          </span>
          <div className="min-w-0">
            <p className={`text-sm ${p.estado === 'aguardando' ? 'text-muted-foreground' : 'font-medium'}`}>
              {p.titulo}
            </p>
            <p className="text-xs text-muted-foreground">{p.detalhe}</p>
          </div>
        </li>
      ))}
    </ol>
  )
}

/** Monta os 5 passos padrão do ciclo a partir dos timestamps. */
export function montarPassosCiclo(g: {
  gruNumero: string
  gruValor?: number
  gruGeradaEm?: string
  gruPago?: boolean
  gruPagoEm?: string
  documentoId?: string | null
  documentoEmitidoEm?: string
  marinhaEnviadaEm?: string
  marinhaConfirmadaEm?: string
}, formatCurrency: (v: number) => string): PassoCiclo[] {
  const fmt = (d?: string) =>
    d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : null
  const temDoc = !!(g.documentoId || g.documentoEmitidoEm)
  return [
    {
      titulo: 'GRU gerada',
      detalhe: `Nº ${g.gruNumero}${g.gruValor != null ? ` · ${formatCurrency(g.gruValor)}` : ''}${
        g.gruGeradaEm ? ` · ${fmt(g.gruGeradaEm)}` : ''}`,
      estado: 'ok',
    },
    {
      titulo: 'GRU paga',
      detalhe: g.gruPago ? `Paga em ${fmt(g.gruPagoEm) ?? '—'}` : 'Aguardando pagamento',
      estado: g.gruPago ? 'ok' : 'atencao',
    },
    {
      titulo: 'Documentação emitida',
      detalhe: g.documentoEmitidoEm
        ? `Emitida em ${fmt(g.documentoEmitidoEm)}`
        : 'Ainda não emitida',
      estado: g.documentoEmitidoEm ? 'ok' : g.gruPago ? 'atencao' : 'aguardando',
    },
    {
      titulo: 'E-mail à Marinha',
      detalhe: g.marinhaEnviadaEm
        ? `Enviado em ${fmt(g.marinhaEnviadaEm)}`
        : temDoc
          ? 'Não enviado — use Reenviar'
          : 'Depende da emissão',
      estado: g.marinhaEnviadaEm ? 'ok' : temDoc ? 'atencao' : 'aguardando',
    },
    {
      titulo: 'Confirmada pela Marinha',
      detalhe: g.marinhaConfirmadaEm
        ? `Devolutiva anexada em ${fmt(g.marinhaConfirmadaEm)}`
        : 'Aguardando devolutiva (e-mail da Marinha à loja)',
      estado: g.marinhaConfirmadaEm ? 'ok' : g.marinhaEnviadaEm ? 'atencao' : 'aguardando',
    },
  ]
}
