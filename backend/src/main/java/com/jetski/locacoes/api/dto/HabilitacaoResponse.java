package com.jetski.locacoes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HabilitacaoResponse {

    private UUID id;
    private UUID reservaId;
    private String via;

    private String chaCategoria;
    private String chaNumero;
    private LocalDate chaValidade;

    private Instant videoaulaEm;
    private Boolean anexoSaude;
    private Boolean anexoRegras;
    private Boolean anexoResidencia;
    private Boolean usaLentes;
    private Boolean usaAparelho;
    private UUID instrutorId;

    private String gruNumero;
    private BigDecimal gruValor;
    private Boolean gruPago;
    private Instant gruPagoEm;

    // GRU gerada automaticamente — PIX e/ou boleto disponíveis para pagamento
    private String gruPixCopiaECola;
    private Instant gruPixExpiracao;
    private Boolean gruBoletoDisponivel;
    private Boolean gruComprovanteDisponivel;

    // Devolutiva da Marinha (CHA-MTA-E confirmada) — anexada manualmente pela loja
    private Instant marinhaConfirmadaEm;
    private Boolean devolutivaDisponivel;

    private Boolean resolvida;
    private Instant createdAt;
    private Instant updatedAt;
}
