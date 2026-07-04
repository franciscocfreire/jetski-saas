"use client";

import { useMemo } from "react";
import { inputCls } from "@/components/ui";

/**
 * Telefone com seletor de país (mesmo modelo do backoffice ui/phone-input):
 * valor em E.164 (+55119...). Brasil é o default; a máscara (11) 99999-9999
 * só se aplica ao +55 — estrangeiro digita o número nacional livre.
 */

type Pais = { code: string; nome: string; dial: string; flag: string };

const PAISES: Pais[] = [
  { code: "BR", nome: "Brasil", dial: "55", flag: "🇧🇷" },
  { code: "PT", nome: "Portugal", dial: "351", flag: "🇵🇹" },
  { code: "US", nome: "EUA/Canadá", dial: "1", flag: "🇺🇸" },
  { code: "AR", nome: "Argentina", dial: "54", flag: "🇦🇷" },
  { code: "UY", nome: "Uruguai", dial: "598", flag: "🇺🇾" },
  { code: "PY", nome: "Paraguai", dial: "595", flag: "🇵🇾" },
  { code: "CL", nome: "Chile", dial: "56", flag: "🇨🇱" },
  { code: "CO", nome: "Colômbia", dial: "57", flag: "🇨🇴" },
  { code: "ES", nome: "Espanha", dial: "34", flag: "🇪🇸" },
  { code: "IT", nome: "Itália", dial: "39", flag: "🇮🇹" },
  { code: "FR", nome: "França", dial: "33", flag: "🇫🇷" },
  { code: "DE", nome: "Alemanha", dial: "49", flag: "🇩🇪" },
  { code: "GB", nome: "Reino Unido", dial: "44", flag: "🇬🇧" },
];

const DEFAULT = PAISES[0];

function maskNacionalBR(digits: string): string {
  const d = digits.slice(0, 11);
  const ddd = d.slice(0, 2);
  const num = d.slice(2);
  let r = ddd ? `(${ddd}` : "";
  if (ddd.length === 2) r += ") ";
  if (num.length <= 4) r += num;
  else if (num.length <= 8) r += `${num.slice(0, 4)}-${num.slice(4)}`;
  else r += `${num.slice(0, 5)}-${num.slice(5)}`;
  return r;
}

/** Quebra um valor (E.164 ou dígitos legados) em país + parte nacional. */
function parse(value: string): { pais: Pais; nacional: string } {
  const raw = value ?? "";
  const digits = raw.replace(/\D/g, "");
  // valores legados (sem "+") são tratados como nacionais do Brasil
  if (!raw.trim().startsWith("+")) return { pais: DEFAULT, nacional: digits };
  const dials = [...new Set(PAISES.map((p) => p.dial))].sort((a, b) => b.length - a.length);
  for (const d of dials) {
    if (digits.startsWith(d)) {
      const pais = PAISES.find((p) => p.dial === d)!;
      return { pais, nacional: digits.slice(d.length) };
    }
  }
  return { pais: DEFAULT, nacional: digits };
}

export function PhoneInput({
  value,
  onChange,
  className,
}: {
  value: string;
  onChange: (v: string) => void;
  className?: string;
}) {
  const { pais, nacional } = useMemo(() => parse(value), [value]);

  const trocarPais = (code: string) => {
    const p = PAISES.find((x) => x.code === code) ?? DEFAULT;
    onChange(nacional ? `+${p.dial}${nacional}` : "");
  };

  const setNumero = (input: string) => {
    const digits = input.replace(/\D/g, "").slice(0, 15);
    onChange(digits ? `+${pais.dial}${digits}` : "");
  };

  const display = pais.dial === "55" ? maskNacionalBR(nacional) : nacional;

  // largura do select via style: o w-full do inputCls vence utilitários
  // de largura pela ordem no CSS gerado — inline style não perde nunca
  return (
    <div className={`flex w-full gap-2 ${className ?? ""}`}>
      <select
        className="h-11 shrink-0 rounded-xl border border-slate-300 bg-white px-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
        style={{ width: 104 }}
        value={pais.code}
        onChange={(e) => trocarPais(e.target.value)}
        aria-label="País do telefone"
      >
        {PAISES.map((p) => (
          <option key={p.code} value={p.code}>
            {p.flag} +{p.dial}
          </option>
        ))}
      </select>
      <input
        className={inputCls + " min-w-0 flex-1"}
        inputMode="tel"
        value={display}
        onChange={(e) => setNumero(e.target.value)}
        placeholder={pais.dial === "55" ? "(11) 99999-9999" : "número"}
      />
    </div>
  );
}
