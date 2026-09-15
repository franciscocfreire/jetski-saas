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
import {
  AcoesStatus,
  AcoesEmissora,
  TesteSmtp,
  TrocarPlano,
  LancarCreditos,
  LimiteDeUsuarios,
} from "./acoes";
import { CadastroDaEmpresa } from "./cadastro";
import { CondicaoComercialDaEmpresa } from "./condicao";
import { ROTULO_TIPO, descreverForma } from "@/lib/condicao";
import { ArquivamentoDaEmpresa, ZonaDePerigo } from "./perigo";
import { EntrarNaEmpresa } from "./suporte";
import { UsuariosDaEmpresa } from "./usuarios";

export const dynamic = "force-dynamic";

/**
 * Detalhe da empresa em seções (não em sub-rotas): a maior parte vem de listas
 * globais que filtramos aqui (exports e usuários são por empresa). Uma sub-rota
 * por aba refaria as mesmas listas a cada troca.
 */
export default async function Empresa({ params }: { params: Promise<{ id: string }> }) {
  const { session, me } = await operadorAtual();
  const { id } = await params;

  let dados;
  try {
    const [
      tenants,
      planos,
      saldos,
      faturas,
      emissoes,
      exports,
      membros,
      solicitacoes,
      limite,
      cadastro,
      convites,
      condicoes,
    ] = await Promise.all([
        platform.tenants(),
        platform.planos(),
        platform.saldos(),
        platform.faturasPendentes(),
        platform.emissoes(competenciaAtual()),
        platform.exports(id).catch(() => []),
        // null = falhou: a seção mostra o erro sem derrubar a página inteira
        platform.membros(id).catch(() => null),
        platform.solicitacoes(id).catch(() => null),
        platform.limiteUsuarios(id).catch(() => null),
        platform.cadastro(id).catch(() => null),
        platform.convites(id).catch(() => null),
        platform.condicoes(id).catch(() => null),
      ]);
    dados = {
      tenants,
      planos,
      saldos,
      faturas,
      emissoes,
      exports,
      membros,
      solicitacoes,
      limite,
      cadastro,
      convites,
      condicoes,
    };
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
  // Tombstone: dados expurgados — a página vira consulta (histórico e arquivamento).
  // O backend recusa as escritas; esconder evita botão que sempre dá erro.
  const excluida = empresa.status === "EXCLUIDO";

  // Rede de emissão (§8.M): quem são as delegadas desta EAMA, e o rótulo do papel.
  const delegadas = dados.tenants.filter(
    (t) => t.papelEmissao === "DELEGADA" && t.emissoraTenantId === id && t.status !== "EXCLUIDO",
  );
  const rotuloPapel =
    empresa.papelEmissao === "DELEGADA"
      ? "delegada"
      : empresa.emissoraHabilitada
        ? "EAMA emissora (habilitada)"
        : empresa.eamaRegistro
          ? "registro EAMA em validação"
          : "não é EAMA";

  // Editar cadastro e gerir usuários: só ADMIN e SUPORTE. FINANCEIRO/LEITURA só visualizam
  // (o backend nega de qualquer forma — esconder evita botão que sempre dá 403).
  const podeEditar = me.papeis.some((p) => p === "PLATFORM_ADMIN" || p === "PLATFORM_SUPORTE");
  // Condição comercial é alçada financeira (mesma de trocar plano) — ver platform.rego.
  const podeFinanceiro = me.papeis.some((p) => p === "PLATFORM_ADMIN" || p === "PLATFORM_FINANCEIRO");
  const precoPlano = dados.planos.find((p) => p.nome === empresa.plano)?.precoMensal ?? null;
  const vigente = dados.condicoes?.find((c) => c.situacao === "VIGENTE");
  const condicaoVigente = vigente
    ? { id: vigente.id, rotulo: `${ROTULO_TIPO[vigente.tipo]} ${descreverForma(vigente)}` }
    : null;

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

      {excluida && (
        <div className="mb-6" data-testid="console-empresa-excluida">
          <Aviso>
            Empresa excluída
            {empresa.excluidoEm && (
              <>
                {" "}em <strong>{dataCurta(empresa.excluidoEm)}</strong>
              </>
            )}
            . Dados e arquivos foram expurgados e o endereço foi liberado. Ficam só o histórico
            de créditos, faturas, emissões e auditoria, e o arquivamento abaixo.
          </Aviso>
        </div>
      )}

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
            <Campo
              rotulo="Vigência"
              valor={
                empresa.assinaturaFim
                  ? dataCurta(empresa.assinaturaFim)
                  : empresa.plano
                    ? "sem vencimento"
                    : "—"
              }
            />
            <Campo
              rotulo="Créditos"
              valor={saldo ? String(saldo.saldo) : "—"}
            />
            <Campo
              rotulo="Emissões no mês"
              valor={emissao ? String(emissao.total) : "0"}
            />
          </dl>
          {!excluida && (
            <div className="mt-5 space-y-3 border-t border-slate-100 pt-4">
              <EntrarNaEmpresa
                tenantId={empresa.id}
                razaoSocial={empresa.razaoSocial}
                backofficeUrl={backofficeUrl}
              />
              <AcoesStatus tenantId={empresa.id} status={empresa.status} />
            </div>
          )}
        </Card>

        {!excluida && (
        <>
        <Card
          titulo="Cadastro"
          descricao={
            podeEditar
              ? "Dados da empresa. Toda alteração exige motivo e fica auditada."
              : "Dados da empresa."
          }
        >
          {dados.cadastro ? (
            <CadastroDaEmpresa
              tenantId={empresa.id}
              cadastro={dados.cadastro}
              podeEditar={podeEditar}
            />
          ) : (
            <Erro>Não foi possível carregar o cadastro da empresa.</Erro>
          )}
        </Card>

        <Card
          titulo="Plano e módulos"
          descricao="Controle de oferta: módulos e limites vêm do plano contratado; o limite de usuários pode ser personalizado por empresa."
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
          <div className="mt-5 border-t border-slate-100 pt-4">
            <div className="text-xs uppercase tracking-wide text-ink-300">Limite de usuários</div>
            <div className="mt-2">
              {dados.limite ? (
                <LimiteDeUsuarios tenantId={empresa.id} limite={dados.limite} />
              ) : (
                <span className="text-sm text-red-700">Não foi possível carregar o limite.</span>
              )}
            </div>
          </div>
        </Card>

        <Card
          titulo="Condição comercial"
          descricao="Isenção ou desconto da mensalidade, com vigência e motivo. Não afeta créditos de emissão."
        >
          {dados.condicoes ? (
            <CondicaoComercialDaEmpresa
              tenantId={empresa.id}
              condicoes={dados.condicoes}
              precoPlano={precoPlano !== null ? Number(precoPlano) : null}
              podeEditar={podeFinanceiro}
            />
          ) : (
            <Erro>Não foi possível carregar a condição comercial.</Erro>
          )}
        </Card>

        <Card
          titulo="Emissão à Marinha (EAMA)"
          descricao="Uma empresa é EAMA emissora (habilitada com capitania e registro), delegada de uma EAMA parceira, ou não é EAMA."
        >
          <dl className="grid grid-cols-2 gap-y-3 text-sm">
            <Campo rotulo="Papel" valor={rotuloPapel} />
            <Campo rotulo="Registro EAMA" valor={empresa.eamaRegistro ?? "não declarado"} />
          </dl>
          <div className="mt-5 border-t border-slate-100 pt-4 text-sm" data-testid="console-empresa-smtp">
            <div className="text-xs uppercase tracking-wide text-ink-300">Servidor de e-mail (SMTP)</div>
            {empresa.smtpCompleto ? (
              <>
                <div className="mt-1 flex flex-wrap items-center gap-2">
                  <Badge tom="ativo">configurado</Badge>
                  {empresa.smtpRemetente && <span className="text-ink-700">{empresa.smtpRemetente}</span>}
                </div>
                {empresa.smtpUsuario &&
                  empresa.smtpRemetente &&
                  empresa.smtpUsuario.toLowerCase() !== empresa.smtpRemetente.toLowerCase() && (
                    <p className="mt-1 text-xs text-amber-700" data-testid="console-smtp-remetente-diverge">
                      Autentica como {empresa.smtpUsuario}. O Gmail troca o remetente por essa conta, a
                      menos que {empresa.smtpRemetente} esteja cadastrado nela como &quot;Enviar e-mail como&quot;.
                    </p>
                  )}
              </>
            ) : (
              <div className="mt-1 flex flex-wrap items-center gap-2">
                <Badge tom="atencao">SMTP não cadastrado</Badge>
                {empresa.papelEmissao === "EMISSORA" && (
                  <span className="text-xs text-ink-500">
                    Sem host, usuário e senha, o ofício à Capitania não é enviado.
                  </span>
                )}
              </div>
            )}
            {empresa.smtpCompleto && podeEditar && (
              <div className="mt-3">
                <TesteSmtp tenantId={empresa.id} />
              </div>
            )}
          </div>
          {empresa.papelEmissao === "DELEGADA" ? (
            <div className="mt-5 border-t border-slate-100 pt-4 text-sm" data-testid="console-empresa-delegada">
              <div className="flex flex-wrap items-center gap-2">
                <span>Opera como delegada de</span>
                {empresa.emissoraTenantId ? (
                  <Link
                    href={`/empresas/${empresa.emissoraTenantId}`}
                    className="font-medium text-brand-700 hover:underline"
                  >
                    {empresa.emissoraNome ?? "EAMA parceira"}
                  </Link>
                ) : (
                  <span className="font-medium">{empresa.emissoraNome ?? "EAMA parceira"}</span>
                )}
                {empresa.vinculoStatus === "BLOQUEADO" ? (
                  <Badge tom="perigo">parceria bloqueada pela EAMA</Badge>
                ) : (
                  <Badge tom="ativo">parceria ativa</Badge>
                )}
              </div>
              <p className="mt-2 text-xs text-ink-300">
                Os documentos saem em nome da EAMA e a capitania é a dela. Uma empresa é emissora ou
                delegada: habilitar como emissora só depois que a parceria for revogada.
              </p>
            </div>
          ) : (
            <>
              {empresa.papelEmissao === "EMISSORA" && delegadas.length > 0 && (
                <div className="mt-5 border-t border-slate-100 pt-4" data-testid="console-empresa-delegadas">
                  <div className="text-xs uppercase tracking-wide text-ink-300">
                    Delegadas ({delegadas.length})
                  </div>
                  <ul className="mt-2 space-y-1.5 border-l border-dashed border-slate-300 pl-4 text-sm">
                    {delegadas.map((d) => (
                      <li key={d.id} className="flex flex-wrap items-center gap-2">
                        <span className="text-ink-300">↳</span>
                        <Link href={`/empresas/${d.id}`} className="text-brand-700 hover:underline">
                          {d.razaoSocial}
                        </Link>
                        {d.vinculoStatus === "BLOQUEADO" && <Badge tom="perigo">bloqueada</Badge>}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              <div className="mt-5 border-t border-slate-100 pt-4">
                <AcoesEmissora
                  tenantId={empresa.id}
                  habilitada={Boolean(empresa.emissoraHabilitada)}
                />
              </div>
            </>
          )}
        </Card>
        </>
        )}

        <Card
          titulo="Créditos de emissão"
          descricao="Ajuste corrige, cortesia concede de graça. Tudo entra no ledger append-only, com motivo, e fica auditado."
        >
          <div className="font-display text-3xl text-brand-800">{saldo?.saldo ?? 0}</div>
          {!excluida && (
            <div className="mt-4">
              <LancarCreditos tenantId={empresa.id} condicao={condicaoVigente} />
            </div>
          )}
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

      {excluida ? (
        <div className="mt-6">
          <ArquivamentoDaEmpresa tenantId={empresa.id} exports={dados.exports} />
        </div>
      ) : (
        <>
          <div className="mt-6">
            <UsuariosDaEmpresa
              tenantId={empresa.id}
              membros={dados.membros}
              convites={dados.convites}
              solicitacoes={dados.solicitacoes}
              podeEditar={podeEditar}
            />
          </div>

          <div className="mt-6">
            <ZonaDePerigo
              tenantId={empresa.id}
              slug={empresa.slug}
              exclusaoAgendadaEm={empresa.exclusaoAgendadaEm ?? null}
              exports={dados.exports}
            />
          </div>
        </>
      )}
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
