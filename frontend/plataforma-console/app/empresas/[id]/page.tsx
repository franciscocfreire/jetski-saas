import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { operadorAtual } from "@/lib/sessao";
import { Shell } from "@/components/Shell";
import { BRL, competenciaAtual, dataCurta, platform } from "@/lib/platform";
import {
  Aviso,
  Badge,
  Card,
  Erro,
  StatusEmpresa,
  Tabela,
  Td,
  Vazio,
} from "@/components/ui";
import { PlatformApiError } from "@/lib/api";
import { AcoesStatus, AcoesEmissora, TrocarPlano, LancarCreditos } from "./acoes";
import { ZonaDePerigo } from "./perigo";
import { EntrarNaEmpresa } from "./suporte";

export const dynamic = "force-dynamic";

/**
 * Detalhe da empresa em seções (não em sub-rotas): a API de plataforma não tem
 * endpoint por empresa — tudo vem de listas globais que filtramos aqui. Uma
 * sub-rota por aba refaria as mesmas listas a cada troca.
 */
export default async function Empresa({ params }: { params: Promise<{ id: string }> }) {
  const { session, me } = await operadorAtual();
  const { id } = await params;

  let dados;
  try {
    const [tenants, planos, saldos, faturas, emissoes, exports] = await Promise.all([
      platform.tenants(),
      platform.planos(),
      platform.saldos(),
      platform.faturasPendentes(),
      platform.emissoes(competenciaAtual()),
      platform.exports(id).catch(() => []),
    ]);
    dados = { tenants, planos, saldos, faturas, emissoes, exports };
  } catch (e) {
    const err = e as PlatformApiError;
    return (
      <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
        <Erro>
          {err.status === 403
            ? "Sua conta não é operador de plataforma."
            : `Falha ao carregar a empresa (${err.status}).`}
        </Erro>
      </Shell>
    );
  }

  const empresa = dados.tenants.find((t) => t.id === id);
  if (!empresa) notFound();

  const saldo = dados.saldos.find((s) => s.tenantId === id);
  const faturasDaEmpresa = dados.faturas.filter((f) => f.tenantId === id);
  const emissao = dados.emissoes.find((e) => e.tenantId === id);
  // O handoff é uma navegação para OUTRO subdomínio (app.*): a URL vem do ambiente,
  // não do host atual — o console vive em admin.*.
  const backofficeUrl = process.env.BACKOFFICE_URL ?? "http://localhost:3001";

  return (
    <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
      <Link
        href="/empresas"
        className="mb-4 inline-flex items-center gap-1 text-sm text-ink-500 hover:text-brand-700"
      >
        <ArrowLeft className="h-4 w-4" /> Empresas
      </Link>

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <h1 className="font-display text-2xl text-ink-900">{empresa.razaoSocial}</h1>
        <StatusEmpresa status={empresa.status} />
        <span className="text-sm text-ink-300">{empresa.slug}</span>
      </div>

      {empresa.exclusaoAgendadaEm && (
        <div className="mb-6">
          <Aviso>
            Exclusão agendada: expurgo em <strong>{dataCurta(empresa.exclusaoAgendadaEm)}</strong>.
            Enquanto isso a empresa segue suspensa e o cancelamento ainda é possível.
          </Aviso>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <Card titulo="Visão geral">
          <dl className="grid grid-cols-2 gap-y-3 text-sm">
            <Campo rotulo="Plano" valor={empresa.plano ?? "—"} />
            <Campo rotulo="Vigência" valor={dataCurta(empresa.assinaturaFim)} />
            <Campo
              rotulo="Créditos"
              valor={saldo ? String(saldo.saldo) : "—"}
            />
            <Campo
              rotulo="Emissões no mês"
              valor={emissao ? String(emissao.total) : "0"}
            />
          </dl>
          <div className="mt-5 space-y-3 border-t border-slate-100 pt-4">
            <EntrarNaEmpresa
              tenantId={empresa.id}
              razaoSocial={empresa.razaoSocial}
              backofficeUrl={backofficeUrl}
            />
            <AcoesStatus tenantId={empresa.id} status={empresa.status} />
          </div>
        </Card>

        <Card
          titulo="Plano e módulos"
          descricao="Controle de oferta: módulos vêm do plano contratado."
        >
          <TrocarPlano
            tenantId={empresa.id}
            planoAtual={empresa.plano ?? null}
            planos={dados.planos}
          />
          <div className="mt-4">
            <div className="text-xs uppercase tracking-wide text-ink-300">Módulos ativos</div>
            <div className="mt-2 flex flex-wrap gap-1.5">
              {empresa.modulos === null || empresa.modulos === undefined ? (
                <Badge tom="marca">todos (plano sem restrição)</Badge>
              ) : empresa.modulos.length === 0 ? (
                <span className="text-sm text-ink-300">nenhum</span>
              ) : (
                empresa.modulos.map((m) => <Badge key={m}>{m}</Badge>)
              )}
            </div>
          </div>
        </Card>

        <Card
          titulo="Emissão à Marinha (EAMA)"
          descricao="Portão cadastral: só habilita com capitania e registro declarados pela empresa."
        >
          <dl className="grid grid-cols-2 gap-y-3 text-sm">
            <Campo rotulo="Registro EAMA" valor={empresa.eamaRegistro ?? "não declarado"} />
            <Campo
              rotulo="Situação"
              valor={empresa.emissoraHabilitada ? "habilitada" : "não habilitada"}
            />
          </dl>
          <div className="mt-5 border-t border-slate-100 pt-4">
            <AcoesEmissora
              tenantId={empresa.id}
              habilitada={Boolean(empresa.emissoraHabilitada)}
            />
          </div>
        </Card>

        <Card
          titulo="Créditos de emissão"
          descricao="Ajuste manual entra no ledger append-only, com motivo, e fica auditado."
        >
          <div className="font-display text-3xl text-brand-800">{saldo?.saldo ?? 0}</div>
          <div className="mt-4">
            <LancarCreditos tenantId={empresa.id} />
          </div>
        </Card>

        <Card titulo="Faturas em conferência">
          <Tabela
            cabecalho={["Competência", "Plano", "Valor", "Vencimento"]}
            vazio="Nenhuma fatura aguardando conferência."
          >
            {faturasDaEmpresa.map((f) => (
              <tr key={f.fatura.id}>
                <Td>{f.fatura.competencia}</Td>
                <Td>{f.fatura.planoNome}</Td>
                <Td>{BRL.format(f.fatura.valor)}</Td>
                <Td>{dataCurta(f.fatura.vencimento)}</Td>
              </tr>
            ))}
          </Tabela>
          {faturasDaEmpresa.length > 0 && (
            <p className="mt-3 text-xs text-ink-300">
              A conferência (confirmar/cancelar) fica em{" "}
              <Link href="/faturamento" className="text-brand-700 hover:underline">
                Faturamento
              </Link>
              , onde a fila global é tratada de uma vez.
            </p>
          )}
        </Card>

        <Card titulo={`Emissões — ${competenciaAtual()}`}>
          {emissao ? (
            <dl className="grid grid-cols-4 gap-y-3 text-sm">
              <Campo rotulo="Documentos" valor={String(emissao.documento)} />
              <Campo rotulo="GRU" valor={String(emissao.gru)} />
              <Campo rotulo="Prévias" valor={String(emissao.previa)} />
              <Campo rotulo="Total" valor={String(emissao.total)} />
            </dl>
          ) : (
            <Vazio>Sem emissões nesta competência.</Vazio>
          )}
        </Card>
      </div>

      <div className="mt-6">
        <ZonaDePerigo
          tenantId={empresa.id}
          slug={empresa.slug}
          exclusaoAgendadaEm={empresa.exclusaoAgendadaEm ?? null}
          exports={dados.exports}
        />
      </div>
    </Shell>
  );
}

function Campo({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-ink-300">{rotulo}</dt>
      <dd className="mt-0.5 text-ink-900">{valor}</dd>
    </div>
  );
}
