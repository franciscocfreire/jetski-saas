import { operadorAtual } from "@/lib/sessao";
import { Shell } from "@/components/Shell";
import { Card, TituloPagina } from "@/components/ui";
import { RotacaoDeChave } from "./acoes";

export const dynamic = "force-dynamic";

export default async function Configuracoes() {
  const { session, me } = await operadorAtual();

  return (
    <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
      <TituloPagina
        titulo="Configurações"
        descricao="Operações globais da plataforma."
      />

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
