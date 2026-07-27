import { operadorAtual } from "@/lib/sessao";
import { Shell } from "@/components/Shell";
import { Card, Erro, TituloPagina } from "@/components/ui";
import { platform } from "@/lib/platform";
import { PlatformApiError } from "@/lib/api";
import type { Seguranca2FAConsole } from "@/lib/types";
import { RotacaoDeChave } from "./acoes";
import { Exigencia2FA } from "./seguranca";

export const dynamic = "force-dynamic";

export default async function Configuracoes() {
  const { session, me } = await operadorAtual();

  // O Keycloak pode estar fora do ar sem que isso derrube a página inteira — a rotação
  // de chave não depende dele.
  let seguranca: Seguranca2FAConsole | null = null;
  let erroSeguranca: string | null = null;
  try {
    seguranca = await platform.seguranca2FAConsole();
  } catch (e) {
    const err = e as PlatformApiError;
    erroSeguranca =
      err.status === 403
        ? "Só PLATFORM_ADMIN vê e muda a política de 2FA do console."
        : `Não foi possível consultar o Keycloak (${err.status}).`;
  }

  return (
    <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
      <TituloPagina
        titulo="Configurações"
        descricao="Operações globais da plataforma."
      />

      <Card
        titulo="Acesso ao console"
        descricao="Quem opera a plataforma entra por aqui — esta é a política de 2FA desta porta."
        className="mb-6"
      >
        {seguranca ? <Exigencia2FA inicial={seguranca} /> : <Erro>{erroSeguranca}</Erro>}
      </Card>

      <Card
        titulo="Rotação de chave de criptografia"
        descricao="Re-cifra os segredos de todas as empresas (senha SMTP por loja) com a JETSKI_SECRET_KEY vigente."
      >
        <p className="mb-4 text-sm text-ink-500">
          Rode depois de trocar a chave no ambiente. É idempotente: segredos já cifrados com a
          chave atual são recontados, não recifrados duas vezes.
        </p>
        <RotacaoDeChave />
      </Card>
    </Shell>
  );
}
