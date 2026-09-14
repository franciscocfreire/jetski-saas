import { Badge, Card, Erro, Tabela, Td } from "@/components/ui";
import { dataCurta } from "@/lib/platform";
import type { ConviteEmpresa, MembroEmpresa, SolicitacaoCadastro } from "@/lib/types";
import { PAPEIS } from "./papeis";
import { AcoesMembro, CancelarConvite, IncluirUsuario } from "./usuarios-acoes";

/**
 * Quem opera a empresa, convites em aberto e quem pediu o cadastro.
 *
 * A empresa gere a própria equipe no backoffice; a plataforma (ADMIN/SUPORTE) também
 * pode incluir, desativar, reativar e remover — sempre com motivo auditado. Remover
 * tira só o vínculo com ESTA empresa: a conta da pessoa continua existindo.
 *
 * `null` em qualquer das listas = a leitura falhou; o resto da página segue de pé.
 */
export function UsuariosDaEmpresa({
  tenantId,
  membros,
  convites,
  solicitacoes,
  podeEditar,
}: {
  tenantId: string;
  membros: MembroEmpresa[] | null;
  convites: ConviteEmpresa[] | null;
  solicitacoes: SolicitacaoCadastro[] | null;
  podeEditar: boolean;
}) {
  const ativos = membros?.filter((m) => m.ativo).length ?? 0;
  const cabecalho = ["Usuário", "Papéis", "Situação", "Desde"];
  if (podeEditar) cabecalho.push("Ações");

  return (
    <Card
      titulo="Usuários da empresa"
      descricao={
        podeEditar
          ? "Equipe com acesso ao backoffice. A empresa gere a própria equipe; aqui a plataforma pode incluir, desativar ou remover alguém, sempre com motivo auditado."
          : "Equipe com acesso ao backoffice e convites em aberto."
      }
      acao={
        membros && membros.length > 0 ? (
          <span className="text-sm text-ink-500">
            {ativos} ativo{ativos === 1 ? "" : "s"} de {membros.length}
          </span>
        ) : undefined
      }
    >
      <QuemPediu solicitacoes={solicitacoes} />

      <div className="mt-4 border-t border-slate-100 pt-4">
        {membros === null ? (
          <Erro>Não foi possível carregar os usuários da empresa.</Erro>
        ) : (
          <Tabela cabecalho={cabecalho} vazio="Nenhum usuário com conta nesta empresa.">
            {membros.map((m) => (
              <tr key={m.usuarioId} className={m.ativo ? undefined : "bg-slate-50/60"}>
                <Td>
                  <div className={m.ativo ? "text-ink-900" : "text-ink-500"}>{m.nome || "—"}</div>
                  <div className="text-xs text-ink-500">{m.email}</div>
                  {m.telefone && <div className="text-xs text-ink-300">{m.telefone}</div>}
                </Td>
                <Td>
                  <Papeis papeis={m.papeis} />
                </Td>
                <Td>
                  <Situacao membro={m} />
                </Td>
                <Td className="whitespace-nowrap">{dataCurta(m.desde)}</Td>
                {podeEditar && (
                  <Td className="text-right">
                    <AcoesMembro
                      tenantId={tenantId}
                      usuarioId={m.usuarioId}
                      nome={m.nome || m.email}
                      ativo={m.ativo}
                    />
                  </Td>
                )}
              </tr>
            ))}
          </Tabela>
        )}
      </div>

      <div className="mt-4 border-t border-slate-100 pt-4">
        <Convites tenantId={tenantId} convites={convites} podeEditar={podeEditar} />
        {podeEditar && (
          <div className="mt-4">
            <IncluirUsuario tenantId={tenantId} />
          </div>
        )}
      </div>
    </Card>
  );
}

function Convites({
  tenantId,
  convites,
  podeEditar,
}: {
  tenantId: string;
  convites: ConviteEmpresa[] | null;
  podeEditar: boolean;
}) {
  const titulo = (
    <div className="text-xs uppercase tracking-wide text-ink-300">
      Convites em aberto{convites && convites.length > 0 ? ` (${convites.length})` : ""}
    </div>
  );

  if (convites === null) {
    return (
      <div>
        {titulo}
        <p className="mt-1 text-sm text-red-700">Não foi possível carregar os convites.</p>
      </div>
    );
  }

  if (convites.length === 0) {
    return (
      <div>
        {titulo}
        <p className="mt-1 text-sm text-ink-300">Nenhum convite aguardando aceite.</p>
      </div>
    );
  }

  const cabecalho = ["Convidado", "Papéis", "Situação", "Enviado"];
  if (podeEditar) cabecalho.push("Ações");

  return (
    <div>
      {titulo}
      <div className="mt-2">
        <Tabela cabecalho={cabecalho}>
          {convites.map((c) => (
            <tr key={c.id}>
              <Td>
                <div className="text-ink-900">{c.nome || "—"}</div>
                <div className="text-xs text-ink-500">{c.email}</div>
              </Td>
              <Td>
                <Papeis papeis={c.papeis} />
              </Td>
              <Td>
                {c.status === "EXPIRED" ? (
                  <Badge tom="perigo">convite expirado</Badge>
                ) : (
                  <Badge tom="atencao">convite pendente</Badge>
                )}
                <div className="mt-1 text-xs text-ink-300">
                  {c.status === "EXPIRED" ? "expirou em" : "vale até"} {dataCurta(c.expiresAt)}
                </div>
              </Td>
              <Td className="whitespace-nowrap">
                {dataCurta(c.lastEmailSentAt ?? c.createdAt)}
                {c.emailSentCount > 1 && (
                  <div className="text-xs text-ink-300">{c.emailSentCount} envios</div>
                )}
              </Td>
              {podeEditar && (
                <Td className="text-right">
                  <CancelarConvite tenantId={tenantId} conviteId={c.id} />
                </Td>
              )}
            </tr>
          ))}
        </Tabela>
      </div>
    </div>
  );
}

function Papeis({ papeis }: { papeis: string[] }) {
  return (
    <div className="flex flex-wrap gap-1">
      {papeis.length === 0 ? (
        <span className="text-ink-300">—</span>
      ) : (
        papeis.map((p) => (
          <Badge key={p} tom={p === "ADMIN_TENANT" ? "marca" : "neutro"}>
            {PAPEIS[p] ?? p}
          </Badge>
        ))
      )}
    </div>
  );
}

/**
 * O dono do signup público só vira usuário ao abrir o link de ativação. Até lá — ou
 * para sempre, se o e-mail foi digitado errado — este é o único lugar onde ele aparece.
 */
function QuemPediu({ solicitacoes }: { solicitacoes: SolicitacaoCadastro[] | null }) {
  const titulo = (
    <div className="text-xs uppercase tracking-wide text-ink-300">Quem pediu o cadastro</div>
  );

  if (solicitacoes === null) {
    return (
      <div>
        {titulo}
        <p className="mt-1 text-sm text-red-700">Não foi possível carregar o pedido de cadastro.</p>
      </div>
    );
  }

  if (solicitacoes.length === 0) {
    return (
      <div>
        {titulo}
        <p className="mt-1 text-sm text-ink-500">
          Sem pedido pelo cadastro público: a empresa foi criada por alguém que já tinha conta
          e entrou direto como administrador (veja a lista abaixo).
        </p>
      </div>
    );
  }

  const naoAtivou = solicitacoes[0].situacao !== "ATIVADO";

  return (
    <div>
      {titulo}
      <ul className="mt-2 space-y-2">
        {solicitacoes.map((s) => (
          <li key={s.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
            <span className="text-ink-900">{s.nome}</span>
            <span className="text-ink-500">{s.email}</span>
            <SituacaoPedido pedido={s} />
            <span className="text-xs text-ink-300">pedido em {dataCurta(s.pedidoEm)}</span>
          </li>
        ))}
      </ul>
      {naoAtivou && (
        <p className="mt-2 text-xs text-ink-500">
          A conta só é criada quando a pessoa abre o link enviado para o e-mail acima. Se ela
          diz que não recebeu, confira se o endereço está correto.
        </p>
      )}
    </div>
  );
}

function SituacaoPedido({ pedido }: { pedido: SolicitacaoCadastro }) {
  switch (pedido.situacao) {
    case "ATIVADO":
      return <Badge tom="ativo">ativou em {dataCurta(pedido.ativadoEm)}</Badge>;
    case "LINK_EXPIRADO":
      return <Badge tom="perigo">link expirou em {dataCurta(pedido.expiraEm)}</Badge>;
    default:
      return <Badge tom="atencao">aguardando ativação · link até {dataCurta(pedido.expiraEm)}</Badge>;
  }
}

/** A causa mais grave primeiro: conta bloqueada vale para todas as empresas da pessoa. */
function Situacao({ membro }: { membro: MembroEmpresa }) {
  if (!membro.contaAtiva) return <Badge tom="perigo">conta bloqueada</Badge>;
  if (!membro.ativo) return <Badge>desativado na empresa</Badge>;
  if (!membro.emailVerificado) return <Badge tom="atencao">e-mail não verificado</Badge>;
  return <Badge tom="ativo">ativo</Badge>;
}
