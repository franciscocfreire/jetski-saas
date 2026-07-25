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

    /**
     * Clients que NUNCA honram dispositivo confiável — o 2FA é sempre desafiado.
     *
     * <p>O console da plataforma opera todas as empresas; seu flow já abre mão do
     * {@code auth-cookie} para que uma sessão SSO do backoffice não valha como login. O
     * cookie de dispositivo confiável tinha exatamente o mesmo efeito por outro caminho:
     * cadastrado no backoffice/portal, ele zerava o 2FA do console (observado em
     * 25/jul — IDENTITY_PROVIDER_POST_LOGIN sem desafio). Como o post-broker é vinculado
     * ao IdP e compartilhado, a distinção precisa morar aqui, na condição, que enxerga o
     * client de origem.
     *
     * <p>É apenas o DEFAULT: a lista efetiva vem da config da execution
     * ({@link TrustedDeviceCheckAuthenticatorFactory#CFG_CLIENTS_SEM_TRUSTED_DEVICE}),
     * então dá para ligar/desligar sem reiniciar o Keycloak.
     */
    static final String CLIENTS_SEM_TRUSTED_DEVICE_PADRAO = "jetski-platform-console";

    @Override
    public boolean matchCondition(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            return false;
        }

        boolean temFator = user.credentialManager().getStoredCredentialsStream()
                .anyMatch(c -> TrustedDevice.TIPOS_2FA.contains(c.getType()));
        if (!temFator) {
            return false; // opt-in: sem fator, não desafia (OTP por e-mail cuida do step-up)
        }

        // STEP-UP: ação sensível de perfil (kc_action) SEMPRE exige o 2FA de
        // verdade — não honra o cookie de dispositivo confiável (senão o trusted
        // device seria a brecha para adicionar/remover fator sem re-verificar).
        if (StepUp.isSensitive(StepUp.kcAction(context.getAuthenticationSession()))) {
            LOG.debugf("MJ_STEPUP_FORCE_2FA realm=%s user=%s", context.getRealm().getName(), user.getId());
            return true;
        }

        // CONSOLE DA PLATAFORMA: dispositivo confiável não vale (ver constante acima).
        if (semTrustedDevice(context)) {
            LOG.debugf("MJ_FORCE_2FA_CLIENT realm=%s user=%s",
                    context.getRealm().getName(), user.getId());
            return true;
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

    /**
     * {@code true} se o client da sessão não honra dispositivo confiável.
     * Compartilhado com o enroll: quem não honra também não cadastra.
     *
     * <p>Lê a config da execution; sem config gravada usa o default. Config vazia
     * ("") desliga a exceção para TODOS os clients — é o kill switch.
     */
    static boolean semTrustedDevice(AuthenticationFlowContext context) {
        if (context == null || context.getAuthenticationSession() == null
                || context.getAuthenticationSession().getClient() == null) {
            return false;
        }
        String clientId = context.getAuthenticationSession().getClient().getClientId();

        // Existir CONFIG na execution é o que manda, não a chave estar preenchida: o
        // Keycloak DESCARTA valor vazio ao gravar, então "desligado" chega aqui como
        // chave AUSENTE. Se caíssemos no default nesse caso, o kill switch não
        // desligaria nada. Sem config alguma (realm recém-importado, antes do script)
        // vale o default — que protege o console.
        String bruto;
        if (context.getAuthenticatorConfig() != null
                && context.getAuthenticatorConfig().getConfig() != null) {
            bruto = context.getAuthenticatorConfig().getConfig()
                    .getOrDefault(TrustedDeviceCheckAuthenticatorFactory
                            .CFG_CLIENTS_SEM_TRUSTED_DEVICE, "");
        } else {
            bruto = CLIENTS_SEM_TRUSTED_DEVICE_PADRAO;
        }
        if (bruto.isBlank()) {
            return false;   // nenhum client excluído → honra dispositivo confiável
        }
        for (String c : bruto.split(",")) {
            if (c.trim().equals(clientId)) {
                return true;
            }
        }
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
