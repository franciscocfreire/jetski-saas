import { operadorAtual } from "@/lib/sessao";
import { Shell } from "@/components/Shell";
import { dataCurta, platform } from "@/lib/platform";
import { Aviso, Card, Erro, Tabela, Td, TituloPagina } from "@/components/ui";
import { PlatformApiError } from "@/lib/api";
import { ConcederAcesso, PapeisDoOperador } from "./acoes";

export const dynamic = "force-dynamic";

/**
 * Operadores da plataforma — quem administra os administradores.
 *
 * Substitui o caminho que existia até aqui: editar PLATFORM_ADMIN_EMAILS no .env e
 * reiniciar o backend, ou um INSERT manual no banco. Ambos sem trilha e sem revisão.
 *
 * A tela inteira é PLATFORM_ADMIN (inclusive a listagem: saber quem opera a plataforma
 * já é informação sensível) — quem não for recebe 403 do backend.
 */
export default async function Operadores() {
  const { session, me } = await operadorAtual();

  let dados;
  try {
    const [operadores, papeis] = await Promise.all([
      platform.operadores(),
      platform.papeisPlataforma(),
    ]);
    dados = { operadores, papeis };
  } catch (e) {
    const err = e as PlatformApiError;
    return (
      <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
        <TituloPagina titulo="Operadores" />
        <Erro>
          {err.status === 403
            ? "Só administradores da plataforma gerenciam operadores."
            : `Falha ao carregar operadores (${err.status}).`}
        </Erro>
      </Shell>
    );
  }

  const admins = dados.operadores.filter((o) =>
    o.papeis.includes("PLATFORM_ADMIN"),
  ).length;

  return (
    <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
      <TituloPagina
        titulo="Operadores"
        descricao={`${dados.operadores.length} com acesso à plataforma · ${admins} administrador(es)`}
      />

      {admins === 1 && (
        <div className="mb-6">
          <Aviso>
            Só existe <strong>um administrador</strong> da plataforma. Se essa conta for
            perdida, devolver o acesso exige SQL manual em produção — promova um segundo.
          </Aviso>
        </div>
      )}

      <div className="space-y-6">
        <Card
          titulo="Com acesso hoje"
          descricao="Qualquer papel aqui enxerga todas as empresas; o papel decide o que pode fazer."
        >
          <Tabela
            cabecalho={["Pessoa", "Papéis", "Desde"]}
            vazio="Nenhum operador — o acesso está só no PLATFORM_ADMIN_EMAILS."
          >
            {dados.operadores.map((o) => (
              <tr key={o.usuarioId}>
                <Td>
                  <div className="font-medium">{o.nome}</div>
                  <div className="text-xs text-ink-300">{o.email}</div>
                  {!o.ativo && (
                    <div className="text-xs text-amber-700">conta inativa</div>
                  )}
                </Td>
                <Td>
                  <PapeisDoOperador
                    usuarioId={o.usuarioId}
                    email={o.email}
                    papeisAtuais={o.papeis}
                    catalogo={dados.papeis}
                  />
                </Td>
                <Td>{dataCurta(o.concedidoEm)}</Td>
              </tr>
            ))}
          </Tabela>
        </Card>

        <Card
          titulo="Conceder acesso"
          descricao="A pessoa precisa já ter conta ativada — o acesso de plataforma não cria usuário."
        >
          <ConcederAcesso catalogo={dados.papeis} />
        </Card>

        <Card titulo="O que cada papel pode">
          <dl className="space-y-3">
            {dados.papeis.map((p) => (
              <div key={p.key}>
                <dt className="font-medium text-ink-900">{p.rotulo}</dt>
                <dd className="text-sm text-ink-500">{p.descricao}</dd>
              </div>
            ))}
          </dl>
          <p className="mt-4 border-t border-slate-100 pt-3 text-xs text-ink-300">
            A matriz completa vive em <code>policies/authz/platform.rego</code>. Toda
            concessão e revogação entra na trilha de auditoria global.
          </p>
        </Card>
      </div>
    </Shell>
  );
}
