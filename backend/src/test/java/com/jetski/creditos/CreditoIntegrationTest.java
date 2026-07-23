package com.jetski.creditos;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Créditos de emissão: ledger append-only, adesão idempotente, lançamento
 * do super admin (cross-tenant via set_config) e APIs do tenant.
 */
@AutoConfigureMockMvc
@DisplayName("Créditos de Emissão Tests")
class CreditoIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired CreditoService creditoService;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @MockBean OPAAuthorizationService opaAuthorizationService;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID TENANT_MARINA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ACME);
        TenantContext.setUsuarioId(USER_ID);
        try {
            jdbcTemplate.execute("INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id, linked_at) " +
                                 "VALUES ('11111111-1111-1111-1111-111111111111', 'keycloak', '11111111-1111-1111-1111-111111111111', NOW())");
        } catch (Exception ignored) {
            // já existe
        }
        when(opaAuthorizationService.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());
        // O ledger é append-only (trigger); a limpeza entre testes exige desabilitar
        // o trigger — algo que só o owner do banco consegue (a própria garantia).
        jdbcTemplate.execute("ALTER TABLE credito_lancamento DISABLE TRIGGER trg_credito_lancamento_append_only");
        jdbcTemplate.update("DELETE FROM credito_lancamento WHERE tenant_id IN (?, ?)", TENANT_ACME, TENANT_MARINA);
        jdbcTemplate.execute("ALTER TABLE credito_lancamento ENABLE TRIGGER trg_credito_lancamento_append_only");
        jdbcTemplate.update("DELETE FROM credito_compra WHERE tenant_id IN (?, ?)", TENANT_ACME, TENANT_MARINA);
        // Preço conhecido para os testes (independe da ordem de execução)
        jdbcTemplate.update("UPDATE plataforma_config SET valor = '5.00' WHERE chave = 'creditos_preco_unitario'");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject(USER_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"));
    }

    /** Comprovante fake como data-URL PNG — conteúdo distinto por semente (dedupe é por sha256). */
    private static String comprovantePng(String semente) {
        return "data:image/png;base64," + java.util.Base64.getEncoder()
            .encodeToString(("PNG-COMPROVANTE-" + semente).getBytes());
    }

    // ============================== Ledger / regras ==============================

    @Test
    @DisplayName("Adesão credita uma única vez (idempotente)")
    void testAdesaoIdempotente() {
        creditoService.lancarAdesao(TENANT_ACME);
        creditoService.lancarAdesao(TENANT_ACME);

        Integer linhas = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM credito_lancamento WHERE tenant_id = ? AND tipo = 'ADESAO'",
            Integer.class, TENANT_ACME);
        assertThat(linhas).isEqualTo(1);
        assertThat(creditoService.saldo(TENANT_ACME)).isEqualTo(5);
    }

    @Test
    @DisplayName("Ledger é append-only: UPDATE e DELETE são rejeitados pelo banco")
    void testAppendOnly() {
        creditoService.lancarAdesao(TENANT_ACME);

        assertThatThrownBy(() ->
            jdbcTemplate.update("UPDATE credito_lancamento SET quantidade = 999 WHERE tenant_id = ?", TENANT_ACME))
            .hasMessageContaining("append-only");
        assertThatThrownBy(() ->
            jdbcTemplate.update("DELETE FROM credito_lancamento WHERE tenant_id = ?", TENANT_ACME))
            .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("Verificação de saldo bloqueia quando não há créditos")
    void testVerificacaoSemSaldo() {
        assertThatThrownBy(() -> creditoService.verificarSaldoDisponivel(TENANT_ACME))
            .hasMessageContaining("Créditos de emissão esgotados");
    }

    @Test
    @DisplayName("Débito consome 1 crédito com saldo_apos correto; sem saldo, lança erro")
    void testDebito() {
        creditoService.lancarAdesao(TENANT_ACME); // +5
        UUID doc1 = UUID.randomUUID();
        UUID reservaDebito = UUID.randomUUID();

        // debitar exige transação do chamador (MANDATORY) — simula a emissão
        transactionTemplate.executeWithoutResult(tx ->
            creditoService.debitarEmissaoDocumento(TENANT_ACME, doc1, reservaDebito));

        assertThat(creditoService.saldo(TENANT_ACME)).isEqualTo(4);
        Integer saldoApos = jdbcTemplate.queryForObject(
            "SELECT saldo_apos FROM credito_lancamento WHERE tipo = 'CONSUMO' AND referencia_id = ?",
            Integer.class, doc1);
        assertThat(saldoApos).isEqualTo(4);

        // zera o saldo via estorno e tenta debitar de novo
        transactionTemplate.executeWithoutResult(tx ->
            creditoService.lancarAjuste(TENANT_ACME, -4, "Zerando para teste", USER_ID));
        assertThat(creditoService.saldo(TENANT_ACME)).isZero();

        assertThatThrownBy(() ->
            transactionTemplate.executeWithoutResult(tx ->
                creditoService.debitarEmissaoDocumento(TENANT_ACME, UUID.randomUUID(), UUID.randomUUID())))
            .hasMessageContaining("Créditos de emissão esgotados");
    }

    // ============================== APIs do tenant ==============================

    @Test
    @DisplayName("Saldo e extrato do tenant refletem os lançamentos")
    void testSaldoEExtrato() throws Exception {
        creditoService.lancarAdesao(TENANT_ACME);

        mockMvc.perform(get("/v1/tenants/{tenantId}/creditos/saldo", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saldo").value(5));

        mockMvc.perform(get("/v1/tenants/{tenantId}/creditos/extrato", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].tipo").value("ADESAO"))
            .andExpect(jsonPath("$[0].quantidade").value(5))
            .andExpect(jsonPath("$[0].saldoApos").value(5));
    }

    @Test
    @DisplayName("VENDEDOR não consulta créditos (403)")
    void testForbiddenParaVendedor() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/creditos/saldo", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(jwt().jwt(j -> j.subject(USER_ID.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_VENDEDOR"))))
            .andExpect(status().isForbidden());
    }

    // ============================== Plataforma ==============================

    @Test
    @DisplayName("Admin lança créditos para outro tenant (set_config fura sessão) com motivo")
    void testPlatformLancamentoCrossTenant() throws Exception {
        mockMvc.perform(post("/v1/platform/creditos/{tenantId}", TENANT_MARINA)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 50, \"motivo\": \"Compra de pacote inicial\"}")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tipo").value("AJUSTE"))
            .andExpect(jsonPath("$.saldoApos").value(50));

        // Consulta cross-tenant de saldos inclui o lançamento
        mockMvc.perform(get("/v1/platform/creditos")
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.slug == 'marina-bay')].saldo").value(50));
    }

    @Test
    @DisplayName("Lançamento sem motivo é rejeitado (400)")
    void testPlatformLancamentoSemMotivo() throws Exception {
        mockMvc.perform(post("/v1/platform/creditos/{tenantId}", TENANT_MARINA)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 10, \"motivo\": \"\"}")
                .with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Débito que deixaria saldo negativo é rejeitado")
    void testPlatformDebitoAlemDoSaldo() throws Exception {
        mockMvc.perform(post("/v1/platform/creditos/{tenantId}", TENANT_MARINA)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": -5, \"motivo\": \"Estorno indevido\"}")
                .with(admin()))
            .andExpect(status().isBadRequest());
    }

    // ============================== Compra de créditos (PIX) ==============================

    @Test
    @DisplayName("Fluxo de compra: solicitar → pendente na plataforma → aprovar credita no ledger")
    void testCompraFluxoCompleto() throws Exception {
        // Tenant solicita anexando o comprovante (txid opcional/legado ainda aceito)
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 30, \"pixTxid\": \"E12345678202607021234\", " +
                         "\"comprovanteBase64\": \"" + comprovantePng("fluxo-completo") + "\"}")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDENTE"))
            .andExpect(jsonPath("$.temComprovante").value(true));

        // Aparece na fila do super admin
        mockMvc.perform(get("/v1/platform/creditos/compras")
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].pixTxid").value("E12345678202607021234"))
            .andExpect(jsonPath("$[0].temComprovante").value(true))
            .andExpect(jsonPath("$[0].quantidade").value(30));

        String compraId = jdbcTemplate.queryForObject(
            "SELECT id::text FROM credito_compra WHERE tenant_id = ? AND pix_txid = 'E12345678202607021234'",
            String.class, TENANT_ACME);

        // Aprovação credita no ledger e marca APROVADA
        mockMvc.perform(post("/v1/platform/creditos/compras/{t}/{c}/aprovar", TENANT_ACME, compraId)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APROVADA"));

        assertThat(creditoService.saldo(TENANT_ACME)).isEqualTo(30);

        // Aprovar de novo → 400 (idempotência por status)
        mockMvc.perform(post("/v1/platform/creditos/compras/{t}/{c}/aprovar", TENANT_ACME, compraId)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isBadRequest());

        // Mesmo txid de novo (comprovante diferente) → 400
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 10, \"pixTxid\": \"E12345678202607021234\", " +
                         "\"comprovanteBase64\": \"" + comprovantePng("txid-repetido") + "\"}")
                .with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rejeição exige motivo e não credita")
    void testCompraRejeitada() throws Exception {
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 10, \"pixTxid\": \"TX-REJ-1\", " +
                         "\"comprovanteBase64\": \"" + comprovantePng("rejeitada") + "\"}")
                .with(admin()))
            .andExpect(status().isOk());
        String compraId = jdbcTemplate.queryForObject(
            "SELECT id::text FROM credito_compra WHERE tenant_id = ? AND pix_txid = 'TX-REJ-1'",
            String.class, TENANT_ACME);

        // Sem motivo → 400
        mockMvc.perform(post("/v1/platform/creditos/compras/{t}/{c}/rejeitar", TENANT_ACME, compraId)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"observacao\": \"\"}")
                .with(admin()))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/v1/platform/creditos/compras/{t}/{c}/rejeitar", TENANT_ACME, compraId)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"observacao\": \"PIX não localizado no extrato\"}")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJEITADA"));

        assertThat(creditoService.saldo(TENANT_ACME)).isZero();
    }

    // ==================== Comprovante PIX por upload (V053) ====================

    @Test
    @DisplayName("Compra sem txid: comprovante basta; aprovação usa o id da compra no motivo")
    void testCompraSoComComprovante() throws Exception {
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 20, \"comprovanteBase64\": \"" + comprovantePng("sem-txid") + "\"}")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDENTE"))
            .andExpect(jsonPath("$.pixTxid").doesNotExist())
            .andExpect(jsonPath("$.temComprovante").value(true));

        String compraId = jdbcTemplate.queryForObject(
            "SELECT id::text FROM credito_compra WHERE tenant_id = ? AND pix_txid IS NULL",
            String.class, TENANT_ACME);

        mockMvc.perform(post("/v1/platform/creditos/compras/{t}/{c}/aprovar", TENANT_ACME, compraId)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APROVADA"));

        String motivo = jdbcTemplate.queryForObject(
            "SELECT motivo FROM credito_lancamento WHERE tenant_id = ? AND tipo = 'AJUSTE'",
            String.class, TENANT_ACME);
        assertThat(motivo).isEqualTo("Compra de créditos — comprovante " + compraId.substring(0, 8));
        assertThat(creditoService.saldo(TENANT_ACME)).isEqualTo(20);
    }

    @Test
    @DisplayName("Comprovante é obrigatório (400 sem ele)")
    void testCompraSemComprovante() throws Exception {
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 10, \"pixTxid\": \"TX-SEM-COMPROVANTE\"}")
                .with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Envie o comprovante do PIX (foto ou PDF)"));
    }

    @Test
    @DisplayName("Mesmo comprovante (sha256) não pode ser usado duas vezes")
    void testDedupePorSha256() throws Exception {
        String comprovante = comprovantePng("dedupe");
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 10, \"comprovanteBase64\": \"" + comprovante + "\"}")
                .with(admin()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 5, \"comprovanteBase64\": \"" + comprovante + "\"}")
                .with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Este comprovante já foi usado em outra compra."));
    }

    @Test
    @DisplayName("Base64 inválido e mime não aceito → 400 com mensagem clara")
    void testComprovanteInvalido() throws Exception {
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 10, \"comprovanteBase64\": \"data:image/png;base64,%%%nao-e-base64%%%\"}")
                .with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Comprovante inválido — envie a foto ou o PDF do comprovante"));

        String txt = java.util.Base64.getEncoder().encodeToString("apenas texto".getBytes());
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 10, \"comprovanteBase64\": \"data:text/plain;base64," + txt + "\"}")
                .with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Formato do comprovante não suportado (use JPG, PNG, WEBP ou PDF)"));
    }

    @Test
    @DisplayName("Base64 puro sem data-URL: PDF identificado pelos magic bytes")
    void testComprovantePdfPuro() throws Exception {
        String pdf = java.util.Base64.getEncoder().encodeToString("%PDF-1.4 comprovante fake".getBytes());
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 10, \"comprovanteBase64\": \"" + pdf + "\"}")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.temComprovante").value(true));

        String ct = jdbcTemplate.queryForObject(
            "SELECT comprovante_content_type FROM credito_compra WHERE tenant_id = ?",
            String.class, TENANT_ACME);
        assertThat(ct).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("Download do comprovante: tenant e plataforma servem os bytes com o content type")
    void testDownloadComprovante() throws Exception {
        byte[] conteudo = "PNG-COMPROVANTE-download".getBytes();
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 10, \"comprovanteBase64\": \"data:image/png;base64,"
                    + java.util.Base64.getEncoder().encodeToString(conteudo) + "\"}")
                .with(admin()))
            .andExpect(status().isOk());
        String compraId = jdbcTemplate.queryForObject(
            "SELECT id::text FROM credito_compra WHERE tenant_id = ?", String.class, TENANT_ACME);

        mockMvc.perform(get("/v1/tenants/{tenantId}/creditos/compras/{compraId}/comprovante",
                    TENANT_ACME, compraId)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(content().bytes(conteudo));

        mockMvc.perform(get("/v1/platform/creditos/compras/{t}/{c}/comprovante", TENANT_ACME, compraId)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(content().bytes(conteudo));
    }

    @Test
    @DisplayName("Download sem comprovante (compra legado) ou compra inexistente → 404")
    void testDownloadComprovante404() throws Exception {
        // Compra legado (pré-V053): só txid, sem comprovante
        UUID compraLegado = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO credito_compra (id, tenant_id, quantidade, pix_txid, status) " +
            "VALUES (?, ?, 10, 'TX-LEGADO-1', 'PENDENTE')", compraLegado, TENANT_ACME);

        mockMvc.perform(get("/v1/tenants/{tenantId}/creditos/compras/{compraId}/comprovante",
                    TENANT_ACME, compraLegado)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/v1/platform/creditos/compras/{t}/{c}/comprovante",
                    TENANT_ACME, UUID.randomUUID())
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Preço configurável: PUT muda o preço e novas compras usam o valor novo")
    void testPrecoConfiguravel() throws Exception {
        // preço seed = 5.00 → R$ 150 = 30 créditos (validado no fluxo completo)
        mockMvc.perform(put("/v1/platform/creditos/config")
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"precoUnitario\": 10.00}")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.precoUnitario").value(10.00));

        // 15 créditos a R$ 10 → valor esperado R$ 150,00 (snapshot do preço)
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 15, \"pixTxid\": \"TX-PRECO-1\", " +
                         "\"comprovanteBase64\": \"" + comprovantePng("preco") + "\"}")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quantidade").value(15))
            .andExpect(jsonPath("$.valorPago").value(150.00))
            .andExpect(jsonPath("$.precoUnitario").value(10.00));

        // Quantidade inválida → 400
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 0, \"pixTxid\": \"TX-PRECO-2\", " +
                         "\"comprovanteBase64\": \"" + comprovantePng("preco-qtd-invalida") + "\"}")
                .with(admin()))
            .andExpect(status().isBadRequest());

        // Preço inválido → 400; restaura o preço seed para os demais testes
        mockMvc.perform(put("/v1/platform/creditos/config")
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"precoUnitario\": 0}")
                .with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/v1/platform/creditos/config")
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"precoUnitario\": 5.00}")
                .with(admin()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PIX copia-e-cola traz valor exato (qtd × preço) e CRC")
    void testPixCopiaECola() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/creditos/pix?quantidade=50", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quantidade").value(50))
            .andExpect(jsonPath("$.valor").value(250.00))
            .andExpect(jsonPath("$.copiaECola", org.hamcrest.Matchers.startsWith("000201")))
            .andExpect(jsonPath("$.copiaECola", org.hamcrest.Matchers.containsString("5406250.00")))
            .andExpect(jsonPath("$.copiaECola", org.hamcrest.Matchers.matchesRegex(".*6304[0-9A-F]{4}$")));

        mockMvc.perform(get("/v1/tenants/{tenantId}/creditos/pix?quantidade=0", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Débito grava a reserva no motivo (extrato autoexplicativo)")
    void testDebitoGravaReservaNoMotivo() {
        creditoService.lancarAdesao(TENANT_ACME);
        UUID doc = UUID.randomUUID();
        UUID reserva = UUID.fromString("574ba29e-ee20-498e-8ae8-5798a1fea9f3");

        transactionTemplate.executeWithoutResult(tx ->
            creditoService.debitarEmissaoDocumento(TENANT_ACME, doc, reserva));

        String motivo = jdbcTemplate.queryForObject(
            "SELECT motivo FROM credito_lancamento WHERE tipo = 'CONSUMO' AND referencia_id = ?",
            String.class, doc);
        assertThat(motivo).isEqualTo("Reserva 574ba29e");
    }

    @Test
    @DisplayName("OPA nega plataforma para quem não é super admin (403)")
    void testPlatformDenyOpa() throws Exception {
        when(opaAuthorizationService.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(false).tenantIsValid(true).build());

        mockMvc.perform(get("/v1/platform/creditos")
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isForbidden());
    }
}
