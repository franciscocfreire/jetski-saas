package com.jetski.tenant.internal;

import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.storage.StorageService;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Export de arquivamento de uma empresa (super admin): um .zip com TODOS os
 * dados do tenant — {@code dados/<tabela>.json} (uma entrada por tabela
 * multi-tenant, via {@code row_to_json}) + {@code arquivos/...} (objetos do
 * storage sob o prefixo do tenant) + {@code manifest.json}.
 *
 * <p>Roda ANTES de reset/expurgo (decisão de produto: automático) — cobre
 * guarda legal de documentos emitidos à Marinha e pedidos LGPD posteriores.
 * O zip vai para o prefixo da PLATAFORMA ({@code _platform/exports/...}),
 * fora do prefixo do tenant; retenção alvo de 90 dias (expurgo dos exports
 * antigos acontece no job da Fase 3).
 *
 * <p>A lista de tabelas é DINÂMICA (information_schema): tabela nova com
 * tenant_id entra no export automaticamente, sem manutenção — diferente do
 * reset, que exige classificação explícita (apagar é decisão; arquivar não).
 * Montagem em arquivo temporário (não em memória): fotos somam centenas de MB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantExportService {

    private static final DateTimeFormatter STAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("America/Sao_Paulo"));

    private final JdbcTemplate jdbcTemplate;
    private final TenantRepository tenantRepository;
    private final StorageService storageService;

    /** Resultado do export: chave do zip no storage + tamanho. */
    public record Export(String key, long bytes, int tabelas, int arquivos) {}

    /**
     * Gera o zip de arquivamento e grava no prefixo da plataforma.
     * Transacional-readOnly para os SELECTs; o upload acontece no final.
     */
    @Transactional(readOnly = true)
    public Export exportar(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Empresa não encontrada: " + tenantId));

        // RLS do tenant alvo (transaction-local) — mesmos motivos do reset
        jdbcTemplate.queryForObject(
            "SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());

        List<String> tabelas = jdbcTemplate.queryForList(
            "SELECT DISTINCT table_name FROM information_schema.columns "
            + "WHERE table_schema = 'public' AND column_name = 'tenant_id' ORDER BY table_name",
            String.class);

        String stamp = STAMP.format(Instant.now());
        String zipKey = String.format("_platform/exports/%s/%s-%s.zip",
            tenantId, tenant.getSlug(), stamp);

        Path tmp = null;
        try {
            tmp = Files.createTempFile("tenant-export-", ".zip");
            int totalArquivos;
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp))) {
                // 1. Dados: uma entrada JSON por tabela (linhas do tenant)
                for (String tabela : tabelas) {
                    String json = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(json_agg(t), '[]'::json)::text FROM "
                        + tabela + " t WHERE tenant_id = ?", String.class, tenantId);
                    entrada(zip, "dados/" + tabela + ".json", json);
                }
                // A própria linha do tenant (configurações, branding, PIX…)
                String tenantJson = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(json_agg(t), '[]'::json)::text FROM tenant t WHERE id = ?",
                    String.class, tenantId);
                entrada(zip, "dados/tenant.json", tenantJson);

                // 2. Arquivos do storage do tenant (fotos, documentos, assinaturas)
                List<String> chaves = storageService.listObjectKeys(tenantId + "/");
                totalArquivos = chaves.size();
                for (String chave : chaves) {
                    zip.putNextEntry(new ZipEntry("arquivos/" + chave));
                    zip.write(storageService.getObject(chave));
                    zip.closeEntry();
                }

                // 3. Manifesto
                entrada(zip, "manifest.json", String.format(
                    "{\"tenantId\":\"%s\",\"slug\":\"%s\",\"razaoSocial\":%s,"
                    + "\"geradoEm\":\"%s\",\"tabelas\":%d,\"arquivos\":%d}",
                    tenantId, tenant.getSlug(), jsonString(tenant.getRazaoSocial()),
                    Instant.now(), tabelas.size() + 1, totalArquivos));
            }

            long bytes = Files.size(tmp);
            try (InputStream in = Files.newInputStream(tmp)) {
                storageService.putObject(zipKey, in, bytes, "application/zip");
            }
            log.warn("[PLATFORM] Export de empresa gerado: tenant={} ({}), key={}, {} tabelas, "
                + "{} arquivos, {} bytes", tenantId, tenant.getSlug(), zipKey,
                tabelas.size() + 1, totalArquivos, bytes);
            return new Export(zipKey, bytes, tabelas.size() + 1, totalArquivos);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar o export do tenant " + tenantId, e);
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            }
        }
    }

    /** Exports já gerados para a empresa (chave + metadados básicos). */
    public List<String> listar(UUID tenantId) {
        return storageService.listObjectKeys("_platform/exports/" + tenantId + "/");
    }

    /** Bytes de um export (download autenticado pelo super admin). */
    public byte[] baixar(UUID tenantId, String key) {
        String prefixo = "_platform/exports/" + tenantId + "/";
        if (!key.startsWith(prefixo) || key.contains("..")) {
            throw new NotFoundException("Export não encontrado: " + key);
        }
        return storageService.getObject(key);
    }

    private void entrada(ZipOutputStream zip, String nome, String conteudo) throws IOException {
        zip.putNextEntry(new ZipEntry(nome));
        zip.write((conteudo != null ? conteudo : "[]").getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String jsonString(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
