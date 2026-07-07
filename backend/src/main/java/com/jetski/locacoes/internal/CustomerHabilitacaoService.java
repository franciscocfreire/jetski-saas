package com.jetski.locacoes.internal;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Habilitações temporárias (CHA-MTA-E) emitidas do CLIENTE, agregadas de
 * TODAS as lojas vinculadas — a temporária é do CONDUTOR (vale 30 dias a
 * partir da emissão) e o nº da GRU é a referência de consulta na Marinha.
 *
 * <p>Mesmo padrão cross-loja do {@link CustomerLocacaoService}: posse via
 * vínculos ({@code app.customer_sub}) + {@code fixarTenant} por vínculo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerHabilitacaoService {

    private static final ZoneId ZONA_EXIBICAO = ZoneId.of("America/Sao_Paulo");

    private final EntityManager entityManager;
    private final CustomerAccountService customerAccountService;
    private final com.jetski.shared.storage.StorageService storageService;

    public record HabilitacaoTemporaria(
        String lojaSlug,
        String lojaNome,
        UUID tenantId,
        UUID reservaId,
        String gruNumero,
        Instant emitidaEm,
        LocalDate validaAte,
        boolean vigente,
        /** Devolutiva da Marinha anexada pela loja — só confirmada é reusável. */
        boolean confirmada,
        Instant confirmadaEm
    ) {}

    /** Temporárias emitidas em todas as lojas vinculadas, mais recente primeiro. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<HabilitacaoTemporaria> minhasTemporarias(String sub) {
        List<HabilitacaoTemporaria> resultado = new ArrayList<>();
        Instant agora = Instant.now();

        for (var v : customerAccountService.vinculos(sub)) {
            fixarTenant(v.getTenantId());
            List<Object[]> rows = entityManager.createNativeQuery("""
                    SELECT r.id, r.documento_emitido_em, h.gru_numero, h.marinha_confirmada_em
                      FROM reserva r
                      JOIN reserva_habilitacao h
                        ON h.reserva_id = r.id AND h.tenant_id = r.tenant_id
                     WHERE r.cliente_id = :clienteId
                       AND r.documento_emitido_em IS NOT NULL
                       AND h.via = 'EMA'
                       AND h.gru_numero IS NOT NULL
                       AND h.gru_pago = true
                     ORDER BY r.documento_emitido_em DESC
                    """)
                .setParameter("clienteId", v.getClienteId())
                .getResultList();

            for (Object[] row : rows) {
                Instant emitidaEm = toInstant(row[1]);
                Instant confirmadaEm = toInstant(row[3]);
                Instant limite = emitidaEm.plus(
                    Duration.ofDays(HabilitacaoService.VALIDADE_TEMPORARIA_DIAS));
                resultado.add(new HabilitacaoTemporaria(
                    v.getSlug(), v.getNome(), v.getTenantId(), (UUID) row[0],
                    (String) row[2], emitidaEm,
                    limite.atZone(ZONA_EXIBICAO).toLocalDate(),
                    limite.isAfter(agora),
                    confirmadaEm != null, confirmadaEm));
            }
        }

        resultado.sort(Comparator.comparing(HabilitacaoTemporaria::emitidaEm).reversed());
        return resultado;
    }

    /**
     * Temporária ELEGÍVEL para reuso mais recente: vigente E confirmada pela
     * Marinha (devolutiva anexada pela loja) — emitida-sem-devolutiva não
     * comprova que a CHA existe.
     */
    @Transactional(readOnly = true)
    public Optional<HabilitacaoTemporaria> vigenteMaisRecente(String sub) {
        return minhasTemporarias(sub).stream()
            .filter(h -> h.vigente() && h.confirmada())
            .findFirst();
    }

    /** PDF da devolutiva da Marinha (posse por vínculo, em qualquer loja). */
    @Transactional(readOnly = true)
    public byte[] documentoConfirmado(String sub, UUID reservaId) {
        for (var v : customerAccountService.vinculos(sub)) {
            fixarTenant(v.getTenantId());
            List<?> keys = entityManager.createNativeQuery("""
                    SELECT h.cha_mtae_s3_key
                      FROM reserva r
                      JOIN reserva_habilitacao h
                        ON h.reserva_id = r.id AND h.tenant_id = r.tenant_id
                     WHERE r.id = :reservaId
                       AND r.cliente_id = :clienteId
                       AND h.cha_mtae_s3_key IS NOT NULL
                    """)
                .setParameter("reservaId", reservaId)
                .setParameter("clienteId", v.getClienteId())
                .getResultList();
            if (!keys.isEmpty()) {
                return storageService.getObject((String) keys.get(0));
            }
        }
        throw new com.jetski.shared.exception.NotFoundException(
            "Confirmação da Marinha ainda não disponível para esta habilitação");
    }

    private static Instant toInstant(Object valor) {
        if (valor == null) return null;
        return valor instanceof java.sql.Timestamp ts ? ts.toInstant() : (Instant) valor;
    }

    private void fixarTenant(UUID tenantId) {
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tid, true)")
            .setParameter("tid", tenantId.toString())
            .getSingleResult();
        com.jetski.shared.security.TenantContext.setTenantId(tenantId);
    }
}
