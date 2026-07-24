package br.com.jetski.keycloak.emailcode;

import org.keycloak.Config;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticator;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

/**
 * Factory da CONDIÇÃO de dispositivo confiável. id {@code mj-trusted-device-check}
 * — usado como condição REQUIRED do subflow CONDITIONAL portal-2fa
 * (infra/keycloak-realm.json e configure-keycloak-2fa.sh).
 */
public class TrustedDeviceCheckAuthenticatorFactory implements ConditionalAuthenticatorFactory {

    public static final String PROVIDER_ID = "mj-trusted-device-check";

    private static final TrustedDeviceCheckAuthenticator SINGLETON = new TrustedDeviceCheckAuthenticator();

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED
    };

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public ConditionalAuthenticator getSingleton() {
        return SINGLETON;
    }

    @Override
    public String getDisplayType() {
        return "Meu Jet — Condição 2FA (opt-in + dispositivo confiável)";
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Roda o 2FA só de quem tem fator e cujo navegador não é confiável.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
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
