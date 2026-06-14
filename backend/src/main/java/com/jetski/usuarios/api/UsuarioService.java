package com.jetski.usuarios.api;

import com.jetski.shared.exception.NotFoundException;
import com.jetski.usuarios.domain.Usuario;
import com.jetski.usuarios.internal.repository.MembroRepository;
import com.jetski.usuarios.internal.repository.UsuarioRepository;
import com.jetski.usuarios.domain.Membro;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public Service for Usuario (User) operations
 *
 * <p>This service is exposed to other modules and provides a clean API
 * for user-related operations without exposing internal repositories.</p>
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final MembroRepository membroRepository;

    /**
     * Find user by email
     *
     * @param email User email
     * @return Usuario
     * @throws NotFoundException if user not found
     */
    public Usuario findByEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + email));
    }

    /**
     * Get user ID from Authentication
     *
     * @param authentication Spring Security Authentication
     * @return User UUID
     * @throws NotFoundException if user not found
     */
    public UUID getUserIdFromAuthentication(Authentication authentication) {
        String email = authentication.getName();
        Usuario usuario = findByEmail(email);
        return usuario.getId();
    }

    /**
     * Resolve a user's display name by ID.
     *
     * <p>Exposed for consumer modules (e.g. audit) that need to show user names
     * without depending on the internal Usuario repository or entity.</p>
     *
     * @param id User ID
     * @return Optional with the user's name, empty if not found
     */
    public Optional<String> resolverNome(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return usuarioRepository.findById(id).map(Usuario::getNome);
    }

    /**
     * Resolve display names for a set of user IDs in a single query.
     *
     * @param ids User IDs
     * @return Map of user ID to name (missing IDs are simply absent)
     */
    public Map<UUID, String> resolverNomes(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> result = new HashMap<>();
        usuarioRepository.findAllById(ids)
                .forEach(u -> result.put(u.getId(), u.getNome()));
        return result;
    }

    /** @return true se já existe usuário com o e-mail informado. */
    public boolean existsByEmail(String email) {
        return usuarioRepository.existsByEmail(email);
    }

    /** @return true se existe usuário com o ID informado. */
    public boolean existsById(UUID id) {
        return usuarioRepository.existsById(id);
    }

    /**
     * Resolve o ID do membro (relação usuário↔tenant) para um usuário em um tenant.
     *
     * @return Optional com o ID do membro, vazio se não houver associação
     */
    public Optional<Integer> findMembroId(UUID tenantId, UUID usuarioId) {
        return membroRepository.findByTenantIdAndUsuarioId(tenantId, usuarioId)
                .map(Membro::getId);
    }
}
