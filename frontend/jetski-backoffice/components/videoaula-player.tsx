"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  Captions,
  CaptionsOff,
  CheckCircle2,
  Loader2,
  Maximize2,
  Minimize2,
  TriangleAlert,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";
import { VIDEOAULAS, type Videoaula } from "@/lib/videoaulas";
import type { VideoaulaIdioma } from "@/lib/api/types";
import {
  useYouTubeIframeApi,
  type YTPlayer,
} from "@/lib/hooks/use-youtube-iframe-api";

type Status =
  "carregando" | "pronto" | "tocando" | "pausado" | "concluido" | "erro";

/** Tolerância (s) do watchdog anti-seek: pulo maior que isto volta ao máximo alcançado. */
const TOLERANCIA_SEEK_S = 1.5;
/** ENDED só vale se o máximo alcançado chegou perto do fim (defesa contra seek programático). */
const MARGEM_FIM_S = 3;
const POLL_MS = 500;
const READY_TIMEOUT_MS = 15_000;

const mmss = (s: number) => {
  const t = Math.max(0, Math.floor(s));
  return `${Math.floor(t / 60)}:${String(t % 60).padStart(2, "0")}`;
};

type FsElement = HTMLElement & {
  webkitRequestFullscreen?: () => Promise<void> | void;
};
type FsDocument = Document & {
  webkitFullscreenElement?: Element | null;
  webkitExitFullscreen?: () => Promise<void> | void;
};

/**
 * Player da videoaula oficial da Marinha (YouTube IFrame API) para o balcão.
 *
 * Regras: sem controles nativos nem teclado; o watchdog devolve ao máximo já
 * assistido se houver pulo; `onConcluido` dispara UMA vez no ENDED. Trocar de
 * idioma reinicia o progresso (até concluir; depois o seletor trava).
 *
 * Tela cheia: Fullscreen API no wrapper (não no iframe); no iPhone (sem API)
 * cai num pseudo-fullscreen `fixed inset-0`.
 *
 * Limitações conhecidas: `controls:0` é um pedido ao YouTube, não garantia; o
 * watchdog corrige em ≤ ~1 s. Não há como detectar mudo/atenção. Se o player
 * não carregar (rede/adblock), `onErro` é chamado e o passo decide o fallback.
 */
export function VideoaulaPlayer({
  videos = VIDEOAULAS,
  idiomaInicial = "pt",
  onConcluido,
  onErro,
  className,
}: {
  videos?: readonly Videoaula[];
  idiomaInicial?: VideoaulaIdioma;
  onConcluido: (idioma: VideoaulaIdioma) => void;
  onErro?: (motivo: string) => void;
  className?: string;
}) {
  const { YT, erro: erroApi } = useYouTubeIframeApi();
  const [idioma, setIdioma] = useState<VideoaulaIdioma>(idiomaInicial);
  const [status, setStatus] = useState<Status>("carregando");
  const [progresso, setProgresso] = useState(0);
  const [duracao, setDuracao] = useState(0);
  const [fullscreen, setFullscreen] = useState(false);
  const [pseudoFs, setPseudoFs] = useState(false);
  // Legendas do YouTube (CC): com controls:0 não há botão nativo. Padrão
  // desligado — a videoaula da Marinha já traz legenda gravada no vídeo.
  const [legendas, setLegendas] = useState(false);
  const legendasRef = useRef(false);

  const wrapperRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const playerRef = useRef<YTPlayer | null>(null);
  const maxRef = useRef(0);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const concluidoRef = useRef(false);
  // Callbacks em ref p/ o efeito do player não depender deles (recriaria o iframe).
  const onConcluidoRef = useRef(onConcluido);
  const onErroRef = useRef(onErro);
  useEffect(() => {
    onConcluidoRef.current = onConcluido;
    onErroRef.current = onErro;
  });

  const video = videos.find((v) => v.idioma === idioma) ?? videos[0];

  const aplicarLegendas = (p: YTPlayer, on: boolean) => {
    try {
      if (on) p.loadModule("captions");
      else p.unloadModule("captions");
    } catch {
      /* módulo indisponível neste embed */
    }
  };
  const toggleLegendas = () => {
    const on = !legendasRef.current;
    legendasRef.current = on;
    setLegendas(on);
    if (playerRef.current) aplicarLegendas(playerRef.current, on);
  };

  const falhar = useCallback((motivo: string) => {
    setStatus("erro");
    onErroRef.current?.(motivo);
  }, []);

  useEffect(() => {
    if (erroApi) falhar(`API do YouTube indisponível (${erroApi})`);
  }, [erroApi, falhar]);

  const pararPoll = () => {
    if (pollRef.current) clearInterval(pollRef.current);
    pollRef.current = null;
  };

  // Cria/destrói o player. GOTCHA: YT.Player SUBSTITUI o elemento alvo pelo
  // <iframe> e destroy() o remove — nunca entregar um nó controlado pelo React:
  // criamos um host imperativo descartável dentro do container.
  useEffect(() => {
    if (!YT || !containerRef.current || !video) return;
    let cancelado = false;
    const host = document.createElement("div");
    containerRef.current.appendChild(host);
    maxRef.current = 0;
    concluidoRef.current = false;
    setProgresso(0);
    setDuracao(0);
    setStatus("carregando");

    const readyTimer = setTimeout(() => {
      if (!cancelado) falhar("o player não respondeu");
    }, READY_TIMEOUT_MS);

    const player = new YT.Player(host, {
      host: "https://www.youtube-nocookie.com",
      videoId: video.id,
      width: "100%",
      height: "100%",
      playerVars: {
        controls: 0,
        disablekb: 1,
        rel: 0,
        modestbranding: 1,
        playsinline: 1,
        fs: 0,
        iv_load_policy: 3,
        enablejsapi: 1,
        // Sem o origin exato os eventos via postMessage não chegam (ENDED nunca dispara).
        origin: window.location.origin,
      },
      events: {
        onReady: (e) => {
          if (cancelado) return;
          clearTimeout(readyTimer);
          setDuracao(e.target.getDuration() || 0);
          setStatus("pronto");
          aplicarLegendas(e.target, legendasRef.current);
        },
        onStateChange: (e) => {
          if (cancelado) return;
          const p = e.target;
          if (e.data === YT.PlayerState.PLAYING) {
            setStatus("tocando");
            setDuracao((d) => d || p.getDuration() || 0);
            // O módulo de legendas só existe depois que a reprodução começa.
            aplicarLegendas(p, legendasRef.current);
            pararPoll();
            pollRef.current = setInterval(() => {
              // Velocidade alterada (DevTools/extensão) → volta ao normal.
              if (p.getPlaybackRate() !== 1) p.setPlaybackRate(1);
              const t = p.getCurrentTime();
              if (t > maxRef.current + TOLERANCIA_SEEK_S) {
                p.seekTo(maxRef.current, true); // pulou → volta
              } else if (t > maxRef.current) {
                maxRef.current = t;
              }
              setProgresso(maxRef.current);
            }, POLL_MS);
          } else if (e.data === YT.PlayerState.PAUSED) {
            setStatus("pausado");
            pararPoll();
          } else if (e.data === YT.PlayerState.ENDED) {
            pararPoll();
            const dur = p.getDuration() || 0;
            // ENDED sem ter chegado perto do fim = seek programático → volta e segue.
            if (dur > 0 && maxRef.current < dur - MARGEM_FIM_S) {
              p.seekTo(maxRef.current, true);
              p.playVideo();
              return;
            }
            if (concluidoRef.current) return;
            concluidoRef.current = true;
            maxRef.current = dur || maxRef.current;
            setProgresso(maxRef.current);
            setStatus("concluido");
            onConcluidoRef.current(video.idioma);
          }
        },
        onError: (e) => {
          if (cancelado) return;
          clearTimeout(readyTimer);
          falhar(`vídeo indisponível (código ${e.data})`);
        },
      },
    });
    playerRef.current = player;

    return () => {
      cancelado = true;
      clearTimeout(readyTimer);
      pararPoll();
      try {
        player.destroy();
      } catch {
        /* player pode nem ter montado */
      }
      host.remove();
      playerRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [YT, video?.id]);

  // ---- Tela cheia ----
  const fsSuportado = () => {
    const el = wrapperRef.current as FsElement | null;
    return !!el && !!(el.requestFullscreen || el.webkitRequestFullscreen);
  };
  const fsAtual = () => {
    const d = document as FsDocument;
    return d.fullscreenElement ?? d.webkitFullscreenElement ?? null;
  };
  const toggleFullscreen = () => {
    const el = wrapperRef.current as FsElement | null;
    if (!el) return;
    if (!fsSuportado()) {
      setPseudoFs((v) => !v);
      return;
    }
    const d = document as FsDocument;
    if (fsAtual()) {
      void (d.exitFullscreen?.() ?? d.webkitExitFullscreen?.());
    } else {
      void (el.requestFullscreen?.() ?? el.webkitRequestFullscreen?.());
    }
  };
  useEffect(() => {
    const onChange = () => setFullscreen(fsAtual() === wrapperRef.current);
    document.addEventListener("fullscreenchange", onChange);
    document.addEventListener("webkitfullscreenchange", onChange);
    return () => {
      document.removeEventListener("fullscreenchange", onChange);
      document.removeEventListener("webkitfullscreenchange", onChange);
    };
  }, []);
  useEffect(() => {
    if (!pseudoFs) return;
    const onKey = (e: KeyboardEvent) =>
      e.key === "Escape" && setPseudoFs(false);
    document.addEventListener("keydown", onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = overflow;
    };
  }, [pseudoFs]);

  const fsAtivo = fullscreen || pseudoFs;
  const concluido = status === "concluido";
  const pct = duracao > 0 ? Math.min(100, (progresso / duracao) * 100) : 0;

  return (
    <div
      ref={wrapperRef}
      className={cn(
        "flex flex-col bg-black text-white",
        fsAtivo
          ? "fixed inset-0 z-[100] h-full w-full"
          : "overflow-hidden rounded-lg",
        className,
      )}
    >
      {/* Barra superior: idioma + tela cheia */}
      <div className="flex items-center justify-between gap-2 bg-black/90 px-3 py-2">
        <div className="flex items-center gap-1">
          {videos.map((v) => (
            <button
              key={v.idioma}
              type="button"
              disabled={concluido || status === "erro"}
              onClick={() => v.idioma !== idioma && setIdioma(v.idioma)}
              className={cn(
                "rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
                v.idioma === idioma
                  ? "bg-white text-black"
                  : "text-white/70 hover:bg-white/10",
                "disabled:cursor-not-allowed disabled:opacity-60",
              )}
            >
              {v.rotulo}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-1">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="text-white hover:bg-white/10 hover:text-white"
            onClick={toggleLegendas}
            disabled={status === "erro" || status === "carregando"}
            title={legendas ? "Desligar legendas" : "Ligar legendas"}
          >
            {legendas ? (
              <Captions className="mr-1 h-4 w-4" />
            ) : (
              <CaptionsOff className="mr-1 h-4 w-4" />
            )}
            Legendas
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="text-white hover:bg-white/10 hover:text-white"
            onClick={toggleFullscreen}
            disabled={status === "erro"}
          >
            {fsAtivo ? (
              <Minimize2 className="mr-1 h-4 w-4" />
            ) : (
              <Maximize2 className="mr-1 h-4 w-4" />
            )}
            {fsAtivo ? "Sair da tela cheia" : "Tela cheia"}
          </Button>
        </div>
      </div>

      {/* Vídeo (o iframe do YouTube é injetado aqui) */}
      <div
        ref={containerRef}
        className={cn(
          "relative w-full bg-black",
          fsAtivo ? "min-h-0 flex-1" : "aspect-video",
          "[&_iframe]:absolute [&_iframe]:inset-0 [&_iframe]:h-full [&_iframe]:w-full",
        )}
      >
        {status === "carregando" && (
          <div className="absolute inset-0 z-10 flex items-center justify-center gap-2 bg-black text-sm text-white/80">
            <Loader2 className="h-5 w-5 animate-spin" /> Carregando videoaula…
          </div>
        )}
        {concluido && (
          // Cobre a tela final do YouTube (vídeos relacionados aparecem mesmo com rel=0).
          <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-2 bg-black/95">
            <CheckCircle2 className="h-12 w-12 text-emerald-400" />
            <p className="text-base font-medium">Videoaula concluída</p>
            <p className="text-xs text-white/70">
              {video?.rotulo} · {mmss(duracao)}
            </p>
          </div>
        )}
        {status === "erro" && (
          <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-2 bg-black px-6 text-center">
            <TriangleAlert className="h-10 w-10 text-amber-400" />
            <p className="text-sm">
              Não foi possível carregar o player do YouTube.
            </p>
          </div>
        )}
      </div>

      {/* Barra inferior: progresso próprio (controls:0 esconde a do YouTube) */}
      <div className="space-y-1 bg-black/90 px-3 py-2">
        <Progress value={pct} className="h-1.5 bg-white/20" />
        <div className="flex items-center justify-between text-xs text-white/80">
          <span>
            {mmss(progresso)} / {mmss(duracao)}
          </span>
          <span>
            {status === "pronto" && "Toque no vídeo para iniciar"}
            {status === "tocando" && "Reproduzindo — não é possível adiantar"}
            {status === "pausado" && "Pausado"}
            {status === "concluido" && "Concluída"}
            {status === "carregando" && "Carregando…"}
            {status === "erro" && "Indisponível"}
          </span>
        </div>
      </div>
    </div>
  );
}
