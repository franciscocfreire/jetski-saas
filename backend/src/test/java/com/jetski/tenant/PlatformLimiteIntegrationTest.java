package com.jetski.tenant;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.internal.PlatformLimiteService;
import com.jetski.tenant.internal.PlatformLimiteService.LimiteUsuarios;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Limite de usuários personalizado por empresa (V068).
 *
 * <p>O ponto que importa travar: a personalização vale no ENFORCEMENT
 * ({@link PlanoLimiteService}), não só na tela. Um limite que o console mostra e o convite
 * ignora seria pior que não ter a configuração.
 */
@DisplayName("Console: limite de usuários por empresa")
class PlatformLimiteIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COM_PLANO = UUID.fromString("9f000000-0000-0000-0000-0000000e6a01");
    private static final UUID SEM_PLANO = UUID.fromString("9f000000-0000-0000-0000-0000000e6a02");
    private static final UUID ADMIN = UUID.fromString("9f000000-0000-0000-0000-0000000e6b01");

    @Autowired PlatformLimiteService service;
    @Autowired PlanoLimiteService planoLimiteService;
    @Autowired JdbcTemplate jdbc;

    private int limiteTrial;

    @BeforeEach
    void setUp() {
        limpar();
        criarTenant(COM_PLANO, "limite-com-plano");
        criarTenant(SEM_PLANO, "limite-sem-plano");
        jdbc.update("INSERT INTO assinatura (tenant_id, plano_id, ciclo, dt_inicio, status) "
            + "SELECT ?, id, 'mensal', CURRENT_DATE, 'ativa' FROM plano WHERE nome = 'Trial' "
            + "ORDER BY id LIMIT 1", COM_PLANO);
        limiteTrial = jdbc.queryForObject(
            "SELECT (limites->>'usuarios_max')::int FROM plano WHERE nome = 'Trial' ORDER BY id LIMIT 1",
            Integer.class);
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo) "
            + "VALUES (?, 'e6-admin@teste.local', 'Admin Limite', TRUE)", ADMIN);
        jdbc.update("INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) "
            + "VALUES (?, ?, '{ADMIN_TENANT}', TRUE)", COM_PLANO, ADMIN);
        TenantContext.setUsuarioId(ADMIN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        limpar();
    }

    private void limpar() {
        jdbc.update("DELETE FROM membro WHERE tenant_id IN (?,?)", COM_PLANO, SEM_PLANO);
        jdbc.update("DELETE FROM usuario WHERE id = ?", ADMIN);
        jdbc.update("DELETE FROM assinatura WHERE tenant_id IN (?,?)", COM_PLANO, SEM_PLANO);
        jdbc.update("UPDATE tenant SET limites_override = '{}'::jsonb WHERE id IN (?,?)",
            COM_PLANO, SEM_PLANO);
    }

    private void criarTenant(UUID id, String slug) {
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, status) "
            + "VALUES (?, ?, ?, 'ATIVO') ON CONFLICT (id) DO NOTHING", id, slug, slug + " Ltda");
    }

    /** Leitura como o convite faz: dentro do contexto da empresa. */
    private Integer limiteNoEnforcement(UUID tenantId, String chave) {
        TenantContext.setTenantId(tenantId);
        return planoLimiteService.limite(tenantId, chave);
    }

    @Test
    @DisplayName("Sem personalização, vale o limite do plano")
    void semPersonalizacao() {
        LimiteUsuarios l = service.usuarios(COM_PLANO);

        assertThat(l.plano()).isEqualTo("Trial");
        assertThat(l.doPlano()).isEqualTo(limiteTrial);
        assertThat(l.personalizado()).isNull();
        assertThat(l.efetivo()).isEqualTo(limiteTrial);
        assertThat(l.ativos()).isEqualTo(1);
    }

    @Test
    @DisplayName("Limite personalizado vence o do plano — também no enforcement do convite")
    void personalizadoVencePlano() {
        LimiteUsuarios l = service.definirUsuarios(COM_PLANO, limiteTrial + 5, "contrato especial");

        assertThat(l.personalizado()).isEqualTo(limiteTrial + 5);
        assertThat(l.efetivo()).isEqualTo(limiteTrial + 5);
        assertThat(l.doPlano()).isEqualTo(limiteTrial);
        assertThat(limiteNoEnforcement(COM_PLANO, "usuarios_max")).isEqualTo(limiteTrial + 5);

        // abaixo do novo teto passa; no teto bloqueia, apontando o suporte (upgrade não resolve)
        planoLimiteService.verificar(COM_PLANO, "usuarios_max", limiteTrial, "usuários");
        assertThatThrownBy(() ->
            planoLimiteService.verificar(COM_PLANO, "usuarios_max", limiteTrial + 5, "usuários"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("suporte");
    }

    @Test
    @DisplayName("Voltar ao plano remove a personalização")
    void voltarAoPlano() {
        service.definirUsuarios(COM_PLANO, 9, "teste");

        LimiteUsuarios l = service.definirUsuarios(COM_PLANO, null, "fim do contrato especial");

        assertThat(l.personalizado()).isNull();
        assertThat(l.efetivo()).isEqualTo(limiteTrial);
        assertThat(limiteNoEnforcement(COM_PLANO, "usuarios_max")).isEqualTo(limiteTrial);
    }

    @Test
    @DisplayName("Empresa sem assinatura: ilimitado, a menos que o console defina um teto")
    void semAssinatura() {
        assertThat(limiteNoEnforcement(SEM_PLANO, "usuarios_max")).isNull();

        LimiteUsuarios l = service.definirUsuarios(SEM_PLANO, 3, "EAMA pequena");

        assertThat(l.plano()).isNull();
        assertThat(l.efetivo()).isEqualTo(3);
        assertThat(limiteNoEnforcement(SEM_PLANO, "usuarios_max")).isEqualTo(3);
    }

    @Test
    @DisplayName("Personalizar usuários não mexe nos outros limites do plano")
    void naoAfetaOutrasChaves() {
        Integer frota = jdbc.queryForObject(
            "SELECT (limites->>'frota_max')::int FROM plano WHERE nome = 'Trial' ORDER BY id LIMIT 1",
            Integer.class);

        service.definirUsuarios(COM_PLANO, 7, "teste");

        assertThat(limiteNoEnforcement(COM_PLANO, "frota_max"))
            .isEqualTo(frota == null || frota < 0 ? null : frota);
    }

    @Test
    @DisplayName("Teto menor que 1 ou sem motivo é recusado")
    void validacoes() {
        assertThatThrownBy(() -> service.definirUsuarios(COM_PLANO, 0, "x"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("pelo menos 1");
        assertThatThrownBy(() -> service.definirUsuarios(COM_PLANO, 5, "  "))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("motivo");
        assertThat(service.usuarios(COM_PLANO).personalizado()).isNull();
    }
}
