'use client'

import { useState, useEffect } from 'react'
import Image from 'next/image'
import Link from 'next/link'
import { useParams } from 'next/navigation'
import { ArrowLeft, MapPin, Star, Clock, Users, Fuel, Calendar, MessageCircle, Loader2, ChevronLeft, ChevronRight, Play, X } from 'lucide-react'
import { ReservationForm } from '@/components/public/reservation-form'
import { marketplaceService, MarketplaceModelo, MarketplaceMidia, getPrincipalImage, getImages, getVideos } from '@/lib/api/services/marketplace'

// Tipo para ofertas com todos os campos necessários
interface OfferingDetail {
  id: string
  modelo: string
  tipo: 'JETSKI' | 'LANCHA'
  empresa: string
  empresaWhatsapp: string
  precoHora?: number
  precoPacote30min?: number
  precoMeiaDiaria?: number
  precoDiaria?: number
  imagemUrl?: string
  midias?: MarketplaceMidia[]
  localizacao: string
  avaliacao?: number
  totalAvaliacoes?: number
  capacidade: number
  potencia: string
  combustivel: string
  descricao: string
  inclusos: string[]
  horarios: string[]
}

// Lanchas fixas (dados estáticos por enquanto)
const lanchasFixas: OfferingDetail[] = [
  {
    id: 'lancha-1',
    modelo: 'Lancha Focker 265',
    tipo: 'LANCHA',
    empresa: 'Marina Premium',
    empresaWhatsapp: '5548977777777',
    precoMeiaDiaria: 1800,
    precoDiaria: 3200,
    imagemUrl: 'https://images.unsplash.com/photo-1567899378494-47b22a2ae96a?w=800&h=600&fit=crop&q=80',
    localizacao: 'Jurerê, SC',
    avaliacao: 4.7,
    totalAvaliacoes: 56,
    capacidade: 8,
    potencia: '200 HP',
    combustivel: 'Gasolina',
    descricao: 'A Focker 265 é uma lancha esportiva premium, ideal para passeios em grupo. Amplo espaço interno, som ambiente e área de sol. Perfeita para um dia inesquecível no mar.',
    inclusos: ['Marinheiro', 'Combustível', 'Gelo e água', 'Seguro completo'],
    horarios: ['Meia diária manhã (08:00-12:00)', 'Meia diária tarde (13:00-17:00)', 'Diária completa (08:00-17:00)'],
  },
  {
    id: 'lancha-2',
    modelo: 'Lancha NX 280',
    tipo: 'LANCHA',
    empresa: 'Náutica Elite',
    empresaWhatsapp: '5548955555555',
    precoMeiaDiaria: 2200,
    precoDiaria: 3800,
    imagemUrl: 'https://images.unsplash.com/photo-1569263979104-865ab7cd8d13?w=800&h=600&fit=crop&q=80',
    localizacao: 'Itapema, SC',
    avaliacao: 4.8,
    totalAvaliacoes: 34,
    capacidade: 10,
    potencia: '250 HP',
    combustivel: 'Gasolina',
    descricao: 'A NX 280 é sinônimo de luxo e sofisticação. Cabine fechada, banheiro, churrasqueira e muito espaço. A escolha certa para celebrações especiais.',
    inclusos: ['Marinheiro', 'Combustível', 'Gelo, água e refrigerante', 'Seguro completo', 'Churrasqueira'],
    horarios: ['Meia diária manhã (08:00-12:00)', 'Meia diária tarde (13:00-17:00)', 'Diária completa (08:00-17:00)'],
  },
  {
    id: 'lancha-3',
    modelo: 'Lancha Phantom 303',
    tipo: 'LANCHA',
    empresa: 'Yacht Club Premium',
    empresaWhatsapp: '5548933333333',
    precoMeiaDiaria: 3500,
    precoDiaria: 6000,
    imagemUrl: 'https://images.unsplash.com/photo-1567899378494-47b22a2ae96a?w=800&h=600&fit=crop&q=80',
    localizacao: 'Balneário Camboriú, SC',
    avaliacao: 4.9,
    totalAvaliacoes: 67,
    capacidade: 12,
    potencia: '300 HP',
    combustivel: 'Gasolina',
    descricao: 'A Phantom 303 é o topo de linha em lanchas de passeio. Design italiano, acabamento premium e todo conforto que você merece. Para momentos verdadeiramente especiais.',
    inclusos: ['Marinheiro experiente', 'Combustível ilimitado', 'Bebidas premium', 'Seguro VIP', 'DJ disponível'],
    horarios: ['Meia diária manhã (08:00-12:00)', 'Meia diária tarde (13:00-17:00)', 'Diária completa (08:00-17:00)', 'Sunset (16:00-19:00)'],
  },
]

// Fallback jetskis (usados se API não retornar dados)
const jetskisFallback: OfferingDetail[] = [
  {
    id: 'fallback-1',
    modelo: 'Sea-Doo GTI 130',
    tipo: 'JETSKI',
    empresa: 'Marina do Sol',
    empresaWhatsapp: '5548999999999',
    precoHora: 250,
    precoPacote30min: 150,
    imagemUrl: 'https://sea-doo.brp.com/content/dam/global/en/sea-doo/my26/studio/recreation/gti/SEA-MY26-GTI-Standard-NoSS-M130-Bright-White-Neo-Mint-00038TB00-Studio-RSIDE-CU.png',
    localizacao: 'Florianópolis, SC',
    avaliacao: 4.8,
    totalAvaliacoes: 127,
    capacidade: 2,
    potencia: '130 HP',
    combustivel: 'Gasolina',
    descricao: 'O Sea-Doo GTI 130 é a combinação perfeita de versatilidade e desempenho. Ideal para quem busca diversão na água com conforto e segurança. Equipado com sistema de frenagem iBR e modo ECO.',
    inclusos: ['Colete salva-vidas', 'Orientação de uso', 'Combustível inicial'],
    horarios: ['08:00', '09:00', '10:00', '11:00', '13:00', '14:00', '15:00', '16:00', '17:00'],
  },
  {
    id: 'fallback-2',
    modelo: 'Sea-Doo Spark 90',
    tipo: 'JETSKI',
    empresa: 'JetSki Praia',
    empresaWhatsapp: '5548966666666',
    precoHora: 180,
    precoPacote30min: 100,
    imagemUrl: 'https://sea-doo.brp.com/content/dam/global/en/sea-doo/my26/studio/rec-lite/spark-trixx/SEA-MY26-SPARK-Trixx-1up-NoSS-M90-Gulfstream-Blue-Orange-Crush-00067TB00-Studio-RSIDE-CU.png',
    localizacao: 'Bombinhas, SC',
    avaliacao: 4.6,
    totalAvaliacoes: 203,
    capacidade: 2,
    potencia: '90 HP',
    combustivel: 'Gasolina',
    descricao: 'O Sea-Doo Spark é leve, ágil e divertido. Ideal para iniciantes e para quem quer se aventurar nas águas com economia. Fácil de pilotar e muito econômico.',
    inclusos: ['Colete salva-vidas', 'Orientação de uso'],
    horarios: ['08:00', '09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00'],
  },
  {
    id: 'fallback-3',
    modelo: 'Sea-Doo RXT-X 325',
    tipo: 'JETSKI',
    empresa: 'Marina do Sol',
    empresaWhatsapp: '5548999999999',
    precoHora: 380,
    precoPacote30min: 220,
    imagemUrl: 'https://sea-doo.brp.com/content/dam/global/en/sea-doo/my26/studio/performance/rxt-x/SEA-MY26-RXT-X-X-Integrated100W-M325-Gulfstream-Blue-Premium-00022TD00-Studio-RSIDE-CU.png',
    localizacao: 'Florianópolis, SC',
    avaliacao: 4.9,
    totalAvaliacoes: 78,
    capacidade: 2,
    potencia: '325 HP',
    combustivel: 'Gasolina',
    descricao: 'O RXT-X 325 é o jetski mais potente da Sea-Doo. Para quem busca adrenalina máxima e performance incomparável. Equipado com sistema de som Bluetooth e GPS.',
    inclusos: ['Colete salva-vidas', 'Orientação de uso', 'Combustível inicial', 'Seguro premium'],
    horarios: ['08:00', '10:00', '14:00', '16:00'],
  },
  {
    id: 'fallback-4',
    modelo: 'Sea-Doo GTX 170',
    tipo: 'JETSKI',
    empresa: 'Águas Claras',
    empresaWhatsapp: '5548944444444',
    precoHora: 290,
    precoPacote30min: 170,
    imagemUrl: 'https://sea-doo.brp.com/content/dam/global/en/sea-doo/my26/studio/touring/gtx/SEA-MY26-GTX-Standard-Integrated100W-M170-Blue-Abyss-Gulfstream-Blue-00011TA00-Studio-RSIDE-CU.png',
    localizacao: 'Porto Belo, SC',
    avaliacao: 4.5,
    totalAvaliacoes: 145,
    capacidade: 3,
    potencia: '170 HP',
    combustivel: 'Gasolina',
    descricao: 'O GTX 170 é o modelo touring da Sea-Doo, feito para longas distâncias com conforto. Banco ergonômico, porta-objetos amplo e autonomia estendida.',
    inclusos: ['Colete salva-vidas', 'Orientação de uso', 'Combustível inicial'],
    horarios: ['08:00', '09:00', '10:00', '14:00', '15:00', '16:00'],
  },
]

/**
 * Mapeia modelo da API para detalhes completos
 */
function mapApiModeloToDetail(modelo: MarketplaceModelo): OfferingDetail {
  return {
    id: modelo.id,
    modelo: modelo.nome,
    tipo: 'JETSKI',
    empresa: modelo.empresaNome,
    empresaWhatsapp: modelo.empresaWhatsapp || '5548999999999',
    precoHora: modelo.precoBaseHora,
    precoPacote30min: modelo.precoPacote30min,
    imagemUrl: getPrincipalImage(modelo),
    midias: modelo.midias,
    localizacao: modelo.localizacao,
    capacidade: modelo.capacidadePessoas || 2,
    potencia: `${modelo.capacidadePessoas || 2 > 2 ? '170' : '130'} HP`, // Estimativa
    combustivel: 'Gasolina',
    descricao: `${modelo.nome} disponível para aluguel em ${modelo.localizacao}. Entre em contato com ${modelo.empresaNome} para mais informações.`,
    inclusos: ['Colete salva-vidas', 'Orientação de uso', 'Combustível inicial'],
    horarios: ['08:00', '09:00', '10:00', '11:00', '13:00', '14:00', '15:00', '16:00', '17:00'],
  }
}

/**
 * Helper to detect and parse video URLs (YouTube, Vimeo, direct)
 */
function parseVideoUrl(url: string): { type: 'youtube' | 'vimeo' | 'direct'; embedUrl?: string; videoId?: string } {
  // YouTube
  const youtubeMatch = url.match(/(?:youtube\.com\/(?:watch\?v=|embed\/)|youtu\.be\/)([a-zA-Z0-9_-]{11})/)
  if (youtubeMatch) {
    return { type: 'youtube', videoId: youtubeMatch[1], embedUrl: `https://www.youtube.com/embed/${youtubeMatch[1]}` }
  }

  // Vimeo
  const vimeoMatch = url.match(/(?:vimeo\.com\/)(\d+)/)
  if (vimeoMatch) {
    return { type: 'vimeo', videoId: vimeoMatch[1], embedUrl: `https://player.vimeo.com/video/${vimeoMatch[1]}` }
  }

  return { type: 'direct' }
}

/**
 * Video Player component for marketplace with YouTube/Vimeo support
 */
function VideoPlayerMarketplace({
  url,
  thumbnailUrl,
  titulo,
  isPlaying,
  onPlay,
}: {
  url: string
  thumbnailUrl?: string
  titulo?: string
  isPlaying: boolean
  onPlay: () => void
}) {
  const parsed = parseVideoUrl(url)

  // Get thumbnail: prefer provided, then YouTube auto-thumbnail, then placeholder
  const getThumbnail = () => {
    if (thumbnailUrl) return thumbnailUrl
    if (parsed.type === 'youtube') {
      return `https://img.youtube.com/vi/${parsed.videoId}/maxresdefault.jpg`
    }
    return null
  }

  const thumbnail = getThumbnail()

  if (!isPlaying) {
    return (
      <div className="relative h-full w-full">
        {/* Thumbnail */}
        {thumbnail ? (
          <Image
            src={thumbnail}
            alt={titulo || 'Vídeo'}
            fill
            className="object-cover"
            onError={(e) => {
              // Fallback for YouTube if maxresdefault not available
              if (parsed.type === 'youtube') {
                e.currentTarget.src = `https://img.youtube.com/vi/${parsed.videoId}/hqdefault.jpg`
              }
            }}
          />
        ) : (
          <div className="h-full w-full bg-black/50 flex items-center justify-center">
            <Play className="h-16 w-16 text-white/50" />
          </div>
        )}
        {/* Play Button Overlay */}
        <button
          onClick={onPlay}
          className="absolute inset-0 flex items-center justify-center bg-black/30 hover:bg-black/40 transition-colors"
        >
          <div className={`w-20 h-20 rounded-full flex items-center justify-center shadow-lg hover:scale-110 transition-transform ${
            parsed.type === 'youtube' ? 'bg-red-600' : 'bg-gold/90'
          }`}>
            <Play className={`h-10 w-10 ml-1 ${parsed.type === 'youtube' ? 'text-white' : 'text-black'}`} fill={parsed.type === 'youtube' ? 'white' : 'black'} />
          </div>
        </button>
      </div>
    )
  }

  // Playing state - show appropriate player
  if (parsed.type === 'youtube' || parsed.type === 'vimeo') {
    return (
      <iframe
        src={`${parsed.embedUrl}?autoplay=1&rel=0`}
        className="h-full w-full"
        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
        allowFullScreen
      />
    )
  }

  // Direct video URL
  return (
    <video
      src={url}
      poster={thumbnailUrl}
      controls
      autoPlay
      playsInline
      preload="metadata"
      className="h-full w-full object-contain bg-black"
    >
      <source src={url} type="video/mp4" />
      <source src={url} type="video/webm" />
      Seu navegador não suporta vídeo.
    </video>
  )
}

/**
 * Gallery component for displaying images and videos
 */
function MediaGallery({ offering }: { offering: OfferingDetail }) {
  const [currentIndex, setCurrentIndex] = useState(0)
  const [isVideoPlaying, setIsVideoPlaying] = useState(false)

  // Build media list: use midias if available, fallback to imagemUrl
  const mediaItems: { tipo: 'IMAGEM' | 'VIDEO'; url: string; thumbnailUrl?: string; titulo?: string }[] = []

  if (offering.midias && offering.midias.length > 0) {
    // Sort by ordem and add all midias
    const sortedMidias = [...offering.midias].sort((a, b) => a.ordem - b.ordem)
    sortedMidias.forEach(m => {
      mediaItems.push({
        tipo: m.tipo,
        url: m.url,
        thumbnailUrl: m.thumbnailUrl,
        titulo: m.titulo,
      })
    })
  } else if (offering.imagemUrl) {
    // Fallback to single image
    mediaItems.push({
      tipo: 'IMAGEM',
      url: offering.imagemUrl,
    })
  }

  const currentMedia = mediaItems[currentIndex]
  const hasMultiple = mediaItems.length > 1

  const goToPrevious = () => {
    setCurrentIndex((prev) => (prev === 0 ? mediaItems.length - 1 : prev - 1))
    setIsVideoPlaying(false)
  }

  const goToNext = () => {
    setCurrentIndex((prev) => (prev === mediaItems.length - 1 ? 0 : prev + 1))
    setIsVideoPlaying(false)
  }

  const goToIndex = (index: number) => {
    setCurrentIndex(index)
    setIsVideoPlaying(false)
  }

  return (
    <div className="relative">
      {/* Main Media Display */}
      <div className="aspect-[4/3] relative rounded-2xl overflow-hidden bg-white/5">
        {currentMedia ? (
          currentMedia.tipo === 'IMAGEM' ? (
            <Image
              src={currentMedia.url}
              alt={currentMedia.titulo || offering.modelo}
              fill
              className="object-cover"
            />
          ) : (
            <VideoPlayerMarketplace
              url={currentMedia.url}
              thumbnailUrl={currentMedia.thumbnailUrl}
              titulo={currentMedia.titulo}
              isPlaying={isVideoPlaying}
              onPlay={() => setIsVideoPlaying(true)}
            />
          )
        ) : (
          <div className="flex h-full items-center justify-center">
            <span className="text-8xl opacity-50">
              {offering.tipo === 'JETSKI' ? '🚤' : '⛵'}
            </span>
          </div>
        )}

        {/* Badge */}
        <div className="absolute top-4 left-4 z-10">
          <span
            className={`px-4 py-2 text-sm tracking-wider uppercase font-medium rounded-full ${
              offering.tipo === 'JETSKI'
                ? 'bg-primary/90 text-white'
                : 'bg-gold/90 text-black'
            }`}
          >
            {offering.tipo === 'JETSKI' ? 'Jet Ski' : 'Lancha'}
          </span>
        </div>

        {/* Rating */}
        {offering.avaliacao && (
          <div className="absolute top-4 right-4 z-10 flex items-center gap-1 px-3 py-2 rounded-full bg-black/60 backdrop-blur-sm">
            <Star className="h-4 w-4 fill-gold text-gold" />
            <span className="text-sm font-medium text-white">
              {offering.avaliacao.toFixed(1)}
            </span>
            {offering.totalAvaliacoes && (
              <span className="text-sm text-white/60">
                ({offering.totalAvaliacoes})
              </span>
            )}
          </div>
        )}

        {/* Navigation Arrows */}
        {hasMultiple && (
          <>
            <button
              onClick={goToPrevious}
              className="absolute left-3 top-1/2 -translate-y-1/2 z-10 w-10 h-10 rounded-full bg-black/60 hover:bg-black/80 flex items-center justify-center transition-colors"
            >
              <ChevronLeft className="h-6 w-6 text-white" />
            </button>
            <button
              onClick={goToNext}
              className="absolute right-3 top-1/2 -translate-y-1/2 z-10 w-10 h-10 rounded-full bg-black/60 hover:bg-black/80 flex items-center justify-center transition-colors"
            >
              <ChevronRight className="h-6 w-6 text-white" />
            </button>
          </>
        )}

        {/* Counter */}
        {hasMultiple && (
          <div className="absolute bottom-4 left-1/2 -translate-x-1/2 z-10 px-3 py-1 rounded-full bg-black/60 text-white text-sm">
            {currentIndex + 1} / {mediaItems.length}
          </div>
        )}

        {/* Media title */}
        {currentMedia?.titulo && (
          <div className="absolute bottom-12 left-4 right-4 z-10">
            <span className="text-sm text-white bg-black/50 px-3 py-1 rounded">
              {currentMedia.titulo}
            </span>
          </div>
        )}
      </div>

      {/* Thumbnails */}
      {hasMultiple && (
        <div className="mt-4 flex gap-2 overflow-x-auto pb-2">
          {mediaItems.map((item, index) => (
            <button
              key={index}
              onClick={() => goToIndex(index)}
              className={`relative flex-shrink-0 w-20 h-14 rounded-lg overflow-hidden border-2 transition-all ${
                index === currentIndex
                  ? 'border-gold ring-2 ring-gold/50'
                  : 'border-white/20 hover:border-white/40'
              }`}
            >
              {item.tipo === 'IMAGEM' ? (
                <Image
                  src={item.url}
                  alt={item.titulo || `Mídia ${index + 1}`}
                  fill
                  className="object-cover"
                />
              ) : (
                <div className="relative h-full w-full bg-black/50">
                  {item.thumbnailUrl ? (
                    <Image
                      src={item.thumbnailUrl}
                      alt={item.titulo || `Vídeo ${index + 1}`}
                      fill
                      className="object-cover"
                    />
                  ) : null}
                  <div className="absolute inset-0 flex items-center justify-center bg-black/30">
                    <Play className="h-5 w-5 text-white" fill="white" />
                  </div>
                </div>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

/**
 * Loading skeleton
 */
function DetailSkeleton() {
  return (
    <div className="min-h-screen bg-abyss pt-20">
      <div className="container py-6">
        <div className="h-6 w-48 bg-white/10 rounded animate-pulse" />
      </div>
      <div className="container pb-20">
        <div className="grid lg:grid-cols-2 gap-12">
          <div className="aspect-[4/3] rounded-2xl bg-white/5 animate-pulse" />
          <div className="space-y-6">
            <div className="h-8 w-32 bg-white/10 rounded animate-pulse" />
            <div className="h-12 w-3/4 bg-white/10 rounded animate-pulse" />
            <div className="h-6 w-48 bg-white/10 rounded animate-pulse" />
            <div className="h-32 bg-white/5 rounded-xl animate-pulse" />
            <div className="grid grid-cols-3 gap-4">
              {[1, 2, 3].map(i => (
                <div key={i} className="h-24 bg-white/5 rounded-xl animate-pulse" />
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function EmbarcacaoDetailPage() {
  const params = useParams()
  const [showReservation, setShowReservation] = useState(false)
  const [offering, setOffering] = useState<OfferingDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const id = params.id as string

  useEffect(() => {
    async function fetchOffering() {
      // Se é uma lancha fixa, usar dados estáticos
      if (id.startsWith('lancha-')) {
        const lancha = lanchasFixas.find(l => l.id === id)
        setOffering(lancha || null)
        setLoading(false)
        return
      }

      // Se é um fallback, usar dados estáticos
      if (id.startsWith('fallback-')) {
        const fallback = jetskisFallback.find(j => j.id === id)
        setOffering(fallback || null)
        setLoading(false)
        return
      }

      // Tentar buscar da API
      try {
        const modelo = await marketplaceService.getModelo(id)
        setOffering(mapApiModeloToDetail(modelo))
      } catch (err) {
        console.error('Erro ao carregar embarcação:', err)
        setError('Embarcação não encontrada')
        // Tentar fallback por ID numérico antigo
        const fallbackById = jetskisFallback[0]
        setOffering(fallbackById || null)
      } finally {
        setLoading(false)
      }
    }

    if (id) {
      fetchOffering()
    }
  }, [id])

  if (loading) {
    return <DetailSkeleton />
  }

  if (!offering) {
    return (
      <div className="min-h-screen bg-abyss flex items-center justify-center">
        <div className="text-center">
          <h1 className="text-2xl text-white mb-4">Embarcação não encontrada</h1>
          <Link href="/" className="text-gold hover:underline">
            Voltar para o início
          </Link>
        </div>
      </div>
    )
  }

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    }).format(value)
  }

  const getPriceDisplay = () => {
    if (offering.tipo === 'LANCHA') {
      if (offering.precoMeiaDiaria) {
        return { valor: offering.precoMeiaDiaria, label: 'meia diária' }
      }
      if (offering.precoDiaria) {
        return { valor: offering.precoDiaria, label: 'diária' }
      }
    }
    if (offering.precoPacote30min) {
      return { valor: offering.precoPacote30min, label: '30min' }
    }
    if (offering.precoHora) {
      return { valor: offering.precoHora, label: 'hora' }
    }
    return { valor: 0, label: '' }
  }

  const priceDisplay = getPriceDisplay()

  return (
    <div className="min-h-screen bg-abyss pt-20">
      {/* Back Button */}
      <div className="container py-6">
        <Link
          href="/#ofertas"
          className="inline-flex items-center gap-2 text-white/60 hover:text-white transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          Voltar para embarcações
        </Link>
      </div>

      {error && (
        <div className="container mb-4">
          <div className="p-3 rounded-lg bg-yellow-500/10 border border-yellow-500/20 text-yellow-200 text-sm">
            {error} - Mostrando dados de demonstração
          </div>
        </div>
      )}

      <div className="container pb-20">
        <div className="grid lg:grid-cols-2 gap-12">
          {/* Gallery Section */}
          <MediaGallery offering={offering} />

          {/* Info Section */}
          <div>
            {/* Header */}
            <div className="mb-6">
              <p className="text-gold text-sm tracking-wider uppercase mb-2">
                {offering.empresa}
              </p>
              <h1 className="font-display text-4xl md:text-5xl font-medium text-white mb-4">
                {offering.modelo}
              </h1>
              <div className="flex items-center gap-2 text-white/60">
                <MapPin className="h-4 w-4" />
                <span>{offering.localizacao}</span>
              </div>
            </div>

            {/* Price */}
            <div className="bg-white/5 rounded-xl p-6 mb-6">
              <p className="text-white/50 text-sm mb-1">A partir de</p>
              <div className="flex items-baseline gap-2">
                <span className="font-display text-4xl text-white">
                  {formatCurrency(priceDisplay.valor)}
                </span>
                <span className="text-white/50">/{priceDisplay.label}</span>
              </div>
              {offering.tipo === 'LANCHA' && offering.precoDiaria && (
                <p className="text-white/40 text-sm mt-2">
                  Diária completa: {formatCurrency(offering.precoDiaria)}
                </p>
              )}
              {offering.tipo === 'JETSKI' && offering.precoHora && (
                <p className="text-white/40 text-sm mt-2">
                  Hora completa: {formatCurrency(offering.precoHora)}
                </p>
              )}
            </div>

            {/* Specs */}
            <div className="grid grid-cols-3 gap-4 mb-6">
              <div className="bg-white/5 rounded-xl p-4 text-center">
                <Users className="h-5 w-5 text-gold mx-auto mb-2" />
                <p className="text-white font-medium">{offering.capacidade}</p>
                <p className="text-white/50 text-xs">Pessoas</p>
              </div>
              <div className="bg-white/5 rounded-xl p-4 text-center">
                <Fuel className="h-5 w-5 text-gold mx-auto mb-2" />
                <p className="text-white font-medium">{offering.potencia}</p>
                <p className="text-white/50 text-xs">Potência</p>
              </div>
              <div className="bg-white/5 rounded-xl p-4 text-center">
                <Clock className="h-5 w-5 text-gold mx-auto mb-2" />
                <p className="text-white font-medium">
                  {offering.tipo === 'JETSKI' ? '30min+' : '4h+'}
                </p>
                <p className="text-white/50 text-xs">Mínimo</p>
              </div>
            </div>

            {/* Description */}
            <div className="mb-6">
              <h3 className="text-white font-medium mb-3">Sobre</h3>
              <p className="text-white/60 leading-relaxed">{offering.descricao}</p>
            </div>

            {/* Included */}
            <div className="mb-8">
              <h3 className="text-white font-medium mb-3">O que está incluso</h3>
              <ul className="grid grid-cols-2 gap-2">
                {offering.inclusos.map((item, index) => (
                  <li
                    key={index}
                    className="flex items-center gap-2 text-white/60 text-sm"
                  >
                    <div className="w-1.5 h-1.5 rounded-full bg-gold" />
                    {item}
                  </li>
                ))}
              </ul>
            </div>

            {/* CTA Buttons */}
            <div className="flex flex-col sm:flex-row gap-4">
              <button
                onClick={() => setShowReservation(true)}
                className="flex-1 inline-flex items-center justify-center gap-2 px-8 py-4 bg-gold text-black font-medium hover:bg-gold/90 transition-all duration-300"
              >
                <Calendar className="h-5 w-5" />
                Reservar Agora
              </button>
              <a
                href={`https://wa.me/${offering.empresaWhatsapp}?text=Olá! Tenho interesse no ${offering.modelo}. Gostaria de mais informações.`}
                target="_blank"
                rel="noopener noreferrer"
                className="flex-1 inline-flex items-center justify-center gap-2 px-8 py-4 bg-green-600 text-white font-medium hover:bg-green-700 transition-all duration-300"
              >
                <MessageCircle className="h-5 w-5" />
                WhatsApp
              </a>
            </div>
          </div>
        </div>
      </div>

      {/* Reservation Modal */}
      {showReservation && (
        <ReservationForm
          offering={offering}
          onClose={() => setShowReservation(false)}
        />
      )}
    </div>
  )
}
