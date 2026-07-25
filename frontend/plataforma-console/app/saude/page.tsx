import { operadorAtual } from "@/lib/sessao";
import { Shell } from "@/components/Shell";
import { platform } from "@/lib/platform";
import { Badge, Card, Erro, TituloPagina } from "@/components/ui";
import { PlatformApiError } from "@/lib/api";
import type { SaudePlataforma } from "@/lib/types";

export const dynamic = "force-dynamic";

/** Nomes técnicos do Actuator → o que a pessoa de plantão precisa entender. */
const APELIDO_INFRA: Record<string, string> = {
  db: "Banco (Postgres)",
  redis: "Cache (Redis)",
  diskSpace: "Disco",
  mail: "SMTP",
  ping: "Aplicação",
  keycloak: "Identidade (Keycloak)",
  opa: "Autorização (OPA)",
  livenessState: "Liveness",
  readinessState: "Readiness",
  ssl: "Certificados",
};

const ROTULO_OPERACAO: Record<string, string> = {
  readModel: "Read model do dashboard",
  emissao: "Emissão à Marinha",
  filas: "Filas de trabalho",
  suporte: "Sessões de suporte",
};

const ROTULO_CAMPO: Record<string, string> = {
  atualizado_em: "Última atualização",
  ultimo_dia: "Último dia consolidado",
  linhas: "Linhas",
  ultimo_documento: "Último documento",
  ultima_gru: "Última GRU",
  ultimos_7_dias: "Emissões (7 dias)",
  empresas_aguardando_aprovacao: "Empresas aguardando aprovação",
  compras_de_credito_pendentes: "Compras de crédito pendentes",
  faturas_em_conferencia: "Faturas em conferência",
  sessoes_ativas: "Sessões ativas agora",
  ultima_sessao: "Última sessão",
};

/** Contadores de fila só importam quando são maiores que zero. */
const FILA = new Set([
  "empresas_aguardando_aprovacao",
  "compras_de_credito_pendentes",
  "faturas_em_conferencia",
  "sessoes_ativas",
]);

function formatar(valor: unknown): string {
  if (valor === null || valor === undefined) return "—";
  if (typeof valor !== "string") return String(valor);

  // Data pura (o dia consolidado do read model) NÃO passa por fuso: `new Date("2026-07-25")`
  // é meia-noite UTC e, convertida para São Paulo, volta um dia — o painel mostraria o job
  // sempre um dia atrasado.
  const soData = valor.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (soData) return `${soData[3]}/${soData[2]}/${soData[1]}`;

  if (/^\d{4}-\d{2}-\d{2}T/.test(valor)) {
    const d = new Date(valor);
    if (!Number.isNaN(d.getTime())) {
      return d.toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo" });
    }
  }
  return valor;
}

export default async function Saude() {
  const { session, me } = await operadorAtual();
  const grafana = process.env.GRAFANA_URL ?? "http://localhost:3000";

  let saude: SaudePlataforma;
  try {
    saude = await platform.saude();
  } catch (e) {
    const err = e as PlatformApiError;
    return (
      <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
        <TituloPagina titulo="Saúde" />
        <Erro>
          {err.status === 403
            ? "Sua conta não é operador de plataforma."
            : `Falha ao consultar a saúde (${err.status}).`}
        </Erro>
      </Shell>
    );
  }

  const infra = Object.entries(saude.infra ?? {});
  const problemas = infra.filter(([, s]) => s !== "UP");

  return (
    <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
      <TituloPagina
        titulo="Saúde"
        descricao="Infraestrutura e sinais de operação — o que quebra ruidoso e o que para em silêncio."
        acao={
          <Badge tom={saude.statusGeral === "UP" ? "ativo" : "perigo"}>
            {saude.statusGeral}
          </Badge>
        }
      />

      {problemas.length > 0 && (
        <div className="mb-6">
          <Erro>
            {problemas.map(([k]) => APELIDO_INFRA[k] ?? k).join(", ")} fora do ar.
          </Erro>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <Card
          titulo="Infraestrutura"
          descricao="Health checks do backend — a mesma fonte do /actuator/health, que não é exposto no edge."
        >
          <ul className="divide-y divide-slate-100">
            {infra.length === 0 && (
              <li className="py-6 text-center text-sm text-ink-300">
                Sem componentes reportados.
              </li>
            )}
            {infra.map(([nome, status]) => (
              <li key={nome} className="flex items-center justify-between py-2.5">
                <span className="text-sm">{APELIDO_INFRA[nome] ?? nome}</span>
                <Badge tom={status === "UP" ? "ativo" : status === "UNKNOWN" ? "neutro" : "perigo"}>
                  {status}
                </Badge>
              </li>
            ))}
          </ul>
        </Card>

        <Card
          titulo="Operação"
          descricao="O que envelhece sem gerar erro: job que não rodou, emissão travada, fila crescendo."
        >
          <div className="space-y-5">
            {Object.entries(saude.operacao ?? {}).map(([bloco, dados]) => (
              <BlocoOperacao key={bloco} nome={bloco} dados={dados} />
            ))}
          </div>
        </Card>
      </div>

      <Card
        className="mt-6"
        titulo="Observabilidade"
        descricao="Séries temporais, logs e alertas ficam no Grafana — esta tela é o retrato de agora."
      >
        <div className="flex flex-wrap gap-2">
          <AtalhoGrafana url={grafana} caminho="/d/jetski-prod-health" rotulo="Saúde de produção" />
          <AtalhoGrafana url={grafana} caminho="/d/jetski-infra" rotulo="Infraestrutura" />
          <AtalhoGrafana url={grafana} caminho="/d/jetski-logs-erros" rotulo="Logs & erros" />
          <AtalhoGrafana url={grafana} caminho="/d/jetski-tenant" rotulo="Visão por empresa" />
          <AtalhoGrafana url={grafana} caminho="/d/jetski-usuarios-auth" rotulo="Usuários & autenticação" />
          <AtalhoGrafana url={grafana} caminho="/alerting/list" rotulo="Alertas" />
        </div>
      </Card>
    </Shell>
  );
}

function BlocoOperacao({
  nome,
  dados,
}: {
  nome: string;
  dados: Record<string, unknown>;
}) {
  const erro = typeof dados?.erro === "string" ? dados.erro : null;
  return (
    <div>
      <h3 className="text-sm font-medium text-ink-700">{ROTULO_OPERACAO[nome] ?? nome}</h3>
      {erro ? (
        <p className="mt-1 text-xs text-amber-700">Indicador indisponível: {erro}</p>
      ) : (
        <dl className="mt-1 space-y-1">
          {Object.entries(dados ?? {}).map(([campo, valor]) => {
            const destaque = FILA.has(campo) && Number(valor) > 0;
            return (
              <div key={campo} className="flex items-baseline justify-between gap-4 text-sm">
                <dt className="text-ink-500">{ROTULO_CAMPO[campo] ?? campo}</dt>
                <dd className={destaque ? "font-medium text-amber-700" : "text-ink-900"}>
                  {formatar(valor)}
                </dd>
              </div>
            );
          })}
        </dl>
      )}
    </div>
  );
}

function AtalhoGrafana({
  url,
  caminho,
  rotulo,
}: {
  url: string;
  caminho: string;
  rotulo: string;
}) {
  return (
    <a
      href={`${url}${caminho}`}
      target="_blank"
      rel="noopener noreferrer"
      className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-ink-700 transition hover:border-brand-400 hover:text-brand-700"
    >
      {rotulo} ↗
    </a>
  );
}
