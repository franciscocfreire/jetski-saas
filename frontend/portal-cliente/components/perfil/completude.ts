import type { CustomerSelf } from "@/lib/api";

/** Fotos exigidas pela emissão; o comprovante de residência pode virar declaração na reserva. */
export const DOCS_OBRIGATORIOS = ["IDENTIDADE", "SELFIE"] as const;

export type Passo = { chave: string; ok: boolean; acao: string; href: string };

/**
 * Espelha CustomerEmaService.toDadosPessoais (nome, CPF, RG, nacionalidade,
 * naturalidade). O endereço fica de fora: é pedido por loja, na reserva.
 */
export function dadosCompletos(self: CustomerSelf): boolean {
  const i = self.identidade ?? {};
  return [self.nome, i.cpf, i.rg, i.nacionalidade, i.naturalidade].every((v) => !!v?.trim());
}

/**
 * Tipos obrigatórios que faltam em alguma loja. Loja cuja lista não carregou
 * (null) não conta — melhor não acusar pendência que talvez não exista.
 */
export function tiposPendentes(self: CustomerSelf, anexos: Record<string, string[] | null>): string[] {
  return DOCS_OBRIGATORIOS.filter((tipo) =>
    self.lojas.some((l) => {
      const presentes = anexos[l.tenantId];
      return presentes != null && !presentes.includes(tipo);
    }),
  );
}

/** Checklist "pronto para emitir a habilitação". Sem loja vinculada, não há fotos a pedir. */
export function passosCadastro(self: CustomerSelf, anexos: Record<string, string[] | null>): Passo[] {
  const passos: Passo[] = [
    {
      chave: "email",
      ok: self.emailVerified,
      acao: "Verifique seu e-mail — sem isso suas reservas não ficam garantidas",
      href: "/conta/verificar-email",
    },
    {
      chave: "dados",
      ok: dadosCompletos(self),
      acao: "Complete seus dados pessoais",
      href: "/conta/perfil/dados",
    },
  ];
  if (self.lojas.length > 0) {
    passos.push({
      chave: "documentos",
      ok: tiposPendentes(self, anexos).length === 0,
      acao: "Envie a foto da identidade e a selfie",
      href: "/conta/perfil/documentos",
    });
  }
  return passos;
}

export function cpfMascarado(cpf?: string): string | null {
  const d = (cpf ?? "").replace(/\D/g, "");
  return d.length === 11 ? `•••.${d.slice(3, 6)}.${d.slice(6, 9)}-••` : null;
}

export function iniciais(nome?: string): string {
  const partes = (nome ?? "").trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) return "?";
  const ultima = partes.length > 1 ? partes[partes.length - 1][0] : "";
  return (partes[0][0] + ultima).toUpperCase();
}

export function fmtData(iso?: string): string | null {
  if (!iso) return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  return m ? `${m[3]}/${m[2]}/${m[1]}` : iso;
}
