package com.jetski.marketplace.internal;

import com.jetski.marketplace.api.dto.MarketplaceMidiaDTO;
import com.jetski.marketplace.api.dto.MarketplaceModeloDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service: MarketplaceService
 *
 * Provides public marketplace data aggregated from multiple tenants.
 * This service does NOT use RLS - it queries across all tenants.
 *
 * Business Rules:
 * - Only models from ATIVO tenants are shown
 * - Only models where tenant.exibirNoMarketplace = true
 * - Only models where modelo.exibirNoMarketplace = true AND modelo.ativo = true
 * - Ordered by tenant.prioridadeMarketplace DESC, then random
 * - Includes media (images/videos) for each model
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Service
public class MarketplaceService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * List all models visible in the public marketplace.
     * Query crosses all tenants (bypasses RLS via local session setting).
     * Includes media items for each model.
     *
     * @return List of marketplace models ordered by priority
     */
    @Transactional(readOnly = true)
    public List<MarketplaceModeloDTO> listPublicModelos() {
        // RLS policy 'marketplace_public_read' on modelo and tenant tables
        // allows SELECT when exibir_no_marketplace = true AND ativo = true
        // No need to set tenant context - the policy handles public access

        String sql = """
            SELECT
                m.id,
                m.nome,
                m.fabricante,
                m.capacidade_pessoas,
                m.preco_base_hora,
                m.pacotes_json,
                m.foto_referencia_url,
                t.razao_social,
                t.whatsapp,
                t.cidade,
                t.uf,
                t.prioridade_marketplace
            FROM modelo m
            INNER JOIN tenant t ON m.tenant_id = t.id
            WHERE m.ativo = true
              AND m.exibir_no_marketplace = true
              AND t.status = 'ATIVO'
              AND t.exibir_no_marketplace = true
            ORDER BY t.prioridade_marketplace DESC, RANDOM()
            """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery(sql).getResultList();

        List<MarketplaceModeloDTO> modelos = results.stream()
            .map(this::mapToDTO)
            .toList();

        // If we have models, fetch their midias
        if (!modelos.isEmpty()) {
            List<UUID> modeloIds = modelos.stream()
                .map(MarketplaceModeloDTO::id)
                .toList();

            Map<UUID, List<MarketplaceMidiaDTO>> midiasMap = fetchMidiasForModelos(modeloIds);

            // Attach midias to each model
            modelos = modelos.stream()
                .map(m -> m.withMidias(midiasMap.getOrDefault(m.id(), List.of())))
                .toList();
        }

        return modelos;
    }

    /**
     * Get a single model by ID from the public marketplace.
     * Includes media items.
     *
     * @param modeloId Model UUID
     * @return Optional containing the model if visible in marketplace
     */
    @Transactional(readOnly = true)
    public Optional<MarketplaceModeloDTO> getPublicModelo(UUID modeloId) {
        // RLS policy 'marketplace_public_read' handles public access
        // No need to set tenant context

        String sql = """
            SELECT
                m.id,
                m.nome,
                m.fabricante,
                m.capacidade_pessoas,
                m.preco_base_hora,
                m.pacotes_json,
                m.foto_referencia_url,
                t.razao_social,
                t.whatsapp,
                t.cidade,
                t.uf,
                t.prioridade_marketplace
            FROM modelo m
            INNER JOIN tenant t ON m.tenant_id = t.id
            WHERE m.id = :modeloId
              AND m.ativo = true
              AND m.exibir_no_marketplace = true
              AND t.status = 'ATIVO'
              AND t.exibir_no_marketplace = true
            """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery(sql)
            .setParameter("modeloId", modeloId)
            .getResultList();

        return results.stream()
            .findFirst()
            .map(row -> {
                MarketplaceModeloDTO dto = mapToDTO(row);
                // Fetch midias for this single model
                List<MarketplaceMidiaDTO> midias = fetchMidiasForModelo(modeloId);
                return dto.withMidias(midias);
            });
    }

    /**
     * Fetch midias for multiple models (batch query for efficiency)
     */
    private Map<UUID, List<MarketplaceMidiaDTO>> fetchMidiasForModelos(List<UUID> modeloIds) {
        if (modeloIds.isEmpty()) {
            return Map.of();
        }

        String sql = """
            SELECT
                mm.id,
                mm.modelo_id,
                mm.tipo,
                mm.url,
                mm.thumbnail_url,
                mm.ordem,
                mm.principal,
                mm.titulo
            FROM modelo_midia mm
            INNER JOIN modelo m ON mm.modelo_id = m.id
            INNER JOIN tenant t ON m.tenant_id = t.id
            WHERE mm.modelo_id IN :modeloIds
              AND m.ativo = true
              AND m.exibir_no_marketplace = true
              AND t.status = 'ATIVO'
              AND t.exibir_no_marketplace = true
            ORDER BY mm.modelo_id, mm.ordem
            """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery(sql)
            .setParameter("modeloIds", modeloIds)
            .getResultList();

        return results.stream()
            .map(this::mapToMidiaDTO)
            .collect(Collectors.groupingBy(MidiaWithModeloId::modeloId,
                Collectors.mapping(MidiaWithModeloId::midia, Collectors.toList())));
    }

    /**
     * Fetch midias for a single model
     */
    private List<MarketplaceMidiaDTO> fetchMidiasForModelo(UUID modeloId) {
        Map<UUID, List<MarketplaceMidiaDTO>> midiasMap = fetchMidiasForModelos(List.of(modeloId));
        return midiasMap.getOrDefault(modeloId, List.of());
    }

    private MarketplaceModeloDTO mapToDTO(Object[] row) {
        UUID id = (UUID) row[0];
        String nome = (String) row[1];
        String fabricante = (String) row[2];
        Integer capacidadePessoas = row[3] != null ? ((Number) row[3]).intValue() : 2;
        BigDecimal precoBaseHora = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;
        String pacotesJson = (String) row[5];
        String fotoReferenciaUrl = (String) row[6];
        String empresaNome = (String) row[7];
        String empresaWhatsapp = (String) row[8];
        String cidade = (String) row[9];
        String uf = (String) row[10];
        Integer prioridade = row[11] != null ? ((Number) row[11]).intValue() : 0;

        BigDecimal precoPacote30min = extractPacote30min(pacotesJson);

        return MarketplaceModeloDTO.of(
            id,
            nome,
            fabricante,
            capacidadePessoas,
            precoBaseHora,
            precoPacote30min,
            fotoReferenciaUrl,
            empresaNome,
            empresaWhatsapp,
            cidade,
            uf,
            prioridade
        );
    }

    /**
     * Helper record to carry modeloId with midia for grouping
     */
    private record MidiaWithModeloId(UUID modeloId, MarketplaceMidiaDTO midia) {}

    private MidiaWithModeloId mapToMidiaDTO(Object[] row) {
        UUID id = (UUID) row[0];
        UUID modeloId = (UUID) row[1];
        String tipo = (String) row[2];
        String url = (String) row[3];
        String thumbnailUrl = (String) row[4];
        Integer ordem = row[5] != null ? ((Number) row[5]).intValue() : 0;
        Boolean principal = row[6] != null ? (Boolean) row[6] : false;
        String titulo = (String) row[7];

        MarketplaceMidiaDTO midia = MarketplaceMidiaDTO.of(id, tipo, url, thumbnailUrl, ordem, principal, titulo);
        return new MidiaWithModeloId(modeloId, midia);
    }

    /**
     * Extract 30min package price from pacotesJson.
     * Format expected: [{"duracao_min": 30, "preco": 180.00}, ...]
     */
    private BigDecimal extractPacote30min(String pacotesJson) {
        if (pacotesJson == null || pacotesJson.isBlank()) {
            return null;
        }

        try {
            // Simple parsing for duracao_min: 30
            // Format: [{"duracao_min": 30, "preco": 180.00}, ...]
            if (pacotesJson.contains("\"duracao_min\":30") || pacotesJson.contains("\"duracao_min\": 30")) {
                // Extract price for 30min package
                int idx = pacotesJson.indexOf("\"duracao_min\":30");
                if (idx == -1) {
                    idx = pacotesJson.indexOf("\"duracao_min\": 30");
                }
                if (idx != -1) {
                    // Find the preco field after this
                    int precoIdx = pacotesJson.indexOf("\"preco\":", idx);
                    if (precoIdx != -1) {
                        int valueStart = precoIdx + 8; // length of "\"preco\":"
                        // Skip whitespace
                        while (valueStart < pacotesJson.length() &&
                               Character.isWhitespace(pacotesJson.charAt(valueStart))) {
                            valueStart++;
                        }
                        int valueEnd = valueStart;
                        while (valueEnd < pacotesJson.length() &&
                               (Character.isDigit(pacotesJson.charAt(valueEnd)) ||
                                pacotesJson.charAt(valueEnd) == '.')) {
                            valueEnd++;
                        }
                        if (valueEnd > valueStart) {
                            String priceStr = pacotesJson.substring(valueStart, valueEnd);
                            return new BigDecimal(priceStr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors, return null
        }
        return null;
    }
}
