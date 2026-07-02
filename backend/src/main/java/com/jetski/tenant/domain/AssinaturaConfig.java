package com.jetski.tenant.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Parametriza, por tenant, o reforço jurídico da assinatura eletrônica (Fase A):
 * página de trilha de auditoria anexada ao PDF e carimbo de tempo sobre o hash.
 * Armazenado como JSONB em {@code tenant.assinatura_config}.
 *
 * <p>Base legal: Lei 14.063/2020 (assinatura eletrônica simples), MP 2.200-2/2001,
 * Código Civil arts. 219/221. O carimbo eleva a prova de anterioridade/integridade.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssinaturaConfig(
        @JsonProperty("paginaAuditoria") Boolean paginaAuditoria,
        @JsonProperty("carimboTempo") CarimboTempo carimboTempo,
        @JsonProperty("otp") Otp otp
) {

    /** Carimbo de tempo. tsaUrl vazio → usa âncora própria (HMAC), sem custo. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CarimboTempo(
            @JsonProperty("ativo") Boolean ativo,
            @JsonProperty("tsaUrl") String tsaUrl
    ) {}

    /** OTP no aceite (Fase B). canal: EMAIL | WHATSAPP. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Otp(
            @JsonProperty("ativo") Boolean ativo,
            @JsonProperty("canal") String canal
    ) {}

    /** TSA gratuita padrão (RFC 3161, sem fé pública ICP-Brasil). */
    public static final String TSA_GRATUITA = "https://freetsa.org/tsr";

    public static AssinaturaConfig padrao() {
        return new AssinaturaConfig(
                true, // página de auditoria ligada
                new CarimboTempo(true, TSA_GRATUITA),
                new Otp(false, "EMAIL"));
    }

    /** Nunca devolve null — campos ausentes caem no padrão. */
    public AssinaturaConfig comDefaults() {
        AssinaturaConfig p = padrao();
        return new AssinaturaConfig(
                paginaAuditoria != null ? paginaAuditoria : p.paginaAuditoria(),
                carimboTempo != null ? carimboTempo : p.carimboTempo(),
                otp != null ? otp : p.otp());
    }

    public boolean paginaAuditoriaOn() {
        return paginaAuditoria == null || paginaAuditoria;
    }

    public boolean carimboOn() {
        return carimboTempo == null || carimboTempo.ativo() == null || carimboTempo.ativo();
    }

    public String tsaUrlOrDefault() {
        String u = carimboTempo != null ? carimboTempo.tsaUrl() : null;
        return (u == null || u.isBlank()) ? TSA_GRATUITA : u;
    }
}
