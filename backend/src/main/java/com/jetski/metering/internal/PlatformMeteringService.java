package com.jetski.metering.internal;

import com.jetski.metering.api.dto.PlatformEmissaoTenantDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agregado de emissões por tenant para o super admin (plataforma).
 *
 * <p>Doutrina da plataforma: <b>sem bypass de RLS</b> — itera os tenants (a tabela
 * {@code tenant} não tem RLS) trocando o tenant da transação com
 * {@code set_config('app.tenant_id', ..., true)} ({@code local=true}: o valor
 * reverte automaticamente ao fim da transação). Mesmo padrão de
 * {@code PlatformTenantService}/{@code PlatformSecretsService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformMeteringService {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private final EntityManager entityManager;

    @Transactional
    public List<PlatformEmissaoTenantDTO> emissoesPorTenant(YearMonth competencia) {
        Instant inicio = competencia.atDay(1).atStartOfDay(ZONA).toInstant();
        Instant fim = competencia.plusMonths(1).atDay(1).atStartOfDay(ZONA).toInstant();

        @SuppressWarnings("unchecked")
        List<Object[]> tenants = entityManager.createNativeQuery(
                "SELECT id, slug, razao_social FROM tenant ORDER BY razao_social")
            .getResultList();

        List<PlatformEmissaoTenantDTO> resultado = new ArrayList<>(tenants.size());
        for (Object[] t : tenants) {
            UUID tenantId = (UUID) t[0];
            entityManager.createNativeQuery("SELECT set_config('app.tenant_id', ?1, true)")
                .setParameter(1, tenantId.toString())
                .getSingleResult();

            @SuppressWarnings("unchecked")
            List<Object[]> counts = entityManager.createNativeQuery(
                    """
                    SELECT tipo, count(*) FROM emissao_uso
                    WHERE ocorrido_em >= ?1 AND ocorrido_em < ?2
                    GROUP BY tipo
                    """)
                .setParameter(1, inicio)
                .setParameter(2, fim)
                .getResultList();

            long documento = 0;
            long gru = 0;
            long previa = 0;
            for (Object[] c : counts) {
                long v = ((Number) c[1]).longValue();
                switch ((String) c[0]) {
                    case "DOCUMENTO" -> documento = v;
                    case "GRU" -> gru = v;
                    case "PREVIA" -> previa = v;
                    default -> { }
                }
            }
            resultado.add(new PlatformEmissaoTenantDTO(
                tenantId, (String) t[1], (String) t[2], documento, gru, previa, documento + gru));
        }
        return resultado;
    }
}
