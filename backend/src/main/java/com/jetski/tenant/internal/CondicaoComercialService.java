package com.jetski.tenant.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import com.jetski.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Condição comercial da mensalidade por empresa (V076): piloto, cortesia, parceria ou
 * preço negociado, com vigência e motivo.
 *
 * <p>A assinatura continua dizendo QUAL plano a empresa usa; a condição diz QUANTO ela
 * paga. Presa à empresa (não à assinatura) para que trocar de plano durante o piloto não
 * derrube a isenção. Não mexe em créditos de emissão.
 *
 * <p>Uma condição por vez: conceder com período sobreposto a outra não encerrada é
 * recusado — o operador encerra a atual antes, de forma explícita e auditada. Início
 * retroativo também é recusado: fatura já emitida se trata em Faturamento (cancelar),
 * não reescrevendo o passado por aqui.
 *
 * <p>Cross-tenant sem bypass: fixa {@code app.tenant_id} na transação, como os demais
 * serviços de plataforma.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CondicaoComercialService {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    public enum Tipo { PILOTO, CORTESIA, PARCERIA, NEGOCIADO }

    public enum Forma { ISENCAO, PERCENTUAL, VALOR_FIXO }

    /**
     * @param valor    % no PERCENTUAL, R$/mês no VALOR_FIXO, null na ISENCAO
     * @param situacao VIGENTE, AGENDADA, EXPIRADA ou ENCERRADA (calculada hoje)
     */
    public record Condicao(
        UUID id, Tipo tipo, Forma forma, BigDecimal valor, LocalDate inicio, LocalDate fim,
        String motivo, UUID concedidaPor, Instant createdAt,
        Instant encerradaEm, UUID encerradaPor, String motivoEncerramento, String situacao) {}

    /** @param inicio nulo = hoje; @param fim nulo = sem prazo (proibido no PILOTO) */
    public record NovaCondicao(
        Tipo tipo, Forma forma, BigDecimal valor, LocalDate inicio, LocalDate fim, String motivo) {}

    /**
     * Vigente num dia D. Encerrar hoje tira a vigência de hoje: a próxima fatura gerada
     * já sai cheia. Mesma regra no read model ({@code PlataformaMetricasService}).
     */
    private static final String VIGENTE_EM = """
        inicio <= CAST(? AS date)
        AND (fim IS NULL OR fim >= CAST(? AS date))
        AND (encerrada_em IS NULL
             OR (encerrada_em AT TIME ZONE 'America/Sao_Paulo')::date > CAST(? AS date))
        """;

    private static final String COLUNAS = """
        id, tipo, forma, valor, inicio, fim, motivo, concedida_por, created_at,
        encerrada_em, encerrada_por, motivo_encerramento
        """;

    private final JdbcTemplate jdbc;
    private final TenantRepository tenantRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Condição vigente no dia, se houver. */
    @Transactional
    public Optional<Condicao> vigente(UUID tenantId, LocalDate dia) {
        setTenant(tenantId);
        return jdbc.query("SELECT " + COLUNAS + " FROM condicao_comercial WHERE tenant_id = ? AND "
                + VIGENTE_EM + " ORDER BY inicio DESC, created_at DESC LIMIT 1",
                mapper(), tenantId, dia.toString(), dia.toString(), dia.toString())
            .stream().findFirst();
    }

    /** Vigente hoje. */
    @Transactional
    public Optional<Condicao> vigenteHoje(UUID tenantId) {
        return vigente(tenantId, hoje());
    }

    /** Histórico completo, mais recente primeiro. */
    @Transactional
    public List<Condicao> historico(UUID tenantId) {
        exigirEmpresa(tenantId);
        setTenant(tenantId);
        return jdbc.query("SELECT " + COLUNAS + " FROM condicao_comercial WHERE tenant_id = ? "
            + "ORDER BY inicio DESC, created_at DESC", mapper(), tenantId);
    }

    @Transactional
    public Condicao conceder(UUID tenantId, NovaCondicao nova) {
        var tenant = exigirEmpresa(tenantId);
        if (nova == null || nova.tipo() == null || nova.forma() == null) {
            throw new BusinessException("Informe o tipo e a forma da condição comercial.");
        }
        String motivo = nova.motivo() == null ? "" : nova.motivo().trim();
        if (motivo.isEmpty()) {
            throw new BusinessException("Informe o motivo (fica registrado na auditoria).");
        }
        if (motivo.length() > 300) {
            throw new BusinessException("Motivo muito longo (máximo de 300 caracteres).");
        }
        LocalDate hoje = hoje();
        LocalDate inicio = nova.inicio() != null ? nova.inicio() : hoje;
        if (inicio.isBefore(hoje)) {
            throw new BusinessException("O início não pode ser retroativo. Fatura já emitida se "
                + "cancela em Faturamento.");
        }
        LocalDate fim = nova.fim();
        if (fim != null && fim.isBefore(inicio)) {
            throw new BusinessException("O término não pode ser antes do início.");
        }
        if (nova.tipo() == Tipo.PILOTO && fim == null) {
            throw new BusinessException("Piloto precisa de data de término.");
        }
        BigDecimal valor = validarValor(nova.forma(), nova.valor());

        setTenant(tenantId);
        Integer sobrepostas = jdbc.queryForObject("""
            SELECT count(*) FROM condicao_comercial
             WHERE tenant_id = ? AND encerrada_em IS NULL
               AND (fim IS NULL OR fim >= CAST(? AS date))
               AND (CAST(? AS date) IS NULL OR inicio <= CAST(? AS date))
            """, Integer.class, tenantId, inicio.toString(),
            fim == null ? null : fim.toString(), fim == null ? null : fim.toString());
        if (sobrepostas != null && sobrepostas > 0) {
            throw new BusinessException("Já existe uma condição comercial nesse período. "
                + "Encerre a atual antes de conceder outra.");
        }

        UUID actor = TenantContext.getUsuarioId();
        UUID id = jdbc.queryForObject("""
            INSERT INTO condicao_comercial
                (tenant_id, tipo, forma, valor, inicio, fim, motivo, concedida_por)
            VALUES (?, ?, ?, ?, CAST(? AS date), CAST(? AS date), ?, ?)
            RETURNING id
            """, UUID.class, tenantId, nova.tipo().name(), nova.forma().name(), valor,
            inicio.toString(), fim == null ? null : fim.toString(), motivo, actor);

        Condicao criada = carregar(tenantId, id);
        String status = tenant.getStatus().name();
        eventPublisher.publishEvent(TenantStatusChangedEvent.of(
            tenantId, "TENANT_CONDICAO_COMERCIAL_CONCEDIDA", status, status, actor,
            descrever(criada) + " — " + motivo, tenant.getRazaoSocial(), tenant.getSlug()));
        log.warn("[PLATFORM] Condição comercial concedida: tenant={}, {}", tenantId, descrever(criada));
        return criada;
    }

    /** Encerra hoje (vigente ou agendada). A linha fica como histórico. */
    @Transactional
    public Condicao encerrar(UUID tenantId, UUID condicaoId, String motivo) {
        var tenant = exigirEmpresa(tenantId);
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException("Informe o motivo do encerramento (fica registrado na auditoria).");
        }
        String motivoLimpo = motivo.trim();
        if (motivoLimpo.length() > 300) {
            throw new BusinessException("Motivo muito longo (máximo de 300 caracteres).");
        }
        setTenant(tenantId);
        Condicao atual = carregar(tenantId, condicaoId);
        if (atual.encerradaEm() != null) {
            throw new BusinessException("Esta condição já foi encerrada.");
        }
        if ("EXPIRADA".equals(atual.situacao())) {
            throw new BusinessException("Esta condição já terminou em " + BR.format(atual.fim()) + ".");
        }
        UUID actor = TenantContext.getUsuarioId();
        jdbc.update("UPDATE condicao_comercial SET encerrada_em = now(), encerrada_por = ?, "
            + "motivo_encerramento = ? WHERE id = ? AND tenant_id = ?",
            actor, motivoLimpo, condicaoId, tenantId);

        Condicao encerrada = carregar(tenantId, condicaoId);
        String status = tenant.getStatus().name();
        eventPublisher.publishEvent(TenantStatusChangedEvent.of(
            tenantId, "TENANT_CONDICAO_COMERCIAL_ENCERRADA", status, status, actor,
            descrever(atual) + " — " + motivoLimpo, tenant.getRazaoSocial(), tenant.getSlug()));
        log.warn("[PLATFORM] Condição comercial encerrada: tenant={}, {}", tenantId, descrever(atual));
        return encerrada;
    }

    /**
     * Mensalidade efetiva com a condição. Nunca encarece: valor fixo acima do plano vale o
     * plano. Mesma regra no read model e no console ({@code lib/condicao.ts}).
     */
    public static BigDecimal valorEfetivo(BigDecimal precoPlano, Condicao condicao) {
        BigDecimal preco = precoPlano == null ? BigDecimal.ZERO : precoPlano;
        if (condicao == null) {
            return preco;
        }
        return switch (condicao.forma()) {
            case ISENCAO -> BigDecimal.ZERO;
            case PERCENTUAL -> preco.multiply(CEM.subtract(condicao.valor()))
                .divide(CEM, 2, RoundingMode.HALF_UP);
            case VALOR_FIXO -> condicao.valor().min(preco);
        };
    }

    /** "Piloto isenta, 15/09/2026 a 31/12/2026". */
    public static String descrever(Condicao c) {
        String forma = switch (c.forma()) {
            case ISENCAO -> "isenta";
            case PERCENTUAL -> "desconto de " + c.valor().stripTrailingZeros().toPlainString() + "%";
            case VALOR_FIXO -> "R$ " + c.valor().setScale(2, RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',') + "/mês";
        };
        String rotulo = c.tipo().name().charAt(0) + c.tipo().name().substring(1).toLowerCase();
        String periodo = c.fim() == null
            ? "desde " + BR.format(c.inicio())
            : BR.format(c.inicio()) + " a " + BR.format(c.fim());
        return rotulo + " " + forma + ", " + periodo;
    }

    // ------------------------------------------------------------------

    private static BigDecimal validarValor(Forma forma, BigDecimal valor) {
        return switch (forma) {
            case ISENCAO -> null;
            case PERCENTUAL -> {
                if (valor == null || valor.signum() <= 0 || valor.compareTo(CEM) > 0) {
                    throw new BusinessException("Desconto deve ser maior que 0% e até 100%.");
                }
                yield valor.setScale(2, RoundingMode.HALF_UP);
            }
            case VALOR_FIXO -> {
                if (valor == null || valor.signum() < 0) {
                    throw new BusinessException("Informe o valor mensal (R$ 0 ou mais).");
                }
                yield valor.setScale(2, RoundingMode.HALF_UP);
            }
        };
    }

    private Condicao carregar(UUID tenantId, UUID id) {
        return jdbc.query("SELECT " + COLUNAS + " FROM condicao_comercial WHERE id = ? AND tenant_id = ?",
                mapper(), id, tenantId)
            .stream().findFirst()
            .orElseThrow(() -> new NotFoundException("Condição comercial não encontrada: " + id));
    }

    private com.jetski.tenant.domain.Tenant exigirEmpresa(UUID tenantId) {
        return tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Empresa não encontrada: " + tenantId));
    }

    private void setTenant(UUID tenantId) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class,
            tenantId.toString());
    }

    private static LocalDate hoje() {
        return LocalDate.now(ZONA);
    }

    private static RowMapper<Condicao> mapper() {
        LocalDate hoje = hoje();
        return (rs, i) -> {
            LocalDate inicio = rs.getObject("inicio", LocalDate.class);
            LocalDate fim = rs.getObject("fim", LocalDate.class);
            Timestamp encerrada = rs.getTimestamp("encerrada_em");
            String situacao = encerrada != null ? "ENCERRADA"
                : inicio.isAfter(hoje) ? "AGENDADA"
                : fim != null && fim.isBefore(hoje) ? "EXPIRADA"
                : "VIGENTE";
            Timestamp criada = rs.getTimestamp("created_at");
            return new Condicao(
                rs.getObject("id", UUID.class),
                Tipo.valueOf(rs.getString("tipo")),
                Forma.valueOf(rs.getString("forma")),
                rs.getBigDecimal("valor"),
                inicio, fim,
                rs.getString("motivo"),
                rs.getObject("concedida_por", UUID.class),
                criada != null ? criada.toInstant() : null,
                encerrada != null ? encerrada.toInstant() : null,
                rs.getObject("encerrada_por", UUID.class),
                rs.getString("motivo_encerramento"),
                situacao);
        };
    }
}
