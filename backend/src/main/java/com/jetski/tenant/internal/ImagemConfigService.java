package com.jetski.tenant.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.shared.exception.BusinessException;
import com.jetski.tenant.api.dto.ImagemCompressaoConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Config de compressão de imagem (por tipo de documento), guardada como JSON em
 * {@code plataforma_config} (chave {@code imagem_compressao}) — mesmo padrão do
 * preço do crédito ({@code CreditoService.precoUnitario/atualizarPrecoUnitario}).
 * Chave-valor global (sem RLS): o super admin grava, o backoffice do tenant lê.
 * Ausente → defaults em código (sem migration).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImagemConfigService {

    private static final String CHAVE = "imagem_compressao";

    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    /** Config vigente; se a chave não existir ou estiver corrompida, devolve defaults. */
    @Transactional(readOnly = true)
    public ImagemCompressaoConfig get() {
        try {
            Object valor = entityManager.createNativeQuery(
                    "SELECT valor FROM plataforma_config WHERE chave = ?1")
                .setParameter(1, CHAVE)
                .getSingleResult();
            return objectMapper.readValue(valor.toString(), ImagemCompressaoConfig.class);
        } catch (NoResultException e) {
            return ImagemCompressaoConfig.defaults();
        } catch (Exception e) {
            log.warn("plataforma_config.{} inválido, usando defaults: {}", CHAVE, e.getMessage());
            return ImagemCompressaoConfig.defaults();
        }
    }

    /** Grava a config (super admin) — upsert na chave-valor global. */
    @Transactional
    public ImagemCompressaoConfig atualizar(ImagemCompressaoConfig config, UUID actor) {
        if (config == null || config.tipos() == null || config.tipos().isEmpty()) {
            throw new BusinessException("Config de imagem não pode ser vazia");
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new BusinessException("Config de imagem inválida: " + e.getMessage());
        }
        entityManager.createNativeQuery("""
                INSERT INTO plataforma_config (chave, valor, updated_at, updated_by)
                VALUES (?1, ?2, now(), ?3)
                ON CONFLICT (chave) DO UPDATE SET valor = EXCLUDED.valor,
                    updated_at = now(), updated_by = EXCLUDED.updated_by
                """)
            .setParameter(1, CHAVE)
            .setParameter(2, json)
            .setParameter(3, actor)
            .executeUpdate();
        log.info("Config de compressão de imagem atualizada por {}", actor);
        return config;
    }
}
