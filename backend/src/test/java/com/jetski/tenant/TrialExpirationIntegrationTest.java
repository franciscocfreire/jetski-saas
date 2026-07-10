package com.jetski.tenant;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.email.EmailService;
import com.jetski.signup.api.dto.TenantSignupRequest;
import com.jetski.signup.internal.TenantSignupService;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.domain.event.TrialExpiringEvent;
import com.jetski.tenant.internal.PlatformTenantService;
import com.jetski.tenant.internal.TrialExpirationService;
import com.jetski.tenant.internal.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Expiração real do trial: vencido → assinatura 'expirada' + empresa SUSPENSA;
 * D-3 → evento de aviso (que vira e-mail aos admins via listener).
 */
@RecordApplicationEvents
@DisplayName("Trial — expiração automática e avisos")
class TrialExpirationIntegrationTest extends AbstractIntegrationTest {

    @Autowired TenantSignupService signupService;
    @Autowired PlatformTenantService platformTenantService;
    @Autowired TrialExpirationService trialExpirationService;
    @Autowired TenantRepository tenantRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEvents events;

    @MockBean EmailService emailService;

    private Tenant novoTenantAprovado(String prefixo) {
        String slug = prefixo + "-" + UUID.randomUUID().toString().substring(0, 8);
        UUID tenantId = signupService.signupNewTenant(new TenantSignupRequest(
            "Empresa " + prefixo, slug, null, slug + "@teste.com", "Dono " + prefixo)).tenantId();
        platformTenantService.approve(tenantId);
        return tenantRepository.findById(tenantId).orElseThrow();
    }

    @Test
    @DisplayName("trial vencido → assinatura 'expirada' e empresa SUSPENSA automaticamente")
    void trialVencidoSuspende() {
        Tenant tenant = novoTenantAprovado("vencido");
        jdbc.update("UPDATE assinatura SET dt_fim = CURRENT_DATE - 1 WHERE tenant_id = ?", tenant.getId());

        boolean suspendeu = trialExpirationService.processar(tenant, LocalDate.now());

        assertThat(suspendeu).isTrue();
        assertThat(jdbc.queryForObject(
            "SELECT status FROM tenant WHERE id = ?", String.class, tenant.getId()))
            .isEqualTo("SUSPENSO");
        assertThat(jdbc.queryForObject(
            "SELECT status FROM assinatura WHERE tenant_id = ?", String.class, tenant.getId()))
            .isEqualTo("expirada");
    }

    @Test
    @DisplayName("trial vencendo em 3 dias → publica TrialExpiringEvent (aviso), sem suspender")
    void trialVencendoAvisa() {
        Tenant tenant = novoTenantAprovado("aviso");
        jdbc.update("UPDATE assinatura SET dt_fim = CURRENT_DATE + 3 WHERE tenant_id = ?", tenant.getId());

        boolean suspendeu = trialExpirationService.processar(tenant, LocalDate.now());

        assertThat(suspendeu).isFalse();
        assertThat(jdbc.queryForObject(
            "SELECT status FROM tenant WHERE id = ?", String.class, tenant.getId()))
            .isEqualTo("ATIVO");
        assertThat(events.stream(TrialExpiringEvent.class)
            .filter(e -> e.tenantId().equals(tenant.getId()))
            .findFirst())
            .hasValueSatisfying(e -> {
                assertThat(e.diasRestantes()).isEqualTo(3);
                assertThat(e.dataFim()).isEqualTo(LocalDate.now().plusDays(3));
            });
    }

    @Test
    @DisplayName("trial no meio do período (ex.: faltam 10 dias) → nada acontece")
    void trialNoMeioNadaFaz() {
        Tenant tenant = novoTenantAprovado("meio");

        boolean suspendeu = trialExpirationService.processar(tenant, LocalDate.now());

        assertThat(suspendeu).isFalse();
        assertThat(events.stream(TrialExpiringEvent.class)
            .filter(e -> e.tenantId().equals(tenant.getId())).count()).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT status FROM tenant WHERE id = ?", String.class, tenant.getId()))
            .isEqualTo("ATIVO");
    }
}
