package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.VinculoInstrutorOperadora;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: instrutores da operadora submetidos à EAMA da parceria (V070).
 *
 * @author Jetski Team
 */
public interface VinculoInstrutorOperadoraRepository
        extends JpaRepository<VinculoInstrutorOperadora, UUID> {

    Optional<VinculoInstrutorOperadora> findByVinculoIdAndInstrutorId(UUID vinculoId, UUID instrutorId);

    List<VinculoInstrutorOperadora> findByVinculoIdOrderBySolicitadoEmDesc(UUID vinculoId);

    List<VinculoInstrutorOperadora> findByInstrutorId(UUID instrutorId);
}
