package com.jetski.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.plataforma.api.PlataformaAuditoriaController;
import com.jetski.plataforma.api.PlataformaSaudeController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoria global e saúde (F5).
 *
 * <p>O ponto central é o escopo da trilha: os testes rodam como superuser, que
 * <strong>bypassa RLS</strong> — então a policy da V057 não protege nada aqui. Quem
 * segura o escopo é o {@code WHERE tenant_id IS NULL} do controller, e é exatamente isso
 * que estes testes travam. A policy em si é validada com role não-superuser no
 * {@code RlsEnforcementIntegrationTest}.
 */
@DisplayName("Auditoria global e saúde da plataforma (F5)")
class PlataformaAuditoriaSaudeIntegrationTest extends AbstractIntegrationTest {

    private static final UUID TENANT = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final String ACAO_GLOBAL = "F5_TESTE_GLOBAL";
    private static final String ACAO_OUTRA = "F5_TESTE_OUTRA";
    private static final String ACAO_TENANT = "F5_TESTE_TENANT";

    @Autowired PlataformaAuditoriaController auditoria;
    @Autowired PlataformaSaudeController saude;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        limpar();
        jdbc.update("""
            INSERT INTO auditoria (tenant_id, acao, entidade, dados_novos)
            VALUES (NULL, ?, 'plataforma', '{"emailAlvo":"alguem@teste.local"}'::jsonb),
                   (NULL, ?, 'plataforma', NULL),
                   (?, ?, 'locacao', NULL)
            """, ACAO_GLOBAL, ACAO_OUTRA, TENANT, ACAO_TENANT);
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    private void limpar() {
        jdbc.update("DELETE FROM auditoria WHERE acao IN (?,?,?)",
            ACAO_GLOBAL, ACAO_OUTRA, ACAO_TENANT);
    }

    private List<String> acoesDaTrilha(String filtro) {
        return auditoria.listar(filtro, 500).getBody().stream()
            .map(l -> (String) l.get("acao"))
            .filter(a -> a.startsWith("F5_TESTE_"))
            .toList();
    }

    @Test
    @DisplayName("Trilha traz só linhas sem empresa — auditoria de tenant nunca aparece")
    void trilhaSoGlobal() {
        assertThat(acoesDaTrilha(null))
            .containsExactlyInAnyOrder(ACAO_GLOBAL, ACAO_OUTRA)
            .doesNotContain(ACAO_TENANT);
    }

    @Test
    @DisplayName("Filtro por ação restringe a trilha")
    void filtroPorAcao() {
        assertThat(acoesDaTrilha(ACAO_GLOBAL)).containsExactly(ACAO_GLOBAL);
        assertThat(acoesDaTrilha(ACAO_TENANT))
            .as("filtrar por ação de empresa não fura o escopo global")
            .isEmpty();
    }

    @Test
    @DisplayName("Ordena do mais recente para o mais antigo e respeita o limite")
    void ordemELimite() {
        var todas = auditoria.listar(null, 1).getBody();
        assertThat(todas).hasSize(1);

        var instantes = auditoria.listar(null, 500).getBody().stream()
            .map(l -> ((java.sql.Timestamp) l.get("created_at")).toInstant())
            .toList();
        assertThat(instantes).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    @DisplayName("Lista de ações alimenta o filtro sem repetir e sem ação de empresa")
    void acoesDistintas() {
        var acoes = auditoria.acoes().getBody();
        assertThat(acoes).contains(ACAO_GLOBAL, ACAO_OUTRA).doesNotContain(ACAO_TENANT);
        assertThat(acoes).doesNotHaveDuplicates();
    }

    /**
     * O driver entrega {@code jsonb} embrulhado num {@code PGobject}, que o Jackson
     * serializa como {@code {"type":"jsonb","value":"{…}"}} — o "antes e depois" da trilha
     * chegava ao console como string escapada dentro de um wrapper, e a coluna de detalhe
     * ficava sempre vazia. Nenhum teste de escopo pegaria isso.
     */
    @Test
    @DisplayName("dados_novos chega como JSON de verdade, não como wrapper do driver")
    void jsonbNaoVemEmbrulhado() {
        var linha = auditoria.listar(ACAO_GLOBAL, 1).getBody().get(0);

        assertThat(linha.get("dados_novos")).isInstanceOf(JsonNode.class);
        assertThat(((JsonNode) linha.get("dados_novos")).path("emailAlvo").asText())
            .isEqualTo("alguem@teste.local");
        assertThat(linha.get("dados_anteriores")).as("null continua null").isNull();
    }

    @Test
    @DisplayName("Saúde reporta infra do Actuator e os quatro sinais de operação")
    void saudeCompleta() {
        var corpo = saude.saude().getBody();

        assertThat(corpo.statusGeral()).isNotBlank();
        assertThat(corpo.infra()).containsKey("db");
        assertThat(corpo.operacao())
            .containsKeys("readModel", "emissao", "filas", "suporte");
    }

    @Test
    @DisplayName("Nenhum indicador de operação quebrou — erro viraria a chave 'erro'")
    void indicadoresConsultaveis() {
        var operacao = saude.saude().getBody().operacao();

        operacao.forEach((bloco, dados) -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> indicador = (Map<String, Object>) dados;
            assertThat(indicador)
                .as("indicador '%s' falhou: %s", bloco, indicador.get("erro"))
                .doesNotContainKey("erro");
        });
    }
}
