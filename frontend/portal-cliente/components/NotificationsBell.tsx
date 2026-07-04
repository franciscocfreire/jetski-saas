"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useSession } from "next-auth/react";
import { Bell, CheckCheck } from "lucide-react";
import {
  getNotificacoes,
  marcarNotificacaoLida,
  marcarTodasLidas,
  type CaixaNotificacoes,
} from "@/lib/api";

/**
 * Sininho do portal (backlog P4): contagem de não lidas + dropdown com as
 * últimas notificações. Atualiza ao montar, ao focar a aba e a cada 60s.
 */
export function NotificationsBell() {
  const { data: session, status } = useSession();
  const router = useRouter();
  const [caixa, setCaixa] = useState<CaixaNotificacoes | null>(null);
  const [aberto, setAberto] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const carregar = useCallback(async () => {
    if (!session?.accessToken) return;
    try {
      setCaixa(await getNotificacoes(session.accessToken));
    } catch {
      // silencioso — sininho nunca quebra a página
    }
  }, [session?.accessToken]);

  useEffect(() => {
    if (status !== "authenticated") return;
    carregar();
    const timer = setInterval(carregar, 60_000);
    const onFocus = () => carregar();
    window.addEventListener("focus", onFocus);
    return () => {
      clearInterval(timer);
      window.removeEventListener("focus", onFocus);
    };
  }, [status, carregar]);

  // fecha ao clicar fora
  useEffect(() => {
    if (!aberto) return;
    const fechar = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setAberto(false);
    };
    document.addEventListener("mousedown", fechar);
    return () => document.removeEventListener("mousedown", fechar);
  }, [aberto]);

  if (status !== "authenticated") return null;

  async function abrir(n: CaixaNotificacoes["itens"][number]) {
    setAberto(false);
    if (!n.lida && session?.accessToken) {
      marcarNotificacaoLida(session.accessToken, n.id).then(carregar).catch(() => {});
    }
    if (n.link) router.push(n.link);
  }

  async function todasLidas() {
    if (!session?.accessToken) return;
    await marcarTodasLidas(session.accessToken).catch(() => {});
    carregar();
  }

  const naoLidas = caixa?.naoLidas ?? 0;

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setAberto((v) => !v)}
        className="relative grid h-9 w-9 place-items-center rounded-lg text-slate-600 hover:bg-slate-100"
        aria-label="Notificações"
      >
        <Bell size={18} />
        {naoLidas > 0 && (
          <span className="absolute -right-0.5 -top-0.5 grid h-4 min-w-4 place-items-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">
            {naoLidas > 9 ? "9+" : naoLidas}
          </span>
        )}
      </button>

      {aberto && (
        <div className="absolute right-0 z-50 mt-2 w-80 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xl">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-2.5">
            <span className="text-sm font-semibold text-ink-900">Notificações</span>
            {naoLidas > 0 && (
              <button
                onClick={todasLidas}
                className="flex items-center gap-1 text-xs text-brand-600 hover:underline"
              >
                <CheckCheck size={13} /> Marcar todas como lidas
              </button>
            )}
          </div>
          <div className="max-h-96 overflow-y-auto">
            {!caixa || caixa.itens.length === 0 ? (
              <p className="px-4 py-8 text-center text-sm text-slate-400">
                Nada por aqui ainda — avisamos quando algo acontecer.
              </p>
            ) : (
              caixa.itens.slice(0, 8).map((n) => (
                <button
                  key={n.id}
                  onClick={() => abrir(n)}
                  className={`block w-full border-b border-slate-50 px-4 py-3 text-left hover:bg-slate-50 ${
                    n.lida ? "opacity-60" : ""
                  }`}
                >
                  <div className="flex items-start gap-2">
                    {!n.lida && (
                      <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-brand-500" />
                    )}
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-ink-900">{n.titulo}</p>
                      {n.mensagem && (
                        <p className="mt-0.5 line-clamp-2 text-xs text-slate-500">{n.mensagem}</p>
                      )}
                      <p className="mt-1 text-[11px] text-slate-400">
                        {new Date(n.criadaEm).toLocaleString("pt-BR", {
                          day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit",
                        })}
                      </p>
                    </div>
                  </div>
                </button>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
