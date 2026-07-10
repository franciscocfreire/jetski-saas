package com.jetski.signup;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.signup.api.dto.TenantSignupRequest;
import com.jetski.signup.api.dto.TenantSignupResponse;
import com.jetski.signup.internal.TenantSignupService;
import com.jetski.tenant.internal.PlatformTenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Fluxo completo de onboarding de empresa: signup público → tenant PENDENTE_APROVACAO
 * (sem trial, super admin notificado) → approve → ATIVO + trial criada + aviso de
 * liberação ao e-mail do signup (admin ainda não ativou o magic-link).
 */
@TestPropertySource(properties = "platform.admin-emails=superadmin@plataforma.com")
@DisplayName("Onboarding de empresa — signup público até a aprovação")
class SignupApprovalFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired TenantSignupService signupService;
    @Autowired PlatformTenantService platformTenantService;
    @Autowired JdbcTemplate jdbc;

    @MockBean EmailService emailService;

    @Test
    @DisplayName("signup nasce PENDENTE_APROVACAO sem trial; approve ativa, cria trial e avisa o fundador")
    void signupPendenteAteAprovacao() {
        String slug = "flow-" + UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = slug + "@teste.com";

        // 1) Signup público
        TenantSignupResponse response = signupService.signupNewTenant(new TenantSignupRequest(
            "Empresa Fluxo Ltda", slug, null, adminEmail, "Fundador Fluxo"));
        UUID tenantId = response.tenantId();

        String status = jdbc.queryForObject(
            "SELECT status FROM tenant WHERE id = ?", String.class, tenantId);
        assertThat(status).isEqualTo("PENDENTE_APROVACAO");

        Integer assinaturas = jdbc.queryForObject(
            "SELECT COUNT(*) FROM assinatura WHERE tenant_id = ?", Integer.class, tenantId);
        assertThat(assinaturas).isZero(); // trial só na aprovação

        // magic-link para o fundador + aviso ao super admin (best-effort)
        verify(emailService).sendInvitationEmail(eq(adminEmail), eq("Fundador Fluxo"), anyString(), anyString());
        verify(emailService).sendNewTenantNotification(
            "superadmin@plataforma.com", "Empresa Fluxo Ltda", slug);

        // 2) Aprovação pelo super admin
        platformTenantService.approve(tenantId);

        status = jdbc.queryForObject("SELECT status FROM tenant WHERE id = ?", String.class, tenantId);
        assertThat(status).isEqualTo("ATIVO");

        assinaturas = jdbc.queryForObject(
            "SELECT COUNT(*) FROM assinatura WHERE tenant_id = ? AND status = 'ativa'", Integer.class, tenantId);
        assertThat(assinaturas).isEqualTo(1); // trial criada na aprovação

        // 3) Admin ainda não ativou o magic-link (sem Membro) → o aviso de liberação
        //    vai para o e-mail do tenant_signup (listener async pós-commit do módulo signup)
        verify(emailService, timeout(5000)).sendTenantStatusNotification(
            eq(adminEmail), eq("TENANT_APPROVED"), eq("Empresa Fluxo Ltda"), isNull());
    }

    @Test
    @DisplayName("slug reservado (www/api/...) → 400 de negócio, tenant não é criado")
    void slugReservadoEhRejeitado() {
        assertThatThrownBy(() -> signupService.signupNewTenant(new TenantSignupRequest(
                "Empresa Esperta Ltda", "api", null, "esperto@teste.com", "Esperto")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("reservado");

        Integer tenants = jdbc.queryForObject(
            "SELECT COUNT(*) FROM tenant WHERE slug = 'api'", Integer.class);
        assertThat(tenants).isZero();
    }
}
