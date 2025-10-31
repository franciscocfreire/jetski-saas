package com.jetski.comissoes.api.dto;

/**
 * Request DTO for approving a commission (GERENTE action)
 *
 * <p>Empty DTO - approval is implicit by the GERENTE calling the endpoint.
 * The aprovadoPor will come from the authenticated user context.</p>
 *
 * @author Jetski Team
 * @since 0.7.0
 */
public class AprovarComissaoRequest {
    // No fields needed
}
