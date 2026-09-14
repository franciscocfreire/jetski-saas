"use client";

import { useState, useTransition } from "react";
import { Botao } from "@/components/Acao";
import {
  cancelarConvite,
  convidarMembro,
  desativarMembro,
  reativarMembro,
  removerMembro,
  type Resultado,
} from "@/lib/actions";
import { PAPEIS } from "./papeis";

type Variante = "primaria" | "secundaria" | "perigo";

/**
 * Botão que abre um campo de motivo (obrigatório, auditado) e, opcionalmente, um
 * aviso de confirmação explícito antes do botão final.
 */
function AcaoComMotivo({
  rotulo,
  rotuloConfirmar = "Confirmar",
  variante = "secundaria",
  aviso,
  acao,
  aoAlternar,
}: {
  rotulo: string;
  rotuloConfirmar?: string;
  variante?: Variante;
  aviso?: string;
  acao: (motivo: string) => Promise<Resultado<unknown>>;
  /** Avisa quem está em volta quando o campo abre/fecha (ex.: esconder a ação vizinha). */
  aoAlternar?: (aberto: boolean) => void;
}) {
  const [aberto, setAbertoLocal] = useState(false);
  const setAberto = (v: boolean) => {
    setAbertoLocal(v);
    aoAlternar?.(v);
  };
  const [motivo, setMotivo] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [pendente, iniciar] = useTransition();

  if (!aberto) {
    return (
      <Botao variante={variante === "primaria" ? "secundaria" : variante} onClick={() => setAberto(true)}>
        {rotulo}
      </Botao>
    );
  }

  return (
    <div className="flex w-full min-w-[16rem] flex-col items-end gap-1">
      {aviso && <p className="max-w-xs text-right text-xs text-amber-800">{aviso}</p>}
      <div className="flex flex-wrap items-center justify-end gap-1.5">
        <input
          autoFocus
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
          placeholder="motivo (obrigatório, auditado)"
          className="w-56 max-w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        />
        <Botao
          variante={variante}
          disabled={pendente || !motivo.trim()}
          onClick={() => {
            setErro(null);
            iniciar(async () => {
              const r = await acao(motivo.trim());
              if (!r.ok) setErro(r.erro);
              else {
                setAberto(false);
                setMotivo("");
              }
            });
          }}
        >
          {pendente ? "…" : rotuloConfirmar}
        </Botao>
        <Botao
          disabled={pendente}
          onClick={() => {
            setAberto(false);
            setErro(null);
          }}
        >
          Voltar
        </Botao>
      </div>
      {erro && <p className="max-w-xs text-right text-xs text-red-700">{erro}</p>}
    </div>
  );
}

/** Ações por linha da tabela de usuários. Só uma fica aberta por vez. */
export function AcoesMembro({
  tenantId,
  usuarioId,
  nome,
  ativo,
}: {
  tenantId: string;
  usuarioId: string;
  nome: string;
  ativo: boolean;
}) {
  const [aberta, setAberta] = useState<"status" | "remover" | null>(null);
  const alternar = (qual: "status" | "remover") => (v: boolean) => setAberta(v ? qual : null);

  return (
    <div className="flex flex-wrap items-start justify-end gap-1.5">
      {aberta !== "remover" &&
        (ativo ? (
          <AcaoComMotivo
            key="desativar"
            rotulo="Desativar"
            rotuloConfirmar="Desativar"
            variante="perigo"
            aoAlternar={alternar("status")}
            acao={(motivo) => desativarMembro(tenantId, usuarioId, motivo)}
          />
        ) : (
          <AcaoComMotivo
            key="reativar"
            rotulo="Reativar"
            rotuloConfirmar="Reativar"
            variante="primaria"
            aoAlternar={alternar("status")}
            acao={(motivo) => reativarMembro(tenantId, usuarioId, motivo)}
          />
        ))}
      {aberta !== "status" && (
        <AcaoComMotivo
          rotulo="Remover"
          rotuloConfirmar="Remover desta empresa"
          variante="perigo"
          aviso={`${nome} perde o acesso SÓ a esta empresa. A conta continua existindo e segue valendo para outras empresas.`}
          aoAlternar={alternar("remover")}
          acao={(motivo) => removerMembro(tenantId, usuarioId, motivo)}
        />
      )}
    </div>
  );
}

/** Cancelar convite pendente/expirado. */
export function CancelarConvite({ tenantId, conviteId }: { tenantId: string; conviteId: string }) {
  return (
    <div className="flex justify-end">
      <AcaoComMotivo
        rotulo="Cancelar convite"
        rotuloConfirmar="Cancelar convite"
        variante="perigo"
        acao={(motivo) => cancelarConvite(tenantId, conviteId, motivo)}
      />
    </div>
  );
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Formulário de convite: vale para conta nova e para quem já tem conta. */
export function IncluirUsuario({ tenantId }: { tenantId: string }) {
  const [aberto, setAberto] = useState(false);
  const [email, setEmail] = useState("");
  const [nome, setNome] = useState("");
  const [papeis, setPapeis] = useState<string[]>([]);
  const [motivo, setMotivo] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);
  const [pendente, iniciar] = useTransition();

  const valido =
    EMAIL.test(email.trim()) &&
    nome.trim().length > 0 &&
    papeis.length > 0 &&
    motivo.trim().length > 0;

  if (!aberto) {
    return (
      <div className="flex flex-wrap items-center gap-2">
        <Botao variante="primaria" onClick={() => setAberto(true)}>
          Incluir usuário
        </Botao>
        {ok && <span className="text-xs text-emerald-700">{ok}</span>}
      </div>
    );
  }

  function alternar(p: string) {
    setPapeis((atual) => (atual.includes(p) ? atual.filter((x) => x !== p) : [...atual, p]));
  }

  return (
    <form
      className="rounded-md border border-slate-200 bg-slate-50/60 p-4"
      onSubmit={(e) => {
        e.preventDefault();
        if (!valido || pendente) return;
        setErro(null);
        setOk(null);
        iniciar(async () => {
          const r = await convidarMembro(tenantId, {
            email: email.trim(),
            nome: nome.trim(),
            papeis,
            motivo: motivo.trim(),
          });
          if (!r.ok) {
            setErro(r.erro);
            return;
          }
          setOk(`Convite enviado para ${r.dados?.email ?? email.trim()}.`);
          setEmail("");
          setNome("");
          setPapeis([]);
          setMotivo("");
          setAberto(false);
        });
      }}
    >
      <div className="text-sm font-medium text-ink-900">Incluir usuário</div>
      <p className="mt-0.5 text-xs text-ink-500">
        Enviamos um convite por e-mail. Serve para quem ainda não tem conta e para quem já tem
        (inclusive em outra empresa): o acesso só começa quando a pessoa aceita pelo link.
      </p>
      <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label className="block text-sm">
          <span className="text-xs uppercase tracking-wide text-ink-300">E-mail *</span>
          <input
            type="email"
            autoFocus
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="mt-1 w-full rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
          />
        </label>
        <label className="block text-sm">
          <span className="text-xs uppercase tracking-wide text-ink-300">Nome *</span>
          <input
            value={nome}
            onChange={(e) => setNome(e.target.value)}
            className="mt-1 w-full rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
          />
        </label>
      </div>
      <fieldset className="mt-3">
        <legend className="text-xs uppercase tracking-wide text-ink-300">Papéis *</legend>
        <div className="mt-1.5 flex flex-wrap gap-x-4 gap-y-2">
          {Object.entries(PAPEIS).map(([chave, rotulo]) => (
            <label key={chave} className="inline-flex items-center gap-1.5 text-sm text-ink-700">
              <input
                type="checkbox"
                checked={papeis.includes(chave)}
                onChange={() => alternar(chave)}
                className="h-4 w-4 rounded border-slate-300"
              />
              {rotulo}
            </label>
          ))}
        </div>
      </fieldset>
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <input
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
          placeholder="motivo (obrigatório, auditado)"
          className="w-64 max-w-full rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
        />
        <Botao type="submit" variante="primaria" disabled={!valido || pendente}>
          {pendente ? "…" : "Enviar convite"}
        </Botao>
        <Botao
          type="button"
          disabled={pendente}
          onClick={() => {
            setAberto(false);
            setErro(null);
          }}
        >
          Cancelar
        </Botao>
      </div>
      {erro && <p className="mt-1 text-xs text-red-700">{erro}</p>}
    </form>
  );
}
