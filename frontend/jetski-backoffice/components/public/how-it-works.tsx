import { Search, MessageCircle, Waves } from 'lucide-react'

const steps = [
  {
    icon: Search,
    number: '01',
    title: 'Explore',
    description: 'Compare modelos, preços e localizações das embarcações de locadoras parceiras.',
  },
  {
    icon: MessageCircle,
    number: '02',
    title: 'Reserve pelo WhatsApp',
    description: 'Fale direto com a locadora, sem intermediário: escolha data, horário e confirme sua reserva.',
  },
  {
    icon: Waves,
    number: '03',
    title: 'Navegue',
    description: 'No embarque, a habilitação CHA-MTA-E e o termo digital são resolvidos na hora, sem papelada.',
  },
]

export function HowItWorks() {
  return (
    <section id="como-funciona" className="py-32 bg-abyss relative overflow-hidden">
      {/* Background Elements */}
      <div className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.02)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.02)_1px,transparent_1px)] bg-[size:64px_64px]" />
      <div className="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-white/10 to-transparent" />
      <div className="absolute bottom-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-white/10 to-transparent" />

      <div className="container relative">
        {/* Header */}
        <div className="text-center mb-20">
          <div className="inline-flex items-center gap-3 mb-6">
            <div className="h-px w-8 bg-gold/60" />
            <span className="text-gold/80 text-sm tracking-[0.3em] uppercase">
              Como Funciona
            </span>
            <div className="h-px w-8 bg-gold/60" />
          </div>
          <h2 className="font-display text-4xl md:text-5xl font-medium text-white">
            Três Passos para o
            <br />
            <span className="text-gold-gradient">Mar</span>
          </h2>
        </div>

        {/* Steps */}
        <div className="grid gap-8 md:grid-cols-3 max-w-5xl mx-auto">
          {steps.map((step, index) => (
            <div
              key={step.title}
              className="relative group"
            >
              {/* Connector Line (hidden on mobile and last item) */}
              {index < steps.length - 1 && (
                <div className="hidden md:block absolute top-16 left-[60%] w-[80%] h-px bg-gradient-to-r from-white/20 to-transparent" />
              )}

              <div className="relative p-8 rounded-2xl bg-white/[0.02] border border-white/[0.05] hover:border-gold/20 transition-all duration-500">
                {/* Number */}
                <div className="absolute -top-4 -right-2 font-display text-6xl font-bold text-white/[0.03]">
                  {step.number}
                </div>

                {/* Icon */}
                <div className="w-14 h-14 rounded-xl bg-white/[0.05] flex items-center justify-center mb-6 group-hover:bg-gold/10 transition-colors duration-500">
                  <step.icon className="h-6 w-6 text-gold" />
                </div>

                {/* Content */}
                <h3 className="font-display text-xl font-medium text-white mb-3">
                  {step.title}
                </h3>
                <p className="text-white/50 leading-relaxed">
                  {step.description}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
