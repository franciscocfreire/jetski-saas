package br.com.jetski.keycloak.emailcode;

import jakarta.ws.rs.core.Cookie;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticator;
import org.keycloak.common.util.Time;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.Map;

/**
 * CONDIÇÃO do 2FA opt-in + skip por dispositivo confiável. Substitui o
 * conditional-user-configured: {@code matchCondition} decide se o subflow de
 * 2FA roda.
 *   - usuário SEM fator (otp/webauthn) → false (não desafia; opt-in);
 *   - COM fator + cookie {@link TrustedDevice#COOKIE} válido → marca SKIP,
 *     atualiza lastUsed e retorna false (dispositivo confiável pula o 2FA);
 *   - COM fator + sem cookie válido → true (desafia webauthn/otp).
 *
 * <p>É ConditionalAuthenticator (não um Authenticator ALTERNATIVE comum) DE
 * PROPÓSITO: a triagem de ALTERNATIVE monta a selection-list por credencial e
 * FILTRAVA um authenticator sem credencial própria — nunca rodava quando o
 * usuário tinha OTP. A condição é sempre avaliada, fora dessa triagem.
 */
public class TrustedDeviceCheckAuthenticator implements ConditionalAuthenticator {

    private static final Logger LOG = Logger.getLogger(TrustedDeviceCheckAuthenticator.class);

    @Override
    public boolean matchCondition(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            return false;
        }

        boolean temFator = user.credentialManager().getStoredCredentialsStream()
                .anyMatch(c -> TrustedDevice.TIPOS_2FA.contains(c.getType()));
        if (!temFator) {
            return false; // opt-in: sem fator, não desafia
        }

        String token = lerCookie(context);
        if (token == null || token.isBlank()) {
            return true; // tem fator, sem cookie → desafia
        }

        String tokenHash = CodeChallenge.hash(token);
        long now = Time.currentTime();
        CredentialModel match = user.credentialManager()
                .getStoredCredentialsByTypeStream(TrustedDevice.TYPE)
                .filter(c -> tokenHash.equals(TrustedDevice.tokenHash(c)))
                .filter(c -> TrustedDevice.expiresAt(c) > now)
                .findFirst()
                .orElse(null);

        if (match == null) {
            return true; // cookie não casa (revogado/expirado) → desafia
        }

        // device confiável: atualiza lastUsed (best-effort), marca SKIP, pula
        try {
            TrustedDevice.touch(match, now);
            user.credentialManager().updateStoredCredential(match);
        } catch (Exception e) {
            LOG.debugf("trusted-device: falha ao atualizar lastUsedAt: %s", e.getMessage());
        }
        context.getAuthenticationSession().setAuthNote(TrustedDevice.NOTE_SKIP, "1");
        LOG.debugf("MJ_TRUSTED_DEVICE_SKIP realm=%s user=%s", context.getRealm().getName(), user.getId());
        return false;
    }

    private String lerCookie(AuthenticationFlowContext context) {
        Map<String, Cookie> cookies = context.getHttpRequest().getHttpHeaders().getCookies();
        if (cookies == null) {
            return null;
        }
        Cookie c = cookies.get(TrustedDevice.COOKIE);
        return c == null ? null : c.getValue();
    }

    // ConditionalAuthenticator traz defaults para authenticate/configuredFor.
    @Override
    public void action(AuthenticationFlowContext context) {
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}
