import Link from "next/link";
import { operadorAtual } from "@/lib/sessao";
import { Shell } from "@/components/Shell";
import { BRL, dataCurta, platform } from "@/lib/platform";
import { Badge, Card, Erro, Tabela, Td, TituloPagina } from "@/components/ui";
import { PlatformApiError } from "@/lib/api";
import { AprovarCompra, PrecoCredito } from "./acoes";

export const dynamic = "force-dynamic";

export default async function Creditos() {
  const { session, me } = await operadorAtual();

  let dados;
  try {
    const [compras, saldos, preco] = await Promise.all([
      platform.comprasPendentes(),
      platform.saldos(),
      platform.precoCredito(),
    ]);
    dados = { compras, saldos, preco };
  } catch (e) {
    const err = e as PlatformApiError;
    return (
      <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
        <TituloPagina titulo="Créditos" />
        <Erro>
          {err.status === 403
            ? "Sua conta não é operador de plataforma."
            : `Falha ao carregar créditos (${err.status}).`}
        </Erro>
      </Shell>
    );
  }

  const semSaldo = dados.saldos.filter((s) => s.saldo <= 0).length;

  return (
    <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
      <TituloPagina
        titulo="Créditos"
        descricao={`${dados.compras.length} compra(s) aguardando conferência · ${semSaldo} empresa(s) sem saldo`}
      />

      <div className="space-y-6">
        <Card
          titulo="Compras aguardando conferência"
          descricao="Confira o PIX no extrato antes de aprovar — a aprovação credita no ledger na hora."
        >
          <Tabela
            cabecalho={["Empresa", "Qtd", "Valor", "Comprovante", "Solicitada", ""]}
            vazio="Nenhuma compra pendente."
          >
            {dados.compras.map((c) => (
              <tr key={c.id}>
                <Td>
                  <Link
                    href={`/empresas/${c.tenantId}`}
                    className="font-medium text-brand-700 hover:underline"
                  >
                    {c.razaoSocial}
                  </Link>
                  <div className="text-xs text-ink-300">{c.slug}</div>
                </Td>
                <Td>{c.quantidade}</Td>
                <Td>
                  {c.valorPago != null ? BRL.format(c.valorPago) : "—"}
                  {c.pixTxid && (
                    <div className="text-xs text-ink-300">txid {c.pixTxid}</div>
                  )}
                </Td>
                <Td>
                  {c.temComprovante ? (
                    <a
                      href={`/api/download?tenantId=${c.tenantId}&compraId=${c.id}`}
                      target="_blank"
                      rel="noreferrer"
                      className="text-brand-700 hover:underline"
                    >
                      abrir
                    </a>
                  ) : (
                    <Badge tom="atencao">sem anexo</Badge>
                  )}
                </Td>
                <Td>{dataCurta(c.createdAt)}</Td>
                <Td>
                  <AprovarCompra tenantId={c.tenantId} compraId={c.id} />
                </Td>
              </tr>
            ))}
          </Tabela>
        </Card>

        <Card
          titulo="Preço do crédito"
          descricao="Valor unitário usado no PIX de compra e no cálculo do valor devido."
        >
          <PrecoCredito atual={dados.preco.precoUnitario} />
        </Card>

        <Card titulo="Saldos por empresa">
          <Tabela cabecalho={["Empresa", "Saldo"]} vazio="Nenhuma empresa.">
            {[...dados.saldos]
              .sort((a, b) => a.saldo - b.saldo)
              .map((s) => (
                <tr key={s.tenantId}>
                  <Td>
                    <Link
                      href={`/empresas/${s.tenantId}`}
                      className="text-brand-700 hover:underline"
                    >
                      {s.razaoSocial}
                    </Link>
                    <div className="text-xs text-ink-300">{s.slug}</div>
                  </Td>
                  <Td>
                    {s.saldo <= 0 ? (
                      <Badge tom="perigo">{s.saldo}</Badge>
                    ) : s.saldo < 10 ? (
                      <Badge tom="atencao">{s.saldo}</Badge>
                    ) : (
                      s.saldo
                    )}
                  </Td>
                </tr>
              ))}
          </Tabela>
        </Card>
      </div>
    </Shell>
  );
}
