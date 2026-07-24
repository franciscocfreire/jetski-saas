package br.com.jetski.keycloak.emailcode;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

/**
 * Factory do enroll de dispositivo confiável. id {@code mj-trusted-device-enroll}
 * — REQUIRED após o fator, oferece "confiar neste navegador".
 */
public class TrustedDeviceEnrollAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "mj-trusted-device-enroll";

    private static final TrustedDeviceEnrollAuthenticator SINGLETON = new TrustedDeviceEnrollAuthenticator();

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED
    };

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public String getDisplayType() {
        return "Meu Jet — Lembrar dispositivo (opt-in pós-2FA)";
    }

    @Override
    public String getReferenceCategory() {
        return "trusted-device";
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
        return "Após o 2FA, oferece confiar neste navegador por 30 dias (checkbox opt-in).";
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
