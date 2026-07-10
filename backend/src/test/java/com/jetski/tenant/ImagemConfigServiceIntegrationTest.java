package com.jetski.tenant;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.tenant.api.dto.ImagemCompressaoConfig;
import com.jetski.tenant.internal.ImagemConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Config de compressão de imagem em plataforma_config: defaults quando ausente,
 * upsert e round-trip do JSON por tipo de documento.
 */
@DisplayName("ImagemConfigService — config de compressão por documento")
class ImagemConfigServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired ImagemConfigService imagemConfigService;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM plataforma_config WHERE chave = 'imagem_compressao'");
    }

    @Test
    @DisplayName("sem linha → defaults (SELFIE mais leve que documentos)")
    void ausenteDevolveDefaults() {
        ImagemCompressaoConfig cfg = imagemConfigService.get();

        assertThat(cfg.tipos()).containsKeys("IDENTIDADE", "SELFIE", "GRU_COMPROVANTE");
        assertThat(cfg.tipos().get("IDENTIDADE").qualidade()).isEqualTo(0.85);
        assertThat(cfg.tipos().get("SELFIE").maxDimensao()).isEqualTo(1280);
    }

    @Test
    @DisplayName("atualizar grava e o get devolve o mesmo JSON (round-trip)")
    void upsertRoundTrip() {
        UUID actor = UUID.randomUUID();
        ImagemCompressaoConfig nova = new ImagemCompressaoConfig(Map.of(
            "IDENTIDADE", new ImagemCompressaoConfig.Preset(1600, 0.7),
            "SELFIE", new ImagemCompressaoConfig.Preset(1024, 0.6)));

        imagemConfigService.atualizar(nova, actor);
        ImagemCompressaoConfig lida = imagemConfigService.get();

        assertThat(lida.tipos().get("IDENTIDADE").maxDimensao()).isEqualTo(1600);
        assertThat(lida.tipos().get("IDENTIDADE").qualidade()).isEqualTo(0.7);
        assertThat(lida.tipos().get("SELFIE").qualidade()).isEqualTo(0.6);

        // persistiu na chave global
        String chave = jdbc.queryForObject(
            "SELECT chave FROM plataforma_config WHERE chave = 'imagem_compressao'", String.class);
        assertThat(chave).isEqualTo("imagem_compressao");
    }

    @Test
    @DisplayName("valor corrompido → get devolve defaults (não quebra o upload)")
    void jsonInvalidoDevolveDefaults() {
        jdbc.update("""
            INSERT INTO plataforma_config (chave, valor, updated_at)
            VALUES ('imagem_compressao', 'not-json', now())
            """);

        ImagemCompressaoConfig cfg = imagemConfigService.get();
        assertThat(cfg.tipos()).containsKey("IDENTIDADE");
    }
}
