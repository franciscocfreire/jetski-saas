package com.jetski.usuarios.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.InternalServerException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.UserProvisioningService;
import com.jetski.shared.security.UserProvisioningService.PasswordCheck;
import com.jetski.shared.storage.StorageService;
import com.jetski.usuarios.api.dto.UserProfileResponse;
import com.jetski.usuarios.domain.Usuario;
import com.jetski.usuarios.domain.UsuarioIdentityProvider;
import com.jetski.usuarios.internal.repository.UsuarioIdentityProviderRepository;
import com.jetski.usuarios.internal.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.Set;
import java.util.UUID;

/**
 * Service: perfil self-service do usuário STAFF (backoffice /dashboard/perfil).
 *
 * <p>Opera exclusivamente sobre o próprio usuário do JWT (sem tenant, sem papel
 * — endpoints /v1/user/me passam fora do OPA, como /v1/user/tenants). A tabela
 * usuario é GLOBAL (sem tenant_id/RLS); o avatar vive no storage sob o prefixo
 * global {@code usuarios/{usuarioId}/...} — fora do export/expurgo por tenant,
 * by design (é dado da pessoa, não da loja).
 *
 * <p>Sincronizações com o Keycloak (nome) são best-effort: o banco é a fonte
 * de exibição; falha no provedor loga warn e não desfaz a gravação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private static final long AVATAR_MAX_BYTES = 512 * 1024;
    private static final Set<String> AVATAR_CONTENT_TYPES =
        Set.of("image/png", "image/jpeg", "image/webp");
    private static final int SENHA_MIN_LENGTH = 8;

    private final UsuarioRepository usuarioRepository;
    private final UsuarioIdentityProviderRepository identityProviderRepository;
    private final UserProvisioningService userProvisioningService;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public UserProfileResponse obterPerfil(UUID usuarioId) {
        Usuario usuario = buscarUsuario(usuarioId);
        return toResponse(usuario, resolveKeycloakUserId(usuarioId));
    }

    @Transactional
    public UserProfileResponse atualizarPerfil(UUID usuarioId, String nome, String telefone) {
        Usuario usuario = buscarUsuario(usuarioId);

        String nomeNormalizado = nome == null ? "" : nome.strip();
        if (nomeNormalizado.length() < 3 || nomeNormalizado.length() > 120) {
            throw new BusinessException("Nome deve ter entre 3 e 120 caracteres");
        }
        String telefoneNormalizado = normalizarTelefone(telefone);

        usuario.setNome(nomeNormalizado);
        usuario.setTelefone(telefoneNormalizado);
        usuarioRepository.save(usuario);

        // Best-effort: espelha o nome no Keycloak (first/last no token futuro);
        // o banco é a fonte de exibição — falha não desfaz a gravação
        String keycloakUserId = resolveKeycloakUserId(usuarioId);
        if (keycloakUserId != null) {
            boolean ok = userProvisioningService.updateUserName(keycloakUserId, nomeNormalizado);
            if (!ok) {
                log.warn("Nome atualizado no banco mas não no Keycloak (best-effort): usuarioId={}", usuarioId);
            }
        }

        log.info("Perfil atualizado: usuarioId={}", usuarioId);
        return toResponse(usuario, keycloakUserId);
    }

    /**
     * Troca de senha self-service: exige credencial de senha própria e valida a
     * senha ATUAL via direct grant antes do reset. Sem retry — senha errada
     * conta para o brute-force do realm.
     */
    @Transactional(readOnly = true)
    public void trocarSenha(UUID usuarioId, String senhaAtual, String novaSenha) {
        Usuario usuario = buscarUsuario(usuarioId);

        if (novaSenha == null || novaSenha.length() < SENHA_MIN_LENGTH) {
            throw new BusinessException("Nova senha deve ter no mínimo " + SENHA_MIN_LENGTH + " caracteres");
        }

        String keycloakUserId = resolveKeycloakUserId(usuarioId);
        if (keycloakUserId == null) {
            throw new BusinessException("Conta sem identidade vinculada ao provedor — troca de senha indisponível");
        }
        if (!userProvisioningService.hasPasswordCredential(keycloakUserId)) {
            throw new BusinessException(
                "Sua conta entra com login Google — a senha é gerenciada pela sua Conta Google");
        }

        PasswordCheck check = userProvisioningService.validatePassword(usuario.getEmail(), senhaAtual);
        switch (check) {
            case INVALID -> throw new BusinessException("Senha atual incorreta");
            case UNAVAILABLE -> throw new InternalServerException(
                "Não foi possível validar a senha no momento — tente novamente em instantes");
            case VALID -> { /* segue */ }
        }

        if (!userProvisioningService.resetPassword(keycloakUserId, novaSenha)) {
            throw new InternalServerException("Não foi possível atualizar a senha — tente novamente em instantes");
        }
        log.info("Senha alterada via perfil self-service: usuarioId={}", usuarioId);
    }

    @Transactional
    public UserProfileResponse uploadAvatar(UUID usuarioId, byte[] content, String contentType) {
        Usuario usuario = buscarUsuario(usuarioId);
        if (content == null || content.length == 0) {
            throw new BusinessException("Arquivo de avatar vazio");
        }
        if (content.length > AVATAR_MAX_BYTES) {
            throw new BusinessException("Avatar excede o limite de 512 KB");
        }
        if (contentType == null || !AVATAR_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException("Formato de avatar não suportado (use PNG, JPEG ou WebP)");
        }
        String ext = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> "png";
        };
        // Prefixo GLOBAL (não tenant): "usuarios" não é UUID, não colide com o
        // export por tenant que lista o prefixo {tenantId}/
        String key = "usuarios/" + usuarioId + "/avatar." + ext;
        storageService.putObject(key, content, contentType);

        String keyAntiga = usuario.getAvatarKey();
        if (keyAntiga != null && !keyAntiga.equals(key)) {
            try {
                storageService.deleteFile(keyAntiga);
            } catch (Exception e) {
                log.warn("Falha ao remover avatar antigo (key={}): {}", keyAntiga, e.getMessage());
            }
        }
        usuario.setAvatarKey(key);
        usuario.setAvatarContentType(contentType);
        usuarioRepository.save(usuario);
        log.info("Avatar atualizado: usuarioId={} ({} bytes, {})", usuarioId, content.length, contentType);
        return toResponse(usuario, resolveKeycloakUserId(usuarioId));
    }

    @Transactional
    public UserProfileResponse removerAvatar(UUID usuarioId) {
        Usuario usuario = buscarUsuario(usuarioId);
        if (usuario.getAvatarKey() != null) {
            try {
                storageService.deleteFile(usuario.getAvatarKey());
            } catch (Exception e) {
                log.warn("Falha ao remover avatar do storage (key={}): {}",
                    usuario.getAvatarKey(), e.getMessage());
            }
            usuario.setAvatarKey(null);
            usuario.setAvatarContentType(null);
            usuarioRepository.save(usuario);
            log.info("Avatar removido: usuarioId={}", usuarioId);
        }
        return toResponse(usuario, resolveKeycloakUserId(usuarioId));
    }

    private Usuario buscarUsuario(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + usuarioId));
    }

    /** provider_user_id (sub) do vínculo keycloak; null se não houver. */
    private String resolveKeycloakUserId(UUID usuarioId) {
        return identityProviderRepository.findByUsuarioIdAndProvider(usuarioId, "keycloak")
            .map(UsuarioIdentityProvider::getProviderUserId)
            .orElse(null);
    }

    private UserProfileResponse toResponse(Usuario usuario, String keycloakUserId) {
        // Flags fail-closed: Keycloak inacessível ⇒ senhaGerenciavel=false e
        // idpFederado=false — a UI mostra "indisponível no momento", não "conta Google"
        boolean senhaGerenciavel = keycloakUserId != null
            && userProvisioningService.hasPasswordCredential(keycloakUserId);
        boolean idpFederado = keycloakUserId != null
            && userProvisioningService.findFederatedIdentity(keycloakUserId, "google") != null;
        return UserProfileResponse.builder()
            .id(usuario.getId())
            .nome(usuario.getNome())
            .email(usuario.getEmail())
            .emailVerified(usuario.getEmailVerified())
            .telefone(usuario.getTelefone())
            .avatarDataUrl(avatarDataUrl(usuario))
            .senhaGerenciavel(senhaGerenciavel)
            .idpFederado(idpFederado)
            .build();
    }

    /** Avatar como data URL base64 (padrão do logo de branding); null se ausente/inacessível. */
    private String avatarDataUrl(Usuario usuario) {
        if (usuario.getAvatarKey() == null) {
            return null;
        }
        try {
            byte[] bytes = storageService.getObject(usuario.getAvatarKey());
            String ct = usuario.getAvatarContentType() != null
                ? usuario.getAvatarContentType() : "image/png";
            return "data:" + ct + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("Avatar inacessível no storage (key={}): {}", usuario.getAvatarKey(), e.getMessage());
            return null;
        }
    }

    private String normalizarTelefone(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return null;
        }
        String v = telefone.strip();
        if (v.length() > 30) {
            throw new BusinessException("Telefone excede o limite de 30 caracteres");
        }
        if (!v.matches("[0-9()+\\- ]+")) {
            throw new BusinessException("Telefone contém caracteres inválidos");
        }
        return v;
    }
}
