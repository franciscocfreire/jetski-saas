'use client'

import { useState, useEffect } from 'react'
import { useParams } from 'next/navigation'
import { MapPin, MessageCircle, Anchor, ArrowRight, Clock, Instagram, Globe, Navigation } from 'lucide-react'
import { JetskiCard, JetskiCardProps } from '@/components/public/jetski-card'
import {
  marketplaceService,
  MarketplaceLoja,
  MarketplaceLojaBranding,
  MarketplaceModelo,
  getPrincipalImage,
} from '@/lib/api/services/marketplace'
import { subBase } from '@/lib/public-hosts'

/**
 * Vitrine pública POR LOJA — servida em www/loja/{slug} e, via middleware,
 * em {slug}.meujet.com.br. Some (404) se a loja não existe, está inativa ou
 * o plano não inclui o módulo LOJA_ONLINE.
 *
 * Fora do grupo (public) de propósito: a página representa a EMPRESA, então
 * não carrega a Navbar/Footer do site Meu Jet — só o selo no rodapé. O
 * visitante da vitrine é cliente final: as embarcações levam ao PORTAL DO
 * CLIENTE (cliente.*, onde ele reserva); só o selo aponta para o site www.
 */

function mapModeloToCard(modelo: MarketplaceModelo, base: string): JetskiCardProps {
  return {
    id: modelo.id,
    href: `${base}/modelo/${modelo.id}`,
    modelo: modelo.nome,
    tipo: 'JETSKI',
    empresa: modelo.empresaNome,
    precoHora: modelo.precoBaseHora,
    precoPacote30min: modelo.precoPacote30min,
    imagemUrl: getPrincipalImage(modelo),
    localizacao: modelo.localizacao,
  }
}

function whatsappHref(whatsapp: string): string {
  let digits = whatsapp.replace(/\D/g, '')
  if (digits.length <= 11) digits = `55${digits}`
  return `https://wa.me/${digits}`
}

function instagramHref(valor: string): string {
  const v = valor.trim()
  if (/instagram\.com/i.test(v)) return v.match(/^https?:\/\//i) ? v : `https://${v}`
  return `https://instagram.com/${v.replace(/^@/, '')}`
}

function VitrineSkeleton() {
  return (
    <div className="container py-32">
      <div className="animate-pulse space-y-10">
        <div className="space-y-4">
          <div className="h-4 w-40 rounded bg-white/[0.05]" />
          <div className="h-12 w-2/3 max-w-lg rounded bg-white/[0.05]" />
          <div className="h-4 w-52 rounded bg-white/[0.05]" />
        </div>
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {[...Array(4)].map((_, i) => (
            <div key={i}>
              <div className="aspect-[4/3] rounded-2xl bg-white/[0.05]" />
              <div className="p-5 space-y-3">
                <div className="h-5 w-3/4 rounded bg-white/[0.05]" />
                <div className="h-4 w-1/2 rounded bg-white/[0.05]" />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function LojaNaoEncontrada() {
  const base = subBase('cliente')
  return (
    <div className="container flex flex-col items-center py-40 text-center">
      <Anchor className="h-12 w-12 text-gold/60 mb-6" />
      <h1 className="font-display text-3xl md:text-4xl text-white mb-4">
        Vitrine não encontrada
      </h1>
      <p className="text-white/50 max-w-md leading-relaxed mb-10">
        Esta loja não existe ou a vitrine dela não está disponível no momento.
        Que tal explorar as embarcações do marketplace?
      </p>
      <a
        href={`${base}/` || '/'}
        className="inline-flex items-center gap-2 rounded-full bg-gold px-6 py-3 text-sm font-medium text-gold-foreground hover:bg-gold/90 transition-colors"
      >
        Ver embarcações disponíveis <ArrowRight className="h-4 w-4" />
      </a>
    </div>
  )
}

export default function LojaVitrinePage() {
  const params = useParams<{ slug: string }>()
  const slug = params.slug

  const [loja, setLoja] = useState<MarketplaceLoja | null>(null)
  const [branding, setBranding] = useState<MarketplaceLojaBranding | null>(null)
  const [modelos, setModelos] = useState<JetskiCardProps[]>([])
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    if (!slug) return
    let vivo = true
    async function carregar() {
      try {
        const [lojaData, modelosData, brandingData] = await Promise.all([
          marketplaceService.getLoja(slug),
          marketplaceService.listModelosByLoja(slug),
          marketplaceService.getLojaBranding(slug).catch(() => null),
        ])
        if (!vivo) return
        setLoja(lojaData)
        setBranding(brandingData)
        const base = subBase('cliente')
        setModelos(modelosData.map((m) => mapModeloToCard(m, base)))
      } catch {
        if (vivo) setNotFound(true)
      } finally {
        if (vivo) setLoading(false)
      }
    }
    carregar()
    return () => {
      vivo = false
    }
  }, [slug])

  return (
    <div className="bg-abyss min-h-screen">
      <section className="bg-premium-navy relative">
        <div className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.015)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.015)_1px,transparent_1px)] bg-[size:64px_64px]" />

        {loading ? (
          <VitrineSkeleton />
        ) : notFound || !loja ? (
          <LojaNaoEncontrada />
        ) : (
          <div className="container relative py-24 md:py-32">
            {/* Cabeçalho da loja */}
            <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-6 mb-16">
              <div className="min-w-0">
                <div className="inline-flex items-center gap-3 mb-4">
                  <div className="h-px w-8 bg-gold/60" />
                  <span className="text-gold/80 text-sm tracking-[0.3em] uppercase">
                    Vitrine oficial
                  </span>
                </div>
                <div className="flex flex-wrap items-center gap-4">
                  {branding?.logoDataUrl && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={branding.logoDataUrl}
                      alt={`Logo ${loja.nome}`}
                      className="h-14 max-w-36 rounded-lg bg-white/95 object-contain p-1.5"
                    />
                  )}
                  <h1 className="min-w-0 font-display text-4xl md:text-5xl font-medium text-white break-words">
                    {loja.nome}
                  </h1>
                </div>
                {(loja.vitrinePraia || loja.cidade || loja.uf) && (
                  <div className="mt-4 flex items-center gap-2 text-white/60">
                    <MapPin className="h-4 w-4 shrink-0 text-gold/70" />
                    <span>
                      {loja.vitrinePraia && (
                        <span className="font-medium text-white/90">{loja.vitrinePraia}</span>
                      )}
                      {loja.vitrinePraia && (loja.cidade || loja.uf) && ' · '}
                      {[loja.cidade, loja.uf].filter(Boolean).join(' · ')}
                    </span>
                  </div>
                )}
              </div>
              {loja.whatsapp && (
                <a
                  href={whatsappHref(loja.whatsapp)}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex w-fit items-center gap-2 rounded-full border border-gold/40 px-6 py-3 text-sm font-medium text-gold hover:bg-gold/10 transition-colors"
                >
                  <MessageCircle className="h-4 w-4" /> Falar no WhatsApp
                </a>
              )}
            </div>

            {/* Embarcações da loja */}
            {modelos.length === 0 ? (
              <p className="text-white/50 text-lg">
                Nenhuma embarcação publicada no momento — volte em breve.
              </p>
            ) : (
              <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                {modelos.map((m) => (
                  <JetskiCard key={m.id} {...m} />
                ))}
              </div>
            )}

            {/* Sobre a empresa */}
            {loja.vitrineDescricao && (
              <div className="mt-16 max-w-3xl">
                <h2 className="text-white font-medium text-xl mb-4">Sobre a {loja.nome}</h2>
                <p className="text-white/60 leading-relaxed whitespace-pre-line">
                  {loja.vitrineDescricao}
                </p>
              </div>
            )}

            {/* Localização + informações */}
            {(loja.vitrineEndereco || loja.vitrineHorario || loja.vitrineInstagram || loja.vitrineSite) && (
              <div className="mt-16 grid gap-6 lg:grid-cols-2">
                {loja.vitrineEndereco && (
                  <div className="overflow-hidden rounded-xl border border-white/10 bg-white/[0.03]">
                    <iframe
                      title={`Mapa — ${loja.vitrineEndereco}`}
                      src={`https://maps.google.com/maps?q=${encodeURIComponent(loja.vitrineEndereco)}&z=15&output=embed`}
                      className="h-72 w-full border-0"
                      loading="lazy"
                      referrerPolicy="no-referrer-when-downgrade"
                    />
                    <div className="flex flex-wrap items-center justify-between gap-3 p-4">
                      <p className="min-w-0 flex-1 basis-52 text-sm text-white/60 break-words">
                        {loja.vitrineEndereco}
                      </p>
                      <a
                        href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(loja.vitrineEndereco)}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex shrink-0 items-center gap-2 rounded-full border border-gold/40 px-4 py-2 text-sm text-gold hover:bg-gold/10 transition-colors"
                      >
                        <Navigation className="h-4 w-4" /> Como chegar
                      </a>
                    </div>
                  </div>
                )}
                <div className="space-y-6 rounded-xl border border-white/10 bg-white/[0.03] p-6">
                  {loja.vitrineHorario && (
                    <div>
                      <h3 className="flex items-center gap-2 text-white font-medium mb-2">
                        <Clock className="h-4 w-4 text-gold/70" /> Horário de funcionamento
                      </h3>
                      <p className="text-white/60 text-sm leading-relaxed whitespace-pre-line">
                        {loja.vitrineHorario}
                      </p>
                    </div>
                  )}
                  {(loja.vitrineInstagram || loja.vitrineSite || loja.whatsapp) && (
                    <div>
                      <h3 className="text-white font-medium mb-3">Contato</h3>
                      <div className="flex flex-wrap gap-3">
                        {loja.whatsapp && (
                          <a
                            href={whatsappHref(loja.whatsapp)}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-2 rounded-full border border-white/15 px-4 py-2 text-sm text-white/80 hover:border-gold/40 hover:text-gold transition-colors"
                          >
                            <MessageCircle className="h-4 w-4" /> WhatsApp
                          </a>
                        )}
                        {loja.vitrineInstagram && (
                          <a
                            href={instagramHref(loja.vitrineInstagram)}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-2 rounded-full border border-white/15 px-4 py-2 text-sm text-white/80 hover:border-gold/40 hover:text-gold transition-colors"
                          >
                            <Instagram className="h-4 w-4" /> Instagram
                          </a>
                        )}
                        {loja.vitrineSite && (
                          <a
                            href={loja.vitrineSite}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-2 rounded-full border border-white/15 px-4 py-2 text-sm text-white/80 hover:border-gold/40 hover:text-gold transition-colors"
                          >
                            <Globe className="h-4 w-4" /> Site
                          </a>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Selo da plataforma */}
            <p className="mt-16 text-center text-sm text-white/30">
              Vitrine publicada via{' '}
              <a
                href={`${subBase('www')}/` || '/'}
                className="text-gold/60 hover:text-gold transition-colors"
              >
                Meu Jet
              </a>
            </p>
          </div>
        )}
      </section>
    </div>
  )
}
