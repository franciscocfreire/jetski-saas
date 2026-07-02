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
        assertThat(creditoService.saldo(TENANT_ACME)).isEqualTo(20);
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
        creditoService.lancarAdesao(TENANT_ACME); // +20
        UUID doc1 = UUID.randomUUID();
        UUID reservaDebito = UUID.randomUUID();

        // debitar exige transação do chamador (MANDATORY) — simula a emissão
        transactionTemplate.executeWithoutResult(tx ->
            creditoService.debitarEmissaoDocumento(TENANT_ACME, doc1, reservaDebito));

        assertThat(creditoService.saldo(TENANT_ACME)).isEqualTo(19);
        Integer saldoApos = jdbcTemplate.queryForObject(
            "SELECT saldo_apos FROM credito_lancamento WHERE tipo = 'CONSUMO' AND referencia_id = ?",
            Integer.class, doc1);
        assertThat(saldoApos).isEqualTo(19);

        // zera o saldo via estorno e tenta debitar de novo
        transactionTemplate.executeWithoutResult(tx ->
            creditoService.lancarAjuste(TENANT_ACME, -19, "Zerando para teste", USER_ID));
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
            .andExpect(jsonPath("$.saldo").value(20));

        mockMvc.perform(get("/v1/tenants/{tenantId}/creditos/extrato", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].tipo").value("ADESAO"))
            .andExpect(jsonPath("$[0].quantidade").value(20))
            .andExpect(jsonPath("$[0].saldoApos").value(20));
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
        // Tenant solicita informando o txid do PIX
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 30, \"pixTxid\": \"E12345678202607021234\"}")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDENTE"));

        // Aparece na fila do super admin
        mockMvc.perform(get("/v1/platform/creditos/compras")
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].pixTxid").value("E12345678202607021234"))
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

        // Mesmo txid de novo → 400
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 10, \"pixTxid\": \"E12345678202607021234\"}")
                .with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rejeição exige motivo e não credita")
    void testCompraRejeitada() throws Exception {
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 10, \"pixTxid\": \"TX-REJ-1\"}")
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
                .content("{\"quantidade\": 15, \"pixTxid\": \"TX-PRECO-1\"}")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quantidade").value(15))
            .andExpect(jsonPath("$.valorPago").value(150.00))
            .andExpect(jsonPath("$.precoUnitario").value(10.00));

        // Quantidade inválida → 400
        mockMvc.perform(post("/v1/tenants/{tenantId}/creditos/compras", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .contentType("application/json")
                .content("{\"quantidade\": 0, \"pixTxid\": \"TX-PRECO-2\"}")
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
