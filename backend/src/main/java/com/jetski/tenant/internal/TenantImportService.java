package com.jetski.tenant.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.storage.StorageService;
import com.jetski.tenant.api.dto.ImportPreviewDTO;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.domain.TenantStatus;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import com.jetski.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Import (restauração) do export de arquivamento de uma empresa — o inverso do
 * {@link TenantExportService}: devolve o tenant ao estado do zip.
 *
 * <p><b>Semântica</b>: dentro de UMA transação, gera um export de segurança do
 * estado atual, apaga as tabelas importáveis ({@code expurgoCompleto} do reset)
 * e reinsere as linhas do zip com os IDs originais; depois (fora da transação —
 * storage não é transacional) substitui os arquivos do prefixo do tenant pelos
 * do zip. <b>Importável</b> = toda tabela com {@code tenant_id} MENOS as
 * {@code TABELAS_PRESERVADAS} do reset (ledger/auditoria/faturas nunca foram
 * apagadas; reimportar duplicaria o append-only). {@code dados/tenant.json} é
 * ignorado: a v1 não mexe na linha do tenant (o reset também não mexe).
 *
 * <p><b>Ordem de INSERT</b>: nenhuma FK do schema é DEFERRABLE, então a ordem é
 * topológica (Kahn sobre {@code pg_constraint}, pais antes de filhos), calculada
 * do catálogo a cada import — tabela nova entra no export dinamicamente e a
 * ordem acompanha sem manutenção (guard: {@code TenantImportOrderTest}).
 *
 * <p><b>Zip é input não confiável</b> (pode vir de upload externo): manifest
 * validado contra o tenant do path, entradas contra path traversal, toda linha
 * de {@code dados/*.json} contra {@code tenant_id} adulterado, e FKs para
 * tabelas globais (ex.: {@code usuario}) pré-validadas ANTES de apagar qualquer
 * coisa — a suíte roda como superuser, então nada disso pode ficar só na RLS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantImportService {

    private static final DateTimeFormatter STAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("America/Sao_Paulo"));

    private static final Pattern NOME_TABELA = Pattern.compile("[a-z_][a-z0-9_]*");
    private static final int MAX_ENTRADAS = 200_000;
    private static final long MAX_DESCOMPRIMIDO_BYTES = 5L * 1024 * 1024 * 1024; // 5 GB

    private final JdbcTemplate jdbcTemplate;
    private final TenantRepository tenantRepository;
    private final TenantExportService tenantExportService;
    private final TenantResetService tenantResetService;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    /** Resultado do import: linhas inseridas por tabela + export de segurança gerado antes. */
    public record Resultado(Map<String, Long> inseridos, int arquivosRestaurados,
        String exportSegurancaKey, long exportSegurancaBytes, List<String> avisos) {}

    private record FaseA(Map<String, Long> inseridos, String exportKey, long exportBytes) {}

    /** Conteúdo estrutural do zip, já validado (manifest + entradas por seção). */
    private record ZipLido(JsonNode manifest, Map<String, ZipEntry> dados, List<ZipEntry> arquivos) {}

    // ------------------------------------------------------------------
    // Preview (dry-run)
    // ------------------------------------------------------------------

    /** Dry-run: o que o import faria — linhas no zip × linhas atuais + avisos. */
    @Transactional(readOnly = true)
    public ImportPreviewDTO preview(UUID tenantId, String key) {
        Tenant tenant = carregarTenant(tenantId);
        validarPrefixo(tenantId, key);

        Path tmp = null;
        try {
            tmp = baixarParaTemp(key);
            try (ZipFile zip = new ZipFile(tmp.toFile())) {
                ZipLido lido = lerEValidarEstrutura(zip, tenantId);
                fixarContexto(tenantId);

                Set<String> conhecidas = tabelasComTenantId();
                Set<String> importaveis = importaveis(conhecidas);
                List<String> avisos = new ArrayList<>();
                avisosEstruturais(lido, tenant, conhecidas, importaveis, avisos);
                avisos.add("A linha da própria empresa (configurações, branding, SMTP) não é restaurada.");

                Map<String, Long> linhasNoZip = new LinkedHashMap<>();
                Map<String, Long> linhasAtuais = new LinkedHashMap<>();
                for (String tabela : new TreeSet<>(lido.dados().keySet())) {
                    if (!importaveis.contains(tabela)) {
                        continue;
                    }
                    String json = lerEntrada(zip, lido.dados().get(tabela));
                    Long noZip = jdbcTemplate.queryForObject(
                        "SELECT json_array_length(?::json)", Long.class, json);
                    Long atuais = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM " + tabela + " WHERE tenant_id = ?",
                        Long.class, tenantId);
                    linhasNoZip.put(tabela, noZip == null ? 0 : noZip);
                    linhasAtuais.put(tabela, atuais == null ? 0 : atuais);
                }

                List<String> fksAusentes = validarFksGlobais(zip, lido, importaveis);
                fksAusentes.forEach(f -> avisos.add("Referência global ausente: " + f));

                JsonNode m = lido.manifest();
                return new ImportPreviewDTO(key,
                    m.path("slug").asText(null), tenant.getSlug(),
                    m.path("geradoEm").asText(null),
                    linhasNoZip, linhasAtuais, lido.arquivos().size(), avisos);
            }
        } catch (IOException e) {
            throw new BusinessException("Não foi possível ler o zip do export: " + e.getMessage());
        } finally {
            apagarTemp(tmp);
        }
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    /**
     * Executa a restauração. Exige o slug ATUAL digitado (confirmação forte,
     * mesmo padrão do reset); o slug de dentro do zip divergente vira aviso.
     */
    public Resultado importar(UUID tenantId, String key, String confirmacaoSlug,
                              boolean ignorarTabelasDesconhecidas) {
        Tenant tenant = carregarTenant(tenantId);
        if (confirmacaoSlug == null || !confirmacaoSlug.trim().equals(tenant.getSlug())) {
            throw new BusinessException(
                "Confirmação inválida: digite o slug exato da empresa (" + tenant.getSlug() + ")");
        }
        validarPrefixo(tenantId, key);

        Path tmp = null;
        try {
            tmp = baixarParaTemp(key);
            try (ZipFile zip = new ZipFile(tmp.toFile())) {
                ZipLido lido = lerEValidarEstrutura(zip, tenantId);
                List<String> avisos = new ArrayList<>();

                // Fase A — transação única: validar → export de segurança → apagar → inserir
                FaseA faseA = transactionTemplate.execute(status -> {
                    fixarContexto(tenantId);
                    // Um reset/exclusão/import por vez por tenant (mesma chave dos demais)
                    jdbcTemplate.queryForObject(
                        "SELECT pg_advisory_xact_lock(hashtextextended(?, 42))", Object.class,
                        tenantId.toString());

                    Set<String> conhecidas = tabelasComTenantId();
                    Set<String> importaveis = importaveis(conhecidas);
                    avisosEstruturais(lido, tenant, conhecidas, importaveis, avisos);

                    List<String> desconhecidas = lido.dados().keySet().stream()
                        .filter(t -> !conhecidas.contains(t)).sorted().toList();
                    if (!desconhecidas.isEmpty() && !ignorarTabelasDesconhecidas) {
                        throw new BusinessException("O zip contém tabelas que não existem mais no "
                            + "sistema: " + String.join(", ", desconhecidas)
                            + ". Reenvie marcando para ignorá-las se quiser prosseguir sem esses dados.");
                    }

                    // Toda linha de todo dados/*.json pertence ao tenant alvo (zip adulterado)
                    for (String tabela : new TreeSet<>(lido.dados().keySet())) {
                        if (!importaveis.contains(tabela)) {
                            continue;
                        }
                        String json = lerEntradaQuieto(zip, lido.dados().get(tabela));
                        Long estranhas = jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM json_populate_recordset(NULL::public." + tabela
                            + ", ?::json) r WHERE r.tenant_id IS DISTINCT FROM ?::uuid",
                            Long.class, json, tenantId.toString());
                        if (estranhas != null && estranhas > 0) {
                            throw new BusinessException("Zip adulterado: " + estranhas + " linha(s) de "
                                + tabela + " não pertencem a esta empresa.");
                        }
                    }

                    List<String> fksAusentes = validarFksGlobais(zip, lido, importaveis);
                    if (!fksAusentes.isEmpty()) {
                        throw new BusinessException("O zip referencia registros globais que não "
                            + "existem mais: " + String.join("; ", fksAusentes));
                    }

                    // Export de segurança ANTES de apagar (falhou → aborta, nada apagado)
                    TenantExportService.Export seguranca = tenantExportService.exportar(tenantId);

                    tenantResetService.expurgoCompleto(tenantId);

                    Map<String, Long> inseridos = inserir(zip, lido, importaveis);
                    realinharSequences(importaveis);

                    return new FaseA(inseridos, seguranca.key(), seguranca.bytes());
                });

                // Fase B — arquivos do storage (fora da transação; falha vira aviso)
                int arquivos = restaurarArquivos(zip, lido, tenantId, avisos);

                // Fase C — trilha global síncrona
                long totalLinhas = faseA.inseridos().values().stream()
                    .mapToLong(Long::longValue).sum();
                eventPublisher.publishEvent(TenantStatusChangedEvent.of(
                    tenantId, "TENANT_IMPORT", tenant.getStatus().name(), tenant.getStatus().name(),
                    TenantContext.getUsuarioId(),
                    "key=" + key + "; tabelas=" + faseA.inseridos().size()
                        + "; linhas=" + totalLinhas + "; arquivos=" + arquivos
                        + "; exportSeguranca=" + faseA.exportKey(),
                    tenant.getRazaoSocial(), tenant.getSlug()));
                log.warn("[PLATFORM] IMPORT de empresa executado: tenant={} ({}), key={}, "
                    + "tabelas={}, linhas={}, arquivos={}, exportSeguranca={}",
                    tenantId, tenant.getSlug(), key, faseA.inseridos().size(), totalLinhas,
                    arquivos, faseA.exportKey());

                return new Resultado(faseA.inseridos(), arquivos,
                    faseA.exportKey(), faseA.exportBytes(), avisos);
            }
        } catch (IOException e) {
            throw new BusinessException("Não foi possível ler o zip do export: " + e.getMessage());
        } finally {
            apagarTemp(tmp);
        }
    }

    // ------------------------------------------------------------------
    // Upload de zip externo
    // ------------------------------------------------------------------

    /**
     * Recebe um zip de export baixado antes (retenção no storage é 90 dias) e o
     * grava no prefixo da plataforma, validado. O import continua sempre por key
     * — o nome gerado casa com o regex de expurgo do {@code TenantExclusaoJob},
     * então o upload também vale por 90 dias.
     */
    public String receberUpload(UUID tenantId, InputStream conteudo) {
        Tenant tenant = carregarTenant(tenantId);
        Path tmp = null;
        try {
            tmp = Files.createTempFile("tenant-import-upload-", ".zip");
            Files.copy(conteudo, tmp, StandardCopyOption.REPLACE_EXISTING);
            try (ZipFile zip = new ZipFile(tmp.toFile())) {
                lerEValidarEstrutura(zip, tenantId);
            }
            String key = String.format("_platform/exports/%s/%s-upload-%s.zip",
                tenantId, tenant.getSlug(), STAMP.format(Instant.now()));
            long bytes = Files.size(tmp);
            try (InputStream in = Files.newInputStream(tmp)) {
                storageService.putObject(key, in, bytes, "application/zip");
            }
            log.warn("[PLATFORM] Upload de export recebido: tenant={} ({}), key={}, {} bytes",
                tenantId, tenant.getSlug(), key, bytes);
            return key;
        } catch (IOException e) {
            throw new BusinessException("Arquivo inválido: não foi possível ler o zip ("
                + e.getMessage() + ")");
        } finally {
            apagarTemp(tmp);
        }
    }

    // ------------------------------------------------------------------
    // Validação estrutural do zip
    // ------------------------------------------------------------------

    private ZipLido lerEValidarEstrutura(ZipFile zip, UUID tenantId) throws IOException {
        Map<String, ZipEntry> dados = new LinkedHashMap<>();
        List<ZipEntry> arquivos = new ArrayList<>();
        ZipEntry manifestEntry = null;

        int entradas = 0;
        long descomprimido = 0;
        Enumeration<? extends ZipEntry> e = zip.entries();
        while (e.hasMoreElements()) {
            ZipEntry entry = e.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String nome = entry.getName();
            if (nome.contains("..") || nome.startsWith("/") || nome.contains("\\")) {
                throw new BusinessException("Zip inválido: entrada com caminho suspeito (" + nome + ")");
            }
            if (++entradas > MAX_ENTRADAS) {
                throw new BusinessException("Zip inválido: mais de " + MAX_ENTRADAS + " entradas.");
            }
            if (entry.getSize() > 0) {
                descomprimido += entry.getSize();
                if (descomprimido > MAX_DESCOMPRIMIDO_BYTES) {
                    throw new BusinessException("Zip inválido: conteúdo descomprimido acima do limite.");
                }
            }

            if (nome.equals("manifest.json")) {
                manifestEntry = entry;
            } else if (nome.startsWith("dados/") && nome.endsWith(".json")) {
                String tabela = nome.substring("dados/".length(), nome.length() - ".json".length());
                if (tabela.equals("tenant")) {
                    continue; // v1 não restaura a linha do tenant
                }
                if (!NOME_TABELA.matcher(tabela).matches()) {
                    throw new BusinessException("Zip inválido: nome de tabela suspeito (" + nome + ")");
                }
                dados.put(tabela, entry);
            } else if (nome.startsWith("arquivos/")) {
                String chave = nome.substring("arquivos/".length());
                if (!chave.startsWith(tenantId + "/")) {
                    throw new BusinessException(
                        "Zip inválido: arquivo fora do prefixo da empresa (" + nome + ")");
                }
                arquivos.add(entry);
            } else {
                throw new BusinessException("Zip inválido: entrada inesperada (" + nome + ")");
            }
        }

        if (manifestEntry == null) {
            throw new BusinessException("Zip inválido: manifest.json ausente — não parece um "
                + "export de arquivamento.");
        }
        if (dados.isEmpty()) {
            throw new BusinessException("Zip inválido: nenhuma entrada dados/*.json.");
        }

        JsonNode manifest = objectMapper.readTree(lerEntrada(zip, manifestEntry));
        String manifestTenant = manifest.path("tenantId").asText(null);
        if (manifestTenant == null || !manifestTenant.equals(tenantId.toString())) {
            throw new BusinessException("Este export pertence a outra empresa (manifest.tenantId="
                + manifestTenant + ").");
        }
        return new ZipLido(manifest, dados, arquivos);
    }

    private void avisosEstruturais(ZipLido lido, Tenant tenant, Set<String> conhecidas,
                                   Set<String> importaveis, List<String> avisos) {
        String slugNoZip = lido.manifest().path("slug").asText(null);
        if (slugNoZip != null && !slugNoZip.equals(tenant.getSlug())) {
            avisos.add("O zip foi gerado quando a empresa tinha o slug \"" + slugNoZip
                + "\" (atual: \"" + tenant.getSlug() + "\").");
        }
        List<String> preservadasNoZip = lido.dados().keySet().stream()
            .filter(t -> conhecidas.contains(t) && !importaveis.contains(t)).sorted().toList();
        if (!preservadasNoZip.isEmpty()) {
            avisos.add("Tabelas preservadas não são restauradas (nunca foram apagadas): "
                + String.join(", ", preservadasNoZip) + ".");
        }
        List<String> desconhecidas = lido.dados().keySet().stream()
            .filter(t -> !conhecidas.contains(t)).sorted().toList();
        if (!desconhecidas.isEmpty()) {
            avisos.add("Tabelas do zip que não existem mais no sistema: "
                + String.join(", ", desconhecidas) + ".");
        }
        List<String> semEntrada = importaveis.stream()
            .filter(t -> !lido.dados().containsKey(t)).sorted().toList();
        if (!semEntrada.isEmpty()) {
            avisos.add("Tabelas atuais sem entrada no zip (export antigo — ficarão vazias): "
                + String.join(", ", semEntrada) + ".");
        }
    }

    // ------------------------------------------------------------------
    // Validações dependentes do banco
    // ------------------------------------------------------------------

    /**
     * FKs de tabela importável apontando para FORA do conjunto importável
     * (globais: usuario, plano…): valida que todo valor referenciado no zip
     * ainda existe. Alvo {@code tenant} fica de fora — a existência do tenant
     * do path já foi verificada. Só FKs de coluna única (multi-coluna não
     * existe entre importável→global hoje; se surgir, o INSERT falha com erro
     * SQL claro e rollback).
     */
    private List<String> validarFksGlobais(ZipFile zip, ZipLido lido, Set<String> importaveis) {
        String csv = String.join(",", importaveis);
        List<Map<String, Object>> fks = jdbcTemplate.query(
            "SELECT src.relname AS tabela, a.attname AS coluna, "
            + "tgt.relname AS alvo, ta.attname AS alvo_col "
            + "FROM pg_constraint c "
            + "JOIN pg_class src ON src.oid = c.conrelid "
            + "JOIN pg_class tgt ON tgt.oid = c.confrelid "
            + "JOIN pg_attribute a ON a.attrelid = src.oid AND a.attnum = c.conkey[1] "
            + "JOIN pg_attribute ta ON ta.attrelid = tgt.oid AND ta.attnum = c.confkey[1] "
            + "WHERE c.contype = 'f' AND array_length(c.conkey, 1) = 1 "
            + "AND src.relnamespace = 'public'::regnamespace "
            + "AND src.relname = ANY(string_to_array(?, ',')) "
            + "AND NOT (tgt.relname = ANY(string_to_array(?, ','))) "
            + "AND tgt.relname <> 'tenant' ORDER BY 1, 2",
            (rs, i) -> Map.of("tabela", rs.getString("tabela"), "coluna", rs.getString("coluna"),
                "alvo", rs.getString("alvo"), "alvo_col", rs.getString("alvo_col")),
            csv, csv);

        List<String> ausentes = new ArrayList<>();
        for (Map<String, Object> fk : fks) {
            String tabela = (String) fk.get("tabela");
            ZipEntry entry = lido.dados().get(tabela);
            if (entry == null) {
                continue;
            }
            String coluna = (String) fk.get("coluna");
            String alvo = (String) fk.get("alvo");
            String alvoCol = (String) fk.get("alvo_col");
            String json = lerEntradaQuieto(zip, entry);
            List<String> valores = jdbcTemplate.queryForList(
                "SELECT DISTINCT r." + coluna + "::text "
                + "FROM json_populate_recordset(NULL::public." + tabela + ", ?::json) r "
                + "LEFT JOIN " + alvo + " t ON t." + alvoCol + " = r." + coluna + " "
                + "WHERE r." + coluna + " IS NOT NULL AND t." + alvoCol + " IS NULL",
                String.class, json);
            for (String v : valores) {
                ausentes.add(tabela + "." + coluna + " → " + alvo + " (" + v + ")");
            }
        }
        return ausentes;
    }

    // ------------------------------------------------------------------
    // Inserção
    // ------------------------------------------------------------------

    private Map<String, Long> inserir(ZipFile zip, ZipLido lido, Set<String> importaveis) {
        Map<String, List<String>> colunasPorTabela = colunasPorTabela(importaveis);
        Map<String, Long> inseridos = new LinkedHashMap<>();
        for (String tabela : ordemTopologica(importaveis)) {
            ZipEntry entry = lido.dados().get(tabela);
            if (entry == null) {
                continue;
            }
            String json = lerEntradaQuieto(zip, entry);
            List<String> chavesDoJson = jdbcTemplate.queryForList(
                "SELECT json_object_keys((?::json)->0)", String.class, json);
            if (chavesDoJson.isEmpty()) {
                continue; // []
            }
            // Interseção com o schema ATUAL: coluna dropada é ignorada; coluna
            // nova fica de fora do INSERT e o DEFAULT dela se aplica.
            Set<String> atuais = new HashSet<>(colunasPorTabela.get(tabela));
            List<String> colunas = chavesDoJson.stream().filter(atuais::contains).toList();
            if (colunas.isEmpty()) {
                continue;
            }
            String lista = String.join(", ", colunas);
            int n = jdbcTemplate.update(
                "INSERT INTO public." + tabela + " (" + lista + ") SELECT " + lista
                + " FROM json_populate_recordset(NULL::public." + tabela + ", ?::json)", json);
            if (n > 0) {
                inseridos.put(tabela, (long) n);
            }
        }
        return inseridos;
    }

    /**
     * Ordem topológica pais→filhos via {@code pg_constraint} (Kahn, desempate
     * alfabético — determinística). FKs para fora do conjunto são ignoradas: os
     * alvos (globais/preservadas) já existem. Auto-FK não existe nas
     * importáveis hoje (guard test); um ciclo real aborta com erro claro.
     */
    List<String> ordemTopologica(Set<String> tabelas) {
        String csv = String.join(",", tabelas);
        List<Map<String, Object>> deps = jdbcTemplate.query(
            "SELECT DISTINCT src.relname AS filho, tgt.relname AS pai "
            + "FROM pg_constraint c "
            + "JOIN pg_class src ON src.oid = c.conrelid "
            + "JOIN pg_class tgt ON tgt.oid = c.confrelid "
            + "WHERE c.contype = 'f' AND src.relnamespace = 'public'::regnamespace "
            + "AND src.relname = ANY(string_to_array(?, ',')) "
            + "AND tgt.relname = ANY(string_to_array(?, ',')) "
            + "AND src.relname <> tgt.relname",
            (rs, i) -> Map.of("filho", rs.getString("filho"), "pai", rs.getString("pai")),
            csv, csv);

        Map<String, Set<String>> pendentes = new HashMap<>();   // filho → pais que faltam
        Map<String, Set<String>> filhosDe = new HashMap<>();    // pai → filhos
        for (String t : tabelas) {
            pendentes.put(t, new HashSet<>());
        }
        for (Map<String, Object> d : deps) {
            String filho = (String) d.get("filho");
            String pai = (String) d.get("pai");
            pendentes.get(filho).add(pai);
            filhosDe.computeIfAbsent(pai, k -> new HashSet<>()).add(filho);
        }

        TreeSet<String> livres = new TreeSet<>();
        pendentes.forEach((t, pais) -> {
            if (pais.isEmpty()) {
                livres.add(t);
            }
        });
        List<String> ordem = new ArrayList<>(tabelas.size());
        Deque<String> fila = new ArrayDeque<>(livres);
        while (!fila.isEmpty()) {
            // Reordena a cada rodada para manter o desempate alfabético estável
            String t = fila.stream().sorted().findFirst().orElseThrow();
            fila.remove(t);
            ordem.add(t);
            for (String filho : filhosDe.getOrDefault(t, Set.of())) {
                Set<String> pais = pendentes.get(filho);
                pais.remove(t);
                if (pais.isEmpty() && !ordem.contains(filho) && !fila.contains(filho)) {
                    fila.add(filho);
                }
            }
        }
        if (ordem.size() != tabelas.size()) {
            List<String> resto = tabelas.stream().filter(t -> !ordem.contains(t)).sorted().toList();
            throw new IllegalStateException(
                "Ciclo de FKs entre tabelas importáveis — import impossível sem tratamento: " + resto);
        }
        return ordem;
    }

    /**
     * Realinha as sequences (serial/bigserial) das tabelas importáveis: os IDs
     * vieram preservados do zip. GREATEST + is_called=true: sequences são
     * globais entre tenants — nunca andam para trás. Exige GRANT UPDATE nas
     * sequences (V058).
     */
    private void realinharSequences(Set<String> importaveis) {
        List<Map<String, Object>> seqs = jdbcTemplate.query(
            "SELECT s.relname AS seq, t.relname AS tab, a.attname AS col "
            + "FROM pg_depend d "
            + "JOIN pg_class s ON s.oid = d.objid AND s.relkind = 'S' "
            + "JOIN pg_class t ON t.oid = d.refobjid "
            + "JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid "
            + "WHERE d.deptype IN ('a', 'i') AND t.relname = ANY(string_to_array(?, ','))",
            (rs, i) -> Map.of("seq", rs.getString("seq"), "tab", rs.getString("tab"),
                "col", rs.getString("col")),
            String.join(",", importaveis));
        for (Map<String, Object> s : seqs) {
            jdbcTemplate.queryForObject(
                "SELECT setval('" + s.get("seq") + "', GREATEST("
                + "(SELECT last_value FROM " + s.get("seq") + "), "
                + "COALESCE((SELECT MAX(" + s.get("col") + ") FROM " + s.get("tab") + "), 1)), true)",
                Long.class);
        }
    }

    // ------------------------------------------------------------------
    // Arquivos do storage (Fase B)
    // ------------------------------------------------------------------

    private int restaurarArquivos(ZipFile zip, ZipLido lido, UUID tenantId, List<String> avisos) {
        for (String chave : storageService.listObjectKeys(tenantId + "/")) {
            try {
                storageService.deleteFile(chave);
            } catch (RuntimeException e) {
                avisos.add("Falha ao remover arquivo atual " + chave + ": " + e.getMessage());
            }
        }
        int restaurados = 0;
        for (ZipEntry entry : lido.arquivos()) {
            String chave = entry.getName().substring("arquivos/".length());
            try {
                long size = entry.getSize();
                if (size >= 0) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        storageService.putObject(chave, in, size, contentType(chave));
                    }
                } else {
                    // Central directory sem tamanho (zip exótico): bufferiza
                    Path tmp = Files.createTempFile("tenant-import-arq-", ".bin");
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                        try (InputStream fin = Files.newInputStream(tmp)) {
                            storageService.putObject(chave, fin, Files.size(tmp), contentType(chave));
                        }
                    } finally {
                        apagarTemp(tmp);
                    }
                }
                restaurados++;
            } catch (IOException | RuntimeException e) {
                avisos.add("Falha ao restaurar arquivo " + chave + ": " + e.getMessage());
            }
        }
        return restaurados;
    }

    private String contentType(String chave) {
        String tipo = URLConnection.guessContentTypeFromName(chave);
        if (tipo != null) {
            return tipo;
        }
        if (chave.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private Set<String> tabelasComTenantId() {
        return new HashSet<>(jdbcTemplate.queryForList(
            "SELECT DISTINCT table_name FROM information_schema.columns "
            + "WHERE table_schema = 'public' AND column_name = 'tenant_id'", String.class));
    }

    private Set<String> importaveis(Set<String> conhecidas) {
        Set<String> r = new HashSet<>(conhecidas);
        r.removeAll(TenantResetService.TABELAS_PRESERVADAS);
        return r;
    }

    private Map<String, List<String>> colunasPorTabela(Set<String> tabelas) {
        Map<String, List<String>> r = new HashMap<>();
        jdbcTemplate.query(
            "SELECT table_name, column_name FROM information_schema.columns "
            + "WHERE table_schema = 'public' AND table_name = ANY(string_to_array(?, ','))",
            rs -> {
                r.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
            },
            String.join(",", tabelas));
        return r;
    }

    private Path baixarParaTemp(String key) throws IOException {
        Path tmp = Files.createTempFile("tenant-import-", ".zip");
        try (InputStream in = storageService.getObjectStream(key)) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }

    private String lerEntrada(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String lerEntradaQuieto(ZipFile zip, ZipEntry entry) {
        try {
            return lerEntrada(zip, entry);
        } catch (IOException e) {
            throw new BusinessException("Não foi possível ler " + entry.getName() + " do zip: "
                + e.getMessage());
        }
    }

    private void validarPrefixo(UUID tenantId, String key) {
        String prefixo = "_platform/exports/" + tenantId + "/";
        if (key == null || !key.startsWith(prefixo) || key.contains("..")) {
            throw new NotFoundException("Export não encontrado: " + key);
        }
    }

    private Tenant carregarTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Empresa não encontrada: " + tenantId));
        if (tenant.getStatus() == TenantStatus.EXCLUIDO) {
            throw new BusinessException(
                "Empresa excluída não pode ser restaurada por aqui (v1 restaura empresa existente).");
        }
        return tenant;
    }

    private void fixarContexto(UUID tenantId) {
        jdbcTemplate.queryForObject(
            "SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
    }

    private void apagarTemp(Path tmp) {
        if (tmp != null) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // temp file órfão não é erro
            }
        }
    }
}
