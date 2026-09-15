"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { BadgeCheck, Loader2, Lock } from "lucide-react";
import { Button, Card, Field, inputCls } from "@/components/ui";
import { getSelf, updateSelf, ApiError, isCpfEmUso, type IdentidadeCliente } from "@/lib/api";
import { maskCpf } from "@/lib/masks";
import { useToast } from "@/components/Toast";
import { usePerfil } from "@/components/perfil/usePerfil";
import { PerfilSubpagina } from "@/components/perfil/secoes";

const CAMPOS_TEXTO = ["dataNascimento", "rg", "orgaoEmissor", "nacionalidade", "naturalidade"] as const;

/** Dados pessoais + documento de identidade (identidade global, vale para todas as lojas). */
export default function PerfilDadosPage() {
  const { token, self, setSelf, erro, carregando } = usePerfil();
  const router = useRouter();
  const { toast } = useToast();
  const [nome, setNome] = useState("");
  const [ident, setIdent] = useState<IdentidadeCliente>({});
  const [salvando, setSalvando] = useState(false);
  const [salvo, setSalvo] = useState(false);
  const [erroSalvar, setErroSalvar] = useState<string | null>(null);

  // Preenche o formulário só na 1ª carga: um refresh do token recarrega o
  // self e não pode apagar o que o cliente está digitando.
  const iniciado = useRef(false);
  useEffect(() => {
    if (self && !iniciado.current) {
      iniciado.current = true;
      setNome(self.nome ?? "");
      setIdent(self.identidade ?? {});
    }
  }, [self]);

  // Edição pendente = difere do que veio do backend (CPF comparado só pelos dígitos)
  const orig = self?.identidade ?? {};
  const digitos = (v?: string) => (v ?? "").replace(/\D/g, "");
  const alterado =
    !!self &&
    (nome !== (self.nome ?? "") ||
      digitos(ident.cpf) !== digitos(orig.cpf) ||
      CAMPOS_TEXTO.some((k) => (ident[k] ?? "") !== (orig[k] ?? "")) ||
      !!ident.estrangeiro !== !!orig.estrangeiro);

  useEffect(() => {
    if (!alterado) return;
    const avisar = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener("beforeunload", avisar);
    return () => window.removeEventListener("beforeunload", avisar);
  }, [alterado]);

  function descartar() {
    if (!self) return;
    setNome(self.nome ?? "");
    setIdent(self.identidade ?? {});
    setErroSalvar(null);
  }

  async function salvar() {
    if (!token) return;
    setSalvando(true);
    setErroSalvar(null);
    setSalvo(false);
    try {
      await updateSelf(token, nome, ident);
      const dados = await getSelf(token);
      setSelf(dados);
      setNome(dados.nome ?? "");
      setIdent(dados.identidade ?? {});
      setSalvo(true);
      toast("Dados salvos");
    } catch (e) {
      if (isCpfEmUso(e)) {
        // CPF pertence a outra conta → fluxo de unificação em /conta/cpf
        router.push("/conta/cpf?next=/conta/perfil/dados");
        return;
      }
      const msg = e instanceof ApiError ? e.message : "Não foi possível salvar.";
      setErroSalvar(msg);
      toast(msg, "erro");
    } finally {
      setSalvando(false);
    }
  }

  const cpfTravado = !!self?.identidade?.cpf;

  return (
    <PerfilSubpagina
      titulo="Dados pessoais"
      sub="Valem para todas as lojas. Endereço e telefone são pedidos por loja, na reserva."
      carregando={carregando}
      erro={erro ?? erroSalvar}
    >
      <Card className="p-6">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Nome completo">
            <input className={inputCls} value={nome} onChange={(e) => setNome(e.target.value)} />
          </Field>
          <div>
            <span className="mb-1.5 block text-sm font-medium text-slate-700">E-mail</span>
            <div className="flex h-11 items-center gap-2 text-sm text-ink-900">
              <span className="truncate">{self?.email}</span>
              {self?.emailVerified && (
                <span title="E-mail verificado">
                  <BadgeCheck size={18} className="shrink-0 text-emerald-600" />
                </span>
              )}
            </div>
          </div>
          <Field label="Data de nascimento">
            <input
              type="date"
              className={inputCls}
              value={ident.dataNascimento ?? ""}
              onChange={(e) => setIdent({ ...ident, dataNascimento: e.target.value })}
            />
          </Field>
        </div>

        <div className="mt-5 flex items-center justify-between gap-4 rounded-xl bg-[#f8f4ea] px-4 py-3">
          <span className="text-sm font-medium text-ink-900">Sou estrangeiro(a)</span>
          <button
            type="button"
            role="switch"
            aria-checked={!!ident.estrangeiro}
            aria-label="Sou estrangeiro(a)"
            onClick={() => setIdent({ ...ident, estrangeiro: !ident.estrangeiro })}
            className={`relative h-6 w-10 shrink-0 rounded-full transition-colors ${
              ident.estrangeiro ? "bg-brand-600" : "bg-slate-300"
            }`}
          >
            <span
              className={`absolute top-[3px] h-[18px] w-[18px] rounded-full bg-white transition-[left] ${
                ident.estrangeiro ? "left-[19px]" : "left-[3px]"
              }`}
            />
          </button>
        </div>

        <p className="mt-6 text-xs font-semibold uppercase tracking-[.08em] text-slate-500">
          Documento de identidade
        </p>
        <div className="mt-3 grid gap-4 sm:grid-cols-2">
          {cpfTravado ? (
            <div>
              <span className="mb-1.5 block text-sm font-medium text-slate-700">CPF</span>
              <div className="flex h-11 items-center gap-2 text-sm tabular-nums text-ink-900">
                <Lock size={15} className="text-slate-400" />
                {maskCpf(ident.cpf ?? "")}
              </div>
              <span className="mt-1 block text-xs text-slate-400">
                Definido uma única vez. Para corrigir, fale com a loja.
              </span>
            </div>
          ) : (
            <Field label="CPF" hint="Você também poderá entrar com o CPF">
              <input
                className={inputCls}
                inputMode="numeric"
                value={maskCpf(ident.cpf ?? "")}
                onChange={(e) => setIdent({ ...ident, cpf: maskCpf(e.target.value) })}
                placeholder="000.000.000-00"
              />
            </Field>
          )}
          <Field label="RG / Identidade">
            <input
              className={inputCls}
              value={ident.rg ?? ""}
              onChange={(e) => setIdent({ ...ident, rg: e.target.value })}
            />
          </Field>
          <Field label="Órgão emissor">
            <input
              className={inputCls}
              value={ident.orgaoEmissor ?? ""}
              onChange={(e) => setIdent({ ...ident, orgaoEmissor: e.target.value })}
              placeholder="SSP/UF"
            />
          </Field>
          <Field label="Nacionalidade">
            <input
              className={inputCls}
              value={ident.nacionalidade ?? ""}
              onChange={(e) => setIdent({ ...ident, nacionalidade: e.target.value })}
              placeholder="Brasileira"
            />
          </Field>
          <Field label="Naturalidade (Cidade/UF)">
            <input
              className={inputCls}
              value={ident.naturalidade ?? ""}
              onChange={(e) => setIdent({ ...ident, naturalidade: e.target.value })}
            />
          </Field>
        </div>

        <div className="mt-6 flex flex-wrap items-center gap-3 border-t border-slate-100 pt-5">
          <span className="flex-1 text-sm">
            {alterado ? (
              <span className="text-amber-700">Você tem alterações não salvas</span>
            ) : (
              salvo && <span className="text-emerald-600">Salvo ✓</span>
            )}
          </span>
          {alterado && (
            <Button variant="outline" onClick={descartar} disabled={salvando}>
              Descartar
            </Button>
          )}
          <Button onClick={salvar} disabled={salvando || !alterado || nome.trim().length < 3}>
            {salvando && <Loader2 size={14} className="animate-spin" />}
            Salvar alterações
          </Button>
        </div>
      </Card>
    </PerfilSubpagina>
  );
}
