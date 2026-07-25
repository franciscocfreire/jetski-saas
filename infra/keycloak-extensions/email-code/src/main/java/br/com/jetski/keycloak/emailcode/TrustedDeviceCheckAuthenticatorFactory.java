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

    /**
     * Clients que NUNCA honram dispositivo confiável (lista separada por vírgula).
     *
     * <p>Vazio = todos os clients honram o dispositivo confiável — é o kill switch:
     * desligar devolve o comportamento anterior sem reiniciar o Keycloak nem mexer no
     * flow. Sem config gravada, vale o default do authenticator.
     */
    public static final String CFG_CLIENTS_SEM_TRUSTED_DEVICE = "clientsSemTrustedDevice";

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
        return true;
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
        ProviderConfigProperty clientes = new ProviderConfigProperty();
        clientes.setName(CFG_CLIENTS_SEM_TRUSTED_DEVICE);
        clientes.setLabel("Clients sem dispositivo confiável");
        clientes.setType(ProviderConfigProperty.STRING_TYPE);
        clientes.setHelpText("Clients (separados por vírgula) em que o 2FA é SEMPRE "
                + "desafiado, ignorando o cookie de dispositivo confiável. Vazio = "
                + "nenhum (todos honram o dispositivo). Default: "
                + TrustedDeviceCheckAuthenticator.CLIENTS_SEM_TRUSTED_DEVICE_PADRAO);
        clientes.setDefaultValue(TrustedDeviceCheckAuthenticator.CLIENTS_SEM_TRUSTED_DEVICE_PADRAO);
        return List.of(clientes);
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
