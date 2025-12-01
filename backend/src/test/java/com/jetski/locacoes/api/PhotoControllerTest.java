package com.jetski.locacoes.api;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.FotoTipo;
import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.JetskiStatus;
import com.jetski.locacoes.domain.Locacao;
import com.jetski.locacoes.domain.LocacaoStatus;
import com.jetski.locacoes.domain.Modelo;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.FotoRepository;
import com.jetski.locacoes.internal.repository.JetskiRepository;
import com.jetski.locacoes.internal.repository.LocacaoRepository;
import com.jetski.locacoes.internal.repository.ModeloRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.locacoes.api.dto.UploadUrlRequest;
import com.jetski.locacoes.api.dto.UploadUrlResponse;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for PhotoController.
 *
 * Tests:
 * - Generate presigned URL for photo upload
 * - Confirm photo upload
 * - List photos by locacao
 * - Get photo by ID
 * - Delete photo
 * - Check-in/check-out photo status
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@AutoConfigureMockMvc
class PhotoControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocacaoRepository locacaoRepository;

    @Autowired
    private FotoRepository fotoRepository;

    @Autowired
    private JetskiRepository jetskiRepository;

    @Autowired
    private ModeloRepository modeloRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    private static final UUID TEST_TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private UUID testLocacaoId;

    @BeforeEach
    void setUp() {
        // Clean up usando SQL direto para evitar problemas com @Transactional
        // Order matters - delete dependent tables first (based on foreign key constraints)
        jdbcTemplate.execute("DELETE FROM foto WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM abastecimento WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        // Break circular FK: reserva ↔ locacao
        jdbcTemplate.execute("UPDATE reserva SET locacao_id = NULL WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM locacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        // Delete ALL reservas that reference jetskis from this tenant (including cross-tenant reservas)
        jdbcTemplate.execute("UPDATE reserva SET jetski_id = NULL WHERE jetski_id IN (SELECT id FROM jetski WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11')");
        jdbcTemplate.execute("DELETE FROM reserva WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM os_manutencao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        // Delete ALL jetskis that reference models from this tenant (handles cross-tenant FK issues)
        jdbcTemplate.execute("DELETE FROM jetski WHERE modelo_id IN (SELECT id FROM modelo WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11')");
        jdbcTemplate.execute("DELETE FROM jetski WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM commission_policy WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM politica_comissao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM fuel_policy WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM modelo WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM cliente WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");

        // Create test modelo
        Modelo modelo = Modelo.builder()
            .tenantId(TEST_TENANT_ID)
            .nome("Test Model")
            .fabricante("Yamaha")
            .capacidadePessoas(3)
            .potenciaHp(180)
            .precoBaseHora(new BigDecimal("200.00"))
            .build();
        modelo = modeloRepository.save(modelo);

        // Create test jetski
        Jetski jetski = Jetski.builder()
            .tenantId(TEST_TENANT_ID)
            .modeloId(modelo.getId())
            .serie("TEST-123")
            .ano(2023)
            .status(JetskiStatus.DISPONIVEL)
            .horimetroAtual(new BigDecimal("100.00"))
            .build();
        jetski = jetskiRepository.save(jetski);

        // Create test cliente
        Cliente cliente = Cliente.builder()
            .tenantId(TEST_TENANT_ID)
            .nome("Test Customer")
            .email("test@example.com")
            .build();
        cliente = clienteRepository.save(cliente);

        // Create test locacao
        Locacao locacao = Locacao.builder()
            .tenantId(TEST_TENANT_ID)
            .jetskiId(jetski.getId())
            .clienteId(cliente.getId())
            .dataCheckIn(LocalDateTime.now())
            .horimetroInicio(new BigDecimal("100.00"))
            .duracaoPrevista(60)
            .status(LocacaoStatus.EM_CURSO)
            .checklistSaidaJson("[\"motor_ok\"]")  // RN05: checklist field
            .build();

        locacao = locacaoRepository.save(locacao);
        testLocacaoId = locacao.getId();

        // Mock OPA to always allow (authorization is tested separately)
        when(opaAuthorizationService.authorize(any()))
            .thenReturn(OPADecision.builder().allow(true).build());
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = {"OPERADOR"})
    @DisplayName("Should generate presigned upload URL successfully")
    void testGenerateUploadUrl_Success() throws Exception {
        // Given
        String requestBody = """
            {
                "locacaoId": "%s",
                "tipoFoto": "CHECKIN_FRENTE",
                "contentType": "image/jpeg",
                "fileSize": 2048576,
                "sha256Hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            }
            """.formatted(testLocacaoId);

        // When/Then
        mockMvc.perform(post("/v1/tenants/{tenantId}/fotos/upload", TEST_TENANT_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fotoId").isNotEmpty())
            .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
            .andExpect(jsonPath("$.key").value(containsString(TEST_TENANT_ID.toString())))
            .andExpect(jsonPath("$.key").value(containsString(testLocacaoId.toString())))
            .andExpect(jsonPath("$.key").value(containsString("CHECKIN_FRENTE")))
            .andExpect(jsonPath("$.expiresAt").isNotEmpty())
            .andExpect(jsonPath("$.contentType").value("image/jpeg"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = {"OPERADOR"})
    @DisplayName("Should return 404 when locacao not found")
    void testGenerateUploadUrl_LocacaoNotFound() throws Exception {
        // Given
        UUID nonExistentLocacaoId = UUID.randomUUID();
        String requestBody = """
            {
                "locacaoId": "%s",
                "tipoFoto": "CHECKIN_FRENTE",
                "contentType": "image/jpeg",
                "fileSize": 2048576,
                "sha256Hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            }
            """.formatted(nonExistentLocacaoId);

        // When/Then
        mockMvc.perform(post("/v1/tenants/{tenantId}/fotos/upload", TEST_TENANT_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value(containsString("Locação não encontrada")));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = {"OPERADOR"})
    @DisplayName("Should return 409 when photo of same type already exists")
    void testGenerateUploadUrl_DuplicatePhotoType() throws Exception {
        // Given - First upload AND confirm (uploadedAt != null)
        // Note: Without confirmation, orphan photos are removed and retry is allowed
        createAndConfirmPhoto(FotoTipo.CHECKIN_FRENTE);

        // When/Then - Second upload (duplicate) should fail with 409
        String requestBody = """
            {
                "locacaoId": "%s",
                "tipoFoto": "CHECKIN_FRENTE",
                "contentType": "image/jpeg",
                "fileSize": 2048576,
                "sha256Hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            }
            """.formatted(testLocacaoId);

        mockMvc.perform(post("/v1/tenants/{tenantId}/fotos/upload", TEST_TENANT_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(containsString("Já existe foto do tipo")));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = {"OPERADOR"})
    @DisplayName("Should return 400 when request is invalid")
    void testGenerateUploadUrl_InvalidRequest() throws Exception {
        // Given - Missing required fields
        String requestBody = """
            {
                "locacaoId": "%s",
                "tipoFoto": "CHECKIN_FRENTE"
            }
            """.formatted(testLocacaoId);

        // When/Then
        mockMvc.perform(post("/v1/tenants/{tenantId}/fotos/upload", TEST_TENANT_ID)
                .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = {"OPERADOR"})
    @DisplayName("Should list photos by locacao")
    void testListFotosByLocacao_Success() throws Exception {
        // Given - Create 2 photos
        createTestPhoto(FotoTipo.CHECKIN_FRENTE);
        createTestPhoto(FotoTipo.CHECKIN_LATERAL_ESQ);

        // When/Then
        mockMvc.perform(get("/v1/tenants/{tenantId}/fotos/locacoes/{locacaoId}", TEST_TENANT_ID, testLocacaoId)
                .header("X-Tenant-Id", TEST_TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].tipo").value("CHECKIN_FRENTE"))
            .andExpect(jsonPath("$[1].tipo").value("CHECKIN_LATERAL_ESQ"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = {"OPERADOR"})
    @DisplayName("Should return empty list when no photos exist")
    void testListFotosByLocacao_EmptyList() throws Exception {
        // When/Then
        mockMvc.perform(get("/v1/tenants/{tenantId}/fotos/locacoes/{locacaoId}", TEST_TENANT_ID, testLocacaoId)
                .header("X-Tenant-Id", TEST_TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = {"OPERADOR"})
    @DisplayName("Should get check-in photos status")
    void testGetCheckInPhotosStatus() throws Exception {
        // Given - Upload 2 out of 4 required photos
        createAndConfirmPhoto(FotoTipo.CHECKIN_FRENTE);
        createAndConfirmPhoto(FotoTipo.CHECKIN_LATERAL_ESQ);

        // When/Then
        mockMvc.perform(get("/v1/tenants/{tenantId}/fotos/locacoes/{locacaoId}/checkin-status",
                TEST_TENANT_ID, testLocacaoId)
                .header("X-Tenant-Id", TEST_TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uploadedCount").value(2))
            .andExpect(jsonPath("$.requiredCount").value(4))
            .andExpect(jsonPath("$.isComplete").value(false))
            .andExpect(jsonPath("$.missingTypes").isArray())
            .andExpect(jsonPath("$.missingTypes", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = {"OPERADOR"})
    @DisplayName("Should return complete status when all check-in photos uploaded")
    void testGetCheckInPhotosStatus_Complete() throws Exception {
        // Given - Upload all 4 required photos
        createAndConfirmPhoto(FotoTipo.CHECKIN_FRENTE);
        createAndConfirmPhoto(FotoTipo.CHECKIN_LATERAL_ESQ);
        createAndConfirmPhoto(FotoTipo.CHECKIN_LATERAL_DIR);
        createAndConfirmPhoto(FotoTipo.CHECKIN_HORIMETRO);

        // When/Then
        mockMvc.perform(get("/v1/tenants/{tenantId}/fotos/locacoes/{locacaoId}/checkin-status",
                TEST_TENANT_ID, testLocacaoId)
                .header("X-Tenant-Id", TEST_TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uploadedCount").value(4))
            .andExpect(jsonPath("$.requiredCount").value(4))
            .andExpect(jsonPath("$.isComplete").value(true))
            .andExpect(jsonPath("$.missingTypes").isArray())
            .andExpect(jsonPath("$.missingTypes", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = {"OPERADOR"})
    @DisplayName("Should get check-out photos status")
    void testGetCheckOutPhotosStatus() throws Exception {
        // Given - Upload 3 out of 4 required photos
        createAndConfirmPhoto(FotoTipo.CHECKOUT_FRENTE);
        createAndConfirmPhoto(FotoTipo.CHECKOUT_LATERAL_ESQ);
        createAndConfirmPhoto(FotoTipo.CHECKOUT_LATERAL_DIR);

        // When/Then
        mockMvc.perform(get("/v1/tenants/{tenantId}/fotos/locacoes/{locacaoId}/checkout-status",
                TEST_TENANT_ID, testLocacaoId)
                .header("X-Tenant-Id", TEST_TENANT_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uploadedCount").value(3))
            .andExpect(jsonPath("$.requiredCount").value(4))
            .andExpect(jsonPath("$.isComplete").value(false))
            .andExpect(jsonPath("$.missingTypes").isArray())
            .andExpect(jsonPath("$.missingTypes", hasSize(1)))
            .andExpect(jsonPath("$.missingTypes[0]").value("CHECKOUT_HORIMETRO"));
    }

    // Helper methods

    private void createTestPhoto(FotoTipo tipo) {
        String requestBody = """
            {
                "locacaoId": "%s",
                "tipoFoto": "%s",
                "contentType": "image/jpeg",
                "fileSize": 2048576,
                "sha256Hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            }
            """.formatted(testLocacaoId, tipo.name());

        try {
            mockMvc.perform(post("/v1/tenants/{tenantId}/fotos/upload", TEST_TENANT_ID)
                    .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test photo", e);
        }
    }

    private void createAndConfirmPhoto(FotoTipo tipo) {
        String requestBody = """
            {
                "locacaoId": "%s",
                "tipoFoto": "%s",
                "contentType": "image/jpeg",
                "fileSize": 2048576,
                "sha256Hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            }
            """.formatted(testLocacaoId, tipo.name());

        try {
            // Generate upload URL
            String response = mockMvc.perform(post("/v1/tenants/{tenantId}/fotos/upload", TEST_TENANT_ID)
                    .header("X-Tenant-Id", TEST_TENANT_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

            // Extract fotoId from response (simple parsing)
            String fotoId = extractFotoId(response);

            // Mark as uploaded (simulate)
            var foto = fotoRepository.findById(UUID.fromString(fotoId)).orElseThrow();
            foto.setUploadedAt(java.time.Instant.now());
            fotoRepository.save(foto);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create and confirm test photo", e);
        }
    }

    private String extractFotoId(String jsonResponse) {
        // Simple JSON parsing to extract fotoId
        int start = jsonResponse.indexOf("\"fotoId\":\"") + 10;
        int end = jsonResponse.indexOf("\"", start);
        return jsonResponse.substring(start, end);
    }
}
