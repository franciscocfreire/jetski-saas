package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.ClienteIdentityProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClienteIdentityProviderRepository extends JpaRepository<ClienteIdentityProvider, UUID> {

    Optional<ClienteIdentityProvider> findByClienteId(UUID clienteId);

    Optional<ClienteIdentityProvider> findByProviderAndProviderUserId(String provider, String providerUserId);

    boolean existsByClienteId(UUID clienteId);
}
