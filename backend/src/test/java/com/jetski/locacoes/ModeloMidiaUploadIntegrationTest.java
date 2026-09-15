package com.jetski.locacoes;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Upload de fotos do modelo (V074): arquivo validado, guardado no storage da empresa e
 * servido publicamente com cache; apagar a mídia apaga o arquivo.
 */
@AutoConfigureMockMvc
@DisplayName("Modelo — upload de fotos")
class ModeloMidiaUploadIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StorageService storageService;

    @MockBean OPAAuthorizationService opa;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID MODELO = UUID.fromString("77777777-7777-4777-8777-000000000074");
    private static final UUID STAFF_USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());
        jdbc.update("UPDATE tenant SET status = 'ATIVO' WHERE id = ?", TENANT_ACME);
        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'Modelo Upload', 'Sea-Doo', 130, 2, 150.00, 5, 50.00, 300.00, FALSE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO, TENANT_ACME);
        jdbc.update("DELETE FROM modelo_midia WHERE modelo_id = ?", MODELO);
        jdbc.update("UPDATE modelo SET foto_referencia_url = NULL WHERE id = ?", MODELO);
    }

    private RequestPostProcessor staff() {
        return jwt().jwt(j -> j.subject(STAFF_USER.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_GERENTE"));
    }

    private static byte[] png(int largura, int altura) throws Exception {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(30, 66, 102));
        g.fillRect(0, 0, largura, altura);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private MvcResult enviar(byte[] conteudo, String nome, String contentType) throws Exception {
        return mockMvc.perform(multipart("/v1/tenants/{t}/modelos/{m}/midias/upload", TENANT_ACME, MODELO)
                .file(new MockMultipartFile("arquivo", nome, contentType, conteudo))
                .param("titulo", "Vista lateral")
                .header("X-Tenant-Id", TENANT_ACME.toString())
                .with(staff()))
            .andReturn();
    }

    @Test
    @DisplayName("upload válido: guarda no storage, vira principal e é servido sem login com cache")
    void uploadValidoServidoPublicamente() throws Exception {
        byte[] imagem = png(320, 200);

        MvcResult r = enviar(imagem, "lateral.png", "image/png");
        assertThat(r.getResponse().getStatus()).isEqualTo(201);

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT id, url, storage_key, tamanho_bytes, principal, titulo FROM modelo_midia WHERE modelo_id = ?",
            MODELO);
        UUID midiaId = (UUID) row.get("id");
        String key = (String) row.get("storage_key");
        assertThat(key).isEqualTo(TENANT_ACME + "/modelos/" + MODELO + "/midias/" + midiaId + ".png");
        assertThat(row.get("url")).isEqualTo("/api/v1/public/midias/" + TENANT_ACME + "/" + midiaId);
        assertThat(row.get("tamanho_bytes")).isEqualTo(imagem.length);
        assertThat(row.get("principal")).isEqualTo(true);
        assertThat(row.get("titulo")).isEqualTo("Vista lateral");
        assertThat(storageService.fileExists(key)).isTrue();
        assertThat(jdbc.queryForObject("SELECT foto_referencia_url FROM modelo WHERE id = ?", String.class, MODELO))
            .isEqualTo(row.get("url"));
        assertThat(r.getResponse().getContentAsString()).contains("\"armazenada\":true");

        byte[] servido = mockMvc.perform(get("/v1/public/midias/{t}/{id}", TENANT_ACME, midiaId))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(Arrays.equals(servido, imagem)).isTrue();
    }

    @Test
    @DisplayName("arquivo que não é imagem: 400 e nada gravado")
    void naoImagemRecusada() throws Exception {
        MvcResult r = enviar("isto não é uma imagem".getBytes(), "falso.jpg", "image/jpeg");

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM modelo_midia WHERE modelo_id = ?", Long.class, MODELO))
            .isZero();
    }

    @Test
    @DisplayName("acima de 5 MB: 400")
    void grandeDemaisRecusada() throws Exception {
        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        grande[0] = (byte) 0xFF;
        grande[1] = (byte) 0xD8;
        grande[2] = (byte) 0xFF;

        MvcResult r = enviar(grande, "grande.jpg", "image/jpeg");

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("imagem enviada não aceita troca de URL; outra empresa não lê o arquivo; apagar remove do storage")
    void urlTravadaOutraEmpresaEApagar() throws Exception {
        assertThat(enviar(png(64, 64), "a.png", "image/png").getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT id, url, storage_key FROM modelo_midia WHERE modelo_id = ?", MODELO);
        UUID midiaId = (UUID) row.get("id");
        String key = (String) row.get("storage_key");

        mockMvc.perform(put("/v1/tenants/{t}/modelos/{m}/midias/{id}", TENANT_ACME, MODELO, midiaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"IMAGEM\",\"url\":\"https://exemplo.com/outra.jpg\"}")
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff()))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/v1/public/midias/{t}/{id}", UUID.randomUUID(), midiaId))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/v1/tenants/{t}/modelos/{m}/midias/{id}", TENANT_ACME, MODELO, midiaId)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff()))
            .andExpect(status().isNoContent());

        assertThat(storageService.fileExists(key)).isFalse();
        mockMvc.perform(get("/v1/public/midias/{t}/{id}", TENANT_ACME, midiaId))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("resposta da listagem marca a mídia enviada como armazenada")
    void listagemMarcaArmazenada() throws Exception {
        assertThat(enviar(png(64, 64), "b.png", "image/png").getResponse().getStatus()).isEqualTo(201);

        mockMvc.perform(get("/v1/tenants/{t}/modelos/{m}/midias", TENANT_ACME, MODELO)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].armazenada").value(true))
            .andExpect(jsonPath("$[0].tipo").value("IMAGEM"));
    }
}
