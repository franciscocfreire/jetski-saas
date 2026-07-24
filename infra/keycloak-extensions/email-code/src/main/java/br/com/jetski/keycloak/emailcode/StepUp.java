package br.com.jetski.keycloak.emailcode;

import org.keycloak.models.Constants;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.Set;

/**
 * Detecção de "step-up sensível" — ações de perfil disparadas via AIA
 * (kc_action) que reduzem/mexem na segurança da conta e portanto exigem
 * re-verificação: nunca basta a sessão logada nem um navegador confiável.
 *
 * <p>O kc_action fica num CLIENT NOTE ("kc_action", {@link Constants#KC_ACTION}).
 * {@code delete_credential:<id>} chega inteiro → casa por prefixo.
 */
public final class StepUp {

    /** Cadastro de fator (o vetor mais perigoso: fator do atacante = acesso). */
    private static final Set<String> CADASTRO = Set.of("CONFIGURE_TOTP", "webauthn-register");
    /** Remoção/desativação (já mandavam max_age=0 do frontend). */
    private static final Set<String> DOWNGRADE_EXATO = Set.of("mj-2fa-disable", "UPDATE_PASSWORD");
    private static final String DELETE_PREFIX = "delete_credential";

    private StepUp() {
    }

    /** kc_action pendente (ou null). */
    public static String kcAction(AuthenticationSessionModel authSession) {
        return authSession == null ? null : authSession.getClientNote(Constants.KC_ACTION);
    }

    /** Ação sensível qualquer (cadastro OU downgrade) — força o 2FA no step-up. */
    public static boolean isSensitive(String action) {
        if (action == null) {
            return false;
        }
        return CADASTRO.contains(action)
                || DOWNGRADE_EXATO.contains(action)
                || action.startsWith(DELETE_PREFIX);
    }

    /** Cadastro de fator/dispositivo — o caso que, sem 2FA, exige OTP por e-mail. */
    public static boolean isCadastro(String action) {
        return action != null && CADASTRO.contains(action);
    }
}
