"use client";

import { useState, useTransition } from "react";
import { Botao } from "@/components/Acao";
import { salvarCadastroEmpresa } from "@/lib/actions";
import type { CadastroEmpresa } from "@/lib/types";

type CampoEditavel = Exclude<keyof CadastroEmpresa, "slug">;

const CAMPOS: Array<{
  chave: CampoEditavel;
  rotulo: string;
  placeholder?: string;
  tipo?: string;
  largo?: boolean;
}> = [
  { chave: "razaoSocial", rotulo: "Razão social", largo: true },
  { chave: "cnpj", rotulo: "CNPJ", placeholder: "00.000.000/0000-00" },
  { chave: "responsavelNome", rotulo: "Responsável" },
  { chave: "telefone", rotulo: "Telefone", placeholder: "+55 11 99999-9999", tipo: "tel" },
  { chave: "whatsapp", rotulo: "WhatsApp", placeholder: "+55 11 99999-9999", tipo: "tel" },
  { chave: "emailOficial", rotulo: "E-mail oficial", tipo: "email", largo: true },
  { chave: "cidade", rotulo: "Cidade" },
  { chave: "uf", rotulo: "UF", placeholder: "SP" },
];

type Form = Record<CampoEditavel, string>;

function paraForm(c: CadastroEmpresa): Form {
  return {
    razaoSocial: c.razaoSocial ?? "",
    cnpj: c.cnpj ?? "",
    responsavelNome: c.responsavelNome ?? "",
    telefone: c.telefone ?? "",
    whatsapp: c.whatsapp ?? "",
    emailOficial: c.emailOficial ?? "",
    cidade: c.cidade ?? "",
    uf: c.uf ?? "",
  };
}

/**
 * Cadastro da empresa. Slug nunca é editável (vai em URLs, vitrine e exports).
 * Sem `podeEditar` (FINANCEIRO/LEITURA) vira ficha somente leitura.
 */
export function CadastroDaEmpresa({
  tenantId,
  cadastro,
  podeEditar,
}: {
  tenantId: string;
  cadastro: CadastroEmpresa;
  podeEditar: boolean;
}) {
  const [base, setBase] = useState<CadastroEmpresa>(cadastro);
  const [form, setForm] = useState<Form>(() => paraForm(cadastro));
  const [motivo, setMotivo] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [ok, setOk] = useState(false);
  const [pendente, iniciar] = useTransition();

  if (!podeEditar) {
    return (
      <dl className="grid grid-cols-1 gap-y-3 text-sm sm:grid-cols-2">
        <Leitura rotulo="Slug" valor={base.slug} />
        {CAMPOS.map((c) => (
          <Leitura key={c.chave} rotulo={c.rotulo} valor={base[c.chave] || "—"} />
        ))}
      </dl>
    );
  }

  const original = paraForm(base);
  const mudou = CAMPOS.some((c) => form[c.chave].trim() !== original[c.chave].trim());
  const ufValida = form.uf.trim() === "" || /^[A-Za-z]{2}$/.test(form.uf.trim());
  const valido =
    mudou && form.razaoSocial.trim().length > 0 && ufValida && motivo.trim().length > 0;

  function salvar() {
    setErro(null);
    setOk(false);
    iniciar(async () => {
      const r = await salvarCadastroEmpresa(tenantId, {
        razaoSocial: form.razaoSocial.trim(),
        cnpj: form.cnpj.trim(),
        responsavelNome: form.responsavelNome.trim(),
        telefone: form.telefone.trim(),
        whatsapp: form.whatsapp.trim(),
        emailOficial: form.emailOficial.trim(),
        cidade: form.cidade.trim(),
        uf: form.uf.trim().toUpperCase(),
        motivo: motivo.trim(),
      });
      if (!r.ok) {
        setErro(r.erro);
        return;
      }
      setBase(r.dados);
      setForm(paraForm(r.dados));
      setMotivo("");
      setOk(true);
    });
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (valido && !pendente) salvar();
      }}
    >
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label className="block text-sm sm:col-span-2">
          <span className="text-xs uppercase tracking-wide text-ink-300">Slug</span>
          <div className="mt-1 rounded-md border border-slate-200 bg-slate-50 px-2 py-1.5 text-ink-500">
            {base.slug}
            <span className="ml-2 text-xs text-ink-300">(não editável)</span>
          </div>
        </label>
        {CAMPOS.map((c) => (
          <label key={c.chave} className={c.largo ? "block text-sm sm:col-span-2" : "block text-sm"}>
            <span className="text-xs uppercase tracking-wide text-ink-300">
              {c.rotulo}
              {c.chave === "razaoSocial" && " *"}
            </span>
            <input
              type={c.tipo ?? "text"}
              value={form[c.chave]}
              maxLength={c.chave === "uf" ? 2 : undefined}
              placeholder={c.placeholder}
              onChange={(e) => {
                const v = c.chave === "uf" ? e.target.value.toUpperCase() : e.target.value;
                setForm((f) => ({ ...f, [c.chave]: v }));
                setOk(false);
              }}
              className="mt-1 w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm"
            />
          </label>
        ))}
      </div>
      <p className="mt-2 text-xs text-ink-300">
        Campo vazio limpa a informação (menos a razão social, que é obrigatória).
      </p>
      <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-4">
        <input
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
          placeholder="motivo (obrigatório, auditado)"
          className="w-64 max-w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        />
        <Botao type="submit" variante="primaria" disabled={!valido || pendente}>
          {pendente ? "…" : "Salvar cadastro"}
        </Botao>
        {mudou && !pendente && (
          <Botao
            type="button"
            onClick={() => {
              setForm(original);
              setErro(null);
            }}
          >
            Descartar
          </Botao>
        )}
      </div>
      {!ufValida && <p className="mt-1 text-xs text-red-700">UF deve ter 2 letras.</p>}
      {erro && <p className="mt-1 text-xs text-red-700">{erro}</p>}
      {ok && <p className="mt-1 text-xs text-emerald-700">Cadastro atualizado.</p>}
    </form>
  );
}

function Leitura({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-ink-300">{rotulo}</dt>
      <dd className="mt-0.5 break-words text-ink-900">{valor}</dd>
    </div>
  );
}
