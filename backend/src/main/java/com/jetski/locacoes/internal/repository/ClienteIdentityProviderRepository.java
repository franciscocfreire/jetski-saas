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

    /**
     * Lookup TENANT-SCOPED do vínculo — obrigatório no fluxo multi-loja: a
     * policy de self-read (V029) expõe vínculos de OUTRAS lojas na mesma
     * transação, então o filtro por tenant precisa ser explícito.
     */
    Optional<ClienteIdentityProvider> findByTenantIdAndProviderAndProviderUserId(
        java.util.UUID tenantId, String provider, String providerUserId);

    boolean existsByClienteId(UUID clienteId);
}
