package com.jetski.tenant.internal.repository;

import com.jetski.tenant.domain.Capitania;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: catálogo de capitanias (plataforma, sem tenant_id).
 *
 * @author Jetski Team
 */
public interface CapitaniaRepository extends JpaRepository<Capitania, UUID> {

    List<Capitania> findByAtivaTrueOrderByNome();

    List<Capitania> findAllByOrderByNome();

    Optional<Capitania> findByCodigoIgnoreCase(String codigo);
}
