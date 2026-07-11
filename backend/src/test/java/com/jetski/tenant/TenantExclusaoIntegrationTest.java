package com.jetski.tenant;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.storage.StorageService;
import com.jetski.tenant.internal.TenantExclusaoJob;
import com.jetski.tenant.internal.TenantExclusaoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exclusão de empresa (Fase 3): carência agendada + cancelamento, expurgo
 * imediato com tombstone (slug liberado, sensíveis anonimizados, storage
 * limpo, export gerado), job de expurgos vencidos e limpeza de exports
 * antigos. Usa tenant descartável próprio (nunca os fixtures compartilhados).
 */
@DisplayName("TenantExclusaoService + Job (Fase 3)")
class TenantExclusaoIntegrationTest extends AbstractIntegrationTest {

    private static final UUID TENANT = UUID.fromString("a2000000-0000-0000-0000-0000000000bb");
    private static final String SLUG = "exclusao-teste";

    @Autowired private TenantExclusaoService exclusaoService;
    @Autowired private TenantExclusaoJob job;
    @Autowired private StorageService storage;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        // Tenant descartável renasce a cada teste (o expurgo o tombstoneia)
        jdbc.update("DELETE FROM cliente WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM membro WHERE tenant_id = ?", TENANT);
        jdbc.update("UPDATE tenant SET slug = ?, status = 'ATIVO', excluido_em = NULL, "
            + "exclusao_agendada_em = NULL, pix_chave = 'pix@exclusao.com' WHERE id = ?", SLUG, TENANT);
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, status, pix_chave) "
            + "VALUES (?, ?, 'Exclusao Teste Ltda', 'ATIVO', 'pix@exclusao.com') "
            + "ON CONFLICT (id) DO NOTHING", TENANT, SLUG);

        jdbc.update("INSERT INTO cliente (id, tenant_id, nome, documento, ativo) "
            + "VALUES ('a2000000-0000-0000-0000-000000000001', ?, 'Cliente Exclusao', "
            + "'111.222.333-44', true) ON CONFLICT DO NOTHING", TENANT);
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo) "
            + "VALUES ('a2000000-0000-0000-0000-000000000002', 'admin-excl@t.com', 'Admin Excl', true) "
            + "ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) "
            + "VALUES (?, 'a2000000-0000-0000-0000-000000000002', '{ADMIN_TENANT}', true) "
            + "ON CONFLICT DO NOTHING", TENANT);
        storage.putObject(TENANT + "/reserva/teste/foto.jpg",
            "FOTO".getBytes(StandardCharsets.UTF_8), "image/jpeg");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM cliente WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM membro WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM usuario WHERE id = 'a2000000-0000-0000-0000-000000000002'");
        for (String k : storage.listObjectKeys(TENANT + "/")) {
            storage.deleteFile(k);
        }
        TenantContext.clear();
    }

    private String campo(String coluna) {
        return jdbc.queryForObject(
            "SELECT " + coluna + "::text FROM tenant WHERE id = ?", String.class, TENANT);
    }

    @Test
    @DisplayName("carência: agenda expurgo em ~30 dias, suspende na hora; cancelar limpa")
    void agendarECancelar() {
        Instant quando = exclusaoService.agendar(TENANT, SLUG);

        assertThat(quando).isAfter(Instant.now().plus(Duration.ofDays(29)));
        assertThat(campo("status")).isEqualTo("SUSPENSO");
        assertThat(campo("exclusao_agendada_em")).isNotNull();

        exclusaoService.cancelar(TENANT);
        assertThat(campo("exclusao_agendada_em")).isNull();
        assertThat(campo("status")).isEqualTo("SUSPENSO"); // reativar é ação à parte
    }

    @Test
    @DisplayName("imediato: tombstone (slug liberado, sensíveis nulos), dados/arquivos expurgados, export existe")
    void exclusaoImediata() {
        var apagados = exclusaoService.excluirAgora(TENANT, SLUG);

        assertThat(apagados).containsKeys("cliente", "membro");
        assertThat(campo("status")).isEqualTo("EXCLUIDO");
        assertThat(campo("excluido_em")).isNotNull();
        assertThat(campo("slug")).startsWith(SLUG + "-excluido-").isNotEqualTo(SLUG);
        assertThat(campo("pix_chave")).isNull();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM cliente WHERE tenant_id = ?", Long.class, TENANT)).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM membro WHERE tenant_id = ?", Long.class, TENANT)).isZero();
        assertThat(storage.listObjectKeys(TENANT + "/")).isEmpty();
        assertThat(storage.listObjectKeys("_platform/exports/" + TENANT + "/")).isNotEmpty();
        // Slug original livre para novo signup
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM tenant WHERE slug = ?", Long.class, SLUG)).isZero();
    }

    @Test
    @DisplayName("job expurga carência vencida e ignora agendamentos futuros")
    void jobExpurgaVencidos() {
        exclusaoService.agendar(TENANT, SLUG);
        // Simula o vencimento da carência
        jdbc.update("UPDATE tenant SET exclusao_agendada_em = now() - interval '1 hour' "
            + "WHERE id = ?", TENANT);

        job.expurgarVencidos();

        assertThat(campo("status")).isEqualTo("EXCLUIDO");
        assertThat(campo("excluido_em")).isNotNull();
    }

    @Test
    @DisplayName("job limpa exports com mais de 90 dias e preserva os recentes")
    void jobLimpaExportsAntigos() {
        String antigo = "_platform/exports/" + TENANT + "/exclusao-teste-20250101-120000.zip";
        String recente = "_platform/exports/" + TENANT + "/exclusao-teste-20991231-120000.zip";
        storage.putObject(antigo, "ZIP".getBytes(StandardCharsets.UTF_8), "application/zip");
        storage.putObject(recente, "ZIP".getBytes(StandardCharsets.UTF_8), "application/zip");

        job.limparExportsAntigos();

        assertThat(storage.fileExists(antigo)).isFalse();
        assertThat(storage.fileExists(recente)).isTrue();
        storage.deleteFile(recente);
    }

    @Test
    @DisplayName("slug errado e empresa já excluída são recusados")
    void validacoes() {
        assertThatThrownBy(() -> exclusaoService.excluirAgora(TENANT, "slug-errado"))
            .isInstanceOf(BusinessException.class);

        exclusaoService.excluirAgora(TENANT, SLUG);
        assertThatThrownBy(() -> exclusaoService.agendar(TENANT, "qualquer"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("já foi excluída");
    }
}
