package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.VinculoEmissao;

import java.time.Instant;
import java.util.UUID;

/**
 * Vínculo de emissão delegada na visão do tenant solicitante (V048).
 *
 * @author Jetski Team
 */
public record VinculoEmissaoResponse(
    UUID id,
    String papel,           // OPERADORA | EMISSORA (relativo ao tenant da sessão)
    UUID parceiroTenantId,
    String parceiroNome,
    String status,
    boolean aguardandoMeuAceite,
    Instant convidadoEm,
    Instant aceitoEm,
    Instant bloqueadoEm,
    Instant revogadoEm,
    String termoTexto
) {
    public static VinculoEmissaoResponse of(VinculoEmissao v, UUID tenantId, String parceiroNome) {
        boolean souOperadora = tenantId.equals(v.getTenantOperadorId());
        UUID parceiro = souOperadora ? v.getTenantEmissorId() : v.getTenantOperadorId();
        boolean aguardandoMeuAceite = v.getStatus() == VinculoEmissao.Status.CONVIDADO
            && !tenantId.equals(v.getConvidadoPorTenant());
        return new VinculoEmissaoResponse(
            v.getId(),
            souOperadora ? "OPERADORA" : "EMISSORA",
            parceiro,
            parceiroNome,
            v.getStatus().name(),
            aguardandoMeuAceite,
            v.getConvidadoEm(),
            v.getAceitoEm(),
            v.getBloqueadoEm(),
            v.getRevogadoEm(),
            v.getTermoTexto());
    }
}
