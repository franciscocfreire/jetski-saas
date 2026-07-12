package com.jetski.tenant.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * White-label por tenant: cores, logo e conteúdo da vitrine pública
 * ({slug}.meujet.com.br). Armazenado como JSONB em {@code tenant.branding}.
 *
 * <p>Todos os campos nulos ⇒ tenant usa a identidade padrão Meu Jet (ver BRAND.md)
 * e a vitrine mostra só nome + embarcações. Chaves em snake_case por
 * compatibilidade com o seed V002 ({@code cor_primaria}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Branding(
        @JsonProperty("cor_primaria") String corPrimaria,
        @JsonProperty("cor_secundaria") String corSecundaria,
        @JsonProperty("logo_key") String logoKey,
        @JsonProperty("logo_content_type") String logoContentType,
        @JsonProperty("vitrine_descricao") String vitrineDescricao,
        @JsonProperty("vitrine_endereco") String vitrineEndereco,
        @JsonProperty("vitrine_praia") String vitrinePraia,
        @JsonProperty("vitrine_horario") String vitrineHorario,
        @JsonProperty("vitrine_instagram") String vitrineInstagram,
        @JsonProperty("vitrine_site") String vitrineSite
) {

    /** Padrão = sem customização: a UI cai nos tokens Meu Jet. */
    public static Branding padrao() {
        return new Branding(null, null, null, null, null, null, null, null, null, null);
    }

    public Branding comDefaults() {
        return this;
    }

    public boolean temLogo() {
        return logoKey != null && !logoKey.isBlank();
    }

    public Branding semLogo() {
        return new Branding(corPrimaria, corSecundaria, null, null,
            vitrineDescricao, vitrineEndereco, vitrinePraia, vitrineHorario, vitrineInstagram, vitrineSite);
    }

    public Branding comLogo(String key, String contentType) {
        return new Branding(corPrimaria, corSecundaria, key, contentType,
            vitrineDescricao, vitrineEndereco, vitrinePraia, vitrineHorario, vitrineInstagram, vitrineSite);
    }

    /** Novas cores preservando logo e vitrine (logo é read-only no PUT de cores). */
    public Branding comCores(String primaria, String secundaria) {
        return new Branding(primaria, secundaria, logoKey, logoContentType,
            vitrineDescricao, vitrineEndereco, vitrinePraia, vitrineHorario, vitrineInstagram, vitrineSite);
    }

    /** Novo conteúdo da vitrine preservando cores e logo. */
    public Branding comVitrine(String descricao, String endereco, String praia, String horario,
                               String instagram, String site) {
        return new Branding(corPrimaria, corSecundaria, logoKey, logoContentType,
            descricao, endereco, praia, horario, instagram, site);
    }
}
