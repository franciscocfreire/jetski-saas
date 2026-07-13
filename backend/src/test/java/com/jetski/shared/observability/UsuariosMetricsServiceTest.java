package com.jetski.shared.observability;

import com.jetski.shared.internal.keycloak.KeycloakAdminService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários do {@link UsuariosMetricsService} (gauges de monitoração
 * de usuários: sessões Keycloak + cadastrados).
 *
 * <p>Foco: registro único dos gauges, atualização a partir das fontes
 * (Keycloak Admin API / banco) e RESILIÊNCIA — falha de fonte mantém o
 * último valor publicado, nunca propaga exceção para o scheduler.
 *
 * <p>A contagem de clientes usa {@link ConnectionCallback} (uma única conexão
 * física — set_config transaction-local + COUNT na MESMA transação); aqui o
 * callback é executado contra uma {@link Connection} mockada, exercitando a
 * lógica real: materialização dos tenants, set_config por tenant, soma,
 * rollback e restauração do autocommit.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuariosMetricsServiceTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private KeycloakAdminService keycloakAdminService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MeterRegistry meterRegistry;
    private UsuariosMetricsService service;

    /** Statement de set_config da última conexão mockada (para verificar o bind por tenant). */
    private PreparedStatement psSetConfig;

    /** Statement de neutralização ('' transaction-local) da última conexão mockada. */
    private PreparedStatement psNeutralizar;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new UsuariosMetricsService(meterRegistry, keycloakAdminService, jdbcTemplate);
    }

    @Test
    @DisplayName("Deve registrar os gauges de cadastrados na criação do service")
    void testGaugesRegistradosNaCriacao() {
        assertThat(meterRegistry.find("jetski.usuarios.cadastrados").gauge()).isNotNull();
        assertThat(meterRegistry.find("jetski.clientes.cadastrados").gauge()).isNotNull();
    }

    @Test
    @DisplayName("Deve publicar sessões ativas por client a partir do client-session-stats")
    void testAtualizarSessoesKeycloak() {
        when(keycloakAdminService.getClientSessionStats()).thenReturn(List.of(
            Map.of("clientId", "jetski-backoffice", "active", "7", "offline", "2"),
            Map.of("clientId", "portal-cliente", "active", "3", "offline", "0")
        ));

        service.atualizarSessoesKeycloak();

        assertThat(gaugeValue("jetski.keycloak.sessoes.ativas", "client_id", "jetski-backoffice"))
            .isEqualTo(7.0);
        assertThat(gaugeValue("jetski.keycloak.sessoes.ativas", "client_id", "portal-cliente"))
            .isEqualTo(3.0);
    }

    @Test
    @DisplayName("Keycloak fora do ar: não propaga exceção e mantém o último valor do gauge")
    void testSessoesKeycloakMantemUltimoValorEmFalha() {
        when(keycloakAdminService.getClientSessionStats())
            .thenReturn(List.of(Map.of("clientId", "jetski-backoffice", "active", "5", "offline", "0")))
            .thenThrow(new RuntimeException("Connection refused"));

        service.atualizarSessoesKeycloak(); // sucesso: publica 5
        service.atualizarSessoesKeycloak(); // falha: NÃO derruba, mantém 5

        assertThat(gaugeValue("jetski.keycloak.sessoes.ativas", "client_id", "jetski-backoffice"))
            .isEqualTo(5.0);
    }

    @Test
    @DisplayName("Deve ignorar entrada sem clientId e tratar active não-numérico como 0")
    void testSessoesKeycloakEntradasMalformadas() {
        when(keycloakAdminService.getClientSessionStats()).thenReturn(List.of(
            Map.of("active", "9"),                                       // sem clientId → ignorada
            Map.of("clientId", "app-mobile", "active", "abc")            // active inválido → 0
        ));

        service.atualizarSessoesKeycloak();

        assertThat(meterRegistry.find("jetski.keycloak.sessoes.ativas").gauges()).hasSize(1);
        assertThat(gaugeValue("jetski.keycloak.sessoes.ativas", "client_id", "app-mobile"))
            .isEqualTo(0.0);
    }

    @Test
    @DisplayName("Deve contar staff ativo (global) e somar clientes tenant a tenant numa única conexão")
    void testAtualizarCadastrados() throws SQLException {
        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM usuario WHERE ativo = true"), eq(Integer.class)))
            .thenReturn(16);
        Connection con = mockConnection(List.of(TENANT_A, TENANT_B), List.of(4, 9));
        stubExecuteComConexao(con);

        service.atualizarCadastrados();

        assertThat(gaugeValue("jetski.usuarios.cadastrados")).isEqualTo(16.0);
        assertThat(gaugeValue("jetski.clientes.cadastrados")).isEqualTo(13.0);
        // neutralização do contexto session-scoped herdado do pool (defesa em profundidade)
        verify(psNeutralizar).executeQuery();
        // set_config transaction-local bindado por tenant, na MESMA conexão
        verify(psSetConfig).setString(1, TENANT_A.toString());
        verify(psSetConfig).setString(1, TENANT_B.toString());
        // transação manual: rollback (só leitura, desfaz o set_config) + autocommit restaurado
        verify(con).setAutoCommit(false);
        verify(con).rollback();
        verify(con).setAutoCommit(true);
    }

    @Test
    @DisplayName("Falha no banco: não propaga exceção e mantém o último valor dos gauges")
    void testCadastradosMantemUltimoValorEmFalha() throws SQLException {
        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM usuario WHERE ativo = true"), eq(Integer.class)))
            .thenReturn(16);
        Connection con = mockConnection(List.of(TENANT_A), List.of(4));
        stubExecuteComConexao(con);

        service.atualizarCadastrados(); // publica 16 / 4

        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM usuario WHERE ativo = true"), eq(Integer.class)))
            .thenThrow(new DataAccessException("Database connection failed") {});
        when(jdbcTemplate.execute(any(ConnectionCallback.class)))
            .thenThrow(new DataAccessException("Database connection failed") {});

        service.atualizarCadastrados(); // falha: NÃO derruba, mantém últimos valores

        assertThat(gaugeValue("jetski.usuarios.cadastrados")).isEqualTo(16.0);
        assertThat(gaugeValue("jetski.clientes.cadastrados")).isEqualTo(4.0);
    }

    @Test
    @DisplayName("Falha só no bloco de clientes não impede a atualização de usuários")
    void testFalhaEmClientesNaoImpedeUsuarios() {
        when(jdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM usuario WHERE ativo = true"), eq(Integer.class)))
            .thenReturn(21);
        when(jdbcTemplate.execute(any(ConnectionCallback.class)))
            .thenThrow(new DataAccessException("relation tenant does not exist") {});

        service.atualizarCadastrados();

        assertThat(gaugeValue("jetski.usuarios.cadastrados")).isEqualTo(21.0);
        assertThat(gaugeValue("jetski.clientes.cadastrados")).isEqualTo(0.0);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Delega o ConnectionCallback do service para a conexão mockada. */
    @SuppressWarnings("unchecked")
    private void stubExecuteComConexao(Connection con) {
        when(jdbcTemplate.execute(any(ConnectionCallback.class)))
            .thenAnswer(inv -> ((ConnectionCallback<Integer>) inv.getArgument(0)).doInConnection(con));
    }

    /**
     * Conexão mockada para {@code contarClientesPlataforma()}: lista de
     * tenants materializável e um COUNT de cliente por tenant (na ordem).
     */
    private Connection mockConnection(List<UUID> tenants, List<Integer> counts) throws SQLException {
        Connection con = mock(Connection.class);
        when(con.getAutoCommit()).thenReturn(true);

        // set_config('app.tenant_id', '', true) — neutraliza contexto herdado do pool
        psNeutralizar = mock(PreparedStatement.class);
        ResultSet rsNeutralizar = mock(ResultSet.class);
        when(con.prepareStatement("SELECT set_config('app.tenant_id', '', true)"))
            .thenReturn(psNeutralizar);
        when(psNeutralizar.executeQuery()).thenReturn(rsNeutralizar);
        when(rsNeutralizar.next()).thenReturn(true);

        // SELECT id FROM tenant — um next()=true por tenant, depois false
        PreparedStatement psTenants = mock(PreparedStatement.class);
        ResultSet rsTenants = mock(ResultSet.class);
        when(con.prepareStatement("SELECT id FROM tenant")).thenReturn(psTenants);
        when(psTenants.executeQuery()).thenReturn(rsTenants);
        OngoingStubbing<Boolean> next = when(rsTenants.next());
        for (int i = 0; i < tenants.size(); i++) {
            next = next.thenReturn(true);
        }
        next.thenReturn(false);
        OngoingStubbing<String> getString = when(rsTenants.getString(1));
        for (UUID tenant : tenants) {
            getString = getString.thenReturn(tenant.toString());
        }

        // set_config('app.tenant_id', ?, true)
        psSetConfig = mock(PreparedStatement.class);
        ResultSet rsSetConfig = mock(ResultSet.class);
        when(con.prepareStatement("SELECT set_config('app.tenant_id', ?, true)"))
            .thenReturn(psSetConfig);
        when(psSetConfig.executeQuery()).thenReturn(rsSetConfig);
        when(rsSetConfig.next()).thenReturn(true);

        // SELECT count(*) FROM cliente — um valor por tenant (na ordem)
        PreparedStatement psCount = mock(PreparedStatement.class);
        ResultSet rsCount = mock(ResultSet.class);
        when(con.prepareStatement("SELECT count(*) FROM cliente")).thenReturn(psCount);
        when(psCount.executeQuery()).thenReturn(rsCount);
        when(rsCount.next()).thenReturn(true);
        OngoingStubbing<Integer> getInt = when(rsCount.getInt(1));
        for (Integer count : counts) {
            getInt = getInt.thenReturn(count);
        }

        return con;
    }

    private double gaugeValue(String name) {
        Gauge gauge = meterRegistry.find(name).gauge();
        assertThat(gauge).isNotNull();
        return gauge.value();
    }

    private double gaugeValue(String name, String tagKey, String tagValue) {
        Gauge gauge = meterRegistry.find(name).tag(tagKey, tagValue).gauge();
        assertThat(gauge).isNotNull();
        return gauge.value();
    }
}
