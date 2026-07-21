package com.jetski.shared.internal.keycloak;

import com.jetski.shared.security.UserProvisioningService.PasswordCheck;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Valida a senha ATUAL de um usuário via Direct Access Grant (ROPC) no client
 * confidencial dedicado {@code jetski-password-check} (direct grant on,
 * standard flow off, sem service account).
 *
 * <p>Não existe API admin "validar senha" no Keycloak — o ROPC em client
 * dedicado é o mecanismo suportado. Usado exclusivamente pela troca de senha
 * self-service do perfil staff.
 *
 * <p>Semântica do resultado:
 * <ul>
 *   <li>200 → {@link PasswordCheck#VALID}</li>
 *   <li>{@code invalid_grant} → {@link PasswordCheck#INVALID} — senha errada
 *       OU required action pendente (ex.: UPDATE_PASSWORD de convite não
 *       concluído) OU conta travada pelo brute-force do realm</li>
 *   <li>demais erros/timeout → {@link PasswordCheck#UNAVAILABLE} (client mal
 *       configurado, Keycloak fora) — nunca reportar como senha errada</li>
 * </ul>
 */
@Slf4j
@Component
class KeycloakPasswordValidator {

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;

    @Value("${keycloak.admin.target-realm}")
    private String targetRealm;

    @Value("${keycloak.password-check.client-id:jetski-password-check}")
    private String clientId;

    @Value("${keycloak.password-check.client-secret:}")
    private String clientSecret;

    // Mesma filosofia de timeout do KeycloakAdminService: falhar rápido
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    PasswordCheck validatePassword(String username, String password) {
        if (clientSecret == null || clientSecret.isBlank()) {
            log.error("keycloak.password-check.client-secret não configurado — validação de senha indisponível");
            return PasswordCheck.UNAVAILABLE;
        }
        try {
            String form = Map.of(
                    "grant_type", "password",
                    "client_id", clientId,
                    "client_secret", clientSecret,
                    "username", username,
                    "password", password,
                    "scope", "openid")
                .entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/realms/" + targetRealm + "/protocol/openid-connect/token"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

            HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return PasswordCheck.VALID;
            }
            if (response.body() != null && response.body().contains("invalid_grant")) {
                log.info("Validação de senha atual falhou (invalid_grant): username={}", username);
                return PasswordCheck.INVALID;
            }
            log.error("Validação de senha indisponível: status={}, body={}",
                response.statusCode(), abbreviate(response.body()));
            return PasswordCheck.UNAVAILABLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PasswordCheck.UNAVAILABLE;
        } catch (Exception e) {
            log.error("Erro ao validar senha no Keycloak: username={}, error={}",
                username, e.getMessage());
            return PasswordCheck.UNAVAILABLE;
        }
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }
}
