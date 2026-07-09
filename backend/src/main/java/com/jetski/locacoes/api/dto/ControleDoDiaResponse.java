package com.jetski.locacoes.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO: Controle do Dia — a "prancheta digital" do operador.
 *
 * <p>Consolida em uma única resposta o movimento do dia: as linhas (locações
 * com check-in no dia + todas as EM_CURSO, vencidas primeiro, e as reservas
 * CONFIRMADAS do dia que ainda vão virar check-in), os totais recebidos por
 * forma de pagamento e a produção por vendedor.
 *
 * <p><b>Atenção aos regimes contábeis distintos (divergência por desenho):</b>
 * <ul>
 *   <li>{@code totalPorForma} é REGIME DE CAIXA: soma o recebido líquido
 *       (PAGAMENTO − ESTORNO) do folio pela data do LANÇAMENTO, na janela do
 *       dia no fuso do tenant — bate com o fechamento diário.</li>
 *   <li>{@code valorTotal} das linhas (e {@code totalDia}/{@code totalPorVendedor})
 *       é COMPETÊNCIA: valor apurado no check-out da locação. Uma reserva paga
 *       ontem com passeio hoje faz os números divergirem no dia — correto.</li>
 * </ul>
 */
public record ControleDoDiaResponse(
    List<Linha> linhas,
    Map<String, BigDecimal> totalPorForma,
    List<TotalVendedor> totalPorVendedor,
    BigDecimal totalDia
) {

    /**
     * Uma linha da prancheta — LOCACAO (movimento real) ou RESERVA (o que
     * ainda vai chegar hoje, só CONFIRMADAs), com nomes resolvidos.
     *
     * <p>Campos por tipo:
     * <ul>
     *   <li>LOCACAO: {@code locacaoId} preenchido, {@code reservaId} null,
     *       {@code formas} do folio; trio de prontidão ({@code pagamentoOk},
     *       {@code habilitacaoOk}, {@code termoOk}, {@code prontaParaCheckin})
     *       sempre null — prontidão é conceito de reserva.</li>
     *   <li>RESERVA: {@code reservaId} preenchido, {@code locacaoId} null,
     *       {@code dataCheckIn} = início previsto, {@code dataCheckOut} null,
     *       {@code formas} vazia; trio de prontidão preenchido (mesma regra
     *       da agenda). NÃO entra em totalDia/totalPorVendedor (competência
     *       só de locações).</li>
     * </ul>
     */
    public record Linha(
        String tipo,
        UUID locacaoId,
        UUID reservaId,
        UUID jetskiId,
        String jetskiSerie,
        UUID modeloId,
        String modeloNome,
        String clienteNome,
        String vendedorNome,
        LocalDateTime dataCheckIn,
        Integer duracaoPrevista,
        LocalDateTime dataCheckOut,
        String status,
        BigDecimal valorTotal,
        List<String> formas,
        Boolean pagamentoOk,
        Boolean habilitacaoOk,
        Boolean termoOk,
        Boolean prontaParaCheckin
    ) {}

    /**
     * Vendedor no dia: produção (soma de valorTotal, competência) e a
     * EXPECTATIVA de comissão — simulada pela mesma hierarquia RN04 do
     * fechamento (campanha → modelo → faixa → default), sem persistir.
     * expectativaComissao null = sem política configurada para o vendedor.
     * O valor oficial continua saindo no fechamento mensal.
     */
    public record TotalVendedor(
        UUID vendedorId,
        String vendedorNome,
        BigDecimal total,
        BigDecimal expectativaComissao
    ) {}
}
