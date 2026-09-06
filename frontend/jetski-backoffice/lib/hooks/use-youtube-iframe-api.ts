'use client'

import { useEffect, useState } from 'react'

/**
 * Loader da YouTube IFrame Player API (sem dependência npm).
 *
 * Tipos mínimos declarados aqui — só o que o player do balcão usa.
 * O script é injetado UMA vez por página (singleton de módulo): idempotente em
 * StrictMode (double-mount) e no HMR (reaproveita `window.YT` já presente).
 */

export type YTPlayerState = -1 | 0 | 1 | 2 | 3 | 5

export interface YTPlayer {
  playVideo(): void
  pauseVideo(): void
  seekTo(seconds: number, allowSeekAhead: boolean): void
  getCurrentTime(): number
  getDuration(): number
  getPlayerState(): YTPlayerState
  getPlaybackRate(): number
  setPlaybackRate(rate: number): void
  loadModule(module: string): void
  unloadModule(module: string): void
  destroy(): void
}

export interface YTPlayerEvent {
  target: YTPlayer
  data: number
}

export interface YTPlayerOptions {
  videoId: string
  host?: string
  width?: string | number
  height?: string | number
  playerVars?: Record<string, string | number>
  events?: {
    onReady?: (e: YTPlayerEvent) => void
    onStateChange?: (e: YTPlayerEvent) => void
    onError?: (e: YTPlayerEvent) => void
  }
}

export interface YTNamespace {
  Player: new (el: HTMLElement | string, opts: YTPlayerOptions) => YTPlayer
  PlayerState: {
    UNSTARTED: -1
    ENDED: 0
    PLAYING: 1
    PAUSED: 2
    BUFFERING: 3
    CUED: 5
  }
}

declare global {
  interface Window {
    YT?: YTNamespace
    onYouTubeIframeAPIReady?: () => void
  }
}

const SCRIPT_SRC = 'https://www.youtube.com/iframe_api'
let carregando: Promise<YTNamespace> | null = null

export function loadYouTubeIframeApi(timeoutMs = 10_000): Promise<YTNamespace> {
  if (typeof window === 'undefined') return Promise.reject(new Error('ssr'))
  if (window.YT?.Player) return Promise.resolve(window.YT)
  if (carregando) return carregando

  carregando = new Promise<YTNamespace>((resolve, reject) => {
    const anterior = window.onYouTubeIframeAPIReady
    const timer = setTimeout(() => {
      carregando = null
      reject(new Error('timeout'))
    }, timeoutMs)
    window.onYouTubeIframeAPIReady = () => {
      anterior?.()
      clearTimeout(timer)
      if (window.YT?.Player) resolve(window.YT)
      else {
        carregando = null
        reject(new Error('iframe_api sem YT.Player'))
      }
    }
    if (!document.querySelector(`script[src="${SCRIPT_SRC}"]`)) {
      const s = document.createElement('script')
      s.src = SCRIPT_SRC
      s.async = true
      s.onerror = () => {
        clearTimeout(timer)
        carregando = null
        reject(new Error('iframe_api bloqueado'))
      }
      document.head.appendChild(s)
    }
  })
  return carregando
}

/** Resolve `window.YT` (ou um erro — adblock/offline/timeout). */
export function useYouTubeIframeApi() {
  const [YT, setYT] = useState<YTNamespace | null>(() =>
    typeof window !== 'undefined' && window.YT?.Player ? window.YT : null
  )
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    if (YT) return
    let cancelado = false
    loadYouTubeIframeApi()
      .then((ns) => {
        if (!cancelado) setYT(ns)
      })
      .catch((e: Error) => {
        if (!cancelado) setErro(e.message || 'falha')
      })
    return () => {
      cancelado = true
    }
  }, [YT])

  return { YT, erro }
}
