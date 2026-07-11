package com.jetski.tenant;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Enforcement de limites do plano (v2 item 2): leitura do jsonb, -1/ausente
 * = ilimitado, sem assinatura = ilimitado, e a negação de negócio (400) com
 * mensagem de upgrade quando o uso atinge o teto.
 */
@DisplayName("PlanoLimiteService (enforcement)")
class PlanoLimiteIntegrationTest extends AbstractIntegrationTest {

    private static final UUID TENANT = UUID.fromString("a4000000-0000-0000-0000-0000000000dd");

    @Autowired private PlanoLimiteService planoLimiteService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM assinatura WHERE tenant_id = ?", TENANT);
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, status) "
            + "VALUES (?, 'limite-teste', 'Limite Teste Ltda', 'ATIVO') "
            + "ON CONFLICT (id) DO NOTHING", TENANT);
        // Trial: frota_max=3, locacoes_mes=50, emissoes_mes=-1 (ilimitado)
        jdbc.update("INSERT INTO assinatura (tenant_id, plano_id, ciclo, dt_inicio, status) "
            + "SELECT ?, id, 'mensal', CURRENT_DATE, 'ativa' FROM plano WHERE nome = 'Trial'", TENANT);
    }

    @Test
    @DisplayName("lê o limite do plano; -1 e chave ausente = ilimitado (null)")
    void leituraDosLimites() {
        assertThat(planoLimiteService.limite(TENANT, "frota_max")).isEqualTo(3);
        assertThat(planoLimiteService.limite(TENANT, "locacoes_mes")).isEqualTo(50);
        assertThat(planoLimiteService.limite(TENANT, "emissoes_mes")).isNull();  // -1
        assertThat(planoLimiteService.limite(TENANT, "chave_inexistente")).isNull();
    }

    @Test
    @DisplayName("uso abaixo do teto passa; no teto lança 400 com mensagem de upgrade")
    void verificacao() {
        assertThatCode(() -> planoLimiteService.verificar(TENANT, "frota_max", 2, "jetskis"))
            .doesNotThrowAnyException();

        assertThatThrownBy(() -> planoLimiteService.verificar(TENANT, "frota_max", 3, "jetskis"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("3/3")
            .hasMessageContaining("upgrade");
    }

    @Test
    @DisplayName("sem assinatura ativa = ilimitado (nunca bloqueia por falha de cadastro)")
    void semAssinaturaIlimitado() {
        jdbc.update("UPDATE assinatura SET status = 'expirada' WHERE tenant_id = ?", TENANT);

        assertThat(planoLimiteService.limite(TENANT, "frota_max")).isNull();
        assertThatCode(() -> planoLimiteService.verificar(TENANT, "frota_max", 999, "jetskis"))
            .doesNotThrowAnyException();
    }
}
