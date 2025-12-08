package com.jetski.marketplace.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO para exibição de modelos no marketplace público.
 * Contém dados agregados do modelo e do tenant para listagem.
 */
public record MarketplaceModeloDTO(
    UUID id,
    String nome,
    String fabricante,
    Integer capacidadePessoas,
    BigDecimal precoBaseHora,
    BigDecimal precoPacote30min,
    String fotoReferenciaUrl,
    String empresaNome,
    String empresaWhatsapp,
    String localizacao,
    Integer prioridade,
    List<MarketplaceMidiaDTO> midias
) {
    /**
     * Factory method para criar DTO a partir de dados de query (sem midias).
     */
    public static MarketplaceModeloDTO of(
            UUID id,
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
            Integer prioridade
    ) {
        String localizacao = formatLocalizacao(cidade, uf);
        return new MarketplaceModeloDTO(
            id,
            nome,
            fabricante,
            capacidadePessoas,
            precoBaseHora,
            precoPacote30min,
            fotoReferenciaUrl,
            empresaNome,
            empresaWhatsapp,
            localizacao,
            prioridade,
            List.of()  // Empty list, will be populated by service
        );
    }

    /**
     * Factory method para criar DTO com midias.
     */
    public static MarketplaceModeloDTO of(
            UUID id,
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
            Integer prioridade,
            List<MarketplaceMidiaDTO> midias
    ) {
        String localizacao = formatLocalizacao(cidade, uf);
        return new MarketplaceModeloDTO(
            id,
            nome,
            fabricante,
            capacidadePessoas,
            precoBaseHora,
            precoPacote30min,
            fotoReferenciaUrl,
            empresaNome,
            empresaWhatsapp,
            localizacao,
            prioridade,
            midias != null ? midias : List.of()
        );
    }

    /**
     * Create a copy with midias list
     */
    public MarketplaceModeloDTO withMidias(List<MarketplaceMidiaDTO> midias) {
        return new MarketplaceModeloDTO(
            this.id,
            this.nome,
            this.fabricante,
            this.capacidadePessoas,
            this.precoBaseHora,
            this.precoPacote30min,
            this.fotoReferenciaUrl,
            this.empresaNome,
            this.empresaWhatsapp,
            this.localizacao,
            this.prioridade,
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
