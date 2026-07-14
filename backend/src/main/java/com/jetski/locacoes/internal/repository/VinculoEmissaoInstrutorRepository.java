package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.VinculoEmissaoInstrutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.UUID;

/**
 * Repository: instrutores designados por parceria de emissão (V049).
 *
 * @author Jetski Team
 */
public interface VinculoEmissaoInstrutorRepository
        extends JpaRepository<VinculoEmissaoInstrutor, UUID> {

    List<VinculoEmissaoInstrutor> findByVinculoId(UUID vinculoId);

    boolean existsByVinculoId(UUID vinculoId);

    @Modifying
    void deleteByVinculoId(UUID vinculoId);
}
