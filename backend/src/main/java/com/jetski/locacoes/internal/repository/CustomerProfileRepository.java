package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {

    Optional<CustomerProfile> findByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<CustomerProfile> findByCpf(String cpf);

    /**
     * Busca por CPF ignorando pontuação — o índice único parcial guarda o CPF
     * como digitado, então "123.456.789-09" e "12345678909" seriam contas
     * distintas sem esta normalização.
     */
    @Query("select p from CustomerProfile p where p.cpf is not null and " +
           "replace(replace(replace(p.cpf, '.', ''), '-', ''), ' ', '') = :cpfDigits")
    Optional<CustomerProfile> findByCpfNormalizado(@Param("cpfDigits") String cpfDigits);
}
