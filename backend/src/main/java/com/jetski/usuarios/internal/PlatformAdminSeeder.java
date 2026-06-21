package com.jetski.usuarios.internal;

import com.jetski.usuarios.domain.Usuario;
import com.jetski.usuarios.internal.repository.UsuarioGlobalRolesRepository;
import com.jetski.usuarios.internal.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Promove (idempotente) os super admins de plataforma definidos em
 * {@code platform.admin-emails} (env {@code PLATFORM_ADMIN_EMAILS}, separados por vírgula)
 * para {@code usuario_global_roles.unrestricted_access = true}.
 *
 * <p>Roda no boot da aplicação. Para cada email:
 * <ul>
 *   <li>se já existe um {@code usuario} → faz upsert do papel global (idempotente);</li>
 *   <li>se ainda não existe → loga aviso (será aplicado no próximo boot após o cadastro).</li>
 * </ul>
 *
 * <p>Mecanismo de bootstrap do 1º super admin sem painel (ver ONBOARDING_EMPRESA_SPEC §A2).
 * Não cria usuário nem usuário no Keycloak — apenas concede o acesso de plataforma a quem
 * já tem identidade. As tabelas {@code usuario}/{@code usuario_global_roles} são globais
 * (sem RLS), então roda sem contexto de tenant.
 *
 * @author Jetski Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAdminSeeder implements ApplicationRunner {

    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

    private final UsuarioRepository usuarioRepository;
    private final UsuarioGlobalRolesRepository globalRolesRepository;

    @Value("${platform.admin-emails:}")
    private String adminEmails;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmails == null || adminEmails.isBlank()) {
            log.info("PLATFORM_ADMIN_EMAILS vazio — nenhum super admin para promover");
            return;
        }

        for (String raw : adminEmails.split(",")) {
            String email = raw.trim();
            if (!email.isEmpty()) {
                promote(email);
            }
        }
    }

    private void promote(String email) {
        Optional<Usuario> usuario = usuarioRepository.findByEmail(email);
        if (usuario.isEmpty()) {
            log.warn("Super admin '{}' ainda não possui usuario — será promovido após o cadastro/próximo boot", email);
            return;
        }

        UUID usuarioId = usuario.get().getId();

        UsuarioGlobalRoles globalRoles = globalRolesRepository.findById(usuarioId)
            .orElseGet(() -> UsuarioGlobalRoles.builder()
                .usuarioId(usuarioId)
                .roles(new String[0])
                .build());

        globalRoles.setUnrestrictedAccess(true);
        globalRoles.setRoles(ensurePlatformAdminRole(globalRoles.getRoles()));

        globalRolesRepository.save(globalRoles);
        log.info("Super admin promovido (unrestricted_access=true): email={}, usuarioId={}", email, usuarioId);
    }

    /**
     * Garante que PLATFORM_ADMIN está presente, preservando papéis globais existentes.
     */
    private String[] ensurePlatformAdminRole(String[] current) {
        Set<String> roles = new LinkedHashSet<>();
        if (current != null) {
            roles.addAll(Arrays.asList(current));
        }
        roles.add(PLATFORM_ADMIN_ROLE);
        return roles.toArray(new String[0]);
    }
}
