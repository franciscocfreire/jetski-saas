import { Logo } from '@/components/logo'

/**
 * Mock ilustrativo do painel (CSS puro, sem screenshot) para o hero da página
 * B2B — mesma técnica do preview de marca. Valores são exemplo.
 */
export function B2bDashboardMock() {
  return (
    <div className="relative" aria-hidden>
      <div className="absolute -inset-4 bg-gold/5 rounded-3xl blur-2xl" />
      <div className="relative flex overflow-hidden rounded-2xl border border-white/10 shadow-premium bg-[#FCFAF6]">
        {/* Sidebar navy */}
        <div className="w-36 shrink-0 space-y-3 bg-[#0A1628] p-4">
          <div className="flex items-center gap-2 border-b border-white/10 pb-3">
            <Logo variant="icon" theme="dark" size={12} />
            <span className="font-display text-[10px] font-semibold uppercase tracking-[0.18em] text-[#F8F4EA]">
              Meu Jet
            </span>
          </div>
          <div className="rounded border-l-2 border-[#C9A24B] bg-[#C9A24B]/10 px-2 py-1.5 text-[10px] font-medium text-[#E3CF9E]">
            Dashboard
          </div>
          {['Agenda', 'Balcão', 'Locações', 'Comissões', 'Fechamentos'].map((item) => (
            <div key={item} className="px-2 py-1 text-[10px] text-white/40">
              {item}
            </div>
          ))}
        </div>

        {/* Conteúdo claro */}
        <div className="flex-1 space-y-4 p-5">
          <div>
            <p className="text-sm font-semibold text-[#12263F]">Bom dia, Marina</p>
            <p className="text-[10px] text-[#7E8DA1]">Sábado, 12 de dezembro</p>
          </div>
          <div className="grid grid-cols-3 gap-2.5">
            {[
              { k: 'Locações hoje', v: '14', d: '+3 vs. ontem' },
              { k: 'Receita do dia', v: 'R$ 8.940', d: '+12%' },
              { k: 'Frota', v: '9/12', d: '2 em manutenção' },
            ].map((s) => (
              <div key={s.k} className="rounded-lg border border-[#E3D9C2] bg-white p-2.5">
                <p className="text-[8px] font-medium uppercase tracking-wide text-[#7E8DA1]">{s.k}</p>
                <p className="text-base font-semibold tabular-nums text-[#1E4266]">{s.v}</p>
                <p className="text-[8px] text-[#2C965D]">{s.d}</p>
              </div>
            ))}
          </div>
          <div className="flex gap-2">
            <span className="rounded-md bg-[#1E4266] px-3 py-1.5 text-[10px] font-medium text-white">
              Nova locação
            </span>
            <span className="rounded-md border border-[#C9A24B] px-3 py-1.5 text-[10px] font-medium text-[#7a5f1e]">
              Fechar o dia
            </span>
          </div>
          {/* Barras ilustrativas */}
          <div className="flex h-16 items-end gap-1.5 rounded-lg border border-[#E3D9C2] bg-white p-2.5">
            {[35, 55, 40, 70, 62, 85, 78].map((h, i) => (
              <div
                key={i}
                className="flex-1 rounded-t-sm"
                style={{ height: `${h}%`, backgroundColor: i === 6 ? '#C9A24B' : '#33689A' }}
              />
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
