package com.jetski.shared.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Backstop de audiência: só aceita access tokens emitidos PARA um client legítimo
 * da plataforma.
 *
 * <p><b>Por quê</b> (P0 da revisão técnica de ago/2026): o realm assina tokens de
 * vários clients (grafana, jetski-password-check, o client de teste jetski-test...)
 * e o {@code JwtDecoder} validava só issuer + validade — qualquer token assinado pelo
 * realm valia na API inteira. Um client de teste esquecido habilitado (ROPC,
 * redirect {@code *}) virava primitiva de account takeover.
 *
 * <p><b>Regra</b>: o Keycloak grava em {@code azp} (authorized party) o client que
 * pediu o token — é esse o claim que identifica "para quem" o token foi emitido
 * (o {@code aud} padrão do Keycloak é {@code account}, igual para todos os clients).
 * Aceita se {@code azp} está na allowlist; sem {@code azp}, aceita só se algum
 * {@code aud} estiver na allowlist. Allowlist vazia = nega tudo (fail-closed).
 */
public class JwtAuthorizedPartyValidator implements OAuth2TokenValidator<Jwt> {

    static final String AZP = "azp";

    private final Set<String> clientsPermitidos;

    public JwtAuthorizedPartyValidator(Collection<String> clientsPermitidos) {
        this.clientsPermitidos = clientsPermitidos == null ? Set.of()
            : clientsPermitidos.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> clientsPermitidos() {
        return clientsPermitidos;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String azp = jwt.getClaimAsString(AZP);
        if (azp != null && !azp.isBlank()) {
            return clientsPermitidos.contains(azp)
                ? OAuth2TokenValidatorResult.success()
                : falha("Token emitido para um client não autorizado a acessar a API (azp=" + azp + ")");
        }
        List<String> aud = jwt.getAudience();
        if (aud != null && aud.stream().anyMatch(clientsPermitidos::contains)) {
            return OAuth2TokenValidatorResult.success();
        }
        return falha("Token sem azp/aud de um client autorizado a acessar a API");
    }

    private static OAuth2TokenValidatorResult falha(String descricao) {
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, descricao, null));
    }
}
