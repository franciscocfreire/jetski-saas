package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.CustomerHabilitacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório da habilitação GLOBAL do cliente (sem tenant/RLS — ver V043).
 * Consultas sempre pelo dono (CPF/sub), nunca listagens abertas.
 */
@Repository
public interface CustomerHabilitacaoRepository extends JpaRepository<CustomerHabilitacao, UUID> {

    Optional<CustomerHabilitacao> findByGruNumero(String gruNumero);

    List<CustomerHabilitacao> findByCpfOrderByEmitidaEmDesc(String cpf);

    List<CustomerHabilitacao> findByProviderUserIdOrderByEmitidaEmDesc(String providerUserId);
}
