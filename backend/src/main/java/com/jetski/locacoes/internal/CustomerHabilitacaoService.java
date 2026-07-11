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
 *
 * <p>Desde a V043, a fonte PRIMÁRIA é o registro global do cliente
 * ({@code customer_habilitacao}, nascido na emissão) — sobrevive a
 * reset/exclusão da loja de origem. A varredura por vínculos permanece
 * como complemento/fallback; deduplicação por nº de GRU.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerHabilitacaoService {

    private static final ZoneId ZONA_EXIBICAO = ZoneId.of("America/Sao_Paulo");

    private static final String PROVIDER = "keycloak";

    private final EntityManager entityManager;
    private final CustomerAccountService customerAccountService;
    private final com.jetski.shared.storage.StorageService storageService;
    private final com.jetski.locacoes.internal.repository.CustomerHabilitacaoRepository customerHabilitacaoRepository;
    private final com.jetski.locacoes.internal.repository.CustomerProfileRepository customerProfileRepository;

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

        // Fonte global (V043): registros que nasceram na emissão e sobrevivem
        // à loja de origem. Dedup por nº de GRU — a linha tenant-scoped (viva)
        // tem prioridade sobre o espelho global.
        java.util.Set<String> vistos = new java.util.HashSet<>();
        for (HabilitacaoTemporaria t : resultado) {
            vistos.add(t.gruNumero());
        }
        for (var g : registrosGlobais(sub)) {
            if (!vistos.add(g.getGruNumero())) {
                continue;
            }
            Instant limite = g.getEmitidaEm().plus(
                Duration.ofDays(HabilitacaoService.VALIDADE_TEMPORARIA_DIAS));
            resultado.add(new HabilitacaoTemporaria(
                null, g.getLojaOrigemNome(), g.getTenantOrigem(), g.getReservaOrigem(),
                g.getGruNumero(), g.getEmitidaEm(),
                limite.atZone(ZONA_EXIBICAO).toLocalDate(),
                limite.isAfter(agora),
                g.getMarinhaConfirmadaEm() != null, g.getMarinhaConfirmadaEm()));
        }

        resultado.sort(Comparator.comparing(HabilitacaoTemporaria::emitidaEm).reversed());
        return resultado;
    }

    /** Registros globais do titular: pelo sub do vínculo e pelo CPF do perfil. */
    private List<com.jetski.locacoes.domain.CustomerHabilitacao> registrosGlobais(String sub) {
        var porSub = customerHabilitacaoRepository.findByProviderUserIdOrderByEmitidaEmDesc(sub);
        var porCpf = customerProfileRepository.findByProviderAndProviderUserId(PROVIDER, sub)
            .map(p -> p.getCpf())
            .filter(c -> c != null && !c.isBlank())
            .map(c -> customerHabilitacaoRepository
                .findByCpfOrderByEmitidaEmDesc(c.replaceAll("\\D", "")))
            .orElse(List.of());
        List<com.jetski.locacoes.domain.CustomerHabilitacao> todos = new ArrayList<>(porSub);
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        porSub.forEach(r -> ids.add(r.getId()));
        porCpf.stream().filter(r -> ids.add(r.getId())).forEach(todos::add);
        return todos;
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
        // Fallback global (V043): a loja de origem pode ter sido expurgada —
        // a cópia do PDF no prefixo da plataforma pertence ao cliente.
        for (var g : registrosGlobais(sub)) {
            if (reservaId.equals(g.getReservaOrigem()) && g.getPdfS3Key() != null) {
                return storageService.getObject(g.getPdfS3Key());
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
