import Link from "next/link";
import { operadorAtual } from "@/lib/sessao";
import { Shell } from "@/components/Shell";
import { platform, dataCurta } from "@/lib/platform";
import { Card, Erro, StatusEmpresa, Tabela, Td, TituloPagina, Badge } from "@/components/ui";
import { PlatformApiError } from "@/lib/api";
import { FiltroEmpresas } from "./filtro";

export const dynamic = "force-dynamic";

/**
 * Lista de empresas — a tabela que hoje mora dentro da página de 775 linhas do
 * backoffice, agora com filtro por status e busca. As ações por empresa ficam
 * no detalhe: numa lista longa, botão de suspender ao lado do de aprovar é
 * convite a erro.
 */
export default async function Empresas({
  searchParams,
}: {
  searchParams: Promise<{ status?: string; q?: string }>;
}) {
  const { session, me } = await operadorAtual();

  const { status: filtroStatus, q } = await searchParams;

  let tenants;
  try {
    tenants = await platform.tenants();
  } catch (e) {
    const err = e as PlatformApiError;
    return (
      <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
        <TituloPagina titulo="Empresas" />
        <Erro>
          {err.status === 403
            ? "Sua conta não é operador de plataforma."
            : `Falha ao listar empresas (${err.status}).`}
        </Erro>
      </Shell>
    );
  }

  const porStatus = tenants.reduce<Record<string, number>>((acc, t) => {
    acc[t.status] = (acc[t.status] ?? 0) + 1;
    return acc;
  }, {});

  const busca = (q ?? "").trim().toLowerCase();
  const filtradas = tenants.filter((t) => {
    if (filtroStatus && t.status !== filtroStatus) return false;
    if (!busca) return true;
    return (
      t.slug.toLowerCase().includes(busca) ||
      t.razaoSocial.toLowerCase().includes(busca)
    );
  });

  return (
    <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
      <TituloPagina
        titulo="Empresas"
        descricao={`${tenants.length} no total · ${porStatus.PENDENTE_APROVACAO ?? 0} aguardando aprovação`}
      />

      <FiltroEmpresas
        porStatus={porStatus}
        statusAtual={filtroStatus}
        buscaAtual={q ?? ""}
        total={tenants.length}
      />

      <Card className="mt-4">
        <Tabela
          cabecalho={["Empresa", "Status", "Plano", "Vigência", "EAMA", "Alertas"]}
          vazio={busca || filtroStatus ? "Nenhuma empresa com esse filtro." : "Nenhuma empresa."}
        >
          {filtradas.map((t) => (
            <tr key={t.id} className="hover:bg-slate-50">
              <Td>
                <Link
                  href={`/empresas/${t.id}`}
                  className="font-medium text-brand-700 hover:underline"
                >
                  {t.razaoSocial}
                </Link>
                <div className="text-xs text-ink-300">{t.slug}</div>
              </Td>
              <Td>
                <StatusEmpresa status={t.status} />
              </Td>
              <Td>{t.plano ?? <span className="text-ink-300">—</span>}</Td>
              {/* Plano pago não tem dt_fim por design (a inadimplência da
                  fatura é quem suspende) — "—" parecia dado faltando. */}
              <Td>
                {t.assinaturaFim ? (
                  dataCurta(t.assinaturaFim)
                ) : t.plano ? (
                  <span className="text-ink-300">sem vencimento</span>
                ) : (
                  <span className="text-ink-300">—</span>
                )}
              </Td>
              <Td>
                {t.emissoraHabilitada ? (
                  <Badge tom="ativo">habilitada</Badge>
                ) : t.eamaRegistro ? (
                  <Badge tom="atencao">declarada</Badge>
                ) : (
                  <span className="text-ink-300">—</span>
                )}
              </Td>
              <Td>
                {t.exclusaoAgendadaEm ? (
                  <Badge tom="perigo">
                    expurgo em {dataCurta(t.exclusaoAgendadaEm)}
                  </Badge>
                ) : (
                  <span className="text-ink-300">—</span>
                )}
              </Td>
            </tr>
          ))}
        </Tabela>
      </Card>
    </Shell>
  );
}
