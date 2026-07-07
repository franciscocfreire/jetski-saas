package com.jetski.locacoes.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ficha COMPLETA da reserva (página de detalhe + PDF): agrega o que hoje
 * vive em 5 endpoints — reserva, cliente (resumo sem PII desnecessária),
 * passeio, extrato do folio, habilitação, aceite, ciclo da GRU e documentos.
 */
public record ReservaFichaResponse(
    ReservaResponse reserva,
    ClienteResumo cliente,
    PasseioResumo passeio,
    FolioExtratoResponse extrato,
    HabilitacaoResponse habilitacao,
    AceiteResponse aceite,
    CicloGru ciclo,
    List<DocumentoConsultaResponse> documentos
) {
    /** Só o necessário para a ficha — CPF mascarado, sem RG/endereço/nascimento. */
    public record ClienteResumo(
        UUID id, String nome, String documentoMascarado,
        String email, String telefone, String whatsapp) {}

    public record PasseioResumo(
        UUID modeloId, String modeloNome, UUID jetskiId, String jetskiSerie) {}

    /** Ciclo Marinha da GRU (mesma visão do módulo GRUs, para uma reserva). */
    public record CicloGru(
        String gruNumero, BigDecimal gruValor, Instant gruGeradaEm,
        Boolean gruPago, Instant gruPagoEm,
        Instant documentoEmitidoEm, Instant marinhaEnviadaEm, Instant marinhaConfirmadaEm) {}
}
