package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.Modelo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: ModeloRepository
 *
 * Handles database operations for jetski models (Modelo).
 *
 * <p><b>IMPORTANTE — RLS NÃO basta aqui:</b> além da policy de isolamento, a tabela
 * {@code modelo} tem a policy {@code marketplace_public_read} (vitrine pública), e
 * policies permissivas somam com OR — uma query sem filtro de tenant devolve TAMBÉM
 * os modelos de OUTROS tenants exibidos no marketplace (vazamento visto ao vivo em
 * 10/jul/2026). Toda query tenant-scoped deste repositório filtra tenant_id
 * explicitamente (regra 1 do CLAUDE.md).
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Repository
public interface ModeloRepository extends JpaRepository<Modelo, UUID> {

    /** Modelos ativos DO TENANT (filtro explícito — ver javadoc da classe). */
    @Query("""
        SELECT m FROM Modelo m
        WHERE m.tenantId = :tenantId AND m.ativo = true
        ORDER BY m.nome ASC
    """)
    List<Modelo> findAllActive(@Param("tenantId") UUID tenantId);

    /** Todos os modelos DO TENANT, incluindo inativos (filtro explícito). */
    @Query("""
        SELECT m FROM Modelo m
        WHERE m.tenantId = :tenantId
        ORDER BY m.nome ASC
    """)
    List<Modelo> findAllByTenant(@Param("tenantId") UUID tenantId);

    /** Busca por id DO TENANT — nunca devolve modelo de marketplace alheio. */
    Optional<Modelo> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Existência por id DO TENANT — modelo de marketplace alheio conta como inexistente. */
    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Nome já usado DO TENANT (unicidade por tenant — o filtro explícito evita
     * colisão falsa com modelo homônimo de outro tenant exibido no marketplace).
     */
    boolean existsByNomeAndTenantId(String nome, UUID tenantId);

    /** Modelos ativos DO TENANT (contagem). */
    @Query("""
        SELECT COUNT(m) FROM Modelo m
        WHERE m.tenantId = :tenantId AND m.ativo = true
    """)
    long countActive(@Param("tenantId") UUID tenantId);
}
