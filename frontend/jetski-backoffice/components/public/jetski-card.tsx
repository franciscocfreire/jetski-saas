import Image from 'next/image'
import Link from 'next/link'
import { MapPin, Star, ArrowRight } from 'lucide-react'
import { cn } from '@/lib/utils'

export interface JetskiCardProps {
  id: string
  modelo: string
  tipo: 'JETSKI' | 'LANCHA'
  empresa: string
  precoHora?: number
  precoPacote30min?: number
  precoMeiaDiaria?: number  // Para lanchas
  precoDiaria?: number      // Para lanchas
  imagemUrl?: string
  localizacao: string
  avaliacao?: number
  totalAvaliacoes?: number
}

export function JetskiCard({
  id,
  modelo,
  tipo,
  empresa,
  precoHora,
  precoPacote30min,
  precoMeiaDiaria,
  precoDiaria,
  imagemUrl,
  localizacao,
  avaliacao,
}: JetskiCardProps) {
  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    }).format(value)
  }

  // Determina preço e label baseado no tipo
  const getPriceDisplay = () => {
    if (tipo === 'LANCHA') {
      if (precoMeiaDiaria) {
        return { valor: precoMeiaDiaria, label: 'meia diária' }
      }
      if (precoDiaria) {
        return { valor: precoDiaria, label: 'diária' }
      }
    }
    // Jetski - preço por tempo
    if (precoPacote30min) {
      return { valor: precoPacote30min, label: '30min' }
    }
    if (precoHora) {
      return { valor: precoHora, label: 'hora' }
    }
    return { valor: 0, label: '' }
  }

  const priceDisplay = getPriceDisplay()

  return (
    <Link href={`/embarcacao/${id}`} className="block">
      <article className="group relative overflow-hidden rounded-2xl bg-white/[0.03] border border-white/[0.08] hover-lift cursor-pointer">
        {/* Image Container */}
        <div className="relative aspect-[4/3] overflow-hidden">
        {imagemUrl ? (
          <Image
            src={imagemUrl}
            alt={modelo}
            fill
            className="object-cover transition-transform duration-700 group-hover:scale-110"
          />
        ) : (
          <div className="flex h-full items-center justify-center bg-gradient-to-br from-white/[0.05] to-white/[0.02]">
            <span className="text-6xl opacity-50">
              {tipo === 'JETSKI' ? '🚤' : '⛵'}
            </span>
          </div>
        )}

        {/* Gradient Overlay */}
        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent" />

        {/* Type Badge */}
        <div className="absolute top-4 left-4">
          <span className={cn(
            "px-3 py-1 text-xs tracking-wider uppercase font-medium rounded-full",
            tipo === 'JETSKI'
              ? "bg-primary/80 text-white"
              : "bg-gold/80 text-black"
          )}>
            {tipo === 'JETSKI' ? 'Jet Ski' : 'Lancha'}
          </span>
        </div>

        {/* Rating */}
        {avaliacao && (
          <div className="absolute top-4 right-4 flex items-center gap-1 px-2 py-1 rounded-full bg-black/50 backdrop-blur-sm">
            <Star className="h-3 w-3 fill-gold text-gold" />
            <span className="text-xs font-medium text-white">{avaliacao.toFixed(1)}</span>
          </div>
        )}

        {/* Price - Bottom of Image */}
        <div className="absolute bottom-4 left-4 right-4">
          <div className="flex items-end justify-between">
            <div>
              <span className="text-white/50 text-xs uppercase tracking-wider">A partir de</span>
              <div className="flex items-baseline gap-1">
                <span className="text-2xl font-display font-medium text-white">
                  {formatCurrency(priceDisplay.valor)}
                </span>
                <span className="text-white/50 text-sm">
                  /{priceDisplay.label}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="p-5">
        {/* Model Name */}
        <h3 className="font-display text-lg font-medium text-white group-hover:text-gold transition-colors duration-300">
          {modelo}
        </h3>

        {/* Company */}
        <p className="text-sm text-white/40 mt-1">{empresa}</p>

        {/* Divider */}
        <div className="h-px bg-white/10 my-4" />

        {/* Footer */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5 text-white/40">
            <MapPin className="h-3.5 w-3.5" />
            <span className="text-xs">{localizacao}</span>
          </div>

          <span className="flex items-center gap-1 text-xs text-white/60 group-hover:text-gold transition-colors duration-300">
            <span>Ver detalhes</span>
            <ArrowRight className="h-3 w-3 transition-transform duration-300 group-hover:translate-x-1" />
          </span>
        </div>
      </div>

      {/* Hover Border Effect */}
      <div className="absolute inset-0 rounded-2xl border border-gold/0 group-hover:border-gold/30 transition-colors duration-500 pointer-events-none" />
      </article>
    </Link>
  )
}
