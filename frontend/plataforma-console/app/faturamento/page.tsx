import Link from "next/link";
import { operadorAtual } from "@/lib/sessao";
import { Shell } from "@/components/Shell";
import { BRL, dataCurta, platform } from "@/lib/platform";
import { Card, Erro, Tabela, Td, TituloPagina } from "@/components/ui";
import { PlatformApiError } from "@/lib/api";
import { AcoesFatura, GerarFaturas } from "./acoes";

export const dynamic = "force-dynamic";

export default async function Faturamento() {
  const { session, me } = await operadorAtual();

  let faturas;
  try {
    faturas = await platform.faturasPendentes();
  } catch (e) {
    const err = e as PlatformApiError;
    return (
      <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
        <TituloPagina titulo="Faturamento" />
        <Erro>
          {err.status === 403
            ? "Sua conta não é operador de plataforma."
            : `Falha ao carregar faturas (${err.status}).`}
        </Erro>
      </Shell>
    );
  }

  const total = faturas.reduce((s, f) => s + f.fatura.valor, 0);
  const hoje = new Date().toISOString().slice(0, 10);
  const vencidas = faturas.filter((f) => f.fatura.vencimento < hoje).length;

  return (
    <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
      <TituloPagina
        titulo="Faturamento"
        descricao={`${faturas.length} fatura(s) em conferência · ${BRL.format(total)} · ${vencidas} vencida(s)`}
        acao={<GerarFaturas />}
      />

      <Card
        titulo="Em conferência"
        descricao="Billing manual assistido: a empresa informa o txid, você confere no extrato e confirma."
      >
        <Tabela
          cabecalho={["Empresa", "Competência", "Plano", "Valor", "Vencimento", "txid", ""]}
          vazio="Nenhuma fatura aguardando conferência."
        >
          {faturas.map((f) => (
            <tr key={f.fatura.id} className={f.fatura.vencimento < hoje ? "bg-amber-50/50" : ""}>
              <Td>
                <Link
                  href={`/empresas/${f.tenantId}`}
                  className="font-medium text-brand-700 hover:underline"
                >
                  {f.razaoSocial}
                </Link>
                <div className="text-xs text-ink-300">{f.slug}</div>
              </Td>
              <Td>{f.fatura.competencia}</Td>
              <Td>{f.fatura.planoNome}</Td>
              <Td>{BRL.format(f.fatura.valor)}</Td>
              <Td>{dataCurta(f.fatura.vencimento)}</Td>
              <Td className="max-w-[12rem] truncate text-xs">
                {f.fatura.txidInformado ?? <span className="text-ink-300">—</span>}
              </Td>
              <Td>
                <AcoesFatura tenantId={f.tenantId} faturaId={f.fatura.id} />
              </Td>
            </tr>
          ))}
        </Tabela>
      </Card>
    </Shell>
  );
}
