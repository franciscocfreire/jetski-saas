package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.dto.JetskiSearchCriteria;
import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.JetskiStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Specification Builder: Jetski Advanced Filtering
 *
 * Builds dynamic JPA Criteria queries for jetski search and filtering.
 * Supports multiple criteria combined with AND logic.
 *
 * Features:
 * - Status filtering (multiple values with IN clause)
 * - Modelo filtering
 * - Serial number search (partial match, case-insensitive)
 * - Hourmeter range filtering
 * - Active status filtering
 *
 * @author Jetski Team
 * @since 0.9.0
 */
public class JetskiSpecification {

    /**
     * Build specification from search criteria
     *
     * @param criteria Search criteria
     * @return Specification for querying jetskis
     */
    public static Specification<Jetski> fromCriteria(JetskiSearchCriteria criteria) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by active status
            if (criteria.getAtivo() != null) {
                predicates.add(criteriaBuilder.equal(root.get("ativo"), criteria.getAtivo()));
            }

            // Filter by status (multiple values)
            if (criteria.getStatus() != null && !criteria.getStatus().isEmpty()) {
                predicates.add(root.get("status").in(criteria.getStatus()));
            }

            // Filter by modelo
            if (criteria.getModeloId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("modeloId"), criteria.getModeloId()));
            }

            // Search by serial number (partial match, case-insensitive)
            if (criteria.getSerie() != null && !criteria.getSerie().isBlank()) {
                String searchPattern = "%" + criteria.getSerie().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("serie")),
                        searchPattern
                ));
            }

            // Filter by hourmeter range
            if (criteria.getHorimetroMin() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("horimetroAtual"),
                        criteria.getHorimetroMin()
                ));
            }

            if (criteria.getHorimetroMax() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("horimetroAtual"),
                        criteria.getHorimetroMax()
                ));
            }

            // Combine all predicates with AND
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Specification: Find jetskis available for rental
     *
     * Business Rule RN06: Only DISPONIVEL jetskis can be reserved
     *
     * @return Specification for available jetskis
     */
    public static Specification<Jetski> isAvailable() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.equal(root.get("ativo"), true),
                criteriaBuilder.equal(root.get("status"), JetskiStatus.DISPONIVEL)
        );
    }

    /**
     * Specification: Find jetskis by modelo
     *
     * @param modeloId Modelo UUID
     * @return Specification for jetskis of this modelo
     */
    public static Specification<Jetski> hasModelo(UUID modeloId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("modeloId"), modeloId);
    }

    /**
     * Specification: Find jetskis in maintenance
     *
     * @return Specification for jetskis in MANUTENCAO status
     */
    public static Specification<Jetski> isInMaintenance() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("status"), JetskiStatus.MANUTENCAO);
    }

    /**
     * Specification: Find jetskis currently rented
     *
     * @return Specification for jetskis in LOCADO status
     */
    public static Specification<Jetski> isRented() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("status"), JetskiStatus.LOCADO);
    }

    /**
     * Specification: Find jetskis needing maintenance (hourmeter above threshold)
     *
     * @param threshold Hourmeter threshold
     * @return Specification for jetskis with hourmeter >= threshold
     */
    public static Specification<Jetski> needsMaintenance(BigDecimal threshold) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.equal(root.get("ativo"), true),
                criteriaBuilder.greaterThanOrEqualTo(root.get("horimetroAtual"), threshold)
        );
    }
}
