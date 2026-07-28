package com.jetski.tenant;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.api.dto.ImportPreviewDTO;
import com.jetski.tenant.internal.TenantImportService;
import com.jetski.tenant.internal.TenantResetService;
import com.jetski.tenant.internal.TenantResetService.Nivel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Import (restauração) de export de arquivamento: round-trip completo
 * (seed → export → reset → import → estado igual), validações contra zip
 * adulterado/externo e upload.
 *
 * <p>Lembrete estrutural: a suíte conecta como superuser (RLS não exercida) —
 * os cenários de segurança daqui provam as validações EXPLÍCITAS do serviço,
 * que são a defesa real também em produção.
 */
@DisplayName("TenantImportService (restauração de arquivamento)")
class TenantImportIntegrationTest extends AbstractIntegrationTest {

    /** Tenant PRÓPRIO e descartável — nunca os fixtures compartilhados. */
    private static final UUID TENANT = UUID.fromString("a2000000-0000-0000-0000-0000000000aa");
    private static final UUID OUTRO_TENANT = UUID.fromString("a2000000-0000-0000-0000-0000000000bb");

    @Autowired private TenantImportService importService;
    @Autowired private TenantResetService resetService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.jetski.shared.storage.StorageService storage;

    private String slug;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, status) "
            + "VALUES (?, 'import-teste', 'Import Teste Ltda', 'ATIVO') ON CONFLICT DO NOTHING", TENANT);
        slug = jdbc.queryForObject("SELECT slug FROM tenant WHERE id = ?", String.class, TENANT);

        jdbc.update("INSERT INTO modelo (id, tenant_id, nome, fabricante, preco_base_hora, ativo) "
            + "VALUES ('a2000000-0000-0000-0000-000000000001', ?, 'Import Modelo', 'Yamaha', 100, true) "
            + "ON CONFLICT DO NOTHING", TENANT);
        jdbc.update("INSERT INTO jetski (id, tenant_id, modelo_id, serie, ano, status, ativo) "
            + "VALUES ('a2000000-0000-0000-0000-000000000002', ?, 'a2000000-0000-0000-0000-000000000001', "
            + "'IMPORT-001', 2024, 'DISPONIVEL', true) ON CONFLICT DO NOTHING", TENANT);
        jdbc.update("INSERT INTO cliente (id, tenant_id, nome, documento, ativo) "
            + "VALUES ('a2000000-0000-0000-0000-000000000003', ?, 'Cliente Import', '111.222.333-44', true) "
            + "ON CONFLICT DO NOTHING", TENANT);
        jdbc.update("INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, "
            + "data_fim_prevista, status, ativo) "
            + "VALUES ('a2000000-0000-0000-0000-000000000004', ?, 'a2000000-0000-0000-0000-000000000001', "
            + "'a2000000-0000-0000-0000-000000000003', now() + interval '1 day', "
            + "now() + interval '1 day 2 hours', 'CONFIRMADA', true) ON CONFLICT DO NOTHING", TENANT);
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo) "
            + "VALUES ('a2000000-0000-0000-0000-000000000006', 'admin-import@t.com', 'Admin Import', true) "
            + "ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo) "
            + "VALUES ('a2000000-0000-0000-0000-000000000007', 'op-import@t.com', 'Op Import', true) "
            + "ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES "
            + "(?, 'a2000000-0000-0000-0000-000000000006', '{ADMIN_TENANT}', true) "
            + "ON CONFLICT DO NOTHING", TENANT);
        jdbc.update("INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES "
            + "(?, 'a2000000-0000-0000-0000-000000000007', '{OPERADOR}', true) "
            + "ON CONFLICT DO NOTHING", TENANT);
        // Um arquivo no storage do tenant, para o round-trip dos arquivos
        storage.putObject(TENANT + "/import-teste/foto.txt",
            "conteudo-foto".getBytes(StandardCharsets.UTF_8), "text/plain");
    }

    @AfterEach
    void tearDown() {
        for (String tabela : new String[]{
                "reserva", "cliente", "jetski", "modelo", "membro", "tenant_access"}) {
            jdbc.update("DELETE FROM " + tabela + " WHERE tenant_id = ?", TENANT);
        }
        jdbc.update("DELETE FROM usuario WHERE id IN "
            + "('a2000000-0000-0000-0000-000000000006', 'a2000000-0000-0000-0000-000000000007')");
        for (String chave : storage.listObjectKeys(TENANT + "/")) {
            storage.deleteFile(chave);
        }
        for (String chave : storage.listObjectKeys("_platform/exports/" + TENANT + "/")) {
            storage.deleteFile(chave);
        }
        TenantContext.clear();
    }

    private long count(String tabela) {
        Long n = jdbc.queryForObject(
            "SELECT count(*) FROM " + tabela + " WHERE tenant_id = ?", Long.class, TENANT);
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------------
    // Round-trip
    // ------------------------------------------------------------------

    @Test
    @DisplayName("round-trip: reset TOTAL seguido de import devolve o estado do export")
    void roundTrip() {
        // O reset gera o export automático — é ele que restauramos
        TenantResetService.Resultado reset = resetService.reset(TENANT, Nivel.TOTAL, slug);
        storage.deleteFile(TENANT + "/import-teste/foto.txt"); // simula perda do arquivo
        assertThat(count("reserva")).isZero();
        assertThat(count("membro")).isEqualTo(1); // reset TOTAL preserva o admin

        TenantImportService.Resultado r =
            importService.importar(TENANT, reset.exportKey(), slug, false);

        assertThat(count("modelo")).isEqualTo(1);
        assertThat(count("jetski")).isEqualTo(1);
        assertThat(count("cliente")).isEqualTo(1);
        assertThat(count("reserva")).isEqualTo(1);
        assertThat(count("membro")).isEqualTo(2); // estado do export, admin E operador
        assertThat(r.inseridos()).containsKeys("modelo", "jetski", "cliente", "reserva", "membro");
        assertThat(r.exportSegurancaKey()).startsWith("_platform/exports/" + TENANT + "/");
        assertThat(r.exportSegurancaBytes()).isPositive();

        // Arquivo do storage restaurado do zip
        byte[] foto = storage.getObject(TENANT + "/import-teste/foto.txt");
        assertThat(new String(foto, StandardCharsets.UTF_8)).isEqualTo("conteudo-foto");

        // Sequence realinhada: inserir membro novo (id via nextval) não colide
        Long maxId = jdbc.queryForObject("SELECT MAX(id) FROM membro", Long.class);
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo) VALUES "
            + "('a2000000-0000-0000-0000-000000000008', 'novo-import@t.com', 'Novo', true) "
            + "ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES "
            + "(?, 'a2000000-0000-0000-0000-000000000008', '{OPERADOR}', true)", TENANT);
        Long novoId = jdbc.queryForObject(
            "SELECT id FROM membro WHERE usuario_id = 'a2000000-0000-0000-0000-000000000008'",
            Long.class);
        assertThat(novoId).isGreaterThan(maxId);
        jdbc.update("DELETE FROM membro WHERE usuario_id = 'a2000000-0000-0000-0000-000000000008'");
        jdbc.update("DELETE FROM usuario WHERE id = 'a2000000-0000-0000-0000-000000000008'");
    }

    @Test
    @DisplayName("preview: linhas no zip × linhas atuais, sem alterar nada")
    void previewNaoAltera() {
        TenantResetService.Resultado reset = resetService.reset(TENANT, Nivel.OPERACIONAL, slug);

        ImportPreviewDTO p = importService.preview(TENANT, reset.exportKey());

        assertThat(p.linhasNoZip().get("reserva")).isEqualTo(1);
        assertThat(p.linhasAtuais().get("reserva")).isZero();
        assertThat(p.slugAtual()).isEqualTo(slug);
        assertThat(p.avisos()).anyMatch(a -> a.contains("linha da própria empresa"));
        assertThat(count("reserva")).isZero(); // preview não importa nada
    }

    // ------------------------------------------------------------------
    // Confirmação e key
    // ------------------------------------------------------------------

    @Test
    @DisplayName("slug errado → recusa sem apagar nada")
    void slugErrado() {
        TenantResetService.Resultado reset = resetService.reset(TENANT, Nivel.OPERACIONAL, slug);
        long clientesAntes = count("cliente");

        assertThatThrownBy(() -> importService.importar(TENANT, reset.exportKey(), "slug-errado", false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("slug");
        assertThat(count("cliente")).isEqualTo(clientesAntes);
    }

    @Test
    @DisplayName("key fora do prefixo da empresa → 404")
    void keyForaDoPrefixo() {
        assertThatThrownBy(() -> importService.importar(
                TENANT, "_platform/exports/" + OUTRO_TENANT + "/qualquer.zip", slug, false))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> importService.importar(
                TENANT, "_platform/exports/" + TENANT + "/../x.zip", slug, false))
            .isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------------
    // Zip adulterado / externo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("manifest de outra empresa → recusa")
    void manifestDeOutraEmpresa() {
        String key = gravarZip("craft-manifest.zip", Map.of(
            "manifest.json", manifest(OUTRO_TENANT),
            "dados/cliente.json", "[]"));

        assertThatThrownBy(() -> importService.importar(TENANT, key, slug, false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("outra empresa");
    }

    @Test
    @DisplayName("linha com tenant_id alheio → zip adulterado, nada apagado")
    void linhaDeOutroTenant() {
        String key = gravarZip("craft-adulterado.zip", Map.of(
            "manifest.json", manifest(TENANT),
            "dados/cliente.json", "[{\"id\":\"a2000000-0000-0000-0000-00000000cccc\","
                + "\"tenant_id\":\"" + OUTRO_TENANT + "\",\"nome\":\"Intruso\","
                + "\"documento\":\"000.000.000-00\",\"ativo\":true}]"));

        assertThatThrownBy(() -> importService.importar(TENANT, key, slug, false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("adulterado");
        assertThat(count("cliente")).isPositive(); // validação vem ANTES de apagar
    }

    @Test
    @DisplayName("path traversal em arquivos/ → recusa")
    void pathTraversal() {
        String keyDotDot = gravarZip("craft-traversal.zip", Map.of(
            "manifest.json", manifest(TENANT),
            "dados/cliente.json", "[]",
            "arquivos/../../evil.txt", "x"));
        assertThatThrownBy(() -> importService.importar(TENANT, keyDotDot, slug, false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("suspeito");

        String keyOutroPrefixo = gravarZip("craft-prefixo.zip", Map.of(
            "manifest.json", manifest(TENANT),
            "dados/cliente.json", "[]",
            "arquivos/" + OUTRO_TENANT + "/evil.txt", "x"));
        assertThatThrownBy(() -> importService.importar(TENANT, keyOutroPrefixo, slug, false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("fora do prefixo");
    }

    @Test
    @DisplayName("tabela desconhecida: recusa sem a flag, aviso com a flag")
    void tabelaDesconhecida() {
        String key = gravarZip("craft-desconhecida.zip", Map.of(
            "manifest.json", manifest(TENANT),
            "dados/tabela_que_nao_existe.json", "[]",
            "dados/cliente.json", "[]"));

        assertThatThrownBy(() -> importService.importar(TENANT, key, slug, false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("tabela_que_nao_existe");

        TenantImportService.Resultado r = importService.importar(TENANT, key, slug, true);
        assertThat(r.avisos()).anyMatch(a -> a.contains("tabela_que_nao_existe"));
    }

    @Test
    @DisplayName("FK global ausente (usuario inexistente) → recusa listando, nada apagado")
    void fkGlobalAusente() {
        String key = gravarZip("craft-fk.zip", Map.of(
            "manifest.json", manifest(TENANT),
            "dados/membro.json", "[{\"tenant_id\":\"" + TENANT + "\","
                + "\"usuario_id\":\"ffffffff-0000-0000-0000-000000000000\","
                + "\"papeis\":[\"OPERADOR\"],\"ativo\":true}]"));

        assertThatThrownBy(() -> importService.importar(TENANT, key, slug, false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("membro.usuario_id");
        assertThat(count("membro")).isEqualTo(2); // nada apagado
    }

    // ------------------------------------------------------------------
    // Upload
    // ------------------------------------------------------------------

    @Test
    @DisplayName("upload: zip válido vira key no prefixo da plataforma")
    void uploadValido() {
        byte[] zip = montarZip(Map.of(
            "manifest.json", manifest(TENANT),
            "dados/cliente.json", "[]"));

        String key = importService.receberUpload(TENANT, new ByteArrayInputStream(zip));

        assertThat(key).startsWith("_platform/exports/" + TENANT + "/");
        assertThat(key).contains("-upload-");
        assertThat(storage.fileExists(key)).isTrue();
    }

    @Test
    @DisplayName("upload sem manifest → recusa")
    void uploadSemManifest() {
        byte[] zip = montarZip(Map.of("dados/cliente.json", "[]"));

        assertThatThrownBy(() -> importService.receberUpload(TENANT, new ByteArrayInputStream(zip)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("manifest");
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private String manifest(UUID tenantId) {
        return "{\"tenantId\":\"" + tenantId + "\",\"slug\":\"" + slug + "\","
            + "\"razaoSocial\":\"Import Teste Ltda\",\"geradoEm\":\"2026-07-28T00:00:00Z\","
            + "\"tabelas\":1,\"arquivos\":0}";
    }

    private byte[] montarZip(Map<String, String> entradas) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                for (Map.Entry<String, String> e : entradas.entrySet()) {
                    zip.putNextEntry(new ZipEntry(e.getKey()));
                    zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String gravarZip(String nome, Map<String, String> entradas) {
        String key = "_platform/exports/" + TENANT + "/" + nome;
        storage.putObject(key, montarZip(entradas), "application/zip");
        return key;
    }
}
