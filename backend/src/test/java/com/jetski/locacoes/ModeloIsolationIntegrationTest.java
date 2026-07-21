package com.jetski.locacoes;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.locacoes.api.ModeloService;
import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.internal.JetskiService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
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
 * Regressão do vazamento de 10/jul/2026: a policy {@code marketplace_public_read}
 * (permissiva, soma com OR) deixava modelos de OUTROS tenants exibidos no
 * marketplace aparecerem na lista tenant-scoped. O filtro explícito de tenant nas
 * queries garante o isolamento MESMO nos testes (superuser bypassa RLS — regra 1).
 */
@DisplayName("Modelo — isolamento por tenant (leak do marketplace_public_read)")
class ModeloIsolationIntegrationTest extends AbstractIntegrationTest {

    private static final UUID TENANT_ACME = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

    @Autowired ModeloService modeloService;
    @Autowired JetskiService jetskiService;
    @Autowired JdbcTemplate jdbc;

    private UUID outroTenant;
    private UUID modeloAcmeMarketplace;

    @BeforeEach
    void setUp() {
        // ACME com um modelo exibido no marketplace (a isca do vazamento)
        modeloAcmeMarketplace = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo, exibir_no_marketplace)
            VALUES (?, ?, 'Leak Marketplace 310', 'Kawasaki', 310, 3, 200.00, 5, 60.00, 500.00,
                    FALSE, TRUE, TRUE)
            """, modeloAcmeMarketplace, TENANT_ACME);
        jdbc.update("UPDATE tenant SET status = 'ATIVO', exibir_no_marketplace = TRUE WHERE id = ?",
            TENANT_ACME);

        // Outro tenant, recém-criado, sem nenhum modelo
        outroTenant = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO tenant (id, slug, razao_social, status, timezone, moeda)
            VALUES (?, ?, 'Empresa Sem Modelos Ltda', 'ATIVO', 'America/Sao_Paulo', 'BRL')
            """, outroTenant, "leak-" + outroTenant.toString().substring(0, 8));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbc.update("DELETE FROM modelo WHERE id = ?", modeloAcmeMarketplace);
        jdbc.update("DELETE FROM tenant WHERE id = ?", outroTenant);
    }

    @Test
    @DisplayName("tenant novo NÃO vê modelos de marketplace de outros tenants na lista")
    void listaNaoVazaModelosDeOutroTenant() {
        TenantContext.setTenantId(outroTenant);

        assertThat(modeloService.listActiveModels()).isEmpty();
        assertThat(modeloService.listAllModels()).isEmpty();
    }

    @Test
    @DisplayName("findById de modelo de outro tenant → 'não encontrado' (mesmo visível no marketplace)")
    void findByIdNaoVazaModeloDeOutroTenant() {
        TenantContext.setTenantId(outroTenant);

        assertThatThrownBy(() -> modeloService.findById(modeloAcmeMarketplace))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("não encontrado");
    }

    @Test
    @DisplayName("nome igual ao de modelo de outro tenant NÃO bloqueia o cadastro")
    void nomeDeOutroTenantNaoColide() {
        TenantContext.setTenantId(TENANT_ACME);
        assertThat(modeloService.listActiveModels())
            .anyMatch(m -> m.getNome().equals("Leak Marketplace 310")); // dono vê o próprio

        TenantContext.setTenantId(outroTenant);
        var criado = modeloService.createModelo(com.jetski.locacoes.domain.Modelo.builder()
            .tenantId(outroTenant)
            .nome("Leak Marketplace 310")
            .precoBaseHora(new java.math.BigDecimal("150.00"))
            .build());
        assertThat(criado.getId()).isNotNull();
        jdbc.update("DELETE FROM modelo WHERE id = ?", criado.getId());
    }

    /**
     * Regressão do incidente de 21/jul/2026 (prod): jetski criado com modelo de
     * OUTRO tenant (aceito pelo antigo existsById sem escopo) gera FK
     * cross-tenant e trava o reset/exclusão da empresa dona do modelo.
     */
    @Test
    @DisplayName("criar jetski com modelo de outro tenant → 'Modelo não encontrado'")
    void createJetskiRejeitaModeloDeOutroTenant() {
        TenantContext.setTenantId(outroTenant);

        assertThatThrownBy(() -> jetskiService.createJetski(Jetski.builder()
                .tenantId(outroTenant)
                .modeloId(modeloAcmeMarketplace)
                .serie("LEAK-CROSS-001")
                .build()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Modelo não encontrado");
    }
}
