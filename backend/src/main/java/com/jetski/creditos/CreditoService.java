package com.jetski.creditos;

import com.jetski.creditos.domain.CreditoCompra;
import com.jetski.creditos.domain.CreditoCompraRepository;
import com.jetski.creditos.domain.CreditoLancamento;
import com.jetski.creditos.domain.CreditoLancamentoRepository;
import com.jetski.creditos.domain.StatusCompra;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final CreditoCompraRepository compraRepository;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${jetski.creditos.adesao:5}")
    private int creditosAdesao;

    @Value("${jetski.creditos.pix-chave:}")
    private String pixChave;

    @Value("${jetski.creditos.pix-nome:Meu Jet}")
    private String pixNome;

    @Value("${jetski.creditos.pix-cidade:Florianopolis}")
    private String pixCidade;

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
     * O motivo grava a reserva para o extrato ser autoexplicativo.
     *
     * @throws BusinessException se o saldo for insuficiente (bloqueia a emissão)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void debitarEmissaoDocumento(UUID tenantId, UUID documentoId, UUID reservaId) {
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
            .motivo(reservaId != null ? "Reserva " + reservaId.toString().substring(0, 8) : null)
            .criadoPor(actorOrNull())
            .build());
        log.info("Crédito debitado: tenant={}, documento={}, reserva={}, saldo {} -> {}",
            tenantId, documentoId, reservaId, saldoAtual, saldoAtual - 1);
    }

    /** Prefixo do motivo do estorno de bônus (usado no cálculo do bônus restante). */
    public static final String MOTIVO_ESTORNO_BONUS = "Estorno de bônus";

    /**
     * Anti-fraude da emissão delegada (EMISSAO_DELEGADA_SPEC §4.1.4/§8.H):
     * zera o bônus de adesão REMANESCENTE da operadora na ativação do vínculo.
     * Estorno append-only limitado ao saldo (nunca negativa) e ao bônus ainda
     * não consumido — créditos COMPRADOS são preservados. Idempotente por
     * vínculo ({@code referencia_id = vinculoId}).
     *
     * <p>Flush explícito no fim: o chamador pode estar rodando numa janela
     * RLS de outro tenant (set_config local) — o INSERT precisa ir ao banco
     * ainda dentro da janela da operadora (gotcha flush×RLS).
     *
     * @return créditos estornados (0 se nada a estornar ou já estornado)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int estornarBonusDelegacao(UUID tenantId, UUID vinculoId, UUID actor) {
        if (repository.existsByTenantIdAndReferenciaId(tenantId, vinculoId)) {
            log.debug("Estorno de bônus já aplicado para o vínculo {} (tenant {})", vinculoId, tenantId);
            return 0;
        }
        lockTenant(tenantId);
        int saldoAtual = repository.saldo(tenantId);
        int bonusRestante = repository.bonusRestante(
            tenantId, TipoLancamento.ADESAO, TipoLancamento.ESTORNO, MOTIVO_ESTORNO_BONUS + "%");
        int quantidade = Math.min(saldoAtual, Math.max(0, bonusRestante));
        if (quantidade <= 0) {
            log.info("Nada a estornar de bônus (tenant={}, saldo={}, bonusRestante={})",
                tenantId, saldoAtual, bonusRestante);
            return 0;
        }
        CreditoLancamento lanc = repository.save(CreditoLancamento.builder()
            .tenantId(tenantId)
            .tipo(TipoLancamento.ESTORNO)
            .quantidade(-quantidade)
            .saldoApos(saldoAtual - quantidade)
            .referenciaId(vinculoId)
            .motivo(MOTIVO_ESTORNO_BONUS + " — ativação da parceria de emissão delegada")
            .criadoPor(actor)
            .build());
        entityManager.flush();
        eventPublisher.publishEvent(CreditoLancadoEvent.of(
            tenantId, lanc.getTipo().name(), lanc.getQuantidade(), lanc.getSaldoApos(),
            lanc.getMotivo(), actor));
        log.info("Bônus estornado na delegação: tenant={}, vinculo={}, quantidade={}, saldo {} -> {}",
            tenantId, vinculoId, quantidade, saldoAtual, saldoAtual - quantidade);
        return quantidade;
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

    // ========== Compra de créditos (PIX manual, aprovação do super admin) ==========

    private static final String CONFIG_PRECO = "creditos_preco_unitario";

    /** Chave PIX fixa da plataforma para compra de créditos. */
    public String pixChave() {
        return pixChave;
    }

    /**
     * PIX copia-e-cola (BR Code) com o valor exato de {@code quantidade} créditos.
     * O mesmo payload é o conteúdo do QR Code exibido pela UI.
     */
    @Transactional(readOnly = true)
    public PixCobranca gerarPixCompra(int quantidade) {
        if (quantidade < 1 || quantidade > 100_000) {
            throw new BusinessException("Informe a quantidade de emissões desejada");
        }
        if (pixChave == null || pixChave.isBlank()) {
            throw new BusinessException("Chave PIX da plataforma não configurada — contate o Meu Jet");
        }
        BigDecimal valor = precoUnitario().multiply(BigDecimal.valueOf(quantidade))
            .setScale(2, RoundingMode.HALF_UP);
        String payload = com.jetski.shared.pix.BrCodePix.gerar(pixChave.trim(), valor, pixNome, pixCidade);
        return new PixCobranca(payload, valor, quantidade);
    }

    /** Cobrança PIX pronta para pagamento (copia-e-cola = conteúdo do QR). */
    public record PixCobranca(String copiaECola, BigDecimal valor, int quantidade) {}

    /** Preço do crédito (R$) — configurável pelo super admin em plataforma_config. */
    @Transactional(readOnly = true)
    public BigDecimal precoUnitario() {
        try {
            Object valor = entityManager.createNativeQuery(
                    "SELECT valor FROM plataforma_config WHERE chave = ?1")
                .setParameter(1, CONFIG_PRECO)
                .getSingleResult();
            return new BigDecimal(valor.toString());
        } catch (jakarta.persistence.NoResultException e) {
            throw new BusinessException("Preço do crédito não configurado — contate o Meu Jet");
        }
    }

    /** Atualiza o preço do crédito (super admin). */
    @Transactional
    public BigDecimal atualizarPrecoUnitario(BigDecimal preco, UUID actor) {
        if (preco == null || preco.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Preço do crédito deve ser maior que zero");
        }
        BigDecimal normalizado = preco.setScale(2, RoundingMode.HALF_UP);
        entityManager.createNativeQuery("""
                INSERT INTO plataforma_config (chave, valor, updated_at, updated_by)
                VALUES (?1, ?2, now(), ?3)
                ON CONFLICT (chave) DO UPDATE SET valor = EXCLUDED.valor,
                    updated_at = now(), updated_by = EXCLUDED.updated_by
                """)
            .setParameter(1, CONFIG_PRECO)
            .setParameter(2, normalizado.toPlainString())
            .setParameter(3, actor)
            .executeUpdate();
        log.info("Preço do crédito atualizado para R$ {} por {}", normalizado, actor);
        return normalizado;
    }

    /**
     * Registra a solicitação de compra POR QUANTIDADE: o tenant escolhe quantas
     * emissões quer e o valor a transferir = quantidade × preço vigente
     * (exato, sem arredondamento — facilita a conferência no extrato bancário).
     * Snapshot do preço fica na solicitação; os créditos só entram na aprovação.
     */
    @Transactional
    public CreditoCompra solicitarCompra(UUID tenantId, int quantidade, String pixTxid) {
        if (quantidade < 1) {
            throw new BusinessException("Informe a quantidade de emissões desejada");
        }
        if (quantidade > 100_000) {
            throw new BusinessException("Quantidade acima do limite por solicitação (100.000)");
        }
        BigDecimal preco = precoUnitario();
        BigDecimal valorEsperado = preco.multiply(BigDecimal.valueOf(quantidade))
            .setScale(2, RoundingMode.HALF_UP);
        String txid = pixTxid == null ? "" : pixTxid.trim();
        if (txid.isEmpty()) {
            throw new BusinessException("Informe o número da transação PIX (comprovante)");
        }
        if (txid.length() > 80) {
            throw new BusinessException("Número da transação PIX muito longo (máx. 80 caracteres)");
        }
        if (compraRepository.existsByTenantIdAndPixTxid(tenantId, txid)) {
            throw new BusinessException("Esta transação PIX já foi usada em outra solicitação");
        }
        CreditoCompra compra = compraRepository.save(CreditoCompra.builder()
            .tenantId(tenantId)
            .quantidade(quantidade)
            .valorPago(valorEsperado)
            .precoUnitario(preco)
            .pixTxid(txid)
            .status(StatusCompra.PENDENTE)
            .criadoPor(actorOrNull())
            .build());
        log.info("Compra de créditos solicitada: tenant={}, quantidade={}, valor=R${}, preco=R${}, txid={}",
            tenantId, quantidade, valorEsperado, preco, txid);
        return compra;
    }

    @Transactional(readOnly = true)
    public List<CreditoCompra> comprasDoTenant(UUID tenantId, int limit) {
        return compraRepository.findByTenantIdOrderByCreatedAtDesc(
            tenantId, PageRequest.of(0, Math.max(1, Math.min(limit, 50))));
    }

    @Transactional(readOnly = true)
    public List<CreditoCompra> comprasPendentesDoTenant(UUID tenantId) {
        return compraRepository.findByTenantIdAndStatusOrderByCreatedAtAsc(tenantId, StatusCompra.PENDENTE);
    }

    /**
     * Aprova a compra: credita no ledger (auditado) e marca APROVADA.
     * Chamado pela plataforma com a sessão já no tenant alvo. Idempotência por status.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CreditoCompra aprovarCompra(UUID tenantId, UUID compraId, UUID actor) {
        CreditoCompra compra = compraRepository.findByIdAndTenantId(compraId, tenantId)
            .orElseThrow(() -> new BusinessException("Solicitação de compra não encontrada"));
        if (compra.getStatus() != StatusCompra.PENDENTE) {
            throw new BusinessException("Solicitação já foi " + compra.getStatus().name().toLowerCase());
        }
        CreditoLancamento lanc = lancarAjuste(tenantId, compra.getQuantidade(),
            "Compra de créditos — PIX " + compra.getPixTxid(), actor);
        compra.setStatus(StatusCompra.APROVADA);
        compra.setDecididoPor(actor);
        compra.setDecididoEm(java.time.Instant.now());
        compra.setLancamentoId(lanc.getId());
        return compraRepository.save(compra);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CreditoCompra rejeitarCompra(UUID tenantId, UUID compraId, String observacao, UUID actor) {
        CreditoCompra compra = compraRepository.findByIdAndTenantId(compraId, tenantId)
            .orElseThrow(() -> new BusinessException("Solicitação de compra não encontrada"));
        if (compra.getStatus() != StatusCompra.PENDENTE) {
            throw new BusinessException("Solicitação já foi " + compra.getStatus().name().toLowerCase());
        }
        if (observacao == null || observacao.isBlank()) {
            throw new BusinessException("Informe o motivo da rejeição");
        }
        compra.setStatus(StatusCompra.REJEITADA);
        compra.setDecididoPor(actor);
        compra.setDecididoEm(java.time.Instant.now());
        compra.setObservacao(observacao.trim());
        log.info("Compra de créditos rejeitada: tenant={}, compra={}, motivo={}", tenantId, compraId, observacao);
        return compraRepository.save(compra);
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
