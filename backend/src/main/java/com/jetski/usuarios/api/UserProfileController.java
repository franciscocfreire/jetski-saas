package com.jetski.usuarios.api;

import com.jetski.shared.exception.NotFoundException;
import com.jetski.usuarios.api.dto.UserProfileResponse;
import com.jetski.usuarios.internal.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Controller: perfil self-service do usuário STAFF autenticado (/v1/user/me).
 *
 * <p>Escopo = exclusivamente o próprio sub do JWT — sem tenant, sem papel.
 * Como /v1/user/tenants, passa FORA do fluxo tenant/OPA (TenantFilter,
 * ActionExtractor e ABACAuthorizationInterceptor pulam user:me/senha/avatar).
 *
 * <p>404 = JWT válido sem cadastro staff (cliente do portal, conta Google sem
 * vínculo) — o backoffice já intercepta antes com o NoTenantGate.
 */
@RestController
@RequestMapping("/v1/user/me")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User", description = "User account and tenant management")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final IdentityProviderMappingService identityMappingService;

    public record UpdateProfileRequest(
        @NotBlank @Size(min = 3, max = 120) String nome,
        @Size(max = 30) String telefone
    ) {}

    public record ChangePasswordRequest(
        @NotBlank String senhaAtual,
        @NotBlank @Size(min = 8, max = 128) String novaSenha
    ) {}

    @GetMapping
    @Operation(
        summary = "Perfil do usuário autenticado",
        description = "Dados pessoais + flags de gestão de senha (self-service).",
        security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<UserProfileResponse> obterPerfil(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userProfileService.obterPerfil(resolveUsuarioId(jwt)));
    }

    @PutMapping
    @Operation(
        summary = "Atualizar nome e telefone do próprio usuário",
        security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<UserProfileResponse> atualizarPerfil(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userProfileService.atualizarPerfil(
            resolveUsuarioId(jwt), request.nome(), request.telefone()));
    }

    @PutMapping("/senha")
    @Operation(
        summary = "Trocar a própria senha",
        description = "Valida a senha atual no provedor antes de redefinir. "
            + "Indisponível para contas que entram só via Google.",
        security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<Void> trocarSenha(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        userProfileService.trocarSenha(
            resolveUsuarioId(jwt), request.senhaAtual(), request.novaSenha());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/avatar", consumes = "multipart/form-data")
    @Operation(
        summary = "Enviar avatar do próprio usuário (PNG/JPEG/WebP, máx. 512 KB)",
        security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<UserProfileResponse> uploadAvatar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(userProfileService.uploadAvatar(
            resolveUsuarioId(jwt), file.getBytes(), file.getContentType()));
    }

    @DeleteMapping("/avatar")
    @Operation(
        summary = "Remover avatar do próprio usuário",
        security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<UserProfileResponse> removerAvatar(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userProfileService.removerAvatar(resolveUsuarioId(jwt)));
    }

    /**
     * sub → usuarioId, com fallback por e-mail (mesmo workaround do
     * UserTenantsController: Keycloak às vezes omite o sub no access token).
     *
     * @throws NotFoundException se o JWT não corresponde a um cadastro staff
     */
    private UUID resolveUsuarioId(Jwt jwt) {
        String providerUserId = jwt.getSubject();
        if (providerUserId == null && jwt.hasClaim("sub")) {
            providerUserId = jwt.getClaimAsString("sub");
        }
        if (providerUserId != null) {
            try {
                return identityMappingService.resolveUsuarioId("keycloak", providerUserId);
            } catch (NotFoundException e) {
                // sub sem mapeamento (ex.: vínculo criado só por e-mail) — tenta e-mail
                log.debug("Sub sem mapeamento keycloak — tentando e-mail: sub={}", providerUserId);
            }
        }
        if (jwt.hasClaim("email")) {
            return identityMappingService.resolveUsuarioIdByEmail(jwt.getClaimAsString("email"));
        }
        throw new NotFoundException("JWT sem identidade staff correspondente");
    }
}
