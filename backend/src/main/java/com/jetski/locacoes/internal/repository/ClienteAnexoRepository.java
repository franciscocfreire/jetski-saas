package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.ClienteAnexo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClienteAnexoRepository extends JpaRepository<ClienteAnexo, UUID> {

    List<ClienteAnexo> findByClienteId(UUID clienteId);

    Optional<ClienteAnexo> findByClienteIdAndTipo(UUID clienteId, ClienteAnexo.Tipo tipo);
}
