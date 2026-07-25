import Link from "next/link";
import { operadorAtual } from "@/lib/sessao";
import { Shell } from "@/components/Shell";
import { platform } from "@/lib/platform";
import { Badge, Card, Erro, Tabela, Td, TituloPagina } from "@/components/ui";
import { PlatformApiError } from "@/lib/api";
import type { TenantSummary } from "@/lib/types";
import { FiltroAcao } from "./filtro";

export const dynamic = "force-dynamic";

/** Ações de concessão/remoção de acesso e entrada em empresa merecem destaque. */
const SENSIVEIS = new Set([
  "PLATAFORMA_ACESSO_CONCEDIDO",
  "PLATAFORMA_ACESSO_REVOGADO",
  "SUPORTE_SESSAO_ABERTA",
]);

function quando(iso: string): string {
  return new Date(iso).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo" });
}

/** Rótulos legíveis para as chaves que os eventos globais carregam hoje. */
const ROTULO_DADO: Record<string, string> = {
  emailAlvo: "conta",
  papeis: "papéis",
  motivo: "motivo",
  cpf: "CPF",
};

/**
 * Mostra o que interessa do JSON sem despejar o objeto inteiro na tabela. IDs internos
 * (sessaoId, operadorId, usuarioAlvo, tenantId) ficam de fora — o operador já vê o e-mail
 * na coluna "Quem" e a empresa na coluna própria.
 */
function resumo(dados: unknown): string {
  if (!dados || typeof dados !== "object") return "";
  const d = dados as Record<string, unknown>;
  const partes: string[] = [];
  if (d.somenteLeitura !== undefined) {
    partes.push(d.somenteLeitura ? "somente leitura" : "COM ESCRITA");
  }
  for (const [chave, rotulo] of Object.entries(ROTULO_DADO)) {
    const v = d[chave];
    if (v === undefined || v === null) continue;
    partes.push(`${rotulo}: ${Array.isArray(v) ? v.join(", ") : String(v)}`);
  }
  return partes.join(" · ");
}

/** A linha global não tem `tenant_id` — a empresa alvo vem dentro do payload. */
function tenantDoEvento(dados: unknown): string | null {
  if (!dados || typeof dados !== "object") return null;
  const id = (dados as Record<string, unknown>).tenantId;
  return typeof id === "string" ? id : null;
}

export default async function Auditoria({
  searchParams,
}: {
  searchParams: Promise<{ acao?: string }>;
}) {
  const { session, me } = await operadorAtual();
  const { acao } = await searchParams;

  let registros;
  let acoes: string[] = [];
  let empresas: TenantSummary[] = [];
  try {
    [registros, acoes, empresas] = await Promise.all([
      platform.auditoria(acao),
      platform.acoesAuditoria(),
      platform.tenants(),
    ]);
  } catch (e) {
    const err = e as PlatformApiError;
    return (
      <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
        <TituloPagina titulo="Auditoria" />
        <Erro>
          {err.status === 403
            ? "Sua conta não é operador de plataforma."
            : `Falha ao carregar a trilha (${err.status}).`}
        </Erro>
      </Shell>
    );
  }

  const nomeDaEmpresa = new Map(empresas.map((t) => [t.id, t.razaoSocial]));

  return (
    <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
      <TituloPagina
        titulo="Auditoria"
        descricao="Trilha global da plataforma: concessão de acesso, sessões de suporte e eventos de identidade."
      />

      <FiltroAcao acoes={acoes} atual={acao} />

      <Card className="mt-4">
        <Tabela
          cabecalho={["Quando", "Ação", "Quem", "Empresa", "Detalhe", "IP"]}
          vazio={
            acao
              ? "Nenhum registro com essa ação."
              : "Trilha vazia — nenhum evento de plataforma registrado ainda."
          }
        >
          {registros.map((r) => (
            <tr key={r.id}>
              <Td className="whitespace-nowrap text-xs">{quando(r.created_at)}</Td>
              <Td>
                {SENSIVEIS.has(r.acao) ? (
                  <Badge tom="atencao">{r.acao}</Badge>
                ) : (
                  <span className="text-sm">{r.acao}</span>
                )}
                {r.entidade && (
                  <div className="text-xs text-ink-300">{r.entidade}</div>
                )}
              </Td>
              <Td className="text-sm">
                {r.usuario_email ?? (
                  <span className="text-ink-300">
                    {r.usuario_id ? r.usuario_id.slice(0, 8) : "—"}
                  </span>
                )}
              </Td>
              <Td className="text-sm">
                {(() => {
                  const id = tenantDoEvento(r.dados_novos);
                  if (!id) return <span className="text-ink-300">—</span>;
                  return (
                    <Link href={`/empresas/${id}`} className="text-brand-700 hover:underline">
                      {nomeDaEmpresa.get(id) ?? id.slice(0, 8)}
                    </Link>
                  );
                })()}
              </Td>
              <Td className="max-w-[22rem] text-xs text-ink-500">
                {resumo(r.dados_novos) || <span className="text-ink-300">—</span>}
              </Td>
              <Td className="text-xs text-ink-300">{r.ip ?? "—"}</Td>
            </tr>
          ))}
        </Tabela>

        <p className="mt-3 text-xs text-ink-300">
          Só eventos de <strong>plataforma</strong> (sem empresa). O que acontece dentro de
          uma empresa fica na auditoria dela — inclusive as ações feitas em modo suporte,
          que são gravadas nos dois lugares.
        </p>
      </Card>
    </Shell>
  );
}
