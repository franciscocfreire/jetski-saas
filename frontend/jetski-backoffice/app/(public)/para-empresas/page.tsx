import type { Metadata } from 'next'
import Link from 'next/link'
import {
  ArrowRight,
  Calendar,
  Camera,
  FileCheck2,
  Landmark,
  LineChart,
  Palette,
  PenLine,
  Store,
  Wallet,
} from 'lucide-react'
import { B2bDashboardMock } from '@/components/public/b2b/dashboard-mock'
import { PlanosSection } from '@/components/public/b2b/planos-section'
import { FaqSection } from '@/components/public/b2b/faq-section'

export const metadata: Metadata = {
  title: 'Meu Jet para Locadoras | Sistema de gestão + marketplace',
  description:
    'Da reserva ao fechamento do dia: documentação NORMAM-212 com GRU automática (PIX/boleto), assinatura digital, comissões e financeiro — num painel só, com a sua marca. Teste grátis 14 dias.',
  openGraph: {
    title: 'Meu Jet para Locadoras',
    description:
      'O sistema completo para locadoras de jetskis: NORMAM-212, GRU com PIX, assinatura digital, comissões e fechamentos.',
    url: 'https://meujet.com.br/para-empresas',
  },
}

const RECURSOS = [
  {
    icon: Calendar,
    titulo: 'Agenda e reservas',
    descricao: 'Agenda por jetski e horário, fila de espera e pendências do dia num painel só.',
  },
  {
    icon: FileCheck2,
    titulo: 'Balcão com NORMAM-212',
    descricao: 'Emissão automática da documentação da Marinha, com GRU gerada e paga por PIX ou boleto.',
  },
  {
    icon: Camera,
    titulo: 'Check-in/out com fotos',
    descricao: 'Fotos obrigatórias e horímetro no início e fim de cada locação — fim das discussões sobre avarias.',
  },
  {
    icon: PenLine,
    titulo: 'Assinatura digital',
    descricao: 'Termo de responsabilidade assinado no celular, com OTP, carimbo de tempo e PDF assinado (PAdES).',
  },
  {
    icon: Wallet,
    titulo: 'Comissões e diárias',
    descricao: 'Regras de comissão por vendedor, presença diária e bônus calculados automaticamente.',
  },
  {
    icon: Landmark,
    titulo: 'Fechamento diário e mensal',
    descricao: 'Consolidação de receitas, combustível e comissões, com trava de edições retroativas.',
  },
  {
    icon: LineChart,
    titulo: 'Financeiro e relatórios',
    descricao: 'Dashboard de receitas vs. despesas, pagamentos, despesas operacionais e auditoria completa.',
  },
  {
    icon: Palette,
    titulo: 'Sua marca',
    descricao: 'White-label: seu logo e suas cores no painel, com endereço próprio {sua-marca}.meujet.com.br.',
  },
]

const PASSOS = [
  {
    numero: '01',
    titulo: 'Cadastre sua empresa',
    descricao: 'Crie a conta em minutos e ganhe um endereço próprio: {sua-marca}.meujet.com.br. Sem cartão de crédito.',
  },
  {
    numero: '02',
    titulo: 'Configure frota e equipe',
    descricao: 'Cadastre jetskis, modelos e preços, convide operadores e vendedores e defina as regras de comissão.',
  },
  {
    numero: '03',
    titulo: 'Opere e acompanhe',
    descricao: 'Atenda no balcão com documentação automática, acompanhe locações em tempo real e feche o dia sem planilha.',
  },
]

export default function ParaEmpresasPage() {
  return (
    <div className="bg-abyss min-h-screen">
      {/* ===== Hero B2B ===== */}
      <section className="relative pt-40 pb-24 overflow-hidden">
        <div className="absolute inset-0 bg-premium-navy" />
        <div className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.02)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.02)_1px,transparent_1px)] bg-[size:64px_64px]" />
        <div className="absolute top-0 right-0 w-[500px] h-[500px] bg-gold/10 rounded-full blur-[150px]" />

        <div className="container relative grid gap-16 lg:grid-cols-2 lg:items-center">
          <div>
            <div className="inline-flex items-center gap-3 mb-6">
              <div className="h-px w-8 bg-gold/60" />
              <span className="text-gold/80 text-sm tracking-[0.3em] uppercase">
                Para Locadoras
              </span>
            </div>
            <h1 className="font-display text-4xl md:text-5xl lg:text-6xl font-medium text-white leading-[1.1]">
              O sistema completo para sua
              <br />
              <span className="text-gold-gradient">locadora de jetskis</span>
            </h1>
            <p className="mt-6 text-lg text-white/60 max-w-xl leading-relaxed">
              Da reserva ao fechamento do dia: documentação NORMAM-212, GRU com
              PIX, assinatura digital e comissões — num painel só, com a sua marca.
            </p>
            <div className="mt-10 flex flex-col sm:flex-row gap-4">
              <Link
                href="/signup"
                className="inline-flex items-center justify-center px-8 py-4 bg-white text-black text-base font-medium hover:bg-white/90 transition-all duration-300"
              >
                Começar teste grátis de 14 dias
                <ArrowRight className="ml-2 h-4 w-4" />
              </Link>
              <Link
                href="#planos"
                className="inline-flex items-center justify-center px-8 py-4 border border-white/20 text-white text-base hover:bg-white/10 hover:border-white/40 transition-all duration-300"
              >
                Ver planos
              </Link>
            </div>
            <p className="mt-4 text-sm text-white/40">
              Sem cartão de crédito · Cancele quando quiser
            </p>
          </div>

          <B2bDashboardMock />
        </div>
      </section>

      {/* ===== Faixa de dor ===== */}
      <section className="py-16 border-y border-white/[0.06]">
        <div className="container text-center">
          <p className="font-display text-2xl md:text-3xl text-white/80 max-w-3xl mx-auto leading-snug">
            Papelada da Marinha, planilha de comissão e caderno de check-in?
            <br />
            <span className="text-gold-gradient">O Meu Jet resolve os três.</span>
          </p>
        </div>
      </section>

      {/* ===== Grid de recursos ===== */}
      <section className="py-28 relative">
        <div className="container">
          <div className="text-center mb-16">
            <div className="inline-flex items-center gap-3 mb-6">
              <div className="h-px w-8 bg-gold/60" />
              <span className="text-gold/80 text-sm tracking-[0.3em] uppercase">Recursos</span>
              <div className="h-px w-8 bg-gold/60" />
            </div>
            <h2 className="font-display text-4xl md:text-5xl font-medium text-white">
              Tudo que a operação
              <br />
              <span className="text-gold-gradient">precisa</span>
            </h2>
          </div>

          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
            {RECURSOS.map((r) => (
              <div
                key={r.titulo}
                className="p-6 rounded-2xl bg-white/[0.02] border border-white/[0.05] hover:border-gold/20 transition-all duration-500"
              >
                <div className="w-12 h-12 rounded-xl bg-white/[0.05] flex items-center justify-center mb-5">
                  <r.icon className="h-5 w-5 text-gold" />
                </div>
                <h3 className="font-display text-lg font-medium text-white mb-2">{r.titulo}</h3>
                <p className="text-sm text-white/50 leading-relaxed">{r.descricao}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ===== Como funciona (B2B) ===== */}
      <section className="py-28 bg-premium-navy relative">
        <div className="container">
          <div className="text-center mb-16">
            <div className="inline-flex items-center gap-3 mb-6">
              <div className="h-px w-8 bg-gold/60" />
              <span className="text-gold/80 text-sm tracking-[0.3em] uppercase">Como começar</span>
              <div className="h-px w-8 bg-gold/60" />
            </div>
            <h2 className="font-display text-4xl md:text-5xl font-medium text-white">
              Operando em <span className="text-gold-gradient">três passos</span>
            </h2>
          </div>

          <div className="grid gap-8 md:grid-cols-3 max-w-5xl mx-auto">
            {PASSOS.map((p) => (
              <div key={p.numero} className="relative p-8 rounded-2xl bg-white/[0.02] border border-white/[0.05]">
                <div className="absolute -top-4 -right-2 font-display text-6xl font-bold text-white/[0.04]">
                  {p.numero}
                </div>
                <h3 className="font-display text-xl font-medium text-white mb-3">{p.titulo}</h3>
                <p className="text-white/50 leading-relaxed text-sm">{p.descricao}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ===== Bônus marketplace ===== */}
      <section className="py-20 border-b border-white/[0.06]">
        <div className="container flex flex-col md:flex-row items-center gap-8 md:gap-16">
          <div className="w-16 h-16 shrink-0 rounded-2xl bg-gold/10 flex items-center justify-center">
            <Store className="h-7 w-7 text-gold" />
          </div>
          <div>
            <h3 className="font-display text-2xl font-medium text-white">
              Bônus: suas embarcações no marketplace Meu Jet
            </h3>
            <p className="mt-2 text-white/50 max-w-2xl leading-relaxed">
              Todo plano inclui vitrine no marketplace — clientes encontram sua frota,
              comparam preços e chamam sua equipe direto no WhatsApp. Um canal de
              aquisição sem custo extra.
            </p>
          </div>
        </div>
      </section>

      {/* ===== Planos ===== */}
      <PlanosSection />

      {/* ===== FAQ ===== */}
      <FaqSection />

      {/* ===== CTA final ===== */}
      <section className="relative py-28 overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-primary/20 via-transparent to-gold/10" />
        <div className="absolute top-0 left-1/3 w-96 h-96 bg-gold/15 rounded-full blur-[150px]" />
        <div className="container relative text-center">
          <h2 className="font-display text-4xl md:text-5xl font-medium text-white max-w-3xl mx-auto leading-tight">
            Pronto para tirar a operação
            <br />
            <span className="text-gold-gradient">do papel?</span>
          </h2>
          <div className="mt-10 flex justify-center">
            <Link
              href="/signup"
              className="inline-flex items-center justify-center px-8 py-4 bg-white text-black text-base font-medium hover:bg-white/90 transition-all duration-300"
            >
              Começar teste grátis de 14 dias
              <ArrowRight className="ml-2 h-4 w-4" />
            </Link>
          </div>
          <p className="mt-4 text-sm text-white/40">
            14 dias de acesso completo · Sem cartão de crédito
          </p>
        </div>
      </section>
    </div>
  )
}
