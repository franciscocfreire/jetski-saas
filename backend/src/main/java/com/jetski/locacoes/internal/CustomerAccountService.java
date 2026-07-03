package com.jetski.locacoes.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.DuplicateUserException;
import com.jetski.shared.security.UserProvisioningService;
import jakarta.persistence.EntityManager;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Conta do CLIENTE FINAL (portal): auto-cadastro (identidade global no Keycloak,
 * role CLIENTE, sem Membro) e visão "self" dos vínculos com lojas.
 *
 * Arquitetura de identidade (PORTAL_CLIENTE_SPEC §2):
 * - O signup cria só a identidade global; o Cliente (tenant-scoped) nasce na
 *   primeira interação com cada loja e é ligado via cliente_identity_provider.
 * - Os vínculos são lidos cross-tenant pela policy RLS de self-read (V029):
 *   o serviço seta app.customer_sub = sub do JWT (transaction-local) e a policy
 *   libera SELECT apenas das linhas do próprio cliente — sem bypass de RLS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAccountService {

    private static final String PROVIDER = "keycloak";

    private final UserProvisioningService userProvisioningService;
    private final EntityManager entityManager;

    /** Auto-cadastro público: identidade global + VERIFY_EMAIL (Keycloak envia o link). */
    public void signup(String nome, String email, String senha) {
        String emailNorm = email.trim().toLowerCase();
        try {
            String providerUserId = userProvisioningService.provisionCustomer(emailNorm, nome.trim(), senha);
            if (providerUserId == null) {
                throw new BusinessException("Não foi possível criar a conta agora — tente novamente em instantes");
            }
            log.info("Cliente auto-registrado: email={}, sub={}", emailNorm, providerUserId);
        } catch (DuplicateUserException e) {
            throw new BusinessException("Já existe uma conta com este e-mail — entre ou recupere a senha");
        }
    }

    /** Lojas às quais este login já está vinculado (1 Cliente por tenant). */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<VinculoLoja> vinculos(String providerUserId) {
        entityManager.createNativeQuery("SELECT set_config('app.customer_sub', :sub, true)")
            .setParameter("sub", providerUserId)
            .getSingleResult();

        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT cip.tenant_id, cip.cliente_id, t.slug, t.razao_social
                  FROM cliente_identity_provider cip
                  JOIN tenant t ON t.id = cip.tenant_id
                 WHERE cip.provider = :provider
                   AND cip.provider_user_id = :sub
                 ORDER BY cip.linked_at
                """)
            .setParameter("provider", PROVIDER)
            .setParameter("sub", providerUserId)
            .getResultList();

        return rows.stream()
            .map(r -> VinculoLoja.builder()
                .tenantId((UUID) r[0])
                .clienteId((UUID) r[1])
                .slug((String) r[2])
                .nome((String) r[3])
                .build())
            .toList();
    }

    /** Atualiza o nome da identidade global (Keycloak). */
    public void atualizarNome(String providerUserId, String nome) {
        if (!userProvisioningService.updateUserName(providerUserId, nome.trim())) {
            throw new BusinessException("Não foi possível atualizar o perfil agora — tente novamente");
        }
    }

    @Value
    @Builder
    public static class VinculoLoja {
        UUID tenantId;
        UUID clienteId;
        String slug;
        String nome;
    }
}
