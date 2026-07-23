package br.com.jetski.keycloak.emailcode;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.List;
import java.util.Set;

/**
 * Required Action que DESATIVA a verificação em duas etapas do usuário
 * removendo TODOS os fatores (TOTP/WebAuthn) de uma vez — a semântica de
 * "desativar 2FA" do perfil (portal e backoffice).
 *
 * <p>Disparada via AIA: {@code kc_action=mj-2fa-disable}. O frontend acompanha
 * com {@code max_age=0}, o que força reautenticação — e como o usuário tem 2FA,
 * essa reautenticação desafia o próprio fator (step-up: prova de posse) ANTES
 * de esta ação rodar. Sem UI própria: quando ela executa, a intenção e a posse
 * já foram confirmadas pelo login.
 */
public class Disable2faRequiredAction implements RequiredActionProvider, RequiredActionFactory {

    private static final Logger LOG = Logger.getLogger(Disable2faRequiredAction.class);

    public static final String PROVIDER_ID = "mj-2fa-disable";

    // Mesma lista de tipos de listSecondFactorCredentials (backend)
    private static final Set<String> TIPOS_SEGUNDO_FATOR =
            Set.of("otp", "webauthn", "webauthn-passwordless");

    private static final Disable2faRequiredAction SINGLETON = new Disable2faRequiredAction();

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        var user = context.getUser();
        var credManager = user.credentialManager();

        List<String> aRemover = credManager.getStoredCredentialsStream()
                .filter(c -> TIPOS_SEGUNDO_FATOR.contains(c.getType()))
                .map(c -> c.getId())
                .toList();

        for (String id : aRemover) {
            credManager.removeStoredCredentialById(id);
        }
        LOG.infof("MJ_2FA_DISABLED realm=%s user=%s fatores_removidos=%d",
                context.getRealm().getName(), user.getId(), aRemover.size());

        // ação one-shot: nada mais a fazer, segue o fluxo
        context.success();
    }

    @Override
    public InitiatedActionSupport initiatedActionSupport() {
        // Habilita o disparo via AIA (kc_action=mj-2fa-disable). Sem isto, o
        // default NOT_SUPPORTED faz o Keycloak IGNORAR silenciosamente a ação.
        return InitiatedActionSupport.SUPPORTED;
    }

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        // Nunca auto-dispara: só via AIA (kc_action) explícito do perfil.
    }

    @Override
    public void processAction(RequiredActionContext context) {
        // Sem formulário — o challenge já resolve e chama success().
        context.success();
    }

    // ---- Factory ----

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayText() {
        return "Meu Jet — Desativar verificação em duas etapas";
    }

    @Override
    public boolean isOneTimeAction() {
        return true;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
