package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.VinculoEmissao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository: vínculos de emissão delegada. A RLS dupla já limita as linhas
 * às parcerias do tenant da sessão; os filtros explícitos ficam por cima
 * (regra 1 do projeto — nunca confiar SÓ na RLS).
 *
 * @author Jetski Team
 */
public interface VinculoEmissaoRepository extends JpaRepository<VinculoEmissao, UUID> {

    List<VinculoEmissao> findByTenantOperadorIdOrTenantEmissorIdOrderByCreatedAtDesc(
        UUID operadorId, UUID emissorId);

    Optional<VinculoEmissao> findFirstByTenantOperadorIdAndStatusIn(
        UUID operadorId, Collection<VinculoEmissao.Status> status);

    boolean existsByTenantOperadorIdAndStatusIn(
        UUID operadorId, Collection<VinculoEmissao.Status> status);
}
