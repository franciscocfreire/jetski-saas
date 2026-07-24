package br.com.jetski.keycloak.emailcode;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.NewCookie;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.common.util.Time;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Após o 2FA passar, oferece "confiar neste navegador por 30 dias" (checkbox
 * opt-in). Marcado → gera token, seta o cookie {@link TrustedDevice#COOKIE}
 * (HttpOnly/Secure/Lax) e persiste uma credential {@code mj-trusted-device}
 * (guardando só o HASH do token). Nas próximas vezes o
 * {@link TrustedDeviceCheckAuthenticator} reconhece e pula o 2FA.
 *
 * <p>REQUIRED no nível do forms (fora do subflow condicional), mas se
 * auto-decide: só mostra a tela quando o usuário TEM um fator 2FA e NÃO entrou
 * por device já confiável. Sem fator ou trusted-skip → segue direto (no-op).
 */
public class TrustedDeviceEnrollAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(TrustedDeviceEnrollAuthenticator.class);

    static final String TPL = "trusted-device-enroll.ftl";
    static final String FIELD = "trustDevice";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();

        // Veio por device confiável (check pulou o 2FA) → não repergunta.
        if ("1".equals(context.getAuthenticationSession().getAuthNote(TrustedDevice.NOTE_SKIP))) {
            context.success();
            return;
        }
        // Step-up de ação de perfil (kc_action) não é login fresco: não oferece
        // "confiar neste navegador".
        if (StepUp.isSensitive(StepUp.kcAction(context.getAuthenticationSession()))) {
            context.success();
            return;
        }
        // Sem fator 2FA cadastrado → não há verificação a dispensar.
        boolean temFator = user != null && user.credentialManager()
                .getStoredCredentialsStream()
                .anyMatch(c -> TrustedDevice.TIPOS_2FA.contains(c.getType()));
        if (!temFator) {
            context.success();
            return;
        }
        context.challenge(context.form().createForm(TPL));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        boolean confiar = "on".equals(form.getFirst(FIELD)) || "true".equals(form.getFirst(FIELD));

        if (confiar) {
            try {
                cadastrarDevice(context);
            } catch (Exception e) {
                // não bloqueia o login por falha ao lembrar o device
                LOG.errorf(e, "MJ_TRUSTED_DEVICE_ENROLL_FAIL realm=%s user=%s",
                        context.getRealm().getName(), context.getUser().getId());
            }
        }
        context.success();
    }

    private void cadastrarDevice(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        long now = Time.currentTime();

        String token = SecretGenerator.getInstance().randomBytesHex(TrustedDevice.TOKEN_BYTES);
        String ua = header(context, "User-Agent");
        String label = rotuloDoUa(ua);

        CredentialModel cm = TrustedDevice.novo(CodeChallenge.hash(token), label, now, ua);
        user.credentialManager().createStoredCredential(cm);

        // cookie no host do SSO — cobre portal e backoffice (identidade única)
        NewCookie cookie = new NewCookie.Builder(TrustedDevice.COOKIE)
                .value(token)
                .path("/realms/" + context.getRealm().getName() + "/")
                .maxAge((int) TrustedDevice.TTL_SECONDS)
                .secure(true)
                .httpOnly(true)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
        context.getSession().getContext().getHttpResponse().setCookieIfAbsent(cookie);

        LOG.infof("MJ_TRUSTED_DEVICE_ENROLLED realm=%s user=%s label=%s",
                context.getRealm().getName(), user.getId(), label);
    }

    private String header(AuthenticationFlowContext context, String name) {
        var headers = context.getHttpRequest().getHttpHeaders().getRequestHeader(name);
        return headers == null || headers.isEmpty() ? "" : headers.get(0);
    }

    /** "Chrome · Windows" a partir do User-Agent (heurística simples). */
    static String rotuloDoUa(String ua) {
        if (ua == null || ua.isBlank()) {
            return "Navegador";
        }
        String nav = "Navegador";
        if (ua.contains("Edg/")) nav = "Edge";
        else if (ua.contains("OPR/") || ua.contains("Opera")) nav = "Opera";
        else if (ua.contains("Chrome/")) nav = "Chrome";
        else if (ua.contains("Firefox/")) nav = "Firefox";
        else if (ua.contains("Safari/")) nav = "Safari";

        String so = "";
        if (ua.contains("Windows")) so = "Windows";
        else if (ua.contains("Android")) so = "Android";
        else if (ua.contains("iPhone") || ua.contains("iPad")) so = "iOS";
        else if (ua.contains("Mac OS")) so = "macOS";
        else if (ua.contains("Linux")) so = "Linux";

        return so.isEmpty() ? nav : nav + " · " + so;
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        // TRUE: enroll é REQUIRED no nível do forms e sempre "pronto pra rodar"
        // (decide internamente se mostra a tela). Fora do subflow condicional,
        // não influencia nenhuma condição.
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}
