package com.jetski.pagamentos.api.dto;

import com.jetski.locacoes.domain.TipoChavePix;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO: DetalhesPendenciasResponse
 *
 * Response containing detailed pending payment items for a seller.
 * Used for partial payment selection - lists all individual items
 * (commissions, daily allowances, and bonuses) that can be selected.
 *
 * @author Jetski Team
 * @since 0.13.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetalhesPendenciasResponse {

    private UUID vendedorId;
    private String vendedorNome;

    // PIX Info
    private String chavePix;
    private TipoChavePix tipoChavePix;
    private boolean temPixCadastrado;

    // List of all pending items
    private List<ItemPendente> itens;

    // Total value of all items
    private BigDecimal valorTotal;
}
