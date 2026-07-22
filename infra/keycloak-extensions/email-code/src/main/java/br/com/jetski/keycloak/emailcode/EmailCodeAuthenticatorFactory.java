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
 * Factory do authenticator de código por e-mail (portal do cliente).
 * id "meujet-email-code" é o nome usado nas executions dos flows
 * (infra/keycloak-realm.json e infra/prod/configure-keycloak-email-code.sh).
 */
public class EmailCodeAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "meujet-email-code";

    private static final EmailCodeAuthenticator SINGLETON = new EmailCodeAuthenticator();

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
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
        // Rótulo no admin console e no "Try another way" (select-authenticator
        // resolve como chave de mensagem do tema — traduzida no i18n do meujet).
        return "Meu Jet — Código por e-mail";
    }

    @Override
    public String getReferenceCategory() {
        return "email-code";
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
        return "Login sem senha: envia um código de 6 dígitos para o e-mail do cliente "
                + "(identificado por e-mail ou CPF). Uso: portal do cliente.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }

    @Override
    public void init(Config.Scope config) {
        // sem config
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // nada
    }

    @Override
    public void close() {
        // stateless
    }
}
