"use client";

import { Card } from "@/components/ui";
import { usePerfil } from "@/components/perfil/usePerfil";
import { LojaRow, PerfilSubpagina } from "@/components/perfil/secoes";

export default function PerfilLojasPage() {
  const { token, self, erro, carregando } = usePerfil();

  return (
    <PerfilSubpagina
      titulo="Lojas vinculadas"
      sub="Sua conta é única — cada loja onde você aluga cria um vínculo aqui. O telefone de contato é por loja."
      carregando={carregando}
      erro={erro}
    >
      <Card className="p-6">
        {!self || self.lojas.length === 0 ? (
          <p className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-500">
            Nenhum vínculo ainda. Ele é criado na sua primeira reserva com uma loja.
          </p>
        ) : (
          <div className="space-y-2">
            {self.lojas.map((loja) => (
              <LojaRow key={loja.tenantId} loja={loja} token={token} />
            ))}
          </div>
        )}
      </Card>
    </PerfilSubpagina>
  );
}
