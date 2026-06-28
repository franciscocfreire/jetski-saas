package com.jetski.tenant.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Parametriza, por tenant, a emissão dos documentos:
 * <ul>
 *   <li>{@code marinha}/{@code cliente}: quais seções vão em cada destino do PDF;</li>
 *   <li>{@code obrigatoriosMarinha}: o que é exigido para liberar o e-mail à Marinha.</li>
 * </ul>
 * Armazenado como JSONB em {@code tenant.documento_config}.
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
        @JsonProperty("cliente") Destino cliente,
        @JsonProperty("obrigatoriosMarinha") ObrigatoriosMarinha obrigatoriosMarinha
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

    /**
     * Itens exigidos para liberar o e-mail à Marinha (EMA). Cada flag liga/desliga
     * a verificação correspondente em {@code pendenciasDocumentacao}. Null = exigido
     * (mantém o gate estrito por padrão).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObrigatoriosMarinha(
            @JsonProperty("identidade") Boolean identidade,
            @JsonProperty("saude") Boolean saude,
            @JsonProperty("regras") Boolean regras,
            @JsonProperty("residencia") Boolean residencia,
            @JsonProperty("instrutor") Boolean instrutor,
            @JsonProperty("nacionalidade") Boolean nacionalidade,
            @JsonProperty("naturalidade") Boolean naturalidade
    ) {
        private boolean req(Boolean b) {
            return b == null || b;
        }

        public boolean identidadeReq() { return req(identidade); }
        public boolean saudeReq() { return req(saude); }
        public boolean regrasReq() { return req(regras); }
        public boolean residenciaReq() { return req(residencia); }
        public boolean instrutorReq() { return req(instrutor); }
        public boolean nacionalidadeReq() { return req(nacionalidade); }
        public boolean naturalidadeReq() { return req(naturalidade); }
    }

    public static DocumentoConfig padrao() {
        return new DocumentoConfig(
                // Marinha: documentação NORMAM completa, sem o Termo de Responsabilidade.
                new Destino(true, true, true, false, true, true),
                // Cliente: tudo, inclusive o Termo.
                new Destino(true, true, true, true, true, true),
                // Obrigatórios à Marinha: tudo exigido (inclui o documento de identidade).
                new ObrigatoriosMarinha(true, true, true, true, true, true, true));
    }

    /** Nunca devolve null — campos/destinos ausentes caem no padrão. */
    public DocumentoConfig comDefaults() {
        DocumentoConfig p = padrao();
        return new DocumentoConfig(
                marinha != null ? marinha : p.marinha(),
                cliente != null ? cliente : p.cliente(),
                obrigatoriosMarinha != null ? obrigatoriosMarinha : p.obrigatoriosMarinha());
    }
}
