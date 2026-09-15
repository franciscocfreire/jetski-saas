"use client";

import { IdCard } from "lucide-react";
import { Card } from "@/components/ui";
import { usePerfil } from "@/components/perfil/usePerfil";
import { DocumentosLoja, MinhasHabilitacoes, PerfilSubpagina } from "@/components/perfil/secoes";

export default function PerfilDocumentosPage() {
  const { token, self, erro, carregando } = usePerfil();

  return (
    <PerfilSubpagina
      titulo="Documentos e habilitações"
      sub="Fotos usadas só para emitir sua habilitação (NORMAM-212/DPC), visíveis apenas para você e para a loja."
      carregando={carregando}
      erro={erro}
    >
      {token && <MinhasHabilitacoes token={token} />}

      <Card className="p-6">
        <h3 className="flex items-center gap-2 font-semibold text-ink-900">
          <IdCard size={18} /> Fotos dos documentos
        </h3>
        {!self || self.lojas.length === 0 ? (
          <p className="mt-2 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-500">
            Os documentos são pedidos por loja, na sua primeira reserva.
          </p>
        ) : (
          <>
            <p className="mt-1 text-sm text-slate-500">
              Identidade e selfie são obrigatórias para a emissão. O comprovante de
              residência pode ser substituído por uma declaração, na reserva.
            </p>
            <div className="mt-4 space-y-5">
              {token && self.lojas.map((loja) => (
                <DocumentosLoja
                  key={loja.tenantId}
                  loja={loja}
                  token={token}
                  mostrarCabecalho={self.lojas.length > 1}
                />
              ))}
            </div>
          </>
        )}
      </Card>
    </PerfilSubpagina>
  );
}
