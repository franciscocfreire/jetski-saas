"use client";

import { useState, useTransition } from "react";
import { Botao } from "@/components/Acao";
import { atualizarPapeis, concederAcesso, revogarAcesso } from "@/lib/actions";
import type { PapelInfo, PapelPlataforma } from "@/lib/types";
import { Badge } from "@/components/ui";

/**
 * Edição in-place dos papéis. Deixar sem nenhum papel = revogar todo o acesso —
 * por isso o botão muda de rótulo: "revogar acesso" é o que de fato acontece, e a
 * pessoa precisa ver isso antes de confirmar.
 *
 * O backend recusa remover o próprio acesso de admin e o último admin da plataforma;
 * a mensagem dele aparece aqui em vez de um erro genérico.
 */
export function PapeisDoOperador({
  usuarioId,
  email,
  papeisAtuais,
  catalogo,
}: {
  usuarioId: string;
  email: string;
  papeisAtuais: PapelPlataforma[];
  catalogo: PapelInfo[];
}) {
  const [editando, setEditando] = useState(false);
  const [marcados, setMarcados] = useState<string[]>(papeisAtuais);
  const [erro, setErro] = useState<string | null>(null);
  const [pendente, iniciar] = useTransition();

  if (!editando) {
    return (
      <div className="flex flex-wrap items-center gap-1.5">
        {papeisAtuais.map((p) => (
          <Badge key={p} tom={p === "PLATFORM_ADMIN" ? "marca" : "neutro"}>
            {catalogo.find((c) => c.key === p)?.rotulo ?? p}
          </Badge>
        ))}
        <button
          onClick={() => {
            setMarcados(papeisAtuais);
            setErro(null);
            setEditando(true);
          }}
          className="text-xs text-brand-700 hover:underline"
        >
          alterar
        </button>
      </div>
    );
  }

  const revogando = marcados.length === 0;

  return (
    <div className="space-y-2">
      <div className="space-y-1">
        {catalogo.map((p) => (
          <label key={p.key} className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              checked={marcados.includes(p.key)}
              onChange={(e) =>
                setMarcados((atual) =>
                  e.target.checked
                    ? [...atual, p.key]
                    : atual.filter((k) => k !== p.key),
                )
              }
              className="mt-1"
            />
            <span className="font-medium">{p.rotulo}</span>
          </label>
        ))}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Botao
          variante={revogando ? "perigo" : "primaria"}
          disabled={pendente}
          onClick={() => {
            setErro(null);
            iniciar(async () => {
              const r = revogando
                ? await revogarAcesso(usuarioId)
                : await atualizarPapeis(usuarioId, marcados);
              if (!r.ok) setErro(r.erro);
              else setEditando(false);
            });
          }}
        >
          {pendente ? "…" : revogando ? `Revogar acesso de ${email}` : "Salvar papéis"}
        </Botao>
        <Botao onClick={() => setEditando(false)} disabled={pendente}>
          Cancelar
        </Botao>
      </div>
      {erro && <p className="text-xs text-red-700">{erro}</p>}
    </div>
  );
}

export function ConcederAcesso({ catalogo }: { catalogo: PapelInfo[] }) {
  const [email, setEmail] = useState("");
  const [marcados, setMarcados] = useState<string[]>([]);
  const [erro, setErro] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);
  const [pendente, iniciar] = useTransition();

  const valido = email.trim().includes("@") && marcados.length > 0;

  return (
    <div className="space-y-3">
      <input
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="e-mail de uma conta já cadastrada"
        className="w-80 rounded-md border border-slate-300 px-3 py-1.5 text-sm"
      />

      <div className="grid gap-2 sm:grid-cols-2">
        {catalogo.map((p) => (
          <label key={p.key} className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              checked={marcados.includes(p.key)}
              onChange={(e) =>
                setMarcados((atual) =>
                  e.target.checked
                    ? [...atual, p.key]
                    : atual.filter((k) => k !== p.key),
                )
              }
              className="mt-1"
            />
            <span>
              <span className="font-medium">{p.rotulo}</span>
              <span className="block text-xs text-ink-500">{p.descricao}</span>
            </span>
          </label>
        ))}
      </div>

      <div className="flex items-center gap-2">
        <Botao
          variante="primaria"
          disabled={!valido || pendente}
          onClick={() => {
            setErro(null);
            setOk(null);
            iniciar(async () => {
              const r = await concederAcesso(email.trim(), marcados);
              if (!r.ok) setErro(r.erro);
              else {
                setOk(`Acesso concedido a ${email.trim()}.`);
                setEmail("");
                setMarcados([]);
              }
            });
          }}
        >
          {pendente ? "…" : "Conceder acesso"}
        </Botao>
        {erro && <span className="text-xs text-red-700">{erro}</span>}
        {ok && <span className="text-xs text-emerald-700">{ok}</span>}
      </div>
    </div>
  );
}
