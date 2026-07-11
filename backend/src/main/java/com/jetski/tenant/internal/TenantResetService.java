package com.jetski.tenant.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import com.jetski.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reset de empresa (super admin): zera dados do tenant em NÍVEIS, preservando
 * o que nunca pode ser apagado. Caso de uso primário: loja testou com dados
 * de brincadeira e vai começar a operar de verdade.
 *
 * <p><b>Níveis</b> (cada um é superconjunto do anterior):
 * <ul>
 *   <li>{@code OPERACIONAL} — reservas, locações, clientes, fotos, documentos,
 *       financeiro operacional. Mantém frota, equipe e configurações.</li>
 *   <li>{@code FROTA} — anterior + modelos, jetskis, instrutores, itens,
 *       políticas de combustível/comissão.</li>
 *   <li>{@code TOTAL} — anterior + vendedores, convites, config de reserva e
 *       equipe (exceto membros ADMIN_TENANT, que permanecem).</li>
 * </ul>
 *
 * <p><b>Nunca apaga</b>: tenant/assinatura (relação comercial), créditos
 * (ledger financeiro append-only), metering (base de cobrança), auditoria
 * (imutável — registra inclusive o próprio reset) e tenant_signup (histórico).
 * Direitos do cliente final (habilitação temporária) já nascem globais
 * (customer_habilitacao, V043) e não dependem da loja.
 *
 * <p><b>Cobertura garantida</b>: o teste de classificação
 * ({@code TenantResetClassificationTest}) falha o build se uma migration criar
 * tabela com tenant_id sem classificá-la aqui — mesmo espírito do
 * 02-verify-rls.sql.
 *
 * <p><b>Execução</b>: deletes rodam com {@code app.tenant_id} fixado
 * (transaction-local) no tenant alvo — a RLS delimita o escopo; tabelas sem
 * RLS (membro/tenant_access, allowlist consciente) usam WHERE tenant_id
 * explícito. Advisory lock transacional evita corrida com outro reset.
 * Arquivos no storage NÃO são removidos nesta fase (órfãos inacessíveis;
 * varridos no export/expurgo da Fase 2/3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantResetService {

    public enum Nivel { OPERACIONAL, FROTA, TOTAL }

    /**
     * Dados operacionais — sempre apagados, em ordem de FK (filhos antes de
     * pais: lançamentos/anexos → reserva → locacao → cliente).
     */
    static final List<String> TABELAS_OPERACIONAL = List.of(
        "reserva_lancamento", "reserva_aceite", "reserva_comprovante",
        "reserva_habilitacao", "documento_emitido", "avaliacao",
        "comissao", "bonus_vendedor", "pagamento_vendedor", "presenca_vendedor",
        "foto", "abastecimento", "locacao_item_opcional", "os_manutencao",
        "despesa_manutencao", "despesa_operacional",
        "fechamento_diario", "fechamento_mensal", "fuel_price_day",
        "cliente_notificacao", "cliente_anexo", "cliente_claim_token",
        "cliente_identity_provider",
        "reserva", "locacao", "cliente");

    /** Cadastro de frota — apagado nos níveis FROTA e TOTAL. */
    static final List<String> TABELAS_FROTA = List.of(
        "modelo_midia", "fuel_policy", "jetski",
        "politica_comissao", "item_opcional", "instrutor", "modelo");

    /** Nível TOTAL (além do especial membro/tenant_access). */
    static final List<String> TABELAS_TOTAL = List.of(
        "vendedor", "convite", "reserva_config");

    /** Tratamento especial no TOTAL: preserva membros ADMIN_TENANT (e seus acessos). */
    static final Set<String> TABELAS_EQUIPE_ESPECIAL = Set.of("membro", "tenant_access");

    /** Nunca apagadas em nenhum nível (com o porquê no javadoc da classe). */
    static final Set<String> TABELAS_PRESERVADAS = Set.of(
        "assinatura", "auditoria", "credito_compra", "credito_lancamento",
        "emissao_uso", "tenant_signup", "fatura");

    private final JdbcTemplate jdbcTemplate;
    private final TenantRepository tenantRepository;
    private final TenantExportService tenantExportService;
    private final ApplicationEventPublisher eventPublisher;

    /** Resultado do reset: contagens apagadas + export de arquivamento gerado antes. */
    public record Resultado(Map<String, Long> apagados, String exportKey, long exportBytes) {}

    /** Contagem por tabela do que o reset apagaria no nível (dry-run p/ a UI). */
    @Transactional(readOnly = true)
    public Map<String, Long> preview(UUID tenantId, Nivel nivel) {
        carregarTenant(tenantId);
        fixarContexto(tenantId);

        Map<String, Long> contagens = new LinkedHashMap<>();
        for (String tabela : tabelasDoNivel(nivel)) {
            Long n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + tabela + " WHERE tenant_id = ?", Long.class, tenantId);
            if (n != null && n > 0) {
                contagens.put(tabela, n);
            }
        }
        if (nivel == Nivel.TOTAL) {
            Long membros = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM membro WHERE tenant_id = ? "
                + "AND NOT ('ADMIN_TENANT' = ANY(papeis))", Long.class, tenantId);
            if (membros != null && membros > 0) {
                contagens.put("membro", membros);
            }
        }
        return contagens;
    }

    /**
     * Executa o reset. Exige o slug digitado (confirmação forte) — compare
     * sempre com o valor atual do banco.
     *
     * @return contagem de linhas apagadas por tabela (ordem de execução)
     */
    @Transactional
    public Resultado reset(UUID tenantId, Nivel nivel, String confirmacaoSlug) {
        Tenant tenant = carregarTenant(tenantId);
        if (confirmacaoSlug == null || !confirmacaoSlug.trim().equals(tenant.getSlug())) {
            throw new BusinessException(
                "Confirmação inválida: digite o slug exato da empresa (" + tenant.getSlug() + ")");
        }

        fixarContexto(tenantId);
        // Um reset por vez por tenant (corrida com operação em curso)
        jdbcTemplate.queryForObject(
            "SELECT pg_advisory_xact_lock(hashtextextended(?, 42))", Object.class,
            tenantId.toString());

        // Export de arquivamento ANTES de apagar (decisão de produto: automático).
        // Falhou o export → o reset inteiro aborta; nada é apagado sem cópia.
        TenantExportService.Export export = tenantExportService.exportar(tenantId);

        Map<String, Long> apagados = new LinkedHashMap<>();
        for (String tabela : tabelasDoNivel(nivel)) {
            int n = jdbcTemplate.update(
                "DELETE FROM " + tabela + " WHERE tenant_id = ?", tenantId);
            if (n > 0) {
                apagados.put(tabela, (long) n);
            }
        }

        if (nivel == Nivel.TOTAL) {
            // Preserva membros ADMIN_TENANT; remove os demais e seus acessos.
            int membros = jdbcTemplate.update(
                "DELETE FROM membro WHERE tenant_id = ? "
                + "AND NOT ('ADMIN_TENANT' = ANY(papeis))", tenantId);
            if (membros > 0) {
                apagados.put("membro", (long) membros);
            }
            int acessos = jdbcTemplate.update(
                "DELETE FROM tenant_access WHERE tenant_id = ? AND usuario_id NOT IN "
                + "(SELECT usuario_id FROM membro WHERE tenant_id = ?)", tenantId, tenantId);
            if (acessos > 0) {
                apagados.put("tenant_access", (long) acessos);
            }
        }

        UUID actor = com.jetski.shared.security.TenantContext.getUsuarioId();
        eventPublisher.publishEvent(TenantStatusChangedEvent.of(
            tenantId, "TENANT_RESET", tenant.getStatus().name(), tenant.getStatus().name(),
            actor, "nivel=" + nivel + "; tabelas=" + apagados.size()
                + "; linhas=" + apagados.values().stream().mapToLong(Long::longValue).sum()
                + "; export=" + export.key(),
            tenant.getRazaoSocial(), tenant.getSlug()));

        log.warn("[PLATFORM] RESET de empresa executado: tenant={} ({}), nivel={}, apagados={}, export={}",
            tenantId, tenant.getSlug(), nivel, apagados, export.key());
        return new Resultado(apagados, export.key(), export.bytes());
    }

    /**
     * Expurgo COMPLETO dos dados (usado pela exclusão de empresa): nível TOTAL
     * sem a exceção de admin — TODOS os membros/acessos saem. Sem export nem
     * evento aqui (o {@code TenantExclusaoService} cuida da pipeline inteira);
     * pressupõe contexto/lock já fixados pelo chamador, na MESMA transação.
     */
    Map<String, Long> expurgoCompleto(UUID tenantId) {
        Map<String, Long> apagados = new LinkedHashMap<>();
        for (String tabela : tabelasDoNivel(Nivel.TOTAL)) {
            int n = jdbcTemplate.update("DELETE FROM " + tabela + " WHERE tenant_id = ?", tenantId);
            if (n > 0) {
                apagados.put(tabela, (long) n);
            }
        }
        for (String tabela : TABELAS_EQUIPE_ESPECIAL) {
            int n = jdbcTemplate.update("DELETE FROM " + tabela + " WHERE tenant_id = ?", tenantId);
            if (n > 0) {
                apagados.put(tabela, (long) n);
            }
        }
        return apagados;
    }

    private List<String> tabelasDoNivel(Nivel nivel) {
        List<String> tabelas = new ArrayList<>(TABELAS_OPERACIONAL);
        if (nivel == Nivel.FROTA || nivel == Nivel.TOTAL) {
            tabelas.addAll(TABELAS_FROTA);
        }
        if (nivel == Nivel.TOTAL) {
            tabelas.addAll(TABELAS_TOTAL);
        }
        return tabelas;
    }

    private Tenant carregarTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Empresa não encontrada: " + tenantId));
    }

    /**
     * Fixa app.tenant_id (transaction-local) no tenant ALVO: os DELETEs rodam
     * sob a RLS desse tenant — sem isso, contexto nulo faria a RLS filtrar
     * tudo e o reset "funcionar" apagando zero linhas.
     */
    private void fixarContexto(UUID tenantId) {
        jdbcTemplate.queryForObject(
            "SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
    }
}
