package com.jetski.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Membro de teste com um papel REAL na empresa.
 *
 * <p>O {@code @PreAuthorize} enxerga o papel do membro no tenant do request (o
 * TenantFilter troca as realm roles do JWT pelos papéis do membro). Simular um
 * VENDEDOR só pondo {@code ROLE_VENDEDOR} no JWT de um usuário que é ADMIN no
 * banco não funciona mais — e não deveria: era exatamente a brecha. Use este
 * helper sempre que o teste precisar de um papel diferente do seed.
 */
public final class MembroDeTeste {

    private MembroDeTeste() {
    }

    /**
     * Garante (idempotente) usuario + mapeamento keycloak + membro ativo com {@code papel}
     * em {@code tenantId}, e devolve o JWT desse usuário.
     */
    public static RequestPostProcessor comPapel(JdbcTemplate jdbc, UUID tenantId, String papel) {
        UUID id = UUID.nameUUIDFromBytes(
            ("membro-teste:" + tenantId + ":" + papel).getBytes(StandardCharsets.UTF_8));

        jdbc.update("""
            INSERT INTO usuario (id, email, nome, ativo, email_verified, created_at, updated_at)
            VALUES (?, ?, ?, true, true, NOW(), NOW())
            ON CONFLICT DO NOTHING
            """, id, "membro." + papel.toLowerCase() + "." + id + "@test.local", "Membro " + papel);
        jdbc.update("""
            INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id, linked_at)
            VALUES (?, 'keycloak', ?, NOW())
            ON CONFLICT DO NOTHING
            """, id, id.toString());
        jdbc.update("""
            INSERT INTO membro (tenant_id, usuario_id, papeis, ativo, created_at, updated_at)
            VALUES (?, ?, ?, true, NOW(), NOW())
            ON CONFLICT DO NOTHING
            """, tenantId, id, new String[]{papel});

        return jwt().jwt(j -> j.subject(id.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_" + papel));
    }
}
