package com.jetski.locacoes.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.locacoes.api.dto.EmissaoEnvioConfig;
import com.jetski.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Chave global {@code emissao_envio} em {@code plataforma_config} — decide se os
 * e-mails da emissão saem dentro do request ou depois do commit.
 *
 * <p>Mesmo padrão de {@code ImagemConfigService}: chave-valor global (sem RLS),
 * SQL nativo, sem cache (a leitura é uma vez por emissão — operação rara e pesada;
 * cache derrubaria a promessa de "liga/desliga sem restart").
 *
 * <p>Chave ausente ⇒ o default vem de {@code jetski.emissao.email-assincrono}, e não
 * de uma constante. É isso que permite ao perfil de teste rodar tudo no modo síncrono
 * sem gravar nada no banco.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmissaoEnvioConfigService {

    private static final String CHAVE = "emissao_envio";

    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${jetski.emissao.email-assincrono:true}")
    private boolean assincronoPadrao;

    /** Config vigente; chave ausente ou corrompida ⇒ o default da aplicação. */
    @Transactional(readOnly = true)
    public EmissaoEnvioConfig get() {
        try {
            Object valor = entityManager.createNativeQuery(
                    "SELECT valor FROM plataforma_config WHERE chave = ?1")
                .setParameter(1, CHAVE)
                .getSingleResult();
            EmissaoEnvioConfig cfg = objectMapper.readValue(valor.toString(), EmissaoEnvioConfig.class);
            return new EmissaoEnvioConfig(cfg.assincronoOn(assincronoPadrao));
        } catch (NoResultException e) {
            return EmissaoEnvioConfig.defaults(assincronoPadrao);
        } catch (Exception e) {
            log.warn("plataforma_config.{} inválido, usando default {}: {}",
                CHAVE, assincronoPadrao, e.getMessage());
            return EmissaoEnvioConfig.defaults(assincronoPadrao);
        }
    }

    /** True quando o envio deve sair do request. Tolera falha de leitura (cai no default). */
    public boolean assincrono() {
        try {
            return Boolean.TRUE.equals(get().assincrono());
        } catch (Exception e) {
            log.warn("Falha ao ler a config de envio, usando default {}: {}", assincronoPadrao, e.getMessage());
            return assincronoPadrao;
        }
    }

    /** Grava a config (super admin) — upsert na chave-valor global. */
    @Transactional
    public EmissaoEnvioConfig atualizar(EmissaoEnvioConfig config, UUID actor) {
        if (config == null || config.assincrono() == null) {
            throw new BusinessException("Informe se o envio de e-mails da emissão é assíncrono");
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new BusinessException("Config de envio inválida: " + e.getMessage());
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
        log.info("Envio de e-mails da emissão: assincrono={} (por {})", config.assincrono(), actor);
        return config;
    }
}
