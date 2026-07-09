package com.jetski.locacoes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO: alteração do vendedor de uma locação EM_CURSO.
 *
 * <p>{@code vendedorId} é nullable de propósito: null DESASSOCIA o vendedor
 * (locação sem vendedor). O guard de status e a existência do vendedor no
 * tenant são validados no service (BusinessException → 400).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVendedorRequest {

    /** Novo vendedor da locação; null remove a associação. */
    private UUID vendedorId;
}
