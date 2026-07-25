import Link from "next/link";
import { operadorAtual } from "@/lib/sessao";
import { Shell } from "@/components/Shell";
import { competenciaAtual, platform } from "@/lib/platform";
import { Card, Erro, Tabela, Td, TituloPagina } from "@/components/ui";
import { PlatformApiError } from "@/lib/api";
import { SeletorCompetencia } from "./seletor";

export const dynamic = "force-dynamic";

/**
 * Metering por empresa na competência. Prévias não são cobráveis — entram como
 * sinal de acompanhamento e por isso ficam fora da coluna "cobrável".
 */
export default async function Emissoes({
  searchParams,
}: {
  searchParams: Promise<{ competencia?: string }>;
}) {
  const { session, me } = await operadorAtual();

  const { competencia } = await searchParams;
  const alvo = competencia ?? competenciaAtual();

  let emissoes;
  try {
    emissoes = await platform.emissoes(alvo);
  } catch (e) {
    const err = e as PlatformApiError;
    return (
      <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
        <TituloPagina titulo="Emissões" />
        <Erro>
          {err.status === 403
            ? "Sua conta não é operador de plataforma."
            : `Falha ao carregar o metering (${err.status}).`}
        </Erro>
      </Shell>
    );
  }

  const totais = emissoes.reduce(
    (acc, e) => ({
      documento: acc.documento + e.documento,
      gru: acc.gru + e.gru,
      previa: acc.previa + e.previa,
      total: acc.total + e.total,
    }),
    { documento: 0, gru: 0, previa: 0, total: 0 },
  );

  return (
    <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
      <TituloPagina
        titulo="Emissões"
        descricao={`${totais.total} cobráveis na competência · ${totais.previa} prévias (não cobráveis)`}
        acao={<SeletorCompetencia atual={alvo} />}
      />

      <Card titulo={`Por empresa — ${alvo}`}>
        <Tabela
          cabecalho={["Empresa", "Documentos", "GRU", "Prévias", "Cobrável"]}
          vazio="Nenhuma emissão nesta competência."
        >
          {[...emissoes]
            .sort((a, b) => b.total - a.total)
            .map((e) => (
              <tr key={e.tenantId}>
                <Td>
                  <Link
                    href={`/empresas/${e.tenantId}`}
                    className="text-brand-700 hover:underline"
                  >
                    {e.razaoSocial}
                  </Link>
                  <div className="text-xs text-ink-300">{e.slug}</div>
                </Td>
                <Td>{e.documento}</Td>
                <Td>{e.gru}</Td>
                <Td className="text-ink-500">{e.previa}</Td>
                <Td className="font-medium">{e.total}</Td>
              </tr>
            ))}
        </Tabela>
      </Card>
    </Shell>
  );
}
