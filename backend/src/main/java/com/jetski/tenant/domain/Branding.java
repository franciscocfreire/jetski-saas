package com.jetski.tenant.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * White-label por tenant: cores e logo exibidos no backoffice (e futuramente no
 * portal do cliente). Armazenado como JSONB em {@code tenant.branding}.
 *
 * <p>Todos os campos nulos ⇒ tenant usa a identidade padrão Meu Jet (ver BRAND.md).
 * Chaves em snake_case por compatibilidade com o seed V002 ({@code cor_primaria}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Branding(
        @JsonProperty("cor_primaria") String corPrimaria,
        @JsonProperty("cor_secundaria") String corSecundaria,
        @JsonProperty("logo_key") String logoKey,
        @JsonProperty("logo_content_type") String logoContentType
) {

    /** Padrão = sem customização: a UI cai nos tokens Meu Jet. */
    public static Branding padrao() {
        return new Branding(null, null, null, null);
    }

    public Branding comDefaults() {
        return this;
    }

    public boolean temLogo() {
        return logoKey != null && !logoKey.isBlank();
    }

    public Branding semLogo() {
        return new Branding(corPrimaria, corSecundaria, null, null);
    }

    public Branding comLogo(String key, String contentType) {
        return new Branding(corPrimaria, corSecundaria, key, contentType);
    }

    /** Novas cores preservando o logo atual (logo é read-only no PUT de cores). */
    public Branding comCores(String primaria, String secundaria) {
        return new Branding(primaria, secundaria, logoKey, logoContentType);
    }
}
