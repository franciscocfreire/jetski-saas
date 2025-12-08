import Link from 'next/link'
import { Hero } from '@/components/public/hero'
import { JetskiGrid } from '@/components/public/jetski-grid'
import { HowItWorks } from '@/components/public/how-it-works'
import { ArrowRight } from 'lucide-react'

export default function HomePage() {
  return (
    <div className="bg-black min-h-screen">
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
                  Coleção
                </span>
              </div>
              <h2 className="font-display text-4xl md:text-5xl font-medium text-white">
                Embarcações em
                <br />
                <span className="text-gold-gradient">Destaque</span>
              </h2>
            </div>

            <p className="text-white/50 max-w-md text-lg leading-relaxed">
              Nossa curadoria das melhores opções disponíveis nas empresas parceiras.
            </p>
          </div>

          {/* Grid */}
          <JetskiGrid />

          {/* View All */}
          <div className="mt-16 text-center">
            <p className="text-white/30 text-sm mb-4">
              Exibindo 8 de 200+ embarcações
            </p>
            <button className="inline-flex items-center gap-2 text-gold hover:text-gold/80 transition-colors duration-300 group">
              <span className="text-sm tracking-wide">Ver todas as embarcações</span>
              <ArrowRight className="h-4 w-4 group-hover:translate-x-1 transition-transform duration-300" />
            </button>
          </div>
        </div>
      </section>

      {/* How It Works */}
      <HowItWorks />

      {/* CTA Section - For Companies */}
      <section className="relative py-32 overflow-hidden">
        {/* Background */}
        <div className="absolute inset-0 bg-gradient-to-br from-primary/20 via-black to-gold/10" />
        <div className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.02)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.02)_1px,transparent_1px)] bg-[size:64px_64px]" />

        {/* Glow Effects */}
        <div className="absolute top-0 left-1/4 w-96 h-96 bg-primary/30 rounded-full blur-[150px]" />
        <div className="absolute bottom-0 right-1/4 w-96 h-96 bg-gold/20 rounded-full blur-[150px]" />

        <div className="container relative text-center">
          <div className="inline-flex items-center gap-3 mb-6">
            <div className="h-px w-8 bg-gold/60" />
            <span className="text-gold/80 text-sm tracking-[0.3em] uppercase">
              Para Empresas
            </span>
            <div className="h-px w-8 bg-gold/60" />
          </div>

          <h2 className="font-display text-4xl md:text-5xl lg:text-6xl font-medium text-white max-w-3xl mx-auto leading-tight">
            Faça Parte do
            <br />
            <span className="text-gold-gradient">Marketplace Premium</span>
          </h2>

          <p className="mt-6 text-lg text-white/50 max-w-2xl mx-auto leading-relaxed">
            Cadastre sua empresa e alcance clientes de alto poder aquisitivo.
            Gestão completa de reservas, locações e finanças em uma única plataforma.
          </p>

          <div className="mt-12 flex flex-col sm:flex-row items-center justify-center gap-4">
            <Link
              href="/signup"
              className="inline-flex items-center justify-center px-8 py-4 bg-white text-black text-base font-medium hover:bg-white/90 transition-all duration-300"
            >
              Cadastrar Minha Empresa
              <ArrowRight className="ml-2 h-4 w-4" />
            </Link>
            <Link
              href="/login"
              className="inline-flex items-center justify-center px-8 py-4 border border-white/20 text-white text-base hover:bg-white/10 hover:border-white/40 transition-all duration-300"
            >
              Já tenho conta
            </Link>
          </div>

          {/* Trust Badges */}
          <div className="mt-16 flex flex-wrap items-center justify-center gap-8 text-white/30 text-sm">
            <div className="flex items-center gap-2">
              <div className="w-2 h-2 rounded-full bg-green-500" />
              <span>Suporte 24/7</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-2 h-2 rounded-full bg-green-500" />
              <span>Pagamentos Seguros</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-2 h-2 rounded-full bg-green-500" />
              <span>Sem taxa de adesão</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  )
}
