import Link from 'next/link'
import { Hero } from '@/components/public/hero'
import { JetskiGrid } from '@/components/public/jetski-grid'
import { HowItWorks } from '@/components/public/how-it-works'
import { ArrowRight, FileCheck2, PenLine, Wallet } from 'lucide-react'

export default function HomePage() {
  return (
    <div className="bg-abyss min-h-screen">
      {/* Hero Section */}
      <Hero />

      {/* Offerings Grid */}
      <section id="ofertas" className="py-32 bg-premium-navy relative">
        {/* Background Elements */}
        <div className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.015)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.015)_1px,transparent_1px)] bg-[size:64px_64px]" />

        <div className="container relative">
          {/* Section Header */}
          <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-6 mb-16">
            <div>
              <div className="inline-flex items-center gap-3 mb-4">
                <div className="h-px w-8 bg-gold/60" />
                <span className="text-gold/80 text-sm tracking-[0.3em] uppercase">
                  Marketplace
                </span>
              </div>
              <h2 className="font-display text-4xl md:text-5xl font-medium text-white">
                Marketplace de
                <br />
                <span className="text-gold-gradient">Embarcações</span>
              </h2>
            </div>

            <p className="text-white/50 max-w-md text-lg leading-relaxed">
              Embarcações reais publicadas pelas locadoras parceiras — reserve direto com quem opera.
            </p>
          </div>

          {/* Grid (a contagem real é exibida pelo próprio grid) */}
          <JetskiGrid />
        </div>
      </section>

      {/* How It Works */}
      <HowItWorks />

      {/* Faixa B2B — funil para /para-empresas */}
      <section className="relative py-32 overflow-hidden">
        {/* Background */}
        <div className="absolute inset-0 bg-gradient-to-br from-primary/20 via-transparent to-gold/10" />
        <div className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.02)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.02)_1px,transparent_1px)] bg-[size:64px_64px]" />

        {/* Glow Effects */}
        <div className="absolute top-0 left-1/4 w-96 h-96 bg-primary/30 rounded-full blur-[150px]" />
        <div className="absolute bottom-0 right-1/4 w-96 h-96 bg-gold/20 rounded-full blur-[150px]" />

        <div className="container relative text-center">
          <div className="inline-flex items-center gap-3 mb-6">
            <div className="h-px w-8 bg-gold/60" />
            <span className="text-gold/80 text-sm tracking-[0.3em] uppercase">
              Para Locadoras
            </span>
            <div className="h-px w-8 bg-gold/60" />
          </div>

          <h2 className="font-display text-4xl md:text-5xl lg:text-6xl font-medium text-white max-w-3xl mx-auto leading-tight">
            Tem uma locadora
            <br />
            de <span className="text-gold-gradient">jetskis?</span>
          </h2>

          <p className="mt-6 text-lg text-white/50 max-w-2xl mx-auto leading-relaxed">
            O Meu Jet é o sistema que cuida da operação inteira — e ainda coloca
            suas embarcações neste marketplace.
          </p>

          {/* Diferenciais reais */}
          <div className="mt-12 grid gap-6 sm:grid-cols-3 max-w-4xl mx-auto text-left">
            <div className="glass rounded-xl p-6">
              <FileCheck2 className="h-6 w-6 text-gold mb-3" />
              <p className="text-white font-medium">Documentação NORMAM-212</p>
              <p className="mt-1 text-sm text-white/50">
                Emissão automática com GRU da Marinha paga por PIX ou boleto.
              </p>
            </div>
            <div className="glass rounded-xl p-6">
              <PenLine className="h-6 w-6 text-gold mb-3" />
              <p className="text-white font-medium">Assinatura digital</p>
              <p className="mt-1 text-sm text-white/50">
                Termos assinados no celular, com OTP e carimbo de tempo.
              </p>
            </div>
            <div className="glass rounded-xl p-6">
              <Wallet className="h-6 w-6 text-gold mb-3" />
              <p className="text-white font-medium">Comissões e fechamentos</p>
              <p className="mt-1 text-sm text-white/50">
                Diárias de vendedores e fechamento do dia sem planilha.
              </p>
            </div>
          </div>

          <div className="mt-12 flex justify-center">
            <Link
              href="/para-empresas"
              className="inline-flex items-center justify-center px-8 py-4 bg-white text-black text-base font-medium hover:bg-white/90 transition-all duration-300"
            >
              Conhecer o Meu Jet para locadoras
              <ArrowRight className="ml-2 h-4 w-4" />
            </Link>
          </div>
        </div>
      </section>
    </div>
  )
}
