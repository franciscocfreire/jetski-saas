"use client";

import { useState } from "react";
import { Search, Loader2, Check, AlertTriangle } from "lucide-react";
import { Field, inputCls } from "./ui";

export type Address = {
  cep: string;
  logradouro: string;
  numero: string;
  complemento: string;
  bairro: string;
  cidade: string;
  uf: string;
};

const EMPTY: Address = {
  cep: "",
  logradouro: "",
  numero: "",
  complemento: "",
  bairro: "",
  cidade: "",
  uf: "",
};

function maskCep(v: string) {
  const d = v.replace(/\D/g, "").slice(0, 8);
  return d.length > 5 ? `${d.slice(0, 5)}-${d.slice(5)}` : d;
}

export function AddressForm({
  onChange,
}: {
  onChange?: (a: Address) => void;
}) {
  const [addr, setAddr] = useState<Address>(EMPTY);
  const [loading, setLoading] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [preenchido, setPreenchido] = useState(false);

  function up(patch: Partial<Address>) {
    setAddr((a) => {
      const next = { ...a, ...patch };
      onChange?.(next);
      return next;
    });
  }

  async function buscarCep(raw: string) {
    const cep = raw.replace(/\D/g, "");
    if (cep.length !== 8) return;
    setLoading(true);
    setErro(null);
    setPreenchido(false);
    try {
      const r = await fetch(`https://viacep.com.br/ws/${cep}/json/`);
      const d = await r.json();
      if (d.erro) {
        setErro("CEP não encontrado. Preencha manualmente.");
      } else {
        up({
          logradouro: d.logradouro || "",
          bairro: d.bairro || "",
          cidade: d.localidade || "",
          uf: d.uf || "",
        });
        setPreenchido(true);
      }
    } catch {
      setErro("Não foi possível consultar o CEP — preencha manualmente.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="grid gap-3 sm:grid-cols-6">
      <div className="sm:col-span-2">
        <Field label="CEP">
          <div className="relative">
            <input
              className={inputCls + " pr-9"}
              inputMode="numeric"
              placeholder="00000-000"
              value={addr.cep}
              onChange={(e) => {
                const masked = maskCep(e.target.value);
                up({ cep: masked });
                if (masked.replace(/\D/g, "").length === 8) buscarCep(masked);
              }}
              onBlur={() => buscarCep(addr.cep)}
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400">
              {loading ? (
                <Loader2 size={16} className="animate-spin" />
              ) : preenchido ? (
                <Check size={16} className="text-emerald-500" />
              ) : (
                <Search size={16} />
              )}
            </span>
          </div>
        </Field>
      </div>

      <div className="sm:col-span-4">
        <Field label="Logradouro">
          <input
            className={inputCls}
            value={addr.logradouro}
            onChange={(e) => up({ logradouro: e.target.value })}
            placeholder="Rua / Avenida"
          />
        </Field>
      </div>

      <div className="sm:col-span-2">
        <Field label="Número">
          <input
            className={inputCls}
            value={addr.numero}
            onChange={(e) => up({ numero: e.target.value })}
          />
        </Field>
      </div>
      <div className="sm:col-span-4">
        <Field label="Complemento">
          <input
            className={inputCls}
            value={addr.complemento}
            onChange={(e) => up({ complemento: e.target.value })}
            placeholder="Apto, bloco… (opcional)"
          />
        </Field>
      </div>

      <div className="sm:col-span-3">
        <Field label="Bairro">
          <input
            className={inputCls}
            value={addr.bairro}
            onChange={(e) => up({ bairro: e.target.value })}
          />
        </Field>
      </div>
      <div className="sm:col-span-2">
        <Field label="Cidade">
          <input
            className={inputCls}
            value={addr.cidade}
            onChange={(e) => up({ cidade: e.target.value })}
          />
        </Field>
      </div>
      <div className="sm:col-span-1">
        <Field label="UF">
          <input
            className={inputCls}
            maxLength={2}
            value={addr.uf}
            onChange={(e) => up({ uf: e.target.value.toUpperCase() })}
          />
        </Field>
      </div>

      {erro && (
        <p className="flex items-center gap-1 text-xs text-amber-700 sm:col-span-6">
          <AlertTriangle size={13} /> {erro}
        </p>
      )}
      {preenchido && !erro && (
        <p className="flex items-center gap-1 text-xs text-emerald-600 sm:col-span-6">
          <Check size={13} /> Endereço preenchido pelo CEP — confira e complete o
          número.
        </p>
      )}
    </div>
  );
}
