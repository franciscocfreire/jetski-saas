"use client";

import { usePerfil } from "@/components/perfil/usePerfil";
import { DispositivosCard, PerfilSubpagina, SegurancaCard } from "@/components/perfil/secoes";

export default function PerfilSegurancaPage() {
  const { token, erro, carregando } = usePerfil();

  return (
    <PerfilSubpagina
      titulo="Segurança"
      sub="Verificação em duas etapas e navegadores confiáveis."
      carregando={carregando}
      erro={erro}
    >
      {token && <SegurancaCard token={token} callbackPath="/conta/perfil/seguranca" />}
      {token && <DispositivosCard token={token} />}
    </PerfilSubpagina>
  );
}
