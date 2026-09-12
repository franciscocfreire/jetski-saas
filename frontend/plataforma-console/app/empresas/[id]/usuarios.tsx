import { Badge, Card, Erro, Tabela, Td } from "@/components/ui";
import { dataCurta } from "@/lib/platform";
import type { MembroEmpresa, SolicitacaoCadastro } from "@/lib/types";

/** Rótulos dos papéis de EMPRESA — os mesmos do backoffice (AVAILABLE_ROLES). */
const PAPEIS: Record<string, string> = {
  ADMIN_TENANT: "Administrador",
  GERENTE: "Gerente",
  OPERADOR: "Operador",
  VENDEDOR: "Vendedor",
  MECANICO: "Mecânico",
  FINANCEIRO: "Financeiro",
};

/**
 * Quem opera a empresa e quem pediu o cadastro. Somente leitura: papéis, convites e
 * desativação são decisão da própria empresa, na tela de usuários do backoffice.
 *
 * `null` em qualquer das listas = a leitura falhou; o resto da página segue de pé.
 */
export function UsuariosDaEmpresa({
  membros,
  solicitacoes,
}: {
  membros: MembroEmpresa[] | null;
  solicitacoes: SolicitacaoCadastro[] | null;
}) {
  const ativos = membros?.filter((m) => m.ativo).length ?? 0;

  return (
    <Card
      titulo="Usuários da empresa"
      descricao="Equipe com acesso ao backoffice. Papéis e convites são geridos pela própria empresa."
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
          <Tabela
            cabecalho={["Usuário", "Papéis", "Situação", "Desde"]}
            vazio="Nenhum usuário com conta nesta empresa."
          >
            {membros.map((m) => (
              <tr key={m.usuarioId} className={m.ativo ? undefined : "opacity-60"}>
                <Td>
                  <div className="text-ink-900">{m.nome || "—"}</div>
                  <div className="text-xs text-ink-500">{m.email}</div>
                  {m.telefone && <div className="text-xs text-ink-300">{m.telefone}</div>}
                </Td>
                <Td>
                  <div className="flex flex-wrap gap-1">
                    {m.papeis.length === 0 ? (
                      <span className="text-ink-300">—</span>
                    ) : (
                      m.papeis.map((p) => (
                        <Badge key={p} tom={p === "ADMIN_TENANT" ? "marca" : "neutro"}>
                          {PAPEIS[p] ?? p}
                        </Badge>
                      ))
                    )}
                  </div>
                </Td>
                <Td>
                  <Situacao membro={m} />
                </Td>
                <Td className="whitespace-nowrap">{dataCurta(m.desde)}</Td>
              </tr>
            ))}
          </Tabela>
        )}
      </div>
    </Card>
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
