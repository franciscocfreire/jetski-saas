package com.jetski.usuarios.domain;

import com.jetski.usuarios.internal.UsuarioGlobalRoles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Domain Entities
 *
 * Tests Lombok-generated builders, getters, setters for:
 * - Usuario
 * - Membro
 * - UsuarioGlobalRoles
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@DisplayName("Domain Entity Tests")
class DomainEntityTest {

    // ========================================================================
    // Usuario Entity Tests
    // ========================================================================

    @Test
    @DisplayName("Usuario builder should create valid entity")
    void usuarioBuilder_ShouldCreateValidEntity() {
        // Given
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        // When
        Usuario usuario = Usuario.builder()
            .id(id)
            .email("test@example.com")
            .nome("Test User")
            .ativo(true)
            .createdAt(now)
            .updatedAt(now)
            .build();

        // Then
        assertThat(usuario.getId()).isEqualTo(id);
        assertThat(usuario.getEmail()).isEqualTo("test@example.com");
        assertThat(usuario.getNome()).isEqualTo("Test User");
        assertThat(usuario.getAtivo()).isTrue();
        assertThat(usuario.getCreatedAt()).isEqualTo(now);
        assertThat(usuario.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Usuario setters should update entity")
    void usuarioSetters_ShouldUpdateEntity() {
        // Given
        Usuario usuario = Usuario.builder()
            .id(UUID.randomUUID())
            .email("old@example.com")
            .nome("Old Name")
            .ativo(false)
            .build();

        // When
        usuario.setEmail("new@example.com");
        usuario.setNome("New Name");
        usuario.setAtivo(true);

        // Then
        assertThat(usuario.getEmail()).isEqualTo("new@example.com");
        assertThat(usuario.getNome()).isEqualTo("New Name");
        assertThat(usuario.getAtivo()).isTrue();
    }

    @Test
    @DisplayName("Usuario should handle inactive status")
    void usuario_ShouldHandleInactiveStatus() {
        // When
        Usuario usuario = Usuario.builder()
            .id(UUID.randomUUID())
            .email("inactive@example.com")
            .nome("Inactive User")
            .ativo(false)
            .build();

        // Then
        assertThat(usuario.getAtivo()).isFalse();
    }

    // ========================================================================
    // Membro Entity Tests
    // ========================================================================

    @Test
    @DisplayName("Membro builder should create valid entity")
    void membroBuilder_ShouldCreateValidEntity() {
        // Given
        UUID tenantId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Instant now = Instant.now();

        // When
        Membro membro = Membro.builder()
            .id(1)
            .tenantId(tenantId)
            .usuarioId(usuarioId)
            .papeis(new String[]{"GERENTE", "OPERADOR"})
            .ativo(true)
            .createdAt(now)
            .updatedAt(now)
            .build();

        // Then
        assertThat(membro.getId()).isEqualTo(1);
        assertThat(membro.getTenantId()).isEqualTo(tenantId);
        assertThat(membro.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(membro.getPapeis()).containsExactly("GERENTE", "OPERADOR");
        assertThat(membro.getAtivo()).isTrue();
        assertThat(membro.getCreatedAt()).isEqualTo(now);
        assertThat(membro.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Membro should support multiple roles")
    void membro_ShouldSupportMultipleRoles() {
        // When
        Membro membro = Membro.builder()
            .id(1)
            .tenantId(UUID.randomUUID())
            .usuarioId(UUID.randomUUID())
            .papeis(new String[]{"ADMIN_TENANT", "GERENTE", "OPERADOR", "FINANCEIRO"})
            .ativo(true)
            .build();

        // Then
        assertThat(membro.getPapeis())
            .hasSize(4)
            .contains("ADMIN_TENANT", "GERENTE", "OPERADOR", "FINANCEIRO");
    }

    @Test
    @DisplayName("Membro should support single role")
    void membro_ShouldSupportSingleRole() {
        // When
        Membro membro = Membro.builder()
            .id(1)
            .tenantId(UUID.randomUUID())
            .usuarioId(UUID.randomUUID())
            .papeis(new String[]{"OPERADOR"})
            .ativo(true)
            .build();

        // Then
        assertThat(membro.getPapeis())
            .hasSize(1)
            .containsExactly("OPERADOR");
    }

    @Test
    @DisplayName("Membro setters should update roles")
    void membroSetters_ShouldUpdateRoles() {
        // Given
        Membro membro = Membro.builder()
            .id(1)
            .tenantId(UUID.randomUUID())
            .usuarioId(UUID.randomUUID())
            .papeis(new String[]{"OPERADOR"})
            .ativo(true)
            .build();

        // When
        membro.setPapeis(new String[]{"GERENTE", "FINANCEIRO"});

        // Then
        assertThat(membro.getPapeis()).containsExactly("GERENTE", "FINANCEIRO");
    }

    @Test
    @DisplayName("Membro should handle inactive status")
    void membro_ShouldHandleInactiveStatus() {
        // When
        Membro membro = Membro.builder()
            .id(1)
            .tenantId(UUID.randomUUID())
            .usuarioId(UUID.randomUUID())
            .papeis(new String[]{"OPERADOR"})
            .ativo(false)
            .build();

        // Then
        assertThat(membro.getAtivo()).isFalse();
    }

    // ========================================================================
    // UsuarioGlobalRoles Entity Tests
    // ========================================================================

    @Test
    @DisplayName("UsuarioGlobalRoles builder should create valid entity")
    void usuarioGlobalRolesBuilder_ShouldCreateValidEntity() {
        // Given
        UUID usuarioId = UUID.randomUUID();
        Instant now = Instant.now();

        // When
        UsuarioGlobalRoles globalRoles = UsuarioGlobalRoles.builder()
            .usuarioId(usuarioId)
            .roles(new String[]{"PLATFORM_ADMIN"})
            .unrestrictedAccess(true)
            .createdAt(now)
            .updatedAt(now)
            .build();

        // Then
        assertThat(globalRoles.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(globalRoles.getRoles()).containsExactly("PLATFORM_ADMIN");
        assertThat(globalRoles.getUnrestrictedAccess()).isTrue();
        assertThat(globalRoles.getCreatedAt()).isEqualTo(now);
        assertThat(globalRoles.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("UsuarioGlobalRoles should support unrestricted access")
    void usuarioGlobalRoles_ShouldSupportUnrestrictedAccess() {
        // When
        UsuarioGlobalRoles globalRoles = UsuarioGlobalRoles.builder()
            .usuarioId(UUID.randomUUID())
            .roles(new String[]{"PLATFORM_ADMIN"})
            .unrestrictedAccess(true)
            .build();

        // Then
        assertThat(globalRoles.getUnrestrictedAccess()).isTrue();
    }

    @Test
    @DisplayName("UsuarioGlobalRoles should support restricted access with global roles")
    void usuarioGlobalRoles_ShouldSupportRestrictedAccessWithGlobalRoles() {
        // When
        UsuarioGlobalRoles globalRoles = UsuarioGlobalRoles.builder()
            .usuarioId(UUID.randomUUID())
            .roles(new String[]{"SUPPORT_AGENT"})
            .unrestrictedAccess(false)
            .build();

        // Then
        assertThat(globalRoles.getUnrestrictedAccess()).isFalse();
        assertThat(globalRoles.getRoles()).containsExactly("SUPPORT_AGENT");
    }

    @Test
    @DisplayName("UsuarioGlobalRoles should support multiple global roles")
    void usuarioGlobalRoles_ShouldSupportMultipleGlobalRoles() {
        // When
        UsuarioGlobalRoles globalRoles = UsuarioGlobalRoles.builder()
            .usuarioId(UUID.randomUUID())
            .roles(new String[]{"PLATFORM_ADMIN", "AUDITOR", "SUPPORT_MANAGER"})
            .unrestrictedAccess(true)
            .build();

        // Then
        assertThat(globalRoles.getRoles())
            .hasSize(3)
            .contains("PLATFORM_ADMIN", "AUDITOR", "SUPPORT_MANAGER");
    }

    // ========================================================================
    // Cross-Entity Tests
    // ========================================================================

    @Test
    @DisplayName("Entities should handle null optional fields")
    void entities_ShouldHandleNullOptionalFields() {
        // When
        Usuario usuario = Usuario.builder()
            .id(UUID.randomUUID())
            .email("test@example.com")
            .nome("Test")
            .ativo(true)
            // createdAt and updatedAt not set
            .build();

        // Then
        assertThat(usuario.getCreatedAt()).isNull();
        assertThat(usuario.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("Entities should support UUID identity")
    void entities_ShouldSupportUuidIdentity() {
        // Given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        // When
        Usuario u1 = Usuario.builder().id(id1).email("test1@example.com").build();
        Usuario u2 = Usuario.builder().id(id2).email("test2@example.com").build();

        // Then
        assertThat(u1.getId()).isNotEqualTo(u2.getId());
        assertThat(u1.getId()).isEqualTo(id1);
        assertThat(u2.getId()).isEqualTo(id2);
    }

    // ========================================================================
    // JPA Lifecycle Callback Tests
    // ========================================================================

    @Test
    @DisplayName("Usuario onCreate() should set timestamps")
    void usuario_onCreate_ShouldSetTimestamps() {
        // Given
        Usuario usuario = new Usuario();
        usuario.setEmail("test@example.com");
        usuario.setNome("Test User");
        usuario.setAtivo(true);
        Instant before = Instant.now();

        // When
        usuario.onCreate();

        // Then
        Instant after = Instant.now();
        assertThat(usuario.getCreatedAt()).isNotNull();
        assertThat(usuario.getUpdatedAt()).isNotNull();
        assertThat(usuario.getCreatedAt()).isBetween(before, after);
        assertThat(usuario.getUpdatedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("Usuario onUpdate() should update timestamp")
    void usuario_onUpdate_ShouldUpdateTimestamp() {
        // Given
        Usuario usuario = new Usuario();
        usuario.setEmail("test@example.com");
        usuario.setNome("Test User");
        usuario.setAtivo(true);
        usuario.onCreate();

        Instant originalUpdatedAt = usuario.getUpdatedAt();

        // When (simulate a small delay)
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        usuario.onUpdate();

        // Then
        assertThat(usuario.getUpdatedAt()).isNotNull();
        assertThat(usuario.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    @DisplayName("Membro onCreate() should set timestamps")
    void membro_onCreate_ShouldSetTimestamps() {
        // Given
        Membro membro = new Membro();
        membro.setTenantId(UUID.randomUUID());
        membro.setUsuarioId(UUID.randomUUID());
        membro.setPapeis(new String[]{"OPERADOR"});
        membro.setAtivo(true);
        Instant before = Instant.now();

        // When
        membro.onCreate();

        // Then
        Instant after = Instant.now();
        assertThat(membro.getCreatedAt()).isNotNull();
        assertThat(membro.getUpdatedAt()).isNotNull();
        assertThat(membro.getCreatedAt()).isBetween(before, after);
        assertThat(membro.getUpdatedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("Membro onUpdate() should update timestamp")
    void membro_onUpdate_ShouldUpdateTimestamp() {
        // Given
        Membro membro = new Membro();
        membro.setTenantId(UUID.randomUUID());
        membro.setUsuarioId(UUID.randomUUID());
        membro.setPapeis(new String[]{"OPERADOR"});
        membro.setAtivo(true);
        membro.onCreate();

        Instant originalUpdatedAt = membro.getUpdatedAt();

        // When (simulate a small delay)
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        membro.onUpdate();

        // Then
        assertThat(membro.getUpdatedAt()).isNotNull();
        assertThat(membro.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    // ========================================================================
    // Additional Setter Tests
    // ========================================================================

    @Test
    @DisplayName("Usuario setters should work for all fields including ID and timestamps")
    void usuario_setters_ShouldWorkForAllFields() {
        // Given
        Usuario usuario = new Usuario();
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2024-01-01T10:00:00Z");
        Instant updated = Instant.parse("2024-01-15T15:00:00Z");

        // When
        usuario.setId(id);
        usuario.setEmail("updated@example.com");
        usuario.setNome("Updated Name");
        usuario.setAtivo(false);
        usuario.setCreatedAt(created);
        usuario.setUpdatedAt(updated);

        // Then
        assertThat(usuario.getId()).isEqualTo(id);
        assertThat(usuario.getEmail()).isEqualTo("updated@example.com");
        assertThat(usuario.getNome()).isEqualTo("Updated Name");
        assertThat(usuario.getAtivo()).isFalse();
        assertThat(usuario.getCreatedAt()).isEqualTo(created);
        assertThat(usuario.getUpdatedAt()).isEqualTo(updated);
    }

    @Test
    @DisplayName("Membro setters should work for timestamps")
    void membro_setters_ShouldWorkForTimestamps() {
        // Given
        Membro membro = new Membro();
        Instant created = Instant.parse("2024-01-01T10:00:00Z");
        Instant updated = Instant.parse("2024-01-15T15:00:00Z");

        // When
        membro.setCreatedAt(created);
        membro.setUpdatedAt(updated);

        // Then
        assertThat(membro.getCreatedAt()).isEqualTo(created);
        assertThat(membro.getUpdatedAt()).isEqualTo(updated);
    }
}
