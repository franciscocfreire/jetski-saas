package com.jetski.usuarios;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.SessaoSuporte;
import com.jetski.shared.security.TenantContext;
import com.jetski.usuarios.internal.SessaoSuporteService;
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
 * Sessão de suporte (F3) — o que substitui o god mode.
 *
 * <p>O que estes testes travam: o código de handoff é de USO ÚNICO e tem prazo; a sessão
 * expira e pode ser revogada na hora; e nada disso vale sem motivo declarado. São as
 * garantias que fazem "entrar numa empresa" ser um ato registrado em vez de um switch.
 */
@DisplayName("Sessão de suporte (F3)")
class SessaoSuporteIntegrationTest extends AbstractIntegrationTest {

    private static final UUID TENANT = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID OPERADOR = UUID.fromString("9f000000-0000-0000-0000-00000000f301");

    @Autowired SessaoSuporteService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        limpar();
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo) "
            + "VALUES (?, 'f3-operador@teste.local', 'Operador F3', TRUE) "
            + "ON CONFLICT (id) DO NOTHING", OPERADOR);
        TenantContext.setUsuarioId(OPERADOR);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        limpar();
    }

    private void limpar() {
        // Antes do usuário: a FK é ON DELETE SET NULL e a trilha perderia o dono.
        jdbc.update("DELETE FROM auditoria WHERE entidade = 'SESSAO_SUPORTE' AND usuario_id = ?",
            OPERADOR);
        jdbc.update("DELETE FROM plataforma_sessao_suporte WHERE operador_id = ?", OPERADOR);
        jdbc.update("DELETE FROM usuario WHERE id = ?", OPERADOR);
    }

    @Test
    @DisplayName("Motivo é obrigatório — acesso sem justificativa não abre")
    void motivoObrigatorio() {
        assertThatThrownBy(() -> service.abrir(TENANT, "   ", true, "127.0.0.1", "teste"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("motivo");
        assertThatThrownBy(() -> service.abrir(TENANT, "ok", true, "127.0.0.1", "teste"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Abrir NÃO dá acesso: sem resgatar o código, não existe sessão válida")
    void abrirSozinhoNaoDaAcesso() {
        var abertura = service.abrir(TENANT, "investigar cobrança duplicada", true, "127.0.0.1", "t");

        // o código não é token: usá-lo como cookie não vale
        assertThat(service.validar(abertura.codigo())).isNull();
        Integer comToken = jdbc.queryForObject(
            "SELECT count(*) FROM plataforma_sessao_suporte WHERE id = ? AND token_hash IS NOT NULL",
            Integer.class, abertura.sessaoId());
        assertThat(comToken).isZero();
    }

    @Test
    @DisplayName("Resgate devolve token válido, apontando para a empresa e o modo declarados")
    void resgateAbreSessao() {
        var abertura = service.abrir(TENANT, "suporte ao checkin travado", true, "127.0.0.1", "t");
        String token = service.resgatar(abertura.codigo());

        SessaoSuporte sessao = service.validar(token);
        assertThat(sessao).isNotNull();
        assertThat(sessao.tenantId()).isEqualTo(TENANT);
        assertThat(sessao.operadorId()).isEqualTo(OPERADOR);
        assertThat(sessao.somenteLeitura()).isTrue();
    }

    @Test
    @DisplayName("Código é de USO ÚNICO — o segundo resgate falha")
    void codigoUsoUnico() {
        var abertura = service.abrir(TENANT, "conferir emissão da marinha", false, "127.0.0.1", "t");
        service.resgatar(abertura.codigo());

        assertThatThrownBy(() -> service.resgatar(abertura.codigo()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("já utilizado");
    }

    @Test
    @DisplayName("Código só vale para QUEM abriu — vazamento não vira acesso de outro")
    void codigoAmarradoAoOperador() {
        var abertura = service.abrir(TENANT, "atendimento ao cliente da acme", true, "127.0.0.1", "t");

        UUID outro = UUID.fromString("9f000000-0000-0000-0000-00000000f302");
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo) "
            + "VALUES (?, 'f3-outro@teste.local', 'Outro', TRUE) ON CONFLICT (id) DO NOTHING", outro);
        try {
            TenantContext.setUsuarioId(outro);
            assertThatThrownBy(() -> service.resgatar(abertura.codigo()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("outro operador");

            // e o dono ainda consegue usar — a tentativa alheia não queimou o código
            TenantContext.setUsuarioId(OPERADOR);
            assertThat(service.validar(service.resgatar(abertura.codigo()))).isNotNull();
        } finally {
            jdbc.update("DELETE FROM usuario WHERE id = ?", outro);
        }
    }

    @Test
    @DisplayName("Código inexistente não abre sessão")
    void codigoInvalido() {
        assertThatThrownBy(() -> service.resgatar("codigo-que-nao-existe"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Código expirado não resgata (prazo é de clicar, não de guardar)")
    void codigoExpirado() {
        var abertura = service.abrir(TENANT, "verificar fechamento do dia", true, "127.0.0.1", "t");
        jdbc.update("UPDATE plataforma_sessao_suporte SET codigo_expira_em = now() - interval '1 minute' "
            + "WHERE id = ?", abertura.sessaoId());

        assertThatThrownBy(() -> service.resgatar(abertura.codigo()))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Sessão expirada deixa de valer — sem renovação por uso")
    void sessaoExpirada() {
        var abertura = service.abrir(TENANT, "acompanhar reserva do cliente", true, "127.0.0.1", "t");
        String token = service.resgatar(abertura.codigo());
        assertThat(service.validar(token)).isNotNull();

        jdbc.update("UPDATE plataforma_sessao_suporte SET expira_em = now() - interval '1 second' "
            + "WHERE id = ?", abertura.sessaoId());

        assertThat(service.validar(token)).isNull();
    }

    @Test
    @DisplayName("Revogação vale na hora — sem cache, sem janela")
    void revogacaoImediata() {
        var abertura = service.abrir(TENANT, "corrigir lançamento errado", false, "127.0.0.1", "t");
        String token = service.resgatar(abertura.codigo());
        assertThat(service.validar(token)).isNotNull();

        service.encerrar(abertura.sessaoId());

        assertThat(service.validar(token)).isNull();
    }

    @Test
    @DisplayName("Segredos não ficam em claro no banco")
    void segredosSaoHash() {
        var abertura = service.abrir(TENANT, "auditoria de credenciais", true, "127.0.0.1", "t");
        String token = service.resgatar(abertura.codigo());

        Integer emClaro = jdbc.queryForObject(
            "SELECT count(*) FROM plataforma_sessao_suporte WHERE id = ? "
            + "AND (codigo_hash = ? OR token_hash = ?)",
            Integer.class, abertura.sessaoId(), abertura.codigo(), token);
        assertThat(emClaro).isZero();
    }

    @Test
    @DisplayName("Trilha registra motivo e modo — a empresa pode consultar quem entrou")
    void trilhaRegistra() {
        service.abrir(TENANT, "cliente relatou cobrança em duplicidade", true, "127.0.0.1", "t");

        var registros = service.listar(TENANT, 10);
        assertThat(registros).isNotEmpty();
        assertThat(registros.get(0).motivo()).contains("duplicidade");
        assertThat(registros.get(0).somenteLeitura()).isTrue();
        assertThat(registros.get(0).operadorId()).isEqualTo(OPERADOR);
    }

    /**
     * Audit DUAL da abertura: uma linha na empresa (dado dela) e uma global (o console lê
     * sem cross-tenant). A linha global não tem {@code tenant_id} — é justamente o que a
     * torna legível pelo console —, então a empresa alvo precisa estar dentro do payload:
     * sem ela a trilha diz "alguém abriu uma sessão" e não em qual empresa.
     *
     * <p><strong>Este teste não prova o bug que existia</strong>: as duas linhas nasciam na
     * mesma transação, e a da empresa violava a RLS quando a rota de plataforma não tinha
     * tenant no contexto — derrubando as duas. Aqui a conexão é superuser e bypassa RLS,
     * então o cenário nem acontece. O que prova é o
     * {@code RlsEnforcementIntegrationTest#auditoriaExigeContextoDoProprioTenant}.
     */
    @Test
    @DisplayName("Abertura grava as DUAS linhas e identifica a empresa alvo")
    void trilhaDualIdentificaAEmpresa() {
        var abertura = service.abrir(TENANT, "cliente relatou cobrança em duplicidade",
            true, "127.0.0.1", "t");

        var linhas = jdbc.queryForList("""
            SELECT tenant_id, dados_novos::text AS dados FROM auditoria
             WHERE acao = 'SUPORTE_SESSAO_ABERTA' AND entidade_id = ?
            """, abertura.sessaoId());

        assertThat(linhas).hasSize(2);
        assertThat(linhas).anySatisfy(l -> assertThat(l.get("tenant_id")).isNull());
        assertThat(linhas).anySatisfy(l -> assertThat(l.get("tenant_id")).isEqualTo(TENANT));
        assertThat(linhas).allSatisfy(l ->
            assertThat((String) l.get("dados"))
                .contains(TENANT.toString())
                .contains("duplicidade"));
    }

}
