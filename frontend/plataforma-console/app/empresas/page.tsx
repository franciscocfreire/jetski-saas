import Link from "next/link";
import { operadorAtual } from "@/lib/sessao";
import { Shell } from "@/components/Shell";
import { platform, dataCurta } from "@/lib/platform";
import { Card, Erro, StatusEmpresa, Tabela, Td, TituloPagina, Badge } from "@/components/ui";
import { PlatformApiError } from "@/lib/api";
import { FiltroEmpresas } from "./filtro";
import { ordenarPorRede } from "@/lib/rede";
import { ROTULO_TIPO, descreverForma } from "@/lib/condicao";
import type { TenantSummary } from "@/lib/types";

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
  searchParams: Promise<{ status?: string; q?: string; condicao?: string; excluidas?: string }>;
}) {
  const { session, me } = await operadorAtual();

  const { status: filtroStatus, q, condicao: filtroCondicao, excluidas } = await searchParams;
  const soComCondicao = filtroCondicao === "1";
  // Excluídas (tombstone) ficam fora por padrão: só aparecem pedindo explicitamente.
  const verExcluidas = excluidas === "1" || filtroStatus === "EXCLUIDO";

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

  const vivas = tenants.filter((t) => t.status !== "EXCLUIDO");
  const qtdExcluidas = tenants.length - vivas.length;

  const porStatus = vivas.reduce<Record<string, number>>((acc, t) => {
    acc[t.status] = (acc[t.status] ?? 0) + 1;
    return acc;
  }, {});

  const delegadasDe = (emissoraId: string) =>
    vivas.filter((t) => t.papelEmissao === "DELEGADA" && t.emissoraTenantId === emissoraId).length;

  const busca = (q ?? "").trim().toLowerCase();
  const filtradas = (verExcluidas ? tenants : vivas).map(semOperacao).filter((t) => {
    if (filtroStatus && t.status !== filtroStatus) return false;
    if (soComCondicao && !t.condicao) return false;
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
        descricao={`${vivas.length} no total · ${porStatus.PENDENTE_APROVACAO ?? 0} aguardando aprovação`}
      />

      <FiltroEmpresas
        porStatus={porStatus}
        statusAtual={filtroStatus}
        buscaAtual={q ?? ""}
        total={vivas.length}
        comCondicao={vivas.filter((t) => t.condicao).length}
        condicaoAtiva={soComCondicao}
        excluidas={qtdExcluidas}
        verExcluidas={verExcluidas}
      />

      <Card className="mt-4">
        <Tabela
          cabecalho={["Empresa", "Status", "Plano", "Vigência", "EAMA", "Alertas"]}
          vazio={busca || filtroStatus || soComCondicao ? "Nenhuma empresa com esse filtro." : "Nenhuma empresa."}
        >
          {/* Rede de emissão (§8.M): delegadas logo abaixo da EAMA emissora delas. */}
          {ordenarPorRede(filtradas).map(({ empresa: t, nivel }) => (
            <tr
              key={t.id}
              className={t.status === "EXCLUIDO" ? "opacity-60 hover:bg-slate-50" : "hover:bg-slate-50"}
              data-papel={t.papelEmissao ?? "NENHUM"}
            >
              <Td>
                <div className={nivel > 0 ? "flex items-start gap-1.5 pl-5" : undefined}>
                  {nivel > 0 && <span className="text-ink-300">↳</span>}
                  <div>
                    <Link
                      href={`/empresas/${t.id}`}
                      className="font-medium text-brand-700 hover:underline"
                    >
                      {t.razaoSocial}
                    </Link>
                    <div className="text-xs text-ink-300">{t.slug}</div>
                  </div>
                </div>
              </Td>
              <Td>
                <StatusEmpresa status={t.status} />
              </Td>
              <Td>
                {t.plano ?? <span className="text-ink-300">—</span>}
                {/* Condição comercial vigente (V076): quem não paga o plano cheio salta aos olhos. */}
                {t.condicao && (
                  <div className="mt-0.5" data-testid="console-empresa-condicao">
                    <Badge tom="marca">
                      {ROTULO_TIPO[t.condicao.tipo]} · {descreverForma(t.condicao)}
                    </Badge>
                    <div className="text-xs text-ink-300">
                      {t.condicao.fim ? `até ${dataCurta(t.condicao.fim)}` : "sem prazo"}
                    </div>
                  </div>
                )}
              </Td>
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
                {t.papelEmissao === "DELEGADA" ? (
                  <div>
                    <Badge tom="marca">delegada</Badge>
                    {nivel === 0 && t.emissoraNome && (
                      <div className="mt-0.5 text-xs text-ink-300">de {t.emissoraNome}</div>
                    )}
                  </div>
                ) : t.emissoraHabilitada ? (
                  <div>
                    <Badge tom="ativo">emissora</Badge>
                    {delegadasDe(t.id) > 0 && (
                      <div className="mt-0.5 text-xs text-ink-300">
                        {delegadasDe(t.id)} {delegadasDe(t.id) === 1 ? "delegada" : "delegadas"}
                      </div>
                    )}
                  </div>
                ) : t.eamaRegistro ? (
                  <Badge tom="atencao">declarada</Badge>
                ) : (
                  <span className="text-ink-300">—</span>
                )}
              </Td>
              <Td>
                {/* Emissora sem SMTP próprio: o ofício à Capitania não sai (só vai pelo e-mail da EAMA). */}
                {t.exclusaoAgendadaEm || (t.papelEmissao === "EMISSORA" && !t.smtpCompleto) ? (
                  <div className="flex flex-wrap gap-1">
                    {t.exclusaoAgendadaEm && (
                      <Badge tom="perigo">expurgo em {dataCurta(t.exclusaoAgendadaEm)}</Badge>
                    )}
                    {t.papelEmissao === "EMISSORA" && !t.smtpCompleto && (
                      <span data-testid="console-alerta-sem-smtp">
                        <Badge tom="atencao">SMTP não cadastrado</Badge>
                      </span>
                    )}
                  </div>
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

/**
 * Tombstone não tem operação: o cadastro que sobrou (emissora_habilitada de antes do
 * expurgo, SMTP zerado) viraria "emissora" e "SMTP não cadastrado" — alerta que ninguém
 * resolve. Na lista, a empresa excluída mostra só nome, slug e status.
 */
function semOperacao(t: TenantSummary): TenantSummary {
  if (t.status !== "EXCLUIDO") return t;
  return {
    ...t,
    plano: null,
    assinaturaFim: null,
    exclusaoAgendadaEm: null,
    condicao: null,
    papelEmissao: null,
    emissoraHabilitada: false,
    eamaRegistro: null,
    emissoraTenantId: null,
    smtpCompleto: true,
  };
}
