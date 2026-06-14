package com.jetski.shared.audit.internal;

import com.jetski.shared.audit.api.dto.AuditoriaDTO;
import com.jetski.shared.audit.api.dto.AuditoriaFilters;
import com.jetski.shared.audit.domain.Auditoria;
import com.jetski.shared.audit.domain.AuditoriaRepository;
import com.jetski.usuarios.domain.Usuario;
import com.jetski.usuarios.internal.repository.UsuarioRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for audit log operations.
 *
 * <p>Provides:
 * <ul>
 *   <li>Paginated listing with dynamic filters</li>
 *   <li>CSV export for compliance</li>
 *   <li>User name resolution for display</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.10.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;
    private final UsuarioRepository usuarioRepository;

    private static final DateTimeFormatter CSV_DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    /**
     * List audit entries with dynamic filters.
     *
     * @param tenantId Tenant ID (RLS will also filter, but we use explicit for clarity)
     * @param filters Filter parameters
     * @param pageable Pagination
     * @return Page of audit DTOs with resolved user names
     */
    @Transactional(readOnly = true)
    public Page<AuditoriaDTO> listar(UUID tenantId, AuditoriaFilters filters, Pageable pageable) {
        log.debug("Listing audit entries: tenant={}, filters={}", tenantId, filters);

        Specification<Auditoria> spec = buildSpecification(tenantId, filters);
        Page<Auditoria> page = auditoriaRepository.findAll(spec, pageable);

        // Batch load user names for efficiency
        Set<UUID> userIds = new HashSet<>();
        page.getContent().forEach(a -> {
            if (a.getUsuarioId() != null) {
                userIds.add(a.getUsuarioId());
            }
        });

        Map<UUID, String> userNames = resolveUserNames(userIds);

        return page.map(a -> toDTO(a, userNames.getOrDefault(a.getUsuarioId(), "Sistema")));
    }

    /**
     * Get single audit entry by ID.
     *
     * @param id Audit entry ID
     * @return Audit DTO
     */
    @Transactional(readOnly = true)
    public Optional<AuditoriaDTO> buscarPorId(UUID id) {
        return auditoriaRepository.findById(id)
                .map(a -> {
                    String userName = a.getUsuarioId() != null
                            ? usuarioRepository.findById(a.getUsuarioId())
                                .map(Usuario::getNome)
                                .orElse("Usuário removido")
                            : "Sistema";
                    return toDTO(a, userName);
                });
    }

    /**
     * Export audit entries to CSV format.
     *
     * @param tenantId Tenant ID
     * @param filters Filter parameters
     * @return CSV bytes
     */
    @Transactional(readOnly = true)
    public byte[] exportarCsv(UUID tenantId, AuditoriaFilters filters) {
        log.info("Exporting audit CSV: tenant={}, filters={}", tenantId, filters);

        Specification<Auditoria> spec = buildSpecification(tenantId, filters);
        List<Auditoria> entries = auditoriaRepository.findAll(spec);

        // Batch load user names
        Set<UUID> userIds = new HashSet<>();
        entries.forEach(a -> {
            if (a.getUsuarioId() != null) {
                userIds.add(a.getUsuarioId());
            }
        });
        Map<UUID, String> userNames = resolveUserNames(userIds);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(baos)) {
            // Header
            writer.println("Data/Hora,Usuário,Ação,Entidade,ID Entidade,IP,Trace ID");

            // Data rows
            for (Auditoria a : entries) {
                String userName = userNames.getOrDefault(a.getUsuarioId(), "Sistema");
                writer.printf("%s,%s,%s,%s,%s,%s,%s%n",
                        escapeCsv(formatDateTime(a.getCreatedAt())),
                        escapeCsv(userName),
                        escapeCsv(a.getAcao()),
                        escapeCsv(a.getEntidade()),
                        a.getEntidadeId() != null ? a.getEntidadeId().toString() : "",
                        escapeCsv(a.getIp()),
                        escapeCsv(a.getTraceId())
                );
            }
        }

        log.info("CSV export completed: {} entries", entries.size());
        return baos.toByteArray();
    }

    // ===================================================================
    // Private Helpers
    // ===================================================================

    private Specification<Auditoria> buildSpecification(UUID tenantId, AuditoriaFilters filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter by tenant
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            // Dynamic filters
            if (filters != null) {
                if (filters.acao() != null && !filters.acao().isBlank()) {
                    predicates.add(cb.equal(root.get("acao"), filters.acao()));
                }

                if (filters.entidade() != null && !filters.entidade().isBlank()) {
                    predicates.add(cb.equal(root.get("entidade"), filters.entidade()));
                }

                if (filters.entidadeId() != null) {
                    predicates.add(cb.equal(root.get("entidadeId"), filters.entidadeId()));
                }

                if (filters.usuarioId() != null) {
                    predicates.add(cb.equal(root.get("usuarioId"), filters.usuarioId()));
                }

                if (filters.dataInicio() != null) {
                    Instant inicio = filters.dataInicio().atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), inicio));
                }

                if (filters.dataFim() != null) {
                    Instant fim = filters.dataFim().plusDays(1).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
                    predicates.add(cb.lessThan(root.get("createdAt"), fim));
                }
            }

            // Default ordering (newest first)
            query.orderBy(cb.desc(root.get("createdAt")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Map<UUID, String> resolveUserNames(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        List<Usuario> users = usuarioRepository.findAllById(userIds);
        Map<UUID, String> result = new HashMap<>();
        users.forEach(u -> result.put(u.getId(), u.getNome()));
        return result;
    }

    private AuditoriaDTO toDTO(Auditoria a, String usuarioNome) {
        return new AuditoriaDTO(
                a.getId(),
                a.getAcao(),
                a.getEntidade(),
                a.getEntidadeId(),
                a.getUsuarioId(),
                usuarioNome,
                a.getIp(),
                a.getTraceId(),
                a.getCreatedAt(),
                a.getDadosAnteriores(),
                a.getDadosNovos()
        );
    }

    private String formatDateTime(Instant instant) {
        return instant != null ? CSV_DATE_FORMAT.format(instant) : "";
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // Escape quotes and wrap in quotes if contains comma or quote
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
