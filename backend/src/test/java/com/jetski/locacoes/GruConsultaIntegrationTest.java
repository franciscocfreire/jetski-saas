package com.jetski.locacoes;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.storage.StorageService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Módulo GRUs: listagem do ciclo (gerada/paga/emitida/Marinha/confirmada) e
 * persistência do resultado do envio no reenvio (V039).
 */
@AutoConfigureMockMvc
@DisplayName("Módulo GRUs — ciclo e envio à Marinha")
class GruConsultaIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StorageService storageService;

    @MockBean OPAAuthorizationService opa;
    @MockBean EmailService emailService;

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID MODELO_ACME = UUID.fromString("77777777-7777-4777-8777-000000000061");
    private static final UUID STAFF_USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private UUID clienteId;

    @BeforeEach
    void setUp() {
        when(opa.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());

        jdbc.update("UPDATE tenant SET status = 'ATIVO', marinha_email = 'capitania@test.com' WHERE id = ?",
            TENANT_ACME);
        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'Gru Modelo', 'Sea-Doo', 130, 2, 150.00, 5, 50.00, 300.00, FALSE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO_ACME, TENANT_ACME);

        jdbc.update("DELETE FROM documento_emitido WHERE reserva_id IN " +
            "(SELECT id FROM reserva WHERE cliente_id IN (SELECT id FROM cliente WHERE email = 'gru@test.com'))");
        jdbc.update("DELETE FROM reserva_habilitacao WHERE reserva_id IN " +
            "(SELECT id FROM reserva WHERE cliente_id IN (SELECT id FROM cliente WHERE email = 'gru@test.com'))");
        jdbc.update("DELETE FROM reserva WHERE cliente_id IN (SELECT id FROM cliente WHERE email = 'gru@test.com')");
        jdbc.update("DELETE FROM cliente WHERE email = 'gru@test.com'");

        clienteId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO cliente (id, tenant_id, nome, email, origem, status_conta, ativo)
            VALUES (?, ?, 'Cliente Gru', 'gru@test.com', 'BALCAO', 'ATIVA', TRUE)
            """, clienteId, TENANT_ACME);
    }

    private RequestPostProcessor staff() {
        return jwt().jwt(j -> j.subject(STAFF_USER.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_GERENTE"));
    }

    private UUID seedReserva(String emitidaInterval) {
        UUID reservaId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista,
                                 status, canal, documento_emitido_em)
            VALUES (?, ?, ?, ?, now() - interval '3 days', now() - interval '3 days' + interval '2 hours',
                    'CONFIRMADA', 'BALCAO', %s)
            """.formatted(emitidaInterval == null ? "NULL" : "now() - interval '" + emitidaInterval + "'"),
            reservaId, TENANT_ACME, MODELO_ACME, clienteId);
        return reservaId;
    }

    private void seedHab(UUID reservaId, String via, String gruNumero) {
        jdbc.update("""
            INSERT INTO reserva_habilitacao (tenant_id, reserva_id, via, gru_numero, gru_pago,
                                             gru_pago_em, gru_gerada_em, resolvida)
            VALUES (?, ?, ?, ?, TRUE, now(), now() - interval '2 days', TRUE)
            """, TENANT_ACME, reservaId, via, gruNumero);
    }

    private UUID seedDocumento(UUID reservaId, boolean marinhaEnviada) {
        UUID docId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO documento_emitido (id, tenant_id, reserva_id, s3_key, hash_sha256,
                                           destinos, emitido_em, marinha_enviado_em, created_at)
            VALUES (?, ?, ?, ?, 'hash-gru-teste', '{"marinha":"capitania@test.com"}'::jsonb,
                    now() - interval '1 day', %s, now())
            """.formatted(marinhaEnviada ? "now() - interval '1 day'" : "NULL"),
            docId, TENANT_ACME, reservaId,
            TENANT_ACME + "/reserva/" + reservaId + "/documentos.pdf");
        return docId;
    }

    @Test
    @DisplayName("GET /grus: ciclo completo — com e sem doc emitido; CHA fica fora; ordenação desc")
    void testListagem() throws Exception {
        UUID comDoc = seedReserva("1 day");
        seedHab(comDoc, "EMA", "608931002438511111");
        seedDocumento(comDoc, true);

        UUID semDoc = seedReserva(null);
        seedHab(semDoc, "EMA", "608931002438522222");
        // a mais recente na geração — deve vir primeiro
        jdbc.update("UPDATE reserva_habilitacao SET gru_gerada_em = now() WHERE reserva_id = ?", semDoc);

        UUID cha = seedReserva(null);
        jdbc.update("""
            INSERT INTO reserva_habilitacao (tenant_id, reserva_id, via, cha_numero, resolvida)
            VALUES (?, ?, 'CHA', 'CHA-123', TRUE)
            """, TENANT_ACME, cha);

        // Asserções autocontidas (a listagem é tenant-wide — outras classes
        // podem deixar GRUs no mesmo tenant): valida SÓ as GRUs deste teste.
        String body = mockMvc.perform(get("/v1/tenants/{t}/grus", TENANT_ACME)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        java.util.List<java.util.Map<String, Object>> itens =
            com.jayway.jsonpath.JsonPath.read(body, "$");
        java.util.Map<String, Object> itemComDoc = itens.stream()
            .filter(i -> "608931002438511111".equals(i.get("gruNumero"))).findFirst().orElseThrow();
        java.util.Map<String, Object> itemSemDoc = itens.stream()
            .filter(i -> "608931002438522222".equals(i.get("gruNumero"))).findFirst().orElseThrow();

        assertThat(itemSemDoc.get("documentoId")).isNull();
        assertThat(itemSemDoc.get("marinhaEnviadaEm")).isNull();
        assertThat(itemComDoc.get("clienteNome")).isEqualTo("Cliente Gru");
        assertThat(itemComDoc.get("gruPago")).isEqualTo(true);
        assertThat(itemComDoc.get("documentoId")).isNotNull();
        assertThat(itemComDoc.get("marinhaEnviadaEm")).isNotNull();
        // ordenação: a gerada agora (semDoc) vem antes da mais antiga
        assertThat(itens.indexOf(itemSemDoc)).isLessThan(itens.indexOf(itemComDoc));
        // via CHA fica fora da listagem
        assertThat(itens.stream().anyMatch(i -> "CHA-123".equals(i.get("gruNumero")))).isFalse();
    }

    @Test
    @DisplayName("Reenvio persiste marinha_enviado_em (V039); falha de SMTP mantém null")
    void testReenvioPersisteEnvio() throws Exception {
        UUID reservaId = seedReserva("1 day");
        seedHab(reservaId, "EMA", "608931002438533333");
        UUID docId = seedDocumento(reservaId, false);
        // PDF no storage para o reenvio ler
        storageService.putObject(TENANT_ACME + "/reserva/" + reservaId + "/documentos.pdf",
            "%PDF-fake".getBytes(), "application/pdf");

        // sucesso → timestamps registrados e subject com o nº da GRU
        mockMvc.perform(post("/v1/tenants/{t}/documentos/{id}/reenviar", TENANT_ACME, docId)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enviadoMarinha").value(true));

        var row = jdbc.queryForMap(
            "SELECT marinha_enviado_em FROM documento_emitido WHERE id = ?", docId);
        assertThat(row.get("marinha_enviado_em")).isNotNull();

        org.mockito.ArgumentCaptor<String> subjects = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(emailService, org.mockito.Mockito.atLeastOnce())
            .sendEmailComAnexo(anyString(), subjects.capture(), anyString(), anyString(), any(), anyString());
        assertThat(subjects.getAllValues()).anyMatch(s -> s.contains("GRU 608931002438533333"));

        // falha de SMTP → não registra (zera e tenta de novo com mock quebrado)
        jdbc.update("UPDATE documento_emitido SET marinha_enviado_em = NULL WHERE id = ?", docId);
        doThrow(new RuntimeException("smtp fora")).when(emailService)
            .sendEmailComAnexo(anyString(), anyString(), anyString(), anyString(), any(), anyString());
        mockMvc.perform(post("/v1/tenants/{t}/documentos/{id}/reenviar", TENANT_ACME, docId)
                .header("X-Tenant-Id", TENANT_ACME.toString()).with(staff()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enviadoMarinha").value(false));
        assertThat(jdbc.queryForMap(
            "SELECT marinha_enviado_em FROM documento_emitido WHERE id = ?", docId)
            .get("marinha_enviado_em")).isNull();
    }
}
