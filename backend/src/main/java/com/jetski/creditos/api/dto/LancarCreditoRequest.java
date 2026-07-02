package com.jetski.creditos.api.dto;

/** Lançamento manual de créditos pelo super admin (± permitido; motivo obrigatório). */
public record LancarCreditoRequest(
        int quantidade,
        String motivo
) {}
