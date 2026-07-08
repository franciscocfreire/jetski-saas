import Link from 'next/link'
import { ArrowRight, Check } from 'lucide-react'

/**
 * Planos e preços — modelo aprovado em jul/2026 (frota + créditos de emissão).
 * A tabela `plano` (seed V002) ainda reflete os valores antigos; a cobrança
 * por plano (metering v2) deve nascer com ESTES números.
 */
const PLANOS = [
  {
    nome: 'Trial',
    preco: 'Grátis',
    periodo: '14 dias',
    descricao: 'Conheça o sistema com acesso completo, sem compromisso.',
    itens: [
      'Acesso a todos os recursos',
      '5 emissões CHA-MTA-E inclusas',
      'Até 3 jetskis · 2 usuários',
      'Sem cartão de crédito',
    ],
    cta: 'Começar agora',
  },
  {
    nome: 'Essencial',
    preco: 'R$ 149',
    periodo: '/mês',
    descricao: 'Para sair do caderno e do WhatsApp sem mudar sua rotina.',
    itens: [
      'Até 3 jetskis · 2 usuários',
      '10 emissões CHA-MTA-E/mês (exced. R$ 20)',
      'Portal do cliente com reserva e sinal PIX',
      'Check-in com fotos e fechamentos',
    ],
    cta: 'Testar 14 dias grátis',
  },
  {
    nome: 'Profissional',
    preco: 'R$ 449',
    periodo: '/mês',
    destaque: true,
    descricao: 'Para a operação que vive de temporada e não pode parar no balcão.',
    itens: [
      'Até 10 jetskis · equipe ilimitada',
      '60 emissões CHA-MTA-E/mês (exced. R$ 12)',
      'Assinatura digital do termo (PAdES)',
      'Comissões, campanhas, combustível e manutenção',
    ],
    cta: 'Testar 14 dias grátis',
  },
  {
    nome: 'Premium',
    preco: 'R$ 799',
    periodo: '/mês',
    descricao: 'Para marinas e frotas grandes que querem a própria marca na frente.',
    itens: [
      'Até 30 jetskis · equipe ilimitada',
      '150 emissões CHA-MTA-E/mês (exced. R$ 8)',
      'White-label: {sua-marca}.meujet.com.br',
      'Destaque no marketplace e suporte prioritário',
    ],
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
            Todo plano começa com 14 dias grátis. Sem fidelidade — e a emissão
            nunca trava no atendimento: excedentes entram na fatura seguinte.
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
                  Recomendado
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

        <p className="mt-8 text-center text-sm text-white/40 max-w-2xl mx-auto">
          Plano anual: 2 meses grátis · Onboarding assistido opcional: R$ 299 com
          20 créditos de bônus · Frota acima de 30 jets ou multi-unidade: plano sob medida.
        </p>

        {/* A conta do crédito — o argumento que fecha */}
        <div className="mt-16 max-w-4xl mx-auto rounded-2xl border border-white/[0.08] bg-white/[0.02] p-8 md:p-10">
          <h3 className="font-display text-2xl md:text-3xl font-medium text-white text-center leading-snug">
            A habilitação temporária que travava seu balcão,
            <br className="hidden md:block" />
            <span className="text-gold-gradient"> agora custa um crédito.</span>
          </h3>
          <div className="mt-8 grid gap-6 sm:grid-cols-[1fr_auto_1fr] items-center max-w-2xl mx-auto text-center">
            <div>
              <p className="text-[11px] uppercase tracking-[0.2em] text-white/40">Na mão, no site da Marinha</p>
              <p className="font-display text-4xl md:text-5xl font-medium text-white/40 line-through decoration-gold decoration-2 tabular-nums">~40 min</p>
              <p className="mt-1 text-sm text-white/40">por habilitação, com o cliente esperando</p>
            </div>
            <span className="text-white/30 text-sm tracking-widest">VS</span>
            <div>
              <p className="text-[11px] uppercase tracking-[0.2em] text-gold/70">Crédito Meu Jet</p>
              <p className="font-display text-4xl md:text-5xl font-medium text-gold tabular-nums">R$ 12</p>
              <p className="mt-1 text-sm text-white/50">emitida em segundos, no balcão ou pelo cliente</p>
            </div>
          </div>
          <p className="mt-8 text-center text-xs text-white/35 max-w-2xl mx-auto leading-relaxed">
            1 crédito = 1 documentação emitida à Marinha, com GRU + PIX, comprovante e
            acompanhamento da devolutiva. A taxa oficial da GRU é paga à União, como sempre.
            A temporária emitida vale 30 dias e é reutilizável.
          </p>
        </div>
      </div>
    </section>
  )
}
