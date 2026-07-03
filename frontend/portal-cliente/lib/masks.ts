/**
 * Máscaras de exibição (mesmo padrão do backoffice: phone-input.tsx).
 * O valor armazenado/enviado é sempre só dígitos; a máscara é visual.
 */

export function onlyDigits(v: string): string {
  return (v ?? "").replace(/\D/g, "");
}

/** (11) 99999-9999 — fixo ou celular BR. */
export function maskTelefoneBR(v: string): string {
  const d = onlyDigits(v).slice(0, 11);
  const ddd = d.slice(0, 2);
  const num = d.slice(2);
  let r = ddd ? `(${ddd}` : "";
  if (ddd.length === 2) r += ") ";
  if (num.length <= 4) r += num;
  else if (num.length <= 8) r += `${num.slice(0, 4)}-${num.slice(4)}`;
  else r += `${num.slice(0, 5)}-${num.slice(5)}`;
  return r;
}

/** 000.000.000-00 */
export function maskCpf(v: string): string {
  const d = onlyDigits(v).slice(0, 11);
  let r = d.slice(0, 3);
  if (d.length > 3) r += "." + d.slice(3, 6);
  if (d.length > 6) r += "." + d.slice(6, 9);
  if (d.length > 9) r += "-" + d.slice(9);
  return r;
}
