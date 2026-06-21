package com.jetski.usuarios.internal;

import com.jetski.usuarios.domain.Usuario;
import com.jetski.usuarios.internal.repository.UsuarioGlobalRolesRepository;
import com.jetski.usuarios.internal.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do PlatformAdminSeeder.
 *
 * Valida o bootstrap idempotente do super admin via PLATFORM_ADMIN_EMAILS.
 *
 * @author Jetski Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformAdminSeeder")
class PlatformAdminSeederTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private UsuarioGlobalRolesRepository globalRolesRepository;

    @InjectMocks
    private PlatformAdminSeeder seeder;

    private void setEmails(String emails) {
        ReflectionTestUtils.setField(seeder, "adminEmails", emails);
    }

    private Usuario usuarioComId(UUID id) {
        Usuario u = mock(Usuario.class);
        when(u.getId()).thenReturn(id);
        return u;
    }

    @Test
    @DisplayName("Promove usuário existente sem global roles → unrestricted=true + PLATFORM_ADMIN")
    void promovesNovoSuperAdmin() {
        UUID id = UUID.randomUUID();
        Usuario u = usuarioComId(id);
        setEmails("boss@empresa.com");
        when(usuarioRepository.findByEmail("boss@empresa.com")).thenReturn(Optional.of(u));
        when(globalRolesRepository.findById(id)).thenReturn(Optional.empty());

        seeder.run(null);

        ArgumentCaptor<UsuarioGlobalRoles> captor = ArgumentCaptor.forClass(UsuarioGlobalRoles.class);
        verify(globalRolesRepository).save(captor.capture());
        UsuarioGlobalRoles saved = captor.getValue();
        assertThat(saved.getUsuarioId()).isEqualTo(id);
        assertThat(saved.getUnrestrictedAccess()).isTrue();
        assertThat(saved.getRoles()).contains("PLATFORM_ADMIN");
    }

    @Test
    @DisplayName("Idempotente: preserva papéis globais existentes e garante PLATFORM_ADMIN")
    void preservaPapeisExistentes() {
        UUID id = UUID.randomUUID();
        setEmails("boss@empresa.com");
        UsuarioGlobalRoles existing = UsuarioGlobalRoles.builder()
            .usuarioId(id)
            .roles(new String[]{"SUPPORT"})
            .unrestrictedAccess(false)
            .build();
        Usuario u = usuarioComId(id);
        when(usuarioRepository.findByEmail("boss@empresa.com")).thenReturn(Optional.of(u));
        when(globalRolesRepository.findById(id)).thenReturn(Optional.of(existing));

        seeder.run(null);

        ArgumentCaptor<UsuarioGlobalRoles> captor = ArgumentCaptor.forClass(UsuarioGlobalRoles.class);
        verify(globalRolesRepository).save(captor.capture());
        UsuarioGlobalRoles saved = captor.getValue();
        assertThat(saved.getUnrestrictedAccess()).isTrue();
        assertThat(saved.getRoles()).containsExactlyInAnyOrder("SUPPORT", "PLATFORM_ADMIN");
    }

    @Test
    @DisplayName("Email sem usuário → não persiste (aplica no próximo boot)")
    void ignoraEmailSemUsuario() {
        setEmails("ninguem@empresa.com");
        when(usuarioRepository.findByEmail("ninguem@empresa.com")).thenReturn(Optional.empty());

        seeder.run(null);

        verify(globalRolesRepository, never()).save(any());
    }

    @Test
    @DisplayName("Vários emails (com espaços) são processados individualmente")
    void processaListaSeparadaPorVirgula() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Usuario u1 = usuarioComId(id1);
        Usuario u2 = usuarioComId(id2);
        setEmails(" a@x.com , b@x.com ");
        when(usuarioRepository.findByEmail("a@x.com")).thenReturn(Optional.of(u1));
        when(usuarioRepository.findByEmail("b@x.com")).thenReturn(Optional.of(u2));
        when(globalRolesRepository.findById(any())).thenReturn(Optional.empty());

        seeder.run(null);

        verify(globalRolesRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("PLATFORM_ADMIN_EMAILS vazio → não toca o banco")
    void vazioNaoFazNada() {
        setEmails("");

        seeder.run(null);

        verifyNoInteractions(usuarioRepository, globalRolesRepository);
    }
}
