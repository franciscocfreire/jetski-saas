package com.jetski.locacoes;

import com.jetski.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O índice único da V067: uma ficha por documento por loja.
 *
 * A garantia estava só no código — e são quatro caminhos que gravam cliente
 * (balcão, portal, perfil, merge de CPF). Um deles esquecer a normalização e a
 * duplicata volta a nascer calada: não quebra nada, só divide o histórico da
 * pessoa em duas fichas e afrouxa a trava anti-takeover do criarPreConta.
 */
@DisplayName("V067 — unicidade do documento por loja")
class ClienteDocumentoUnicoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private static final String SQL = """
        INSERT INTO cliente (id, tenant_id, nome, documento, documento_tipo, origem, status_conta, ativo)
        VALUES (?, ?, ?, ?, ?, 'BALCAO', 'SEM_LOGIN', TRUE)
        """;

    private UUID inserir(UUID tenant, String nome, String documento, String tipo) {
        UUID id = UUID.randomUUID();
        jdbc.update(SQL, id, tenant, nome, documento, tipo);
        return id;
    }

    @Test
    @DisplayName("mesmo documento na mesma loja é rejeitado pelo banco")
    void duplicataNaMesmaLoja() {
        UUID tenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, slug, razao_social) VALUES (?, ?, ?)",
            tenant, "ux-" + tenant.toString().substring(0, 8), "Loja do Teste de Unicidade");

        inserir(tenant, "Primeira ficha", "84721590350", "CPF");

        assertThatThrownBy(() -> inserir(tenant, "Segunda ficha", "84721590350", "CPF"))
            .hasMessageContaining("ux_cliente_tenant_documento");
    }

    @Test
    @DisplayName("o mesmo CPF em OUTRA loja é outra ficha — o índice é por tenant")
    void mesmoDocumentoEmOutraLoja() {
        UUID lojaA = UUID.randomUUID();
        UUID lojaB = UUID.randomUUID();
        for (UUID t : new UUID[]{lojaA, lojaB}) {
            jdbc.update("INSERT INTO tenant (id, slug, razao_social) VALUES (?, ?, ?)",
                t, "ux-" + t.toString().substring(0, 8), "Loja " + t);
        }

        // Regra de produto: o dedupe do balcão é POR LOJA. A mesma pessoa
        // atendida em duas locadoras tem duas fichas, de propósito.
        inserir(lojaA, "Helena na loja A", "84721590350", "CPF");
        inserir(lojaB, "Helena na loja B", "84721590350", "CPF");

        // Escopo nos DOIS tenants deste teste: a suíte compartilha o banco e
        // outra classe pode ter inserido o mesmo CPF em outra loja — contar
        // global deixaria o teste dependente da ordem de execução.
        Integer n = jdbc.queryForObject(
            "SELECT count(*) FROM cliente WHERE documento = ? AND tenant_id IN (?, ?)",
            Integer.class, "84721590350", lojaA, lojaB);
        assertThat(n).isEqualTo(2);
    }

    @Test
    @DisplayName("fichas sem documento convivem — o índice é parcial")
    void leadsSemDocumento() {
        UUID tenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, slug, razao_social) VALUES (?, ?, ?)",
            tenant, "ux-" + tenant.toString().substring(0, 8), "Loja dos Leads");

        // Lead capturado na praia: nome e telefone, sem documento. Se o índice
        // não fosse parcial, o segundo lead da loja seria rejeitado.
        inserir(tenant, "Lead 1", null, null);
        inserir(tenant, "Lead 2", null, null);

        Integer n = jdbc.queryForObject(
            "SELECT count(*) FROM cliente WHERE tenant_id = ? AND documento IS NULL",
            Integer.class, tenant);
        assertThat(n).isEqualTo(2);
    }

    @Test
    @DisplayName("passaporte e CPF de mesmo valor não colidem — o tipo entra na chave")
    void tiposDiferentesNaoColidem() {
        UUID tenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, slug, razao_social) VALUES (?, ?, ?)",
            tenant, "ux-" + tenant.toString().substring(0, 8), "Loja dos Tipos");

        inserir(tenant, "Brasileira", "12345678901", "CPF");
        inserir(tenant, "Estrangeiro", "12345678901", "PASSAPORTE");

        Integer n = jdbc.queryForObject(
            "SELECT count(*) FROM cliente WHERE tenant_id = ?", Integer.class, tenant);
        assertThat(n).isEqualTo(2);
    }
}
