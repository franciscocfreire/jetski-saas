import Link from "next/link";
import { operadorAtual } from "@/lib/sessao";
import { Shell } from "@/components/Shell";
import { BRL, dataCurta, platform } from "@/lib/platform";
import { Aviso, Card, Erro, Tabela, Td, TituloPagina } from "@/components/ui";
import { PlatformApiError } from "@/lib/api";
import type { DashboardPlataforma, TenantSummary } from "@/lib/types";

export const dynamic = "force-dynamic";

/**
 * Visão geral da plataforma (F4).
 *
 * Lê o read model (`plataforma_metrica_diaria`) com uma chamada — antes disso não havia
 * como consolidar sem varrer empresa a empresa a cada carregamento.
 */
export default async function Home() {
  const { session, me } = await operadorAtual();

  let dash: DashboardPlataforma | null = null;
  let tenants: TenantSummary[] = [];
  let erro: { status: number; mensagem: string } | null = null;

  try {
    [dash, tenants] = await Promise.all([platform.dashboard(30), platform.tenants()]);
  } catch (e) {
    const err = e as PlatformApiError;
    erro = { status: err.status ?? 0, mensagem: err.message ?? "Falha ao consultar" };
  }

  const porStatus = tenants.reduce<Record<string, number>>((acc, t) => {
    acc[t.status] = (acc[t.status] ?? 0) + 1;
    return acc;
  }, {});
  const t = dash?.totais ?? {};
  const n = (k: string) => Number(t[k] ?? 0);

  return (
    <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
      <TituloPagina
        titulo="Visão geral"
        descricao={
          dash?.atualizadoEm
            ? `Últimos ${dash.dias} dias · dados de ${dataCurta(dash.atualizadoEm)}`
            : "Console da plataforma"
        }
      />

      {erro?.status === 403 && (
        <Erro>Sua conta não é operador de plataforma.</Erro>
      )}
      {erro && erro.status !== 403 && (
        <Erro>Falha ao carregar ({erro.status}): {erro.mensagem}</Erro>
      )}

      {dash && !dash.atualizadoEm && (
        <div className="mb-6">
          <Aviso>
            O read model ainda não foi calculado — o job roda às 04:15. Os números abaixo
            ficam zerados até lá; dá para forçar com{" "}
            <code>POST /v1/platform/dashboard/recalcular</code>.
          </Aviso>
        </div>
      )}

      {dash && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Indicador titulo="Empresas" valor={String(tenants.length)}
              nota={Object.entries(porStatus).map(([s, q]) => `${q} ${s.toLowerCase()}`).join(" · ")} />
            <Indicador titulo="MRR" valor={BRL.format(n("mrr"))}
              nota="soma dos planos vigentes" />
            <Indicador titulo="Receita (30d)" valor={BRL.format(n("receita_bruta"))}
              nota={`${n("locacoes")} locações`} />
            <Indicador titulo="Em aberto" valor={BRL.format(n("valor_em_aberto"))}
              nota={`${n("faturas_abertas")} fatura(s)`}
              alerta={n("faturas_abertas") > 0} />
          </div>

          <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Indicador titulo="Emissões cobráveis" valor={String(n("emissoes_cobraveis"))}
              nota={`${n("emissoes_previa")} prévias (não cobráveis)`} />
            <Indicador titulo="Créditos consumidos" valor={String(n("creditos_consumidos"))} />
            <Indicador titulo="Reservas (30d)" valor={String(n("reservas"))}
              nota={`${n("no_shows")} no-show`} />
            <Indicador titulo="Receita comissionável"
              valor={BRL.format(n("receita_comissionavel"))}
              nota="sem combustível (RN04)" />
          </div>

          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <Card titulo="Top empresas por receita" descricao="Janela de 30 dias.">
              <Tabela
                cabecalho={["Empresa", "Locações", "Receita", "Emissões"]}
                vazio="Nenhum movimento na janela."
              >
                {dash.topEmpresas.map((e) => (
                  <tr key={e.id}>
                    <Td>
                      <Link href={`/empresas/${e.id}`} className="text-brand-700 hover:underline">
                        {e.razao_social}
                      </Link>
                      <div className="text-xs text-ink-300">{e.slug}</div>
                    </Td>
                    <Td>{e.locacoes}</Td>
                    <Td>{BRL.format(Number(e.receita_bruta))}</Td>
                    <Td>{e.emissoes}</Td>
                  </tr>
                ))}
              </Tabela>
            </Card>

            <Card titulo="Movimento por dia" descricao="Locações e receita da plataforma.">
              <SerieDiaria serie={dash.serie} />
            </Card>
          </div>
        </>
      )}
    </Shell>
  );
}

function Indicador({
  titulo,
  valor,
  nota,
  alerta,
}: {
  titulo: string;
  valor: string;
  nota?: string;
  alerta?: boolean;
}) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="text-xs uppercase tracking-wide text-ink-300">{titulo}</div>
      <div
        className={`mt-1 font-display text-2xl ${alerta ? "text-amber-700" : "text-brand-800"}`}
      >
        {valor}
      </div>
      {nota && <div className="mt-0.5 text-xs text-ink-500">{nota}</div>}
    </div>
  );
}

/**
 * Barras proporcionais em CSS puro — sem biblioteca de gráfico por enquanto: são 30
 * pontos e o objetivo é ver a forma da série, não explorar dados.
 */
function SerieDiaria({
  serie,
}: {
  serie: DashboardPlataforma["serie"];
}) {
  const comMovimento = serie.filter((d) => Number(d.receita_bruta) > 0 || d.locacoes > 0);
  if (comMovimento.length === 0) {
    return <p className="py-6 text-center text-sm text-ink-300">Sem movimento na janela.</p>;
  }
  const teto = Math.max(...serie.map((d) => Number(d.receita_bruta)), 1);

  return (
    <div className="flex h-40 items-end gap-1">
      {serie.map((d) => {
        const altura = Math.round((Number(d.receita_bruta) / teto) * 100);
        return (
          <div
            key={d.dia}
            className="flex-1 rounded-t bg-brand-500/70 transition hover:bg-brand-600"
            style={{ height: `${Math.max(altura, 2)}%` }}
            title={`${d.dia}: ${BRL.format(Number(d.receita_bruta))} · ${d.locacoes} locação(ões)`}
          />
        );
      })}
    </div>
  );
}
