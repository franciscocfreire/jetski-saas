'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { ArrowDown } from 'lucide-react'

export function Hero() {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [videoLoaded, setVideoLoaded] = useState(false)
  const [videoError, setVideoError] = useState(false)

  useEffect(() => {
    // Garantir que o vídeo toque após carregamento
    if (videoRef.current) {
      videoRef.current.play().catch(() => {
        // Autoplay pode ser bloqueado em alguns browsers
        setVideoError(true)
      })
    }
  }, [])

  return (
    <section className="relative min-h-screen flex items-center justify-center overflow-hidden bg-black">
      {/* Background Video */}
      <div className="absolute inset-0">
        {/* Video Element */}
        {!videoError && (
          <video
            ref={videoRef}
            autoPlay
            loop
            muted
            playsInline
            onLoadedData={() => setVideoLoaded(true)}
            onError={() => setVideoError(true)}
            className={`absolute inset-0 w-full h-full object-cover transition-opacity duration-1000 ${videoLoaded ? 'opacity-100' : 'opacity-0'}`}
          >
            {/* Vídeos de água/oceano - Pexels free stock */}
            <source src="https://player.vimeo.com/external/368763065.sd.mp4?s=13d7c0c2e8f6f0b4f2a4c0b7d9a7c0a6e8f1b2c3&profile_id=164&oauth2_token_id=57447761" type="video/mp4" />
            <source src="https://cdn.pixabay.com/video/2020/05/25/40130-424930032_large.mp4" type="video/mp4" />
          </video>
        )}

        {/* Fallback gradient - sempre visível até vídeo carregar ou em caso de erro */}
        <div
          className={`absolute inset-0 transition-opacity duration-1000 ${videoLoaded && !videoError ? 'opacity-0' : 'opacity-100'}`}
          style={{
            background: 'linear-gradient(135deg, #0c1929 0%, #0a1628 25%, #061220 50%, #0d1f35 75%, #0a1628 100%)'
          }}
        >
          {/* Animated water effect */}
          <div className="absolute inset-0 opacity-30">
            <div className="absolute inset-0 bg-[url('data:image/svg+xml,%3Csvg viewBox=%220 0 400 400%22 xmlns=%22http://www.w3.org/2000/svg%22%3E%3Cfilter id=%22noiseFilter%22%3E%3CfeTurbulence type=%22fractalNoise%22 baseFrequency=%220.009%22 numOctaves=%223%22 stitchTiles=%22stitch%22/%3E%3C/filter%3E%3Crect width=%22100%25%22 height=%22100%25%22 filter=%22url(%23noiseFilter)%22/%3E%3C/svg%3E')] animate-pulse" />
          </div>
        </div>

        {/* Dark Overlay sobre o vídeo */}
        <div className="absolute inset-0 bg-gradient-to-b from-black/50 via-black/30 to-black/70" />

        {/* Vignette effect */}
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,transparent_0%,rgba(0,0,0,0.5)_100%)]" />
      </div>

      {/* Animated Gradient Orbs */}
      <div className="absolute top-1/4 -left-32 w-96 h-96 bg-primary/20 rounded-full blur-[120px] animate-pulse" />
      <div className="absolute bottom-1/4 -right-32 w-96 h-96 bg-gold/10 rounded-full blur-[120px] animate-pulse" style={{ animationDelay: '1s' }} />

      {/* Grid Pattern Overlay */}
      <div className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.02)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.02)_1px,transparent_1px)] bg-[size:64px_64px]" />

      <div className="container relative z-10 py-32">
        <div className="mx-auto max-w-4xl text-center">
          {/* Premium Badge */}
          <div className="inline-flex items-center gap-3 mb-8">
            <div className="h-px w-12 bg-gradient-to-r from-transparent to-gold/60" />
            <span className="text-gold/80 text-sm tracking-[0.3em] uppercase font-medium">
              Experiências Exclusivas
            </span>
            <div className="h-px w-12 bg-gradient-to-l from-transparent to-gold/60" />
          </div>

          {/* Main Heading - Serif */}
          <h1 className="font-display text-5xl md:text-6xl lg:text-7xl xl:text-8xl font-medium tracking-tight text-white leading-[1.1]">
            Viva o{' '}
            <span className="text-gold-gradient">Luxo</span>
            <br />
            <span className="text-white/90">Sobre as Águas</span>
          </h1>

          {/* Decorative Line */}
          <div className="mx-auto my-8 w-24 line-gold" />

          {/* Subtitle */}
          <p className="text-lg md:text-xl text-white/60 max-w-2xl mx-auto leading-relaxed">
            Descubra as melhores embarcações de luxo do Brasil.
            Jetskis e lanchas premium das melhores empresas do Brasil.
          </p>

          {/* CTAs */}
          <div className="mt-12 flex flex-col sm:flex-row items-center justify-center gap-4">
            <Link
              href="#ofertas"
              className="inline-flex items-center justify-center text-base px-8 py-4 bg-white text-black font-medium hover:bg-gray-100 active:bg-gray-200 transition-all duration-300"
            >
              Explorar Embarcações
              <ArrowDown className="ml-2 h-4 w-4" />
            </Link>
            <Link
              href="/signup"
              className="inline-flex items-center justify-center text-base px-8 py-4 border-2 border-white/30 text-white font-medium hover:bg-white/10 hover:border-white/50 active:bg-white/20 transition-all duration-300"
            >
              Para Empresas
            </Link>
          </div>

          {/* Stats - Premium Style */}
          <div className="mt-24 grid grid-cols-3 gap-8 max-w-3xl mx-auto">
            <div className="text-center">
              <div className="font-display text-4xl md:text-5xl text-white font-medium">50+</div>
              <div className="mt-2 text-sm text-white/40 tracking-wide uppercase">Empresas Parceiras</div>
            </div>
            <div className="text-center border-x border-white/10 px-4">
              <div className="font-display text-4xl md:text-5xl text-white font-medium">200+</div>
              <div className="mt-2 text-sm text-white/40 tracking-wide uppercase">Embarcações</div>
            </div>
            <div className="text-center">
              <div className="font-display text-4xl md:text-5xl text-white font-medium">10k+</div>
              <div className="mt-2 text-sm text-white/40 tracking-wide uppercase">Experiências</div>
            </div>
          </div>
        </div>

        {/* Scroll Indicator */}
        <div className="absolute bottom-8 left-1/2 -translate-x-1/2 flex flex-col items-center gap-2 text-white/40">
          <span className="text-xs tracking-widest uppercase">Scroll</span>
          <div className="w-px h-12 bg-gradient-to-b from-white/40 to-transparent animate-pulse" />
        </div>
      </div>
    </section>
  )
}
