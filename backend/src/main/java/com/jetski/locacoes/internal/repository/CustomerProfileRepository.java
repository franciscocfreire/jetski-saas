package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {

    Optional<CustomerProfile> findByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<CustomerProfile> findByCpf(String cpf);
}
