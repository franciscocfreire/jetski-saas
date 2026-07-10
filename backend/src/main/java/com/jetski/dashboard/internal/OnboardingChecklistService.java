package com.jetski.dashboard.internal;

import com.jetski.dashboard.api.dto.OnboardingChecklistResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Checklist "primeiros passos" da empresa recém-aprovada — tudo derivado de
 * contagens/flags dos dados reais, em UMA query nativa (padrão do
 * DashboardMetricsService: JdbcTemplate evita depender de repositórios internal
 * de outros módulos). A chamada é do próprio tenant (X-Tenant-Id da sessão),
 * então a RLS escopa naturalmente — ainda assim cada subquery filtra por
 * tenant_id explícito (regra 1 do projeto: nunca confiar SÓ na RLS).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingChecklistService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public OnboardingChecklistResponse checklist(UUID tenantId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
            """
            SELECT
              (SELECT count(*) FROM modelo    WHERE tenant_id = ? AND ativo)  AS modelos,
              (SELECT count(*) FROM jetski    WHERE tenant_id = ? AND ativo)  AS jetskis,
              (SELECT count(*) FROM instrutor WHERE tenant_id = ? AND ativo)  AS instrutores,
              (SELECT count(*) FROM membro    WHERE tenant_id = ? AND ativo)  AS membros,
              (SELECT count(*) FROM locacao   WHERE tenant_id = ?)            AS locacoes,
              (SELECT marinha_email IS NOT NULL AND marinha_email <> ''
                 FROM tenant WHERE id = ?)                                    AS marinha,
              (SELECT pix_chave IS NOT NULL AND pix_chave <> ''
                 FROM tenant WHERE id = ?)                                    AS pix
            """,
            tenantId, tenantId, tenantId, tenantId, tenantId, tenantId, tenantId);

        return OnboardingChecklistResponse.of(
            asLong(row.get("modelos")) > 0,
            asLong(row.get("jetskis")) > 0,
            asLong(row.get("instrutores")) > 0,
            Boolean.TRUE.equals(row.get("marinha")),
            Boolean.TRUE.equals(row.get("pix")),
            asLong(row.get("membros")) > 1, // mais que o admin fundador
            asLong(row.get("locacoes")) > 0);
    }

    private static long asLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }
}
