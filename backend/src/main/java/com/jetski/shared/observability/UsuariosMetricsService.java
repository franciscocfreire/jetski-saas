package com.jetski.shared.observability;

import com.jetski.shared.internal.keycloak.KeycloakAdminService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Métricas de monitoração de usuários para o dashboard Grafana:
 *
 * <ul>
 *   <li>{@code jetski.keycloak.sessoes.ativas} (tag {@code client_id}) —
 *       sessões logadas AGORA por client, via Admin API do Keycloak
 *       ({@code client-session-stats}), a cada ~60s;</li>
 *   <li>{@code jetski.usuarios.cadastrados} — staff ativo (tabela global
 *       {@code usuario}, sem RLS), a cada ~5min;</li>
 *   <li>{@code jetski.clientes.cadastrados} — total de clientes da
 *       plataforma, a cada ~5min.</li>
 * </ul>
 *
 * <p><b>RLS de {@code cliente}</b>: a tabela tem RLS por tenant (FORCE) e o
 * scheduler roda sem tenant context — sem contexto a contagem direta seria 0
 * (app roda como {@code jetski_app}, sem BYPASSRLS). A tabela {@code tenant}
 * libera leitura com contexto NULO (V042, ramo de jobs globais), então
 * itera-se tenant a tenant com {@code set_config('app.tenant_id', <id>, true)}
 * — escopado à TRANSAÇÃO, o valor reverte no rollback (nunca vaza para a
 * sessão do pool).
 *
 * <p><b>Por que {@link ConnectionCallback} e não TransactionTemplate</b>: o
 * {@code set_config(..., is_local=true)} só tem efeito se o {@code COUNT}
 * rodar na MESMA conexão física e na MESMA transação. Com TransactionTemplate
 * + JdbcTemplate esse vínculo não se sustentou aqui (mismatch de identidade
 * entre o {@code TenantAwareDataSource} e o DataSource registrado no
 * transaction manager): o {@code set_config} rodava em autocommit numa
 * conexão, evaporava no fim do statement, e o {@code COUNT} rodava noutra
 * conexão sem contexto — a policy {@code tenant_id = get_current_tenant_id()}
 * com NULL filtrava tudo e o gauge publicava 0. O {@link ConnectionCallback}
 * pina UMA conexão física para toda a varredura, com transação aberta
 * manualmente ({@code autoCommit=false}) e {@code rollback} ao final
 * (é só leitura; desfaz o set_config transaction-local).
 *
 * <p><b>Resiliência</b>: Keycloak/banco fora do ar NUNCA derruba o scheduler —
 * exceção vira log warn e o gauge mantém o último valor publicado.
 *
 * <p>Nomes de métrica sem sufixo reservado do Prometheus ({@code .total},
 * {@code .count}…) — sufixo reservado já quebrou o scrape neste projeto.
 */
@Slf4j
@Service
public class UsuariosMetricsService {

    private final KeycloakAdminService keycloakAdminService;
    private final JdbcTemplate jdbcTemplate;

    /** Registrado UMA vez; linhas re-registradas a cada tick (overwrite=true). */
    private final MultiGauge sessoesAtivas;

    private final AtomicInteger usuariosCadastrados = new AtomicInteger(0);
    private final AtomicInteger clientesCadastrados = new AtomicInteger(0);

    public UsuariosMetricsService(MeterRegistry meterRegistry,
                                  KeycloakAdminService keycloakAdminService,
                                  JdbcTemplate jdbcTemplate) {
        this.keycloakAdminService = keycloakAdminService;
        this.jdbcTemplate = jdbcTemplate;

        this.sessoesAtivas = MultiGauge.builder("jetski.keycloak.sessoes.ativas")
                .description("Sessões ativas no Keycloak por client (client-session-stats)")
                .register(meterRegistry);

        Gauge.builder("jetski.usuarios.cadastrados", usuariosCadastrados, AtomicInteger::get)
                .description("Usuários de staff cadastrados e ativos (tabela global usuario)")
                .register(meterRegistry);

        Gauge.builder("jetski.clientes.cadastrados", clientesCadastrados, AtomicInteger::get)
                .description("Clientes cadastrados no total da plataforma (todas as lojas)")
                .register(meterRegistry);
    }

    /**
     * Poll das sessões ativas do Keycloak (~60s). Em falha, mantém o último
     * conjunto de linhas publicado (não re-registra o MultiGauge).
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void atualizarSessoesKeycloak() {
        try {
            List<Map<String, String>> stats = keycloakAdminService.getClientSessionStats();
            List<MultiGauge.Row<?>> rows = new ArrayList<>(stats.size());
            for (Map<String, String> stat : stats) {
                String clientId = stat.get("clientId");
                if (clientId == null) {
                    continue;
                }
                rows.add(MultiGauge.Row.of(
                        Tags.of("client_id", clientId), parseLongSafe(stat.get("active"))));
            }
            sessoesAtivas.register(rows, true);
            log.debug("Sessões Keycloak atualizadas: {} client(s)", rows.size());
        } catch (Exception e) {
            // Keycloak fora do ar não derruba o scheduler; gauge mantém o último valor
            log.warn("Falha ao consultar client-session-stats do Keycloak (gauge mantém último valor): {}",
                    e.getMessage());
        }
    }

    /**
     * Cadastrados (~5min): staff ativo (global) e clientes por tenant (RLS).
     * Try/catch independentes: falha em um não impede o outro; em falha o
     * gauge mantém o último valor.
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 20_000)
    public void atualizarCadastrados() {
        try {
            Integer usuarios = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM usuario WHERE ativo = true", Integer.class);
            usuariosCadastrados.set(usuarios != null ? usuarios : 0);
        } catch (Exception e) {
            log.warn("Falha ao contar usuários cadastrados (gauge mantém último valor): {}",
                    e.getMessage());
        }

        try {
            clientesCadastrados.set(contarClientesPlataforma());
        } catch (Exception e) {
            log.warn("Falha ao contar clientes cadastrados (gauge mantém último valor): {}",
                    e.getMessage());
        }
    }

    /**
     * Soma de clientes de TODAS as lojas, tenant a tenant sob RLS, numa ÚNICA
     * conexão física ({@link ConnectionCallback}) com transação manual — ver
     * javadoc da classe para o porquê (TransactionTemplate não vinculava o
     * set_config e o COUNT à mesma conexão).
     *
     * <p><b>Neutralização de contexto herdado</b> (defesa em profundidade): a
     * conexão vem do pool e pode carregar um {@code app.tenant_id}
     * SESSION-scoped emitido pelo {@code TenantAwareDataSource} no checkout,
     * quando algum ThreadLocal de tenant vazou numa thread de scheduler (já
     * aconteceu — job de customer setando TenantContext sem limpar). Com
     * contexto herdado, {@code SELECT id FROM tenant} devolveria só aquele
     * tenant e a soma sairia errada SEM erro. Por isso a transação abre com
     * {@code set_config('app.tenant_id', '', true)} — transaction-local;
     * {@code get_current_tenant_id()} faz {@code NULLIF(..., '')} e devolve
     * NULL, então {@code ''} é seguro AQUI. NÃO usar {@code RESET}: é
     * session-scoped e desfaria o comportamento esperado do wrapper na
     * conexão do pool.
     *
     * <p>A lista de tenants é MATERIALIZADA antes do primeiro
     * {@code set_config} por tenant: com contexto nulo a policy de
     * {@code tenant} libera a leitura, e um cursor aberto sobre {@code tenant}
     * reavaliaria a policy a cada fetch depois do primeiro set_config
     * (pararia de iterar).
     *
     * <p>{@code rollback} ao final: é só leitura e desfaz os
     * {@code set_config(..., is_local=true)} antes de a conexão voltar ao pool.
     */
    private int contarClientesPlataforma() {
        Integer total = jdbcTemplate.execute((ConnectionCallback<Integer>) con -> {
            boolean autoCommitOriginal = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                // Neutraliza app.tenant_id session-scoped herdado do pool
                // (transaction-local; '' → get_current_tenant_id() = NULL)
                try (PreparedStatement psLimpar = con.prepareStatement(
                             "SELECT set_config('app.tenant_id', '', true)");
                     ResultSet rs = psLimpar.executeQuery()) {
                    rs.next();
                }

                // Materializa ANTES de qualquer set_config por tenant (contexto nulo → policy libera)
                List<String> tenantIds = new ArrayList<>();
                try (PreparedStatement psTenants = con.prepareStatement("SELECT id FROM tenant");
                     ResultSet rs = psTenants.executeQuery()) {
                    while (rs.next()) {
                        tenantIds.add(rs.getString(1));
                    }
                }

                int soma = 0;
                try (PreparedStatement psSetConfig = con.prepareStatement(
                             "SELECT set_config('app.tenant_id', ?, true)");
                     PreparedStatement psCount = con.prepareStatement(
                             "SELECT count(*) FROM cliente")) {
                    for (String tenantId : tenantIds) {
                        psSetConfig.setString(1, tenantId);
                        try (ResultSet rs = psSetConfig.executeQuery()) {
                            rs.next();
                        }
                        try (ResultSet rs = psCount.executeQuery()) {
                            if (rs.next()) {
                                soma += rs.getInt(1);
                            }
                        }
                    }
                }
                return soma;
            } finally {
                con.rollback();
                con.setAutoCommit(autoCommitOriginal);
            }
        });
        return total != null ? total : 0;
    }

    private long parseLongSafe(String value) {
        try {
            return value != null ? Long.parseLong(value) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
