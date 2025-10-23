package com.jetski.usuarios.internal.repository;

import com.jetski.usuarios.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository: Usuario
 *
 * Spring Data JPA repository for Usuario entity.
 * Provides CRUD operations and custom queries.
 *
 * @author Jetski Team
 * @since 0.4.0
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /**
     * Find user by email.
     *
     * @param email user email
     * @return Optional containing user if found
     */
    Optional<Usuario> findByEmail(String email);

    /**
     * Check if user with email exists.
     *
     * @param email user email
     * @return true if exists
     */
    boolean existsByEmail(String email);
}
