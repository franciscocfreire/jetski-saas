package com.jetski.metering.internal;

import com.jetski.metering.api.dto.EmissaoMensalDTO;
import com.jetski.metering.domain.EmissaoUsoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Série mensal de uso do tenant (meses sem uso vêm zerados —
 * padrão de agregação por YearMonth do DashboardFinanceiroService).
 */
@Service
@RequiredArgsConstructor
public class MeteringQueryService {

    /** Fuso operacional padrão da plataforma (mesmo dos fechamentos). */
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final int MAX_MESES = 24;

    private final EmissaoUsoRepository repository;

    @Transactional(readOnly = true)
    public List<EmissaoMensalDTO> serieMensal(UUID tenantId, int meses) {
        int n = Math.max(1, Math.min(meses, MAX_MESES));
        YearMonth atual = YearMonth.now(ZONA);
        YearMonth inicio = atual.minusMonths(n - 1L);
        Instant inicioInstant = inicio.atDay(1).atStartOfDay(ZONA).toInstant();

        Map<String, long[]> porMes = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            porMes.put(inicio.plusMonths(i).toString(), new long[3]); // [documento, gru, previa]
        }
        for (Object[] row : repository.contarPorMesETipo(tenantId, inicioInstant)) {
            long[] tot = porMes.get((String) row[0]);
            if (tot == null) {
                continue;
            }
            long count = ((Number) row[2]).longValue();
            switch ((String) row[1]) {
                case "DOCUMENTO" -> tot[0] = count;
                case "GRU" -> tot[1] = count;
                case "PREVIA" -> tot[2] = count;
                default -> { }
            }
        }

        List<EmissaoMensalDTO> serie = new ArrayList<>(n);
        porMes.forEach((competencia, t) ->
            serie.add(new EmissaoMensalDTO(competencia, t[0], t[1], t[2], t[0] + t[1])));
        return serie;
    }
}
