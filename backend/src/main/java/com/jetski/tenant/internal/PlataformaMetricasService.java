package com.jetski.tenant.internal;

import com.jetski.shared.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read model da plataforma (F4): agregado diário por empresa.
 *
 * <p><strong>Por que existe:</strong> não havia visão consolidada. O padrão do resto da
 * plataforma é iterar todos os tenants re-setando {@code app.tenant_id} a cada volta
 * (ver {@code PlatformFaturaService.pendentesConferencia}) — aceitável para uma fila,
 * inviável para um dashboard com vários indicadores, porque o custo cresce linearmente
 * com o número de empresas A CADA carregamento de tela.
 *
 * <p>Aqui a varredura acontece UMA vez por dia, fora do request, e o console lê uma
 * tabela de plataforma com um único SELECT. Sem {@code BYPASSRLS} e sem abrir furo em
 * tabela operacional: o cálculo continua entrando empresa a empresa, com a RLS ativa.
 *
 * <p><strong>Janela móvel</strong> em vez de "só ontem": locação editada antes do
 * fechamento, estorno e pagamento tardio mudam o passado recente. Recalcular os últimos
 * dias custa pouco e evita número errado congelado.
 *
 * @since 0.9.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlataformaMetricasService {

    /** Dias recalculados a cada execução — cobre ajuste retroativo sem varrer a história. */
    static final int JANELA_DIAS = 7;

    private final JdbcTemplate jdbc;

    /** @param empresas empresas processadas; @param dias dias recalculados por empresa */
    public record Resultado(int empresas, int dias, int linhas) {}

    /**
     * Recalcula o intervalo [de, ate] para TODAS as empresas.
     *
     * <p>O {@code TenantContext} é limpo no {@code finally} de cada empresa: este código
     * roda na thread do scheduler, e um ThreadLocal vazado ali contamina o
     * {@code TenantAwareDataSource} de todos os jobs seguintes — já aconteceu neste
     * projeto e é caro de diagnosticar (job A escrevendo com o tenant do job B).
     */
    @Transactional
    public Resultado recalcular(LocalDate de, LocalDate ate) {
        UUID tenantAnterior = TenantContext.getTenantId();
        List<Map<String, Object>> empresas = jdbc.queryForList(
            "SELECT id, slug FROM tenant WHERE excluido_em IS NULL ORDER BY slug");
        int linhas = 0;
        try {
            for (Map<String, Object> empresa : empresas) {
                UUID tenantId = (UUID) empresa.get("id");
                for (LocalDate dia = de; !dia.isAfter(ate); dia = dia.plusDays(1)) {
                    linhas += calcularDia(tenantId, dia);
                }
            }
        } finally {
            TenantContext.clear();
            if (tenantAnterior != null) {
                TenantContext.setTenantId(tenantAnterior);
            }
        }
        long dias = de.datesUntil(ate.plusDays(1)).count();
        log.info("[METRICAS] Read model atualizado: {} empresa(s) × {} dia(s) = {} linha(s)",
            empresas.size(), dias, linhas);
        return new Resultado(empresas.size(), (int) dias, linhas);
    }

    /** Janela padrão do job: os últimos {@link #JANELA_DIAS} dias, incluindo hoje. */
    @Transactional
    public Resultado recalcularJanela() {
        LocalDate hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        return recalcular(hoje.minusDays(JANELA_DIAS - 1L), hoje);
    }

    /**
     * Um dia de uma empresa. Escopa a RLS por transação ({@code set_config local=true}) —
     * mesmo padrão dos demais serviços de plataforma, sem bypass.
     */
    private int calcularDia(UUID tenantId, LocalDate dia) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
            String.class, tenantId.toString());

        jdbc.update("""
            INSERT INTO plataforma_metrica_diaria (
                tenant_id, dia, locacoes, reservas, no_shows,
                receita_bruta, receita_comissionavel,
                emissoes_documento, emissoes_gru, emissoes_previa,
                creditos_consumidos, saldo_creditos_fim,
                mrr, plano_nome, faturas_abertas, valor_em_aberto, atualizado_em)
            SELECT
                ?::uuid, ?::date,
                COALESCE(op.locacoes, 0), COALESCE(rv.reservas, 0), COALESCE(rv.no_shows, 0),
                COALESCE(op.receita_bruta, 0), COALESCE(op.receita_comissionavel, 0),
                COALESCE(em.documento, 0), COALESCE(em.gru, 0), COALESCE(em.previa, 0),
                COALESCE(cr.consumidos, 0), COALESCE(cr.saldo_fim, 0),
                COALESCE(pl.preco_mensal, 0), pl.nome,
                COALESCE(fa.abertas, 0), COALESCE(fa.valor, 0), now()
            FROM (SELECT 1) AS _
            LEFT JOIN LATERAL (
                SELECT count(*) AS locacoes,
                       SUM(COALESCE(valor_total, 0)) AS receita_bruta,
                       -- RN04: base comissionável exclui combustível
                       SUM(COALESCE(valor_total, 0) - COALESCE(combustivel_custo, 0))
                           AS receita_comissionavel
                  FROM locacao
                 WHERE tenant_id = ?::uuid
                   AND (data_check_in AT TIME ZONE 'America/Sao_Paulo')::date = ?::date
            ) op ON TRUE
            LEFT JOIN LATERAL (
                SELECT count(*) FILTER (WHERE TRUE) AS reservas,
                       count(*) FILTER (WHERE status = 'NO_SHOW') AS no_shows
                  FROM reserva
                 WHERE tenant_id = ?::uuid
                   AND (created_at AT TIME ZONE 'America/Sao_Paulo')::date = ?::date
            ) rv ON TRUE
            LEFT JOIN LATERAL (
                SELECT count(*) FILTER (WHERE tipo = 'DOCUMENTO') AS documento,
                       count(*) FILTER (WHERE tipo = 'GRU') AS gru,
                       count(*) FILTER (WHERE tipo = 'PREVIA') AS previa
                  FROM emissao_uso
                 WHERE tenant_id = ?::uuid
                   AND (ocorrido_em AT TIME ZONE 'America/Sao_Paulo')::date = ?::date
            ) em ON TRUE
            LEFT JOIN LATERAL (
                SELECT COALESCE(SUM(-quantidade) FILTER (WHERE tipo = 'CONSUMO'), 0) AS consumidos,
                       (SELECT saldo_apos FROM credito_lancamento
                         WHERE tenant_id = ?::uuid
                           AND (created_at AT TIME ZONE 'America/Sao_Paulo')::date <= ?::date
                         ORDER BY created_at DESC, id DESC LIMIT 1) AS saldo_fim
                  FROM credito_lancamento
                 WHERE tenant_id = ?::uuid
                   AND (created_at AT TIME ZONE 'America/Sao_Paulo')::date = ?::date
            ) cr ON TRUE
            LEFT JOIN LATERAL (
                SELECT p.nome, p.preco_mensal
                  FROM assinatura a JOIN plano p ON p.id = a.plano_id
                 WHERE a.tenant_id = ?::uuid
                   AND a.dt_inicio <= ?::date
                   AND (a.dt_fim IS NULL OR a.dt_fim >= ?::date)
                 ORDER BY a.dt_inicio DESC LIMIT 1
            ) pl ON TRUE
            LEFT JOIN LATERAL (
                SELECT count(*) AS abertas, COALESCE(SUM(valor), 0) AS valor
                  FROM fatura
                 WHERE tenant_id = ?::uuid AND status NOT IN ('PAGA', 'CANCELADA')
            ) fa ON TRUE
            ON CONFLICT (tenant_id, dia) DO UPDATE SET
                locacoes = EXCLUDED.locacoes, reservas = EXCLUDED.reservas,
                no_shows = EXCLUDED.no_shows, receita_bruta = EXCLUDED.receita_bruta,
                receita_comissionavel = EXCLUDED.receita_comissionavel,
                emissoes_documento = EXCLUDED.emissoes_documento,
                emissoes_gru = EXCLUDED.emissoes_gru,
                emissoes_previa = EXCLUDED.emissoes_previa,
                creditos_consumidos = EXCLUDED.creditos_consumidos,
                saldo_creditos_fim = EXCLUDED.saldo_creditos_fim,
                mrr = EXCLUDED.mrr, plano_nome = EXCLUDED.plano_nome,
                faturas_abertas = EXCLUDED.faturas_abertas,
                valor_em_aberto = EXCLUDED.valor_em_aberto,
                atualizado_em = now()
            """,
            tenantId, dia, tenantId, dia, tenantId, dia, tenantId, dia,
            tenantId, dia, tenantId, dia, tenantId, dia, dia, tenantId);
        return 1;
    }
}
