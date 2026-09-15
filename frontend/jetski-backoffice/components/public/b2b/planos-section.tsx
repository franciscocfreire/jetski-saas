import Link from 'next/link'
import { ArrowRight, Mail } from 'lucide-react'

/**
 * Planos — sem valores publicados enquanto os sócios decidem o modelo de
 * cobrança (plano × pré-pago × pós-pago, set/2026). Os valores de lançamento
 * de jul/2026 (R$ 149/449/799 + excedente) saíram do site porque divergiam do
 * que o sistema cobra. Quando o modelo for decidido, publicar aqui os mesmos
 * números que o backend aplica.
 */
export function PlanosSection() {
  return (
    <section id="planos" className="py-28 relative scroll-mt-24">
      <div className="container">
        <div className="text-center mb-12">
          <div className="inline-flex items-center gap-3 mb-6">
            <div className="h-px w-8 bg-gold/60" />
            <span className="text-gold/80 text-sm tracking-[0.3em] uppercase">Planos</span>
            <div className="h-px w-8 bg-gold/60" />
          </div>
          <h2 className="font-display text-4xl md:text-5xl font-medium text-white">
            Um plano para o <span className="text-gold-gradient">tamanho da sua frota</span>
          </h2>
          <p className="mt-4 text-white/50 max-w-xl mx-auto">
            Estamos definindo os valores de lançamento. Comece com 14 dias grátis,
            com acesso completo, ou fale com a gente para montar a proposta da sua locadora.
          </p>
          <div className="mt-8 flex flex-col sm:flex-row gap-4 justify-center">
            <Link
              href="/signup"
              className="inline-flex items-center justify-center gap-2 bg-white px-8 py-4 text-base font-medium text-black hover:bg-white/90 transition-all duration-300"
            >
              Começar teste grátis de 14 dias
              <ArrowRight className="h-4 w-4" />
            </Link>
            <a
              href="mailto:suporte@meujet.com.br?subject=Proposta%20Meu%20Jet"
              className="inline-flex items-center justify-center gap-2 border border-white/20 px-8 py-4 text-base text-white hover:bg-white/10 hover:border-white/40 transition-all duration-300"
            >
              <Mail className="h-4 w-4" />
              Falar com a gente
            </a>
          </div>
        </div>

        {/* A conta do tempo — o argumento que fecha */}
        <div className="mt-16 max-w-4xl mx-auto rounded-2xl border border-white/[0.08] bg-white/[0.02] p-8 md:p-10">
          <h3 className="font-display text-2xl md:text-3xl font-medium text-white text-center leading-snug">
            A habilitação temporária que travava seu balcão,
            <br className="hidden md:block" />
            <span className="text-gold-gradient"> agora sai em segundos.</span>
          </h3>
          <div className="mt-8 grid gap-6 sm:grid-cols-[1fr_auto_1fr] items-center max-w-2xl mx-auto text-center">
            <div>
              <p className="text-[11px] uppercase tracking-[0.2em] text-white/40">Na mão, no site da Marinha</p>
              <p className="font-display text-4xl md:text-5xl font-medium text-white/40 line-through decoration-gold decoration-2 tabular-nums">~40 min</p>
              <p className="mt-1 text-sm text-white/40">por habilitação, com o cliente esperando</p>
            </div>
            <span className="text-white/30 text-sm tracking-widest">VS</span>
            <div>
              <p className="text-[11px] uppercase tracking-[0.2em] text-gold/70">Com o Meu Jet</p>
              <p className="font-display text-4xl md:text-5xl font-medium text-gold">segundos</p>
              <p className="mt-1 text-sm text-white/50">emitida no balcão ou pelo próprio cliente</p>
            </div>
          </div>
          <p className="mt-8 text-center text-xs text-white/35 max-w-2xl mx-auto leading-relaxed">
            Cada documentação é emitida à Marinha com GRU + PIX, comprovante e
            acompanhamento da devolutiva. A taxa oficial da GRU é paga à União, como sempre.
            A temporária emitida vale 30 dias e é reutilizável.
          </p>
        </div>
      </div>
    </section>
  )
}
