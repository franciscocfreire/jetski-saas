package com.jetski.dashboard;

import com.jetski.dashboard.api.dto.OnboardingChecklistResponse;
import com.jetski.dashboard.internal.OnboardingChecklistService;
import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.email.EmailService;
import com.jetski.signup.api.dto.TenantSignupRequest;
import com.jetski.signup.internal.TenantSignupService;
import com.jetski.tenant.internal.PlatformTenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checklist "primeiros passos": empresa recém-aprovada tem tudo pendente;
 * cada flag vira true conforme os dados reais aparecem (nada persistido à mão).
 */
@DisplayName("Onboarding — checklist de primeiros passos")
class OnboardingChecklistIntegrationTest extends AbstractIntegrationTest {

    @Autowired TenantSignupService signupService;
    @Autowired PlatformTenantService platformTenantService;
    @Autowired OnboardingChecklistService checklistService;
    @Autowired JdbcTemplate jdbc;

    @MockBean EmailService emailService;

    @Test
    @DisplayName("empresa nova → tudo pendente; flags viram true conforme os dados aparecem")
    void checklistAutoDetecta() {
        String slug = "onb-" + UUID.randomUUID().toString().substring(0, 8);
        UUID tenantId = signupService.signupNewTenant(new TenantSignupRequest(
            "Empresa Onboarding Ltda", slug, null, slug + "@teste.com", "Dono Onboarding")).tenantId();
        platformTenantService.approve(tenantId);

        OnboardingChecklistResponse inicial = checklistService.checklist(tenantId);
        assertThat(inicial.temModelo()).isFalse();
        assertThat(inicial.temJetski()).isFalse();
        assertThat(inicial.marinhaEmailConfigurado()).isFalse();
        assertThat(inicial.pixConfigurado()).isFalse();
        assertThat(inicial.equipeConvidada()).isFalse();
        assertThat(inicial.primeiraLocacaoFeita()).isFalse();
        assertThat(inicial.completo()).isFalse();

        // Modelo + jetski cadastrados
        UUID modeloId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'Onb Modelo', 'Sea-Doo', 130, 2, 150.00, 5, 50.00, 300.00, FALSE, TRUE)
            """, modeloId, tenantId);
        jdbc.update("""
            INSERT INTO jetski (id, tenant_id, modelo_id, serie, horimetro_atual, status, ativo)
            VALUES (?, ?, ?, 'SN-ONB-01', 0, 'DISPONIVEL', TRUE)
            """, UUID.randomUUID(), tenantId, modeloId);

        // Configurações da empresa
        jdbc.update("UPDATE tenant SET marinha_email = 'capitania@teste.com', pix_chave = 'pix@onb.com' WHERE id = ?",
            tenantId);

        OnboardingChecklistResponse depois = checklistService.checklist(tenantId);
        assertThat(depois.temModelo()).isTrue();
        assertThat(depois.temJetski()).isTrue();
        assertThat(depois.marinhaEmailConfigurado()).isTrue();
        assertThat(depois.pixConfigurado()).isTrue();
        assertThat(depois.equipeConvidada()).isFalse();   // admin ainda não ativou / ninguém convidado
        assertThat(depois.primeiraLocacaoFeita()).isFalse();
        assertThat(depois.completo()).isFalse();
    }
}
