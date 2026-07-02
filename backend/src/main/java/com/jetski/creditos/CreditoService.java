package com.jetski.creditos;

import com.jetski.creditos.domain.CreditoLancamento;
import com.jetski.creditos.domain.CreditoLancamentoRepository;
import com.jetski.creditos.domain.TipoLancamento;
import com.jetski.creditos.domain.event.CreditoLancadoEvent;
import com.jetski.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * API pública do módulo de créditos (exposta no root, padrão TenantQueryService).
 *
 * <p>O débito é síncrono e participa da transação do chamador (emissão):
 * advisory lock por tenant serializa débitos concorrentes — saldo nunca fica
 * negativo. Toda escrita é um novo lançamento (o ledger é append-only no banco).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditoService {

    public static final String MSG_SALDO_ESGOTADO =
        "Créditos de emissão esgotados. Fale com o Meu Jet para adquirir mais créditos.";

    private final CreditoLancamentoRepository repository;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${jetski.creditos.adesao:20}")
    private int creditosAdesao;

    @Transactional(readOnly = true)
    public int saldo(UUID tenantId) {
        return repository.saldo(tenantId);
    }

    /**
     * Fail-fast barato (sem lock) antes do trabalho pesado da emissão.
     * O débito definitivo re-verifica sob lock.
     */
    @Transactional(readOnly = true)
    public void verificarSaldoDisponivel(UUID tenantId) {
        if (repository.saldo(tenantId) < 1) {
            throw new BusinessException(MSG_SALDO_ESGOTADO);
        }
    }

    /**
     * Debita 1 crédito pelo documento emitido à Marinha. Deve rodar DENTRO da
     * transação da emissão: se a emissão falhar depois, o débito reverte junto.
     *
     * @throws BusinessException se o saldo for insuficiente (bloqueia a emissão)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void debitarEmissaoDocumento(UUID tenantId, UUID documentoId) {
        lockTenant(tenantId);
        int saldoAtual = repository.saldo(tenantId);
        if (saldoAtual < 1) {
            throw new BusinessException(MSG_SALDO_ESGOTADO);
        }
        repository.save(CreditoLancamento.builder()
            .tenantId(tenantId)
            .tipo(TipoLancamento.CONSUMO)
            .quantidade(-1)
            .saldoApos(saldoAtual - 1)
            .referenciaId(documentoId)
            .criadoPor(actorOrNull())
            .build());
        log.info("Crédito debitado: tenant={}, documento={}, saldo {} -> {}",
            tenantId, documentoId, saldoAtual, saldoAtual - 1);
    }

    /** Grant de adesão (idempotente pelo unique parcial — 1 ADESAO por tenant). */
    @Transactional
    public void lancarAdesao(UUID tenantId) {
        if (creditosAdesao <= 0) {
            return;
        }
        if (repository.existsByTenantIdAndTipo(tenantId, TipoLancamento.ADESAO)) {
            log.debug("Adesão já creditada para o tenant {}", tenantId);
            return;
        }
        lockTenant(tenantId);
        int saldoAtual = repository.saldo(tenantId);
        CreditoLancamento lanc = repository.save(CreditoLancamento.builder()
            .tenantId(tenantId)
            .tipo(TipoLancamento.ADESAO)
            .quantidade(creditosAdesao)
            .saldoApos(saldoAtual + creditosAdesao)
            .motivo("Créditos de adesão")
            .build());
        eventPublisher.publishEvent(CreditoLancadoEvent.of(
            tenantId, lanc.getTipo().name(), lanc.getQuantidade(), lanc.getSaldoApos(),
            lanc.getMotivo(), null));
        log.info("Créditos de adesão lançados: tenant={}, quantidade={}", tenantId, creditosAdesao);
    }

    /**
     * Lançamento manual (super admin): positivo credita, negativo debita.
     * Chamado com a sessão já apontando para o tenant alvo (set_config).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CreditoLancamento lancarAjuste(UUID tenantId, int quantidade, String motivo, UUID actor) {
        if (quantidade == 0) {
            throw new BusinessException("Quantidade do lançamento não pode ser zero");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException("Motivo é obrigatório no lançamento de créditos");
        }
        lockTenant(tenantId);
        int saldoAtual = repository.saldo(tenantId);
        int novoSaldo = saldoAtual + quantidade;
        if (novoSaldo < 0) {
            throw new BusinessException(
                "Lançamento deixaria o saldo negativo (saldo atual: " + saldoAtual + ")");
        }
        TipoLancamento tipo = quantidade > 0 ? TipoLancamento.AJUSTE : TipoLancamento.ESTORNO;
        CreditoLancamento lanc = repository.save(CreditoLancamento.builder()
            .tenantId(tenantId)
            .tipo(tipo)
            .quantidade(quantidade)
            .saldoApos(novoSaldo)
            .motivo(motivo.trim())
            .criadoPor(actor)
            .build());
        eventPublisher.publishEvent(CreditoLancadoEvent.of(
            tenantId, tipo.name(), quantidade, novoSaldo, lanc.getMotivo(), actor));
        log.info("Créditos lançados pelo admin: tenant={}, quantidade={}, saldo {} -> {}, actor={}",
            tenantId, quantidade, saldoAtual, novoSaldo, actor);
        return lanc;
    }

    @Transactional(readOnly = true)
    public List<CreditoLancamento> extrato(UUID tenantId, int limit) {
        return repository.findByTenantIdOrderByCreatedAtDesc(
            tenantId, PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
    }

    /** Serializa movimentos do mesmo tenant na transação corrente. */
    private void lockTenant(UUID tenantId) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?1, 42))")
            .setParameter(1, tenantId.toString())
            .getSingleResult();
    }

    private UUID actorOrNull() {
        try {
            return com.jetski.shared.security.TenantContext.getUsuarioId();
        } catch (Exception e) {
            return null;
        }
    }
}
