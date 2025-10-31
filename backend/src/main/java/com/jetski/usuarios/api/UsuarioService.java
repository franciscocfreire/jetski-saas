package com.jetski.usuarios.api;

import com.jetski.shared.exception.NotFoundException;
import com.jetski.usuarios.domain.Usuario;
import com.jetski.usuarios.internal.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
