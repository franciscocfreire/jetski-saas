import Link from 'next/link'
import { ArrowRight, Check } from 'lucide-react'

/**
 * Planos e preços — valores espelham o seed de `plano` (V002).
 * Se os planos mudarem no backend, atualizar aqui também.
 */
const PLANOS = [
  {
    nome: 'Trial',
    preco: 'Grátis',
    periodo: '14 dias',
    destaque: true,
    descricao: 'Conheça o sistema com acesso completo, sem compromisso.',
    itens: ['Acesso a todos os recursos', 'Até 3 jetskis', '2 usuários', 'Sem cartão de crédito'],
    cta: 'Começar agora',
  },
  {
    nome: 'Basic',
    preco: 'R$ 99',
    periodo: '/mês',
    descricao: 'Para operações enxutas que querem sair da planilha.',
    itens: ['Até 5 jetskis', '3 usuários', '100 locações/mês', 'Marketplace incluso'],
    cta: 'Testar 14 dias grátis',
  },
  {
    nome: 'Pro',
    preco: 'R$ 299',
    periodo: '/mês',
    descricao: 'Para locadoras em crescimento, com equipe e vendedores.',
    itens: ['Até 20 jetskis', '10 usuários', '500 locações/mês', 'Comissões e fechamentos'],
    cta: 'Testar 14 dias grátis',
  },
  {
    nome: 'Enterprise',
    preco: 'R$ 999',
    periodo: '/mês',
    descricao: 'Para grandes operações e marinas com múltiplos pontos.',
    itens: ['Até 100 jetskis', '50 usuários', 'Locações ilimitadas', 'White-label completo'],
    cta: 'Testar 14 dias grátis',
  },
]

export function PlanosSection() {
  return (
    <section id="planos" className="py-28 relative scroll-mt-24">
      <div className="container">
        <div className="text-center mb-16">
          <div className="inline-flex items-center gap-3 mb-6">
            <div className="h-px w-8 bg-gold/60" />
            <span className="text-gold/80 text-sm tracking-[0.3em] uppercase">Planos</span>
            <div className="h-px w-8 bg-gold/60" />
          </div>
          <h2 className="font-display text-4xl md:text-5xl font-medium text-white">
            Preços de <span className="text-gold-gradient">lançamento</span>
          </h2>
          <p className="mt-4 text-white/50 max-w-xl mx-auto">
            Todo plano começa com 14 dias grátis. Sem taxa de adesão, sem fidelidade.
          </p>
        </div>

        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4 max-w-6xl mx-auto">
          {PLANOS.map((p) => (
            <div
              key={p.nome}
              className={
                p.destaque
                  ? 'relative p-7 rounded-2xl border border-gold/40 bg-gold/[0.04] shadow-gold-glow'
                  : 'relative p-7 rounded-2xl border border-white/[0.08] bg-white/[0.02] hover:border-white/20 transition-colors duration-300'
              }
            >
              {p.destaque && (
                <span className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full bg-gold px-3 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-[#231A05]">
                  Comece aqui
                </span>
              )}
              <h3 className="font-display text-lg font-medium text-white">{p.nome}</h3>
              <div className="mt-3 flex items-baseline gap-1">
                <span className="font-display text-4xl font-medium text-white tabular-nums">{p.preco}</span>
                <span className="text-sm text-white/40">{p.periodo}</span>
              </div>
              <p className="mt-3 text-sm text-white/50 leading-relaxed min-h-[3.5rem]">{p.descricao}</p>
              <ul className="mt-5 space-y-2.5">
                {p.itens.map((item) => (
                  <li key={item} className="flex items-start gap-2 text-sm text-white/70">
                    <Check className="mt-0.5 h-4 w-4 shrink-0 text-gold" />
                    {item}
                  </li>
                ))}
              </ul>
              <Link
                href="/signup"
                className={
                  p.destaque
                    ? 'mt-7 inline-flex w-full items-center justify-center gap-2 bg-white px-4 py-3 text-sm font-medium text-black hover:bg-white/90 transition-all duration-300'
                    : 'mt-7 inline-flex w-full items-center justify-center gap-2 border border-white/20 px-4 py-3 text-sm text-white hover:bg-white/10 hover:border-white/40 transition-all duration-300'
                }
              >
                {p.cta}
                <ArrowRight className="h-4 w-4" />
              </Link>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
