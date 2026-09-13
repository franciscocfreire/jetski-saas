package com.jetski.shared.config;

import com.jetski.shared.config.TenantAwareDataSourceConfig.TenantAwareDataSource;
import com.jetski.shared.security.TenantContext;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * P0 da revisão técnica (ago/2026): o {@link TenantAwareDataSource} precisa falhar
 * FECHADO. Se o {@code set_config}/{@code RESET} do GUC de RLS falhar, a conexão
 * pode carregar o {@code app.tenant_id} da requisição anterior (GUC de sessão +
 * reuso do Hikari) — ela nunca pode ser entregue ao chamador.
 */
@DisplayName("TenantAwareDataSource: contexto RLS falha fechado")
class TenantAwareDataSourceTest {

    private static final UUID TENANT = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("sucesso: fixa app.tenant_id e app.unrestricted e entrega a conexão")
    void entregaConexaoComContexto() throws SQLException {
        Connection conn = mock(Connection.class);
        Statement st = mock(Statement.class);
        when(conn.createStatement()).thenReturn(st);
        DataSource alvo = mock(DataSource.class);
        when(alvo.getConnection()).thenReturn(conn);
        TenantContext.setTenantId(TENANT);

        Connection entregue = new TenantAwareDataSource(alvo).getConnection();

        assertThat(entregue).isSameAs(conn);
        verify(st).execute("SELECT set_config('app.tenant_id', '" + TENANT + "', false)");
        verify(st).execute("SELECT set_config('app.unrestricted', '', false)");
        verify(conn, never()).close();
    }

    @Test
    @DisplayName("falha ao fixar o tenant: lança, fecha a conexão e não a entrega")
    void falhaAoFixarTenantLancaEFecha() throws SQLException {
        Connection conn = mock(Connection.class);
        Statement st = mock(Statement.class);
        when(conn.createStatement()).thenReturn(st);
        when(st.execute(anyString())).thenThrow(new SQLException("conexão abortada", "25P02"));
        DataSource alvo = mock(DataSource.class);
        when(alvo.getConnection()).thenReturn(conn);
        TenantContext.setTenantId(TENANT);

        assertThatThrownBy(() -> new TenantAwareDataSource(alvo).getConnection())
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("contexto RLS")
            .hasCauseInstanceOf(SQLException.class);

        verify(conn).close();
    }

    @Test
    @DisplayName("falha no RESET (sem tenant): também falha fechado — é o caso do GUC obsoleto")
    void falhaNoResetSemTenantLancaEFecha() throws SQLException {
        Connection conn = mock(Connection.class);
        Statement st = mock(Statement.class);
        when(conn.createStatement()).thenReturn(st);
        when(st.execute("RESET app.tenant_id")).thenThrow(new SQLException("I/O error"));
        DataSource alvo = mock(DataSource.class);
        when(alvo.getConnection(anyString(), anyString())).thenReturn(conn);

        assertThatThrownBy(() -> new TenantAwareDataSource(alvo).getConnection("u", "p"))
            .isInstanceOf(SQLException.class);

        verify(conn).close();
    }

    @Test
    @DisplayName("falha no app.unrestricted: falha fechado mesmo com o tenant já fixado")
    void falhaNoUnrestrictedLancaEFecha() throws SQLException {
        Connection conn = mock(Connection.class);
        Statement st = mock(Statement.class);
        when(conn.createStatement()).thenReturn(st);
        when(st.execute(startsWith("SELECT set_config('app.unrestricted'")))
            .thenThrow(new SQLException("timeout"));
        DataSource alvo = mock(DataSource.class);
        when(alvo.getConnection()).thenReturn(conn);
        TenantContext.setTenantId(TENANT);

        assertThatThrownBy(() -> new TenantAwareDataSource(alvo).getConnection())
            .isInstanceOf(SQLException.class);

        verify(conn).close();
    }

    @Test
    @DisplayName("Hikari: a conexão é DESPEJADA do pool (close() sozinho a devolveria para reuso)")
    void hikariDespejaConexao() throws SQLException {
        Connection conn = mock(Connection.class);
        when(conn.createStatement()).thenThrow(new SQLException("conexão quebrada", "08006"));
        HikariDataSource hikari = mock(HikariDataSource.class);
        when(hikari.getConnection()).thenReturn(conn);
        TenantContext.setTenantId(TENANT);

        assertThatThrownBy(() -> new TenantAwareDataSource(hikari).getConnection())
            .isInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("08006"));

        verify(hikari).evictConnection(conn);
        verify(conn).close();
    }
}
