package com.jetski.tenant.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Parametriza, por tenant, quais seções do documento consolidado vão para cada
 * destino na emissão (Marinha vs Cliente). Armazenado como JSONB em
 * {@code tenant.documento_config}.
 *
 * <p>Seções: 1-C (residência), 5-C (saúde), 5-B (instrutor/demonstração),
 * Termo de Responsabilidade (uso da MA), anexos do cliente (identidade/
 * comprovante/selfie) e comprovante de pagamento da GRU.
 *
 * <p>Padrão: a Marinha recebe a documentação NORMAM mas <b>não</b> o Termo de
 * Responsabilidade (instrumento privado loja↔cliente); o cliente recebe tudo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentoConfig(
        @JsonProperty("marinha") Destino marinha,
        @JsonProperty("cliente") Destino cliente
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Destino(
            @JsonProperty("residencia") Boolean residencia,
            @JsonProperty("saude") Boolean saude,
            @JsonProperty("instrutor") Boolean instrutor,
            @JsonProperty("termo") Boolean termo,
            @JsonProperty("anexosCliente") Boolean anexosCliente,
            @JsonProperty("comprovanteGru") Boolean comprovanteGru
    ) {
        /** Trata null como "incluir" (campo novo num JSON antigo não some da emissão). */
        public boolean inc(Boolean b) {
            return b == null || b;
        }

        public boolean residenciaOn() { return inc(residencia); }
        public boolean saudeOn() { return inc(saude); }
        public boolean instrutorOn() { return inc(instrutor); }
        public boolean termoOn() { return inc(termo); }
        public boolean anexosClienteOn() { return inc(anexosCliente); }
        public boolean comprovanteGruOn() { return inc(comprovanteGru); }
    }

    public static DocumentoConfig padrao() {
        return new DocumentoConfig(
                // Marinha: documentação NORMAM completa, sem o Termo de Responsabilidade.
                new Destino(true, true, true, false, true, true),
                // Cliente: tudo, inclusive o Termo.
                new Destino(true, true, true, true, true, true));
    }

    /** Nunca devolve null — campos/destinos ausentes caem no padrão. */
    public DocumentoConfig comDefaults() {
        DocumentoConfig p = padrao();
        return new DocumentoConfig(
                marinha != null ? marinha : p.marinha(),
                cliente != null ? cliente : p.cliente());
    }
}
