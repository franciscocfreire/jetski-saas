package com.jetski.marketplace.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO para exibição de modelos no marketplace público.
 * Contém dados agregados do modelo e do tenant para listagem.
 *
 * tenantId/lojaSlug permitem ao portal do cliente rotear a reserva para a
 * loja correta (P1) e montar a vitrine por loja (/loja/{slug}).
 */
public record MarketplaceModeloDTO(
    UUID id,
    UUID tenantId,
    String lojaSlug,
    String nome,
    String fabricante,
    Integer capacidadePessoas,
    BigDecimal precoBaseHora,
    BigDecimal precoPacote30min,
    String fotoReferenciaUrl,
    String empresaNome,
    String empresaWhatsapp,
    String localizacao,
    /** Cidade/UF crus para filtros (localizacao é a versão formatada de exibição). */
    String cidade,
    String uf,
    /** Praia/ponto de encontro da loja (branding.vitrine_praia) — base da busca por praia. */
    String praia,
    Integer prioridade,
    BigDecimal notaMedia,
    Integer totalAvaliacoes,
    List<MarketplaceMidiaDTO> midias
) {
    /**
     * Factory method para criar DTO a partir de dados de query (sem midias).
     */
    public static MarketplaceModeloDTO of(
            UUID id,
            UUID tenantId,
            String lojaSlug,
            String nome,
            String fabricante,
            Integer capacidadePessoas,
            BigDecimal precoBaseHora,
            BigDecimal precoPacote30min,
            String fotoReferenciaUrl,
            String empresaNome,
            String empresaWhatsapp,
            String cidade,
            String uf,
            String praia,
            Integer prioridade,
            BigDecimal notaMedia,
            Integer totalAvaliacoes
    ) {
        return new MarketplaceModeloDTO(
            id,
            tenantId,
            lojaSlug,
            nome,
            fabricante,
            capacidadePessoas,
            precoBaseHora,
            precoPacote30min,
            fotoReferenciaUrl,
            empresaNome,
            empresaWhatsapp,
            formatLocalizacao(cidade, uf),
            cidade,
            uf,
            praia,
            prioridade,
            notaMedia,
            totalAvaliacoes,
            List.of()
        );
    }

    /**
     * Create a copy with midias list
     */
    public MarketplaceModeloDTO withMidias(List<MarketplaceMidiaDTO> midias) {
        return new MarketplaceModeloDTO(
            this.id,
            this.tenantId,
            this.lojaSlug,
            this.nome,
            this.fabricante,
            this.capacidadePessoas,
            this.precoBaseHora,
            this.precoPacote30min,
            this.fotoReferenciaUrl,
            this.empresaNome,
            this.empresaWhatsapp,
            this.localizacao,
            this.cidade,
            this.uf,
            this.praia,
            this.prioridade,
            this.notaMedia,
            this.totalAvaliacoes,
            midias != null ? midias : List.of()
        );
    }

    private static String formatLocalizacao(String cidade, String uf) {
        if (cidade != null && uf != null) {
            return cidade + ", " + uf;
        } else if (cidade != null) {
            return cidade;
        } else if (uf != null) {
            return uf;
        }
        return "Brasil";
    }
}
