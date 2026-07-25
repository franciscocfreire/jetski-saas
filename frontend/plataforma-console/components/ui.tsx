import { clsx } from "clsx";

/** Kit mínimo do console. Não é shadcn — só o necessário para as telas da F1. */

export function Card({
  titulo,
  descricao,
  acao,
  children,
  className,
}: {
  titulo?: string;
  descricao?: string;
  acao?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={clsx("rounded-lg border border-slate-200 bg-white", className)}>
      {(titulo || acao) && (
        <header className="flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
          <div className="min-w-0">
            {titulo && <h2 className="font-display text-lg text-ink-900">{titulo}</h2>}
            {descricao && <p className="mt-0.5 text-sm text-ink-500">{descricao}</p>}
          </div>
          {acao && <div className="shrink-0">{acao}</div>}
        </header>
      )}
      <div className="px-5 py-4">{children}</div>
    </section>
  );
}

const TOM = {
  ativo: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  atencao: "bg-amber-50 text-amber-800 ring-amber-200",
  perigo: "bg-red-50 text-red-700 ring-red-200",
  neutro: "bg-slate-100 text-ink-700 ring-slate-200",
  marca: "bg-brand-50 text-brand-700 ring-brand-200",
} as const;

export function Badge({
  children,
  tom = "neutro",
}: {
  children: React.ReactNode;
  tom?: keyof typeof TOM;
}) {
  return (
    <span
      className={clsx(
        "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset",
        TOM[tom],
      )}
    >
      {children}
    </span>
  );
}

/** Status da empresa → tom visual. ATIVO/TRIAL operam; o resto exige atenção. */
export function StatusEmpresa({ status }: { status: string }) {
  const tom: keyof typeof TOM =
    status === "ATIVO"
      ? "ativo"
      : status === "TRIAL"
        ? "marca"
        : status === "PENDENTE_APROVACAO"
          ? "atencao"
          : "perigo";
  return <Badge tom={tom}>{status.replace(/_/g, " ")}</Badge>;
}

export function Tabela({
  cabecalho,
  children,
  vazio,
}: {
  cabecalho: string[];
  children: React.ReactNode;
  vazio?: string;
}) {
  const temLinhas = Array.isArray(children) ? children.length > 0 : Boolean(children);
  if (!temLinhas && vazio) {
    return <p className="py-6 text-center text-sm text-ink-300">{vazio}</p>;
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[36rem] text-sm">
        <thead>
          <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-ink-300">
            {cabecalho.map((c) => (
              <th key={c} className="px-3 py-2 font-medium">
                {c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">{children}</tbody>
      </table>
    </div>
  );
}

export function Td({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return <td className={clsx("px-3 py-2.5 align-middle", className)}>{children}</td>;
}

export function Vazio({ children }: { children: React.ReactNode }) {
  return <p className="py-6 text-center text-sm text-ink-300">{children}</p>;
}

export function Erro({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-800">
      {children}
    </div>
  );
}

export function Aviso({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-md border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">
      {children}
    </div>
  );
}

export function TituloPagina({
  titulo,
  descricao,
  acao,
}: {
  titulo: string;
  descricao?: string;
  acao?: React.ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="font-display text-2xl text-ink-900">{titulo}</h1>
        {descricao && <p className="mt-1 text-sm text-ink-500">{descricao}</p>}
      </div>
      {acao}
    </div>
  );
}
