package com.jetski.creditos.api.dto;

import java.math.BigDecimal;

/** Atualização do preço do crédito pelo super admin. */
public record AtualizarPrecoRequest(BigDecimal precoUnitario) {}
