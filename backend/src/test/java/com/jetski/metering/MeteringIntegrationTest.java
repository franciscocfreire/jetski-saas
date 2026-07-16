package com.jetski.metering;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.locacoes.event.DocumentoPreviewGeradoEvent;
import com.jetski.locacoes.event.GruEmitidaEvent;
import com.jetski.metering.internal.MeteringEventListener;
import com.jetski.reservas.domain.event.DocumentosEmitidosEvent;
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

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Metering de emissões: idempotência do listener, série mensal do tenant e
 * agregado cross-tenant da plataforma (iteração com set_config, sem bypass de RLS).
 */
@AutoConfigureMockMvc
@DisplayName("Metering de Emissões Tests")
class MeteringIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MeteringEventListener listener;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockBean OPAAuthorizationService opaAuthorizationService;

    // Tenants do seed (V002/V999): acme e marina-bay
    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID TENANT_MARINA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ACME);
        try {
            jdbcTemplate.execute("INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id, linked_at) " +
                                 "VALUES ('11111111-1111-1111-1111-111111111111', 'keycloak', '11111111-1111-1111-1111-111111111111', NOW())");
        } catch (Exception ignored) {
            // já existe
        }
        when(opaAuthorizationService.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());
        limparUsos(TENANT_ACME);
        limparUsos(TENANT_MARINA);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void limparUsos(UUID tenantId) {
        jdbcTemplate.update("DELETE FROM emissao_uso WHERE tenant_id = ?", tenantId);
    }

    /** O listener é @Async — aguarda (polling) até a contagem esperada aparecer. */
    private void aguardarContagem(String tipo, UUID referenciaId, int esperado) {
        long deadline = System.currentTimeMillis() + 10_000;
        Integer atual = 0;
        while (System.currentTimeMillis() < deadline) {
            atual = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM emissao_uso WHERE tipo = ? AND referencia_id = ?",
                Integer.class, tipo, referenciaId);
            if (atual != null && atual >= esperado) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(atual).isEqualTo(esperado);
        // margem para um possível insert duplicado atrasado
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject(USER_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"));
    }

    private RequestPostProcessor vendedor() {
        return jwt().jwt(j -> j.subject(USER_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_VENDEDOR"));
    }

    // ============================== Listener ==============================

    @Test
    @DisplayName("Evento duplicado de documento conta uma única vez (idempotência)")
    void testListenerIdempotente() {
        UUID docId = UUID.randomUUID();
        Instant quando = Instant.now();
        DocumentosEmitidosEvent ev = new DocumentosEmitidosEvent(
            TENANT_ACME, UUID.randomUUID(), docId, "marinha,cliente", USER_ID, null, quando);

        listener.onDocumentosEmitidos(ev);
        listener.onDocumentosEmitidos(ev);

        aguardarContagem("DOCUMENTO", docId, 1);
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM emissao_uso WHERE tipo = 'DOCUMENTO' AND referencia_id = ?",
            Integer.class, docId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Regeneração legítima de GRU (ocorrido_em novo) conta de novo")
    void testGruRegeneracaoConta() {
        UUID habId = UUID.randomUUID();
        UUID reservaId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-07-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-07-01T12:00:00Z");

        listener.onGruEmitida(new GruEmitidaEvent(TENANT_ACME, reservaId, habId, "PIX", t1));
        listener.onGruEmitida(new GruEmitidaEvent(TENANT_ACME, reservaId, habId, "PIX", t1)); // reprocesso
        listener.onGruEmitida(new GruEmitidaEvent(TENANT_ACME, reservaId, habId, "BOLETO", t2)); // regeneração

        aguardarContagem("GRU", habId, 2);
    }

    // ============================== API do tenant ==============================

    @Test
    @DisplayName("Série mensal soma por tipo e zera meses sem uso")
    void testSerieMensal() throws Exception {
        Instant agora = Instant.now();
        UUID docId = UUID.randomUUID();
        UUID habId = UUID.randomUUID();
        UUID reservaPrevia = UUID.randomUUID();
        listener.onDocumentosEmitidos(new DocumentosEmitidosEvent(
            TENANT_ACME, UUID.randomUUID(), docId, "marinha,cliente", USER_ID, null, agora));
        listener.onGruEmitida(new GruEmitidaEvent(
            TENANT_ACME, UUID.randomUUID(), habId, "PIX", agora));
        listener.onDocumentoPreviewGerado(new DocumentoPreviewGeradoEvent(
            TENANT_ACME, reservaPrevia, "MARINHA", agora));
        aguardarContagem("DOCUMENTO", docId, 1);
        aguardarContagem("GRU", habId, 1);
        aguardarContagem("PREVIA", reservaPrevia, 1);

        String competencia = YearMonth.now(ZoneId.of("America/Sao_Paulo")).toString();

        mockMvc.perform(get("/v1/tenants/{tenantId}/metering/emissoes?meses=3", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[2].competencia").value(competencia))
            .andExpect(jsonPath("$[2].documento").value(1))
            .andExpect(jsonPath("$[2].gru").value(1))
            .andExpect(jsonPath("$[2].previa").value(1))
            .andExpect(jsonPath("$[2].total").value(2))
            .andExpect(jsonPath("$[0].total").value(0));
    }

    @Test
    @DisplayName("VENDEDOR não consulta metering (403)")
    void testMeteringForbiddenParaVendedor() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/metering/emissoes", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(vendedor()))
            .andExpect(status().isForbidden());
    }

    // ============================== API da plataforma ==============================

    @Test
    @DisplayName("Plataforma agrega emissões de todos os tenants (iteração RLS)")
    void testPlatformAgregaCrossTenant() throws Exception {
        Instant agora = Instant.now();
        UUID docId = UUID.randomUUID();
        UUID habId = UUID.randomUUID();
        // RLS: o insert do listener precisa casar com o tenant da sessão — troca o contexto
        listener.onDocumentosEmitidos(new DocumentosEmitidosEvent(
            TENANT_ACME, UUID.randomUUID(), docId, "marinha,cliente", USER_ID, null, agora));
        aguardarContagem("DOCUMENTO", docId, 1);
        TenantContext.setTenantId(TENANT_MARINA);
        listener.onGruEmitida(new GruEmitidaEvent(
            TENANT_MARINA, UUID.randomUUID(), habId, "PIX", agora));
        aguardarContagem("GRU", habId, 1); // contexto já é MARINA (RLS enxerga a linha)
        TenantContext.setTenantId(TENANT_ACME);

        String competencia = YearMonth.now(ZoneId.of("America/Sao_Paulo")).toString();

        // Asserta contra a VERDADE do banco, não contra contagem absoluta:
        // listeners @Async de testes anteriores (desta e de outras classes)
        // podem pousar linhas DEPOIS do limparUsos do setUp — o que se valida
        // aqui é a AGREGAÇÃO cross-tenant do endpoint, não o total exato.
        int docsAcme = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM emissao_uso WHERE tenant_id = ? AND tipo = 'DOCUMENTO' " +
            "AND to_char(ocorrido_em AT TIME ZONE 'America/Sao_Paulo', 'YYYY-MM') = ?",
            Integer.class, TENANT_ACME, competencia);
        int grusMarina = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM emissao_uso WHERE tenant_id = ? AND tipo = 'GRU' " +
            "AND to_char(ocorrido_em AT TIME ZONE 'America/Sao_Paulo', 'YYYY-MM') = ?",
            Integer.class, TENANT_MARINA, competencia);

        mockMvc.perform(get("/v1/platform/metering/emissoes?competencia=" + competencia)
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.slug == 'acme')].documento").value(docsAcme))
            .andExpect(jsonPath("$[?(@.slug == 'marina-bay')].gru").value(grusMarina));
    }

    @Test
    @DisplayName("Plataforma nega acesso quando OPA nega (403)")
    void testPlatformDenyOpa() throws Exception {
        when(opaAuthorizationService.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(false).tenantIsValid(true).build());

        mockMvc.perform(get("/v1/platform/metering/emissoes")
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(admin()))
            .andExpect(status().isForbidden());
    }
}
