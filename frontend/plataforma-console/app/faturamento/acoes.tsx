"use client";

import { Acao, AcaoComTexto } from "@/components/Acao";
import { cancelarFatura, confirmarFatura, gerarFaturas } from "@/lib/actions";

export function AcoesFatura({
  tenantId,
  faturaId,
}: {
  tenantId: string;
  faturaId: string;
}) {
  return (
    <div className="flex flex-wrap items-start gap-2">
      <Acao
        variante="primaria"
        rotulo="Confirmar"
        confirmar="Pagamento conferido no extrato?"
        acao={() => confirmarFatura(tenantId, faturaId)}
      />
      <AcaoComTexto
        variante="perigo"
        rotulo="Cancelar"
        placeholder="observação (obrigatória)"
        acao={(obs) => cancelarFatura(tenantId, faturaId, obs)}
      />
    </div>
  );
}

/**
 * Geração manual do lote do mês. O job das 06:00 já faz isso sozinho — o botão
 * existe para reprocessar quando o job falhou. É idempotente no backend.
 */
export function GerarFaturas() {
  return (
    <Acao
      rotulo="Gerar faturas do mês"
      confirmar="Gerar o lote da competência atual?"
      acao={() => gerarFaturas()}
    />
  );
}
