package com.jetski.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.comissoes.api.dto.PagarComissaoRequest;
import com.jetski.comissoes.api.dto.PoliticaComissaoRequest;
import com.jetski.comissoes.domain.NivelPolitica;
import com.jetski.comissoes.domain.StatusComissao;
import com.jetski.comissoes.domain.TipoComissao;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.security.TenantAccessInfo;
import com.jetski.usuarios.internal.TenantAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Comissao API (ComissaoController + PoliticaComissaoController)
 *
 * Tests commission management and policy management endpoints with database and Spring context
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@AutoConfigureMockMvc
@DisplayName("Integration: Comissao API")
class ComissaoControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    @MockBean
    private TenantAccessService tenantAccessService;

    @MockBean
    private com.jetski.usuarios.api.UsuarioService usuarioService;

    private static final UUID TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String USER_EMAIL = "gerente.teste@test.com";
    private static final UUID VENDEDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MODELO_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID JETSKI_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CLIENTE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @BeforeEach
    @Transactional
    void setUp() {
        ensureTestEntitiesExist();

        // Mock tenant access
        when(tenantAccessService.validateAccess(any(String.class), eq(USER_ID.toString()), any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID requestedTenantId = invocation.getArgument(2, UUID.class);
                if (requestedTenantId.equals(TENANT_ID)) {
                    return TenantAccessInfo.builder()
                        .hasAccess(true)
                        .roles(List.of("GERENTE", "ADMIN_TENANT", "FINANCEIRO"))
                        .unrestricted(false)
                        .usuarioId(USER_ID)
                        .build();
                } else {
                    return TenantAccessInfo.builder()
                        .hasAccess(false)
                        .roles(List.of())
                        .unrestricted(false)
                        .build();
                }
            });

        // Mock OPA to allow all requests
        OPADecision allowDecision = OPADecision.builder()
            .allow(true)
            .tenantIsValid(true)
            .build();

        when(opaAuthorizationService.authorize(any(OPAInput.class)))
            .thenReturn(allowDecision);

        // Mock UsuarioService to return USER_ID for any Authentication
        when(usuarioService.getUserIdFromAuthentication(any()))
            .thenReturn(USER_ID);
    }

    /**
     * Ensure test entities exist in database (idempotent)
     */
    private void ensureTestEntitiesExist() {
        // Modelo
        jdbcTemplate.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'SeaDoo GTI SE 130', 'Sea-Doo', 130, 2, 150.00, 5, 50.00, 300.00, FALSE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO_ID, TENANT_ID);

        // Jetski
        jdbcTemplate.update("""
            INSERT INTO jetski (id, tenant_id, modelo_id, serie, placa, ano, horimetro_atual, status, ativo)
            VALUES (?, ?, ?, 'JET-COM-001', 'COM-1234', 2023, 45.2, 'disponivel', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, JETSKI_ID, TENANT_ID, MODELO_ID);

        // Cliente
        jdbcTemplate.update("""
            INSERT INTO cliente (id, tenant_id, nome, documento, email, telefone, ativo)
            VALUES (?, ?, 'Cliente Teste Comissão', '12345678901', 'cliente.comissao@test.com', '11999999999', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, CLIENTE_ID, TENANT_ID);

        // Vendedor
        jdbcTemplate.update("""
            INSERT INTO vendedor (id, tenant_id, nome, tipo, ativo)
            VALUES (?, ?, 'Vendedor Teste Comissão', 'interno', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, VENDEDOR_ID, TENANT_ID);

        // Usuario + Membro (for vendedor)
        jdbcTemplate.update("""
            INSERT INTO usuario (id, email, nome, ativo)
            VALUES (?, 'vendedor.comissao@test.com', 'Vendedor Teste', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, VENDEDOR_ID);

        jdbcTemplate.update("""
            INSERT INTO membro (id, tenant_id, usuario_id, papeis)
            VALUES (999991, ?, ?, ARRAY['VENDEDOR'])
            ON CONFLICT (tenant_id, usuario_id) DO NOTHING
            """, TENANT_ID, VENDEDOR_ID);

        // Usuario autenticado (usado nos testes com JWT)
        // Note: email = USER_ID.toString() to match JWT subject
        jdbcTemplate.update("""
            INSERT INTO usuario (id, email, nome, ativo)
            VALUES (?, ?, 'Gerente Teste', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, USER_ID, USER_ID.toString());

        jdbcTemplate.update("""
            INSERT INTO membro (id, tenant_id, usuario_id, papeis)
            VALUES (999992, ?, ?, ARRAY['GERENTE', 'ADMIN_TENANT', 'FINANCEIRO'])
            ON CONFLICT (tenant_id, usuario_id) DO NOTHING
            """, TENANT_ID, USER_ID);
    }

    // ===================================================================
    // PoliticaComissaoController Tests
    // ===================================================================

    @Test
    @DisplayName("POST /politicas-comissao - Should create VENDEDOR policy")
    void testCriarPoliticaVendedor() throws Exception {
        PoliticaComissaoRequest request = PoliticaComissaoRequest.builder()
            .nome("Política Vendedor Premium")
            .nivel(NivelPolitica.VENDEDOR)
            .tipo(TipoComissao.PERCENTUAL)
            .vendedorId(VENDEDOR_ID)
            .percentualComissao(new BigDecimal("12.50"))
            .ativa(true)
            .descricao("Comissão padrão do vendedor")
            .build();

        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/politicas-comissao")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.nome").value("Política Vendedor Premium"))
            .andExpect(jsonPath("$.nivel").value("VENDEDOR"))
            .andExpect(jsonPath("$.tipo").value("PERCENTUAL"))
            .andExpect(jsonPath("$.percentualComissao").value(12.50))
            .andExpect(jsonPath("$.vendedorId").value(VENDEDOR_ID.toString()))
            .andExpect(jsonPath("$.ativa").value(true));
    }

    @Test
    @DisplayName("POST /politicas-comissao - Should create MODELO policy")
    void testCriarPoliticaModelo() throws Exception {
        PoliticaComissaoRequest request = PoliticaComissaoRequest.builder()
            .nome("Política Modelo SeaDoo")
            .nivel(NivelPolitica.MODELO)
            .tipo(TipoComissao.PERCENTUAL)
            .modeloId(MODELO_ID)
            .percentualComissao(new BigDecimal("15.00"))
            .ativa(true)
            .build();

        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/politicas-comissao")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nivel").value("MODELO"))
            .andExpect(jsonPath("$.modeloId").value(MODELO_ID.toString()));
    }

    @Test
    @DisplayName("POST /politicas-comissao - Should create ESCALONADO policy")
    void testCriarPoliticaEscalonado() throws Exception {
        PoliticaComissaoRequest request = PoliticaComissaoRequest.builder()
            .nome("Política Escalonada 2h+")
            .nivel(NivelPolitica.DURACAO)
            .tipo(TipoComissao.ESCALONADO)
            .percentualComissao(new BigDecimal("10.00"))
            .percentualExtra(new BigDecimal("15.00"))
            .duracaoMinMinutos(120)
            .ativa(true)
            .build();

        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/politicas-comissao")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tipo").value("ESCALONADO"))
            .andExpect(jsonPath("$.percentualComissao").value(10.00))
            .andExpect(jsonPath("$.percentualExtra").value(15.00))
            .andExpect(jsonPath("$.duracaoMinMinutos").value(120));
    }

    @Test
    @DisplayName("POST /politicas-comissao - Should fail when VENDEDOR policy missing vendedorId")
    void testCriarPolitica_VendedorSemVendedorId() throws Exception {
        PoliticaComissaoRequest request = PoliticaComissaoRequest.builder()
            .nome("Política Inválida")
            .nivel(NivelPolitica.VENDEDOR)
            .tipo(TipoComissao.PERCENTUAL)
            .percentualComissao(new BigDecimal("10.00"))
            .ativa(true)
            .build();

        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/politicas-comissao")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("vendedorId é obrigatório")));
    }

    @Test
    @DisplayName("GET /politicas-comissao - Should list active policies")
    void testListarPoliticasAtivas() throws Exception {
        // Create a policy first
        createTestPolitica();

        mockMvc.perform(get("/v1/tenants/" + TENANT_ID + "/politicas-comissao")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[*].ativa").value(everyItem(is(true))));
    }

    @Test
    @DisplayName("GET /politicas-comissao/{id} - Should find policy by ID")
    void testBuscarPoliticaPorId() throws Exception {
        UUID politicaId = createTestPolitica();

        mockMvc.perform(get("/v1/tenants/" + TENANT_ID + "/politicas-comissao/" + politicaId)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(politicaId.toString()))
            .andExpect(jsonPath("$.nivel").value("VENDEDOR"));
    }

    @Test
    @DisplayName("PUT /politicas-comissao/{id} - Should update policy")
    void testAtualizarPolitica() throws Exception {
        UUID politicaId = createTestPolitica();

        PoliticaComissaoRequest request = PoliticaComissaoRequest.builder()
            .nome("Política Atualizada")
            .nivel(NivelPolitica.VENDEDOR)
            .tipo(TipoComissao.PERCENTUAL)
            .vendedorId(VENDEDOR_ID)
            .percentualComissao(new BigDecimal("20.00"))
            .ativa(true)
            .build();

        mockMvc.perform(put("/v1/tenants/" + TENANT_ID + "/politicas-comissao/" + politicaId)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Política Atualizada"))
            .andExpect(jsonPath("$.percentualComissao").value(20.00));
    }

    @Test
    @DisplayName("PATCH /politicas-comissao/{id}/toggle - Should toggle policy active status")
    void testTogglePolitica() throws Exception {
        UUID politicaId = createTestPolitica();

        // First toggle (active -> inactive)
        mockMvc.perform(patch("/v1/tenants/" + TENANT_ID + "/politicas-comissao/" + politicaId + "/toggle")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ativa").value(false));

        // Second toggle (inactive -> active)
        mockMvc.perform(patch("/v1/tenants/" + TENANT_ID + "/politicas-comissao/" + politicaId + "/toggle")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ativa").value(true));
    }

    // ===================================================================
    // ComissaoController Tests
    // ===================================================================

    @Test
    @DisplayName("GET /comissoes/{id} - Should find commission by ID")
    void testBuscarComissaoPorId() throws Exception {
        UUID politicaId = createTestPolitica();
        UUID locacaoId = createTestLocacao();
        UUID comissaoId = createTestComissao(locacaoId, politicaId);

        mockMvc.perform(get("/v1/tenants/" + TENANT_ID + "/comissoes/" + comissaoId)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(comissaoId.toString()))
            .andExpect(jsonPath("$.vendedorId").value(VENDEDOR_ID.toString()))
            .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @Test
    @DisplayName("GET /comissoes/vendedor/{vendedorId} - Should list commissions by seller")
    void testListarComissoesPorVendedor() throws Exception {
        UUID politicaId = createTestPolitica();
        UUID locacaoId = createTestLocacao();
        createTestComissao(locacaoId, politicaId);

        mockMvc.perform(get("/v1/tenants/" + TENANT_ID + "/comissoes/vendedor/" + VENDEDOR_ID)
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].vendedorId").value(VENDEDOR_ID.toString()));
    }

    @Test
    @DisplayName("GET /comissoes/pendentes - Should list pending commissions")
    void testListarComissoesPendentes() throws Exception {
        UUID politicaId = createTestPolitica();
        UUID locacaoId = createTestLocacao();
        createTestComissao(locacaoId, politicaId);

        mockMvc.perform(get("/v1/tenants/" + TENANT_ID + "/comissoes/pendentes")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[*].status").value(everyItem(is("PENDENTE"))));
    }

    @Test
    @DisplayName("GET /comissoes/aguardando-pagamento - Should list approved commissions")
    void testListarComissoesAguardandoPagamento() throws Exception {
        UUID politicaId = createTestPolitica();
        UUID locacaoId = createTestLocacao();
        UUID comissaoId = createTestComissao(locacaoId, politicaId);

        // Approve first
        jdbcTemplate.update("""
            UPDATE comissao
            SET status = ?, aprovado_por = ?, aprovado_em = ?
            WHERE id = ?
            """, StatusComissao.APROVADA.name(), USER_ID, java.sql.Timestamp.from(Instant.now()), comissaoId);

        mockMvc.perform(get("/v1/tenants/" + TENANT_ID + "/comissoes/aguardando-pagamento")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[*].status").value(everyItem(is("APROVADA"))));
    }

    @Test
    @DisplayName("GET /comissoes/periodo - Should list commissions by period")
    void testListarComissoesPorPeriodo() throws Exception {
        UUID politicaId = createTestPolitica();
        UUID locacaoId = createTestLocacao();
        createTestComissao(locacaoId, politicaId);

        Instant inicio = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant fim = Instant.now().plus(1, ChronoUnit.DAYS);

        mockMvc.perform(get("/v1/tenants/" + TENANT_ID + "/comissoes/periodo")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .param("inicio", inicio.toString())
                .param("fim", fim.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /comissoes/{id}/aprovar - Should approve commission")
    void testAprovarComissao() throws Exception {
        UUID politicaId = createTestPolitica();
        UUID locacaoId = createTestLocacao();
        UUID comissaoId = createTestComissao(locacaoId, politicaId);

        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/comissoes/" + comissaoId + "/aprovar")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APROVADA"))
            .andExpect(jsonPath("$.aprovadoPor").value(USER_ID.toString()))
            .andExpect(jsonPath("$.aprovadoEm").exists());
    }

    @Test
    @DisplayName("POST /comissoes/{id}/pagar - Should mark commission as paid")
    void testPagarComissao() throws Exception {
        UUID politicaId = createTestPolitica();
        UUID locacaoId = createTestLocacao();
        UUID comissaoId = createTestComissao(locacaoId, politicaId);

        // Approve first
        jdbcTemplate.update("""
            UPDATE comissao
            SET status = ?, aprovado_por = ?, aprovado_em = ?
            WHERE id = ?
            """, StatusComissao.APROVADA.name(), USER_ID, java.sql.Timestamp.from(Instant.now()), comissaoId);

        PagarComissaoRequest request = PagarComissaoRequest.builder()
            .referenciaPagamento("PIX-2025-001")
            .build();

        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/comissoes/" + comissaoId + "/pagar")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAGA"))
            .andExpect(jsonPath("$.pagoPor").value(USER_ID.toString()))
            .andExpect(jsonPath("$.pagoEm").exists())
            .andExpect(jsonPath("$.referenciaPagamento").value("PIX-2025-001"));
    }

    @Test
    @DisplayName("POST /comissoes/{id}/pagar - Should fail when commission not approved")
    void testPagarComissao_NaoAprovada() throws Exception {
        UUID politicaId = createTestPolitica();
        UUID locacaoId = createTestLocacao();
        UUID comissaoId = createTestComissao(locacaoId, politicaId);

        PagarComissaoRequest request = PagarComissaoRequest.builder()
            .referenciaPagamento("PIX-2025-001")
            .build();

        mockMvc.perform(post("/v1/tenants/" + TENANT_ID + "/comissoes/" + comissaoId + "/pagar")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString())
                    .claim("tenant_id", TENANT_ID.toString())))
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("Apenas comissões aprovadas podem ser pagas")));
    }

    // ===================================================================
    // Helper Methods
    // ===================================================================

    /**
     * Creates a test policy and returns its ID
     */
    private UUID createTestPolitica() {
        UUID politicaId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO politica_comissao (id, tenant_id, nome, nivel, tipo, vendedor_id,
                                           percentual_comissao, ativa, created_by)
            VALUES (?, ?, 'Política Teste', 'VENDEDOR', 'PERCENTUAL', ?, 10.00, TRUE, ?)
            ON CONFLICT (id) DO NOTHING
            """, politicaId, TENANT_ID, VENDEDOR_ID, USER_ID);
        return politicaId;
    }

    /**
     * Creates a test rental and returns its ID
     */
    private UUID createTestLocacao() {
        UUID locacaoId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant checkIn = now.minus(2, ChronoUnit.HOURS);
        jdbcTemplate.update("""
            INSERT INTO locacao (id, tenant_id, vendedor_id, jetski_id, cliente_id,
                                 data_check_in, data_check_out, valor_total, status,
                                 horimetro_inicio, horimetro_fim, duracao_prevista, minutos_usados)
            VALUES (?, ?, ?, ?, ?, ?, ?, 500.00, 'FINALIZADA', 100.0, 102.0, 120, 120)
            ON CONFLICT (id) DO NOTHING
            """, locacaoId, TENANT_ID, VENDEDOR_ID, JETSKI_ID, CLIENTE_ID,
            java.sql.Timestamp.from(checkIn), java.sql.Timestamp.from(now));
        return locacaoId;
    }

    /**
     * Creates a test commission and returns its ID
     */
    private UUID createTestComissao(UUID locacaoId, UUID politicaId) {
        UUID comissaoId = UUID.randomUUID();
        Instant dataLocacao = Instant.now();
        jdbcTemplate.update("""
            INSERT INTO comissao (id, tenant_id, locacao_id, vendedor_id, politica_id,
                                  status, data_locacao, valor_total_locacao, valor_combustivel,
                                  valor_multas, valor_taxas, valor_comissionavel, valor_comissao,
                                  tipo_comissao, percentual_aplicado, politica_nome, politica_nivel)
            VALUES (?, ?, ?, ?, ?, ?, ?, 500.00, 0.00, 0.00, 0.00, 500.00, 50.00,
                    'PERCENTUAL', 10.00, 'Política Teste', 'VENDEDOR')
            ON CONFLICT (id) DO NOTHING
            """, comissaoId, TENANT_ID, locacaoId, VENDEDOR_ID, politicaId,
            StatusComissao.PENDENTE.name(), java.sql.Timestamp.from(dataLocacao));
        return comissaoId;
    }
}
