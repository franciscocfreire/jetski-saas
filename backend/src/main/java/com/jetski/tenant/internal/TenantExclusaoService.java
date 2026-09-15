package com.jetski.tenant.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.storage.StorageService;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.domain.TenantStatus;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import com.jetski.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Exclusão de empresa (super admin) — Fase 3 do plano reset/exclusão.
 *
 * <p><b>Fluxo padrão (carência)</b>: {@link #agendar} suspende o acesso na
 * hora e agenda o expurgo para D+{@value #CARENCIA_DIAS} — o prazo prometido
 * nos Termos de Uso ("dados disponíveis por 30 dias"). Cancelável via
 * {@link #cancelar} até o job rodar. <b>Imediato</b>: {@link #excluirAgora}
 * executa o expurgo na mesma chamada (empresas de teste).
 *
 * <p><b>Expurgo</b> ({@link #expurgar}): export de arquivamento (Fase 2) →
 * deleção completa dos dados (nível TOTAL sem exceção de admin) → remoção dos
 * arquivos do prefixo do tenant no storage → TOMBSTONE: a linha do tenant
 * fica com status EXCLUIDO, slug renomeado (libera o slug para novo signup) e
 * campos sensíveis anonimizados. DELETE físico do tenant é impossível por
 * design — o ledger de créditos (append-only por trigger) referencia o tenant
 * com FK RESTRICT; o tombstone preserva o histórico fiscal da plataforma.
 *
 * <p>Contas Keycloak não são removidas: staff sem tenant_access não acessa
 * nada, e clientes finais são contas da plataforma (cross-tenant) por design.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantExclusaoService {

    public static final int CARENCIA_DIAS = 30;

    private static final DateTimeFormatter DATA_SLUG =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("America/Sao_Paulo"));

    private final TenantRepository tenantRepository;
    private final TenantResetService tenantResetService;
    private final TenantExportService tenantExportService;
    private final StorageService storageService;
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /** Agenda a exclusão (carência): suspende agora, expurga em D+30. */
    @Transactional
    public Instant agendar(UUID tenantId, String confirmacaoSlug) {
        Tenant tenant = carregarVivo(tenantId);
        validarSlug(tenant, confirmacaoSlug);
        exigirSemParceriaEmVigor(tenantId);

        String statusAntes = tenant.getStatus().name();
        Instant quando = Instant.now().plus(Duration.ofDays(CARENCIA_DIAS));
        tenant.setStatus(TenantStatus.SUSPENSO);
        tenant.setExclusaoAgendadaEm(quando);
        tenantRepository.save(tenant);

        eventPublisher.publishEvent(TenantStatusChangedEvent.of(
            tenantId, "TENANT_EXCLUSAO_AGENDADA", statusAntes, TenantStatus.SUSPENSO.name(),
            TenantContext.getUsuarioId(),
            "expurgo agendado para " + LocalDate.ofInstant(quando, ZoneId.of("America/Sao_Paulo")),
            tenant.getRazaoSocial(), tenant.getSlug()));
        log.warn("[PLATFORM] Exclusão AGENDADA: tenant={} ({}), expurgo em {}",
            tenantId, tenant.getSlug(), quando);
        return quando;
    }

    /** Cancela uma exclusão agendada (a empresa permanece SUSPENSA — reativar é ação à parte). */
    @Transactional
    public void cancelar(UUID tenantId) {
        Tenant tenant = carregarVivo(tenantId);
        if (tenant.getExclusaoAgendadaEm() == null) {
            throw new BusinessException("Esta empresa não tem exclusão agendada");
        }
        tenant.setExclusaoAgendadaEm(null);
        tenantRepository.save(tenant);

        eventPublisher.publishEvent(TenantStatusChangedEvent.of(
            tenantId, "TENANT_EXCLUSAO_CANCELADA", tenant.getStatus().name(),
            tenant.getStatus().name(), TenantContext.getUsuarioId(), null,
            tenant.getRazaoSocial(), tenant.getSlug()));
        log.warn("[PLATFORM] Exclusão CANCELADA: tenant={} ({})", tenantId, tenant.getSlug());
    }

    /** Expurgo imediato (dupla confirmação na API) — mesmo pipeline do job. */
    @Transactional
    public Map<String, Long> excluirAgora(UUID tenantId, String confirmacaoSlug) {
        Tenant tenant = carregarVivo(tenantId);
        validarSlug(tenant, confirmacaoSlug);
        return expurgar(tenant);
    }

    /**
     * Pipeline de expurgo (job e imediato). Transação do chamador; advisory
     * lock compartilhado com o reset (mesma chave) evita corrida entre eles.
     */
    @Transactional
    public Map<String, Long> expurgar(Tenant tenant) {
        UUID tenantId = tenant.getId();
        // RLS do tenant alvo + lock (mesmos do reset)
        jdbcTemplate.queryForObject(
            "SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
        jdbcTemplate.queryForObject(
            "SELECT pg_advisory_xact_lock(hashtextextended(?, 42))", Object.class,
            tenantId.toString());

        // 0. Parceria de emissão em vigor bloqueia (vale também para o job: se surgiu
        //    durante a carência, o expurgo falha, loga e tenta de novo no próximo ciclo)
        exigirSemParceriaEmVigor(tenantId);

        // 1. Arquivamento (falhou → aborta; nada é apagado sem cópia)
        TenantExportService.Export export = tenantExportService.exportar(tenantId);

        // 2. Dados (TOTAL sem exceção de admin)
        Map<String, Long> apagados = tenantResetService.expurgoCompleto(tenantId);

        // 3. Arquivos do tenant no storage
        int arquivos = 0;
        for (String chave : storageService.listObjectKeys(tenantId + "/")) {
            storageService.deleteFile(chave);
            arquivos++;
        }

        // 4. Tombstone: anonimiza e libera o slug
        String statusAntes = tenant.getStatus().name();
        String slugOriginal = tenant.getSlug();
        String sufixo = DATA_SLUG.format(Instant.now()) + "-"
            + UUID.randomUUID().toString().substring(0, 4);
        tenant.setSlug(slugOriginal + "-excluido-" + sufixo);
        tenant.setStatus(TenantStatus.EXCLUIDO);
        tenant.setExcluidoEm(Instant.now());
        tenant.setExclusaoAgendadaEm(null);
        tenant.setPixChave(null);
        // Sensíveis PELA ENTIDADE: um UPDATE via JDBC aqui era desfeito no commit — o flush
        // do Hibernate regrava a linha inteira (sem @DynamicUpdate) com os valores carregados.
        tenant.setSmtpHost(null);
        tenant.setSmtpUsername(null);
        tenant.setSmtpPassword(null);
        tenant.setSmtpFrom(null);
        tenant.setEmailRemetente(null);
        tenant.setWhatsapp(null);
        tenant.setMarinhaEmail(null);
        tenant.setBranding(null);
        tenant.setExibirNoMarketplace(false);
        tenant.setEmissoraHabilitada(false);
        tenantRepository.saveAndFlush(tenant);
        // Convites de parceria ainda não aceitos (dos dois lados) morrem com a empresa —
        // senão o outro lado poderia aceitar e criar parceria com um tombstone.
        jdbcTemplate.update("UPDATE vinculo_emissao SET status = 'REVOGADO', revogado_em = now(), "
            + "revogado_por = ?, updated_at = now() "
            + "WHERE (tenant_emissor_id = ? OR tenant_operador_id = ?) AND status = 'CONVIDADO'",
            TenantContext.getUsuarioId(), tenantId, tenantId);
        // Assinatura encerrada (histórico comercial permanece)
        jdbcTemplate.update("UPDATE assinatura SET status = 'expirada' "
            + "WHERE tenant_id = ? AND status <> 'expirada'", tenantId);

        eventPublisher.publishEvent(TenantStatusChangedEvent.of(
            tenantId, "TENANT_EXCLUIDO", statusAntes, TenantStatus.EXCLUIDO.name(),
            TenantContext.getUsuarioId(),
            "linhas=" + apagados.values().stream().mapToLong(Long::longValue).sum()
                + "; arquivos=" + arquivos + "; export=" + export.key(),
            tenant.getRazaoSocial(), slugOriginal));
        log.warn("[PLATFORM] Empresa EXPURGADA: tenant={} ({}), linhas={}, arquivos={}, export={}",
            tenantId, slugOriginal,
            apagados.values().stream().mapToLong(Long::longValue).sum(), arquivos, export.key());
        return apagados;
    }

    private Tenant carregarVivo(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Empresa não encontrada: " + tenantId));
        if (tenant.getStatus() == TenantStatus.EXCLUIDO) {
            throw new BusinessException("Esta empresa já foi excluída");
        }
        return tenant;
    }

    /**
     * Empresa com parceria de emissão em vigor (ATIVA ou BLOQUEADA) não é excluída:
     * EAMA com delegadas deixaria as operadoras emitindo em nome de um tombstone, e a
     * delegada sumiria do painel da EAMA sem aviso. A revogação é feita antes, pelo
     * fluxo da parceria (com as notificações às partes). {@code vinculo_emissao} só é
     * visível às partes: fixa o tenant alvo na transação.
     */
    private void exigirSemParceriaEmVigor(UUID tenantId) {
        jdbcTemplate.queryForObject(
            "SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
        Integer delegadas = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vinculo_emissao WHERE tenant_emissor_id = ? "
            + "AND status IN ('ATIVO', 'BLOQUEADO')", Integer.class, tenantId);
        if (delegadas != null && delegadas > 0) {
            throw new BusinessException("Esta empresa é EAMA emissora de " + delegadas
                + (delegadas == 1 ? " delegada" : " delegadas")
                + " com parceria em vigor. Revogue as parcerias antes de excluir.");
        }
        Integer comoDelegada = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vinculo_emissao WHERE tenant_operador_id = ? "
            + "AND status IN ('ATIVO', 'BLOQUEADO')", Integer.class, tenantId);
        if (comoDelegada != null && comoDelegada > 0) {
            throw new BusinessException("Esta empresa é delegada de uma EAMA com parceria em vigor. "
                + "Revogue a parceria antes de excluir.");
        }
    }

    private void validarSlug(Tenant tenant, String confirmacaoSlug) {
        if (confirmacaoSlug == null || !confirmacaoSlug.trim().equals(tenant.getSlug())) {
            throw new BusinessException(
                "Confirmação inválida: digite o slug exato da empresa (" + tenant.getSlug() + ")");
        }
    }
}
