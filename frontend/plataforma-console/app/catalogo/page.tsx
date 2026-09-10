import { operadorAtual } from "@/lib/sessao";
import { Shell } from "@/components/Shell";
import { platform } from "@/lib/platform";
import { Card, Erro, TituloPagina } from "@/components/ui";
import { PlatformApiError } from "@/lib/api";
import { Capitanias, EmissaoEnvio, ImagemConfig, ModulosPorPlano } from "./editores";

export const dynamic = "force-dynamic";

/** Catálogo da plataforma: o que é oferecido, não o que uma empresa contratou. */
export default async function Catalogo() {
  const { session, me } = await operadorAtual();

  let dados;
  try {
    const [planos, modulos, capitanias, imagem, emissao] = await Promise.all([
      platform.planos(),
      platform.modulos(),
      platform.capitanias(),
      platform.imagemConfig(),
      platform.emissaoEnvioConfig(),
    ]);
    dados = { planos, modulos, capitanias, imagem, emissao };
  } catch (e) {
    const err = e as PlatformApiError;
    return (
      <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
        <TituloPagina titulo="Catálogo" />
        <Erro>
          {err.status === 403
            ? "Sua conta não é operador de plataforma."
            : `Falha ao carregar o catálogo (${err.status}).`}
        </Erro>
      </Shell>
    );
  }

  return (
    <Shell email={session.user?.email} admin={me.admin} papeis={me.papeis}>
      <TituloPagina
        titulo="Catálogo"
        descricao="Planos e módulos, capitanias e compressão de imagem — configuração global da plataforma."
      />

      <div className="space-y-6">
        <Card
          titulo="Módulos por plano"
          descricao="Nenhum módulo marcado = plano sem restrição (todos liberados)."
        >
          <ModulosPorPlano planos={dados.planos} catalogo={dados.modulos} />
        </Card>

        <Card
          titulo="Capitanias"
          descricao="Catálogo usado no perfil de emissão da empresa e no e-mail oficial da Marinha."
        >
          <Capitanias capitanias={dados.capitanias} />
        </Card>

        <Card
          titulo="Emissão de documentos"
          descricao="Como os e-mails da emissão (ofício à Capitania e via do cliente) saem."
        >
          <EmissaoEnvio assincrono={dados.emissao.assincrono ?? true} />
        </Card>

        <Card
          titulo="Compressão de imagem"
          descricao="Aplicada no navegador antes do upload. A assinatura (PNG) fica de fora."
        >
          <ImagemConfig tipos={dados.imagem.tipos ?? {}} />
        </Card>
      </div>
    </Shell>
  );
}
