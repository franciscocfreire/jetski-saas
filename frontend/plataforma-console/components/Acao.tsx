"use client";

import { useState, useTransition } from "react";
import { clsx } from "clsx";
import type { Resultado } from "@/lib/actions";

const VARIANTE = {
  primaria: "bg-brand-700 text-white hover:bg-brand-600",
  secundaria: "border border-slate-300 bg-white text-ink-700 hover:bg-slate-50",
  perigo: "bg-red-700 text-white hover:bg-red-600",
} as const;

export function Botao({
  children,
  variante = "secundaria",
  className,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variante?: keyof typeof VARIANTE;
}) {
  return (
    <button
      {...props}
      className={clsx(
        "inline-flex items-center justify-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50",
        VARIANTE[variante],
        className,
      )}
    >
      {children}
    </button>
  );
}

/**
 * Botão que dispara uma server action e mostra o erro do backend inline.
 *
 * `confirmar` exige um clique extra (dupla confirmação) — usado nas ações que
 * mudam o status de uma empresa. Para reset/exclusão, que exigem digitar o slug,
 * use o `DialogoPerigo`.
 */
export function Acao<T>({
  acao,
  rotulo,
  confirmar,
  variante = "secundaria",
  aoConcluir,
}: {
  acao: () => Promise<Resultado<T>>;
  rotulo: string;
  confirmar?: string;
  variante?: keyof typeof VARIANTE;
  aoConcluir?: (r: Resultado<T>) => void;
}) {
  const [pendente, iniciar] = useTransition();
  const [confirmando, setConfirmando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  function disparar() {
    setErro(null);
    iniciar(async () => {
      const r = await acao();
      if (!r.ok) setErro(r.erro);
      setConfirmando(false);
      aoConcluir?.(r);
    });
  }

  if (confirmar && confirmando) {
    return (
      <span className="inline-flex items-center gap-1.5">
        <span className="text-xs text-ink-500">{confirmar}</span>
        <Botao variante={variante} disabled={pendente} onClick={disparar}>
          {pendente ? "…" : "Confirmar"}
        </Botao>
        <Botao onClick={() => setConfirmando(false)} disabled={pendente}>
          Cancelar
        </Botao>
      </span>
    );
  }

  return (
    <span className="inline-flex flex-col items-start gap-1">
      <Botao
        variante={variante}
        disabled={pendente}
        onClick={() => (confirmar ? setConfirmando(true) : disparar())}
      >
        {pendente ? "…" : rotulo}
      </Botao>
      {erro && <span className="text-xs text-red-700">{erro}</span>}
    </span>
  );
}

/** Ação que exige um texto (motivo/observação) antes de disparar. */
export function AcaoComTexto<T>({
  acao,
  rotulo,
  placeholder,
  variante = "secundaria",
  obrigatorio = true,
}: {
  acao: (texto: string) => Promise<Resultado<T>>;
  rotulo: string;
  placeholder: string;
  variante?: keyof typeof VARIANTE;
  obrigatorio?: boolean;
}) {
  const [aberto, setAberto] = useState(false);
  const [texto, setTexto] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [pendente, iniciar] = useTransition();

  if (!aberto) {
    return (
      <Botao variante={variante} onClick={() => setAberto(true)}>
        {rotulo}
      </Botao>
    );
  }

  return (
    <span className="inline-flex flex-col items-start gap-1">
      <span className="inline-flex items-center gap-1.5">
        <input
          autoFocus
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          placeholder={placeholder}
          className="w-56 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        />
        <Botao
          variante={variante}
          disabled={pendente || (obrigatorio && !texto.trim())}
          onClick={() => {
            setErro(null);
            iniciar(async () => {
              const r = await acao(texto);
              if (!r.ok) setErro(r.erro);
              else {
                setAberto(false);
                setTexto("");
              }
            });
          }}
        >
          {pendente ? "…" : "Confirmar"}
        </Botao>
        <Botao onClick={() => setAberto(false)} disabled={pendente}>
          Cancelar
        </Botao>
      </span>
      {erro && <span className="text-xs text-red-700">{erro}</span>}
    </span>
  );
}
