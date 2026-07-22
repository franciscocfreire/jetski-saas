package br.com.jetski.keycloak.emailcode;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.common.util.Time;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.Constants;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Login sem senha do portal do cliente: código de 6 dígitos por e-mail,
 * identificando-se por e-mail OU CPF (username dos clientes = CPF, definido
 * pelo backend em KeycloakAdminService.definirCpf).
 *
 * Duas telas num authenticator só (dispatch pelo campo oculto mjAction):
 *   email-code-id.ftl     → pede e-mail/CPF (mjAction=request)
 *   email-code-verify.ftl → pede o código  (mjAction=verify|resend|back)
 *
 * Anti-enumeração: TODO caminho do "request" (conta inexistente, desabilitada,
 * sem e-mail, brute-force lock, falha de SMTP) avança para a tela de código com
 * a mesma resposta neutra — nunca revelamos se a conta existe.
 * Sem auto-criação de usuário (cadastro é no portal/balcão — nunca JIT cego).
 */
public class EmailCodeAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(EmailCodeAuthenticator.class);

    // Templates renderizados pelo tema de login "meujet" (páginas Keycloakify)
    static final String TPL_ID = "email-code-id.ftl";
    static final String TPL_VERIFY = "email-code-verify.ftl";
    // Template de e-mail resolvido pelo email theme "meujet-email" (dentro deste JAR)
    static final String TPL_EMAIL = "email-code.ftl";

    // Auth notes (estado de UI da authentication session — sobrevive reload).
    // O DESAFIO em si (hash/expiração/tentativas) vive no SingleUseObjectProvider
    // chaveado por usuário: sobrevive a restart do fluxo (cliente reabre o login
    // dentro do cooldown e o código do e-mail continua valendo), é single-use
    // entre sessões e o contador de tentativas é global (sem grinding paralelo).
    static final String NOTE_STATE = "MJ_EC_STATE";
    static final String NOTE_USER_ID = "MJ_EC_USER_ID";
    static final String NOTE_DEST = "MJ_EC_DEST";
    static final String NOTE_TYPED = "MJ_EC_TYPED";
    static final String NOTE_CD_UNTIL = "MJ_EC_CD_UNTIL";

    // Notas do desafio no SingleUseObjectProvider
    static final String SUO_HASH = "hash";
    static final String SUO_EXPIRES = "expires";
    static final String SUO_TRIES = "tries";

    // Estados da tela 2: CHOOSE = identificado, senha em primeiro plano (nenhum
    // e-mail enviado ainda — só sai quando o cliente pedir o código);
    // CODE_SENT = código solicitado, tela do código em primeiro plano.
    static final String STATE_CHOOSE = "CHOOSE";
    static final String STATE_CODE_SENT = "CODE_SENT";

    // Campos do form
    static final String FIELD_ACTION = "mjAction";
    static final String FIELD_IDENTIFIER = "identifier";
    static final String FIELD_CODE = "code";
    static final String FIELD_PASSWORD = "password";

    // Cooldown de envio é cross-session (senão reiniciar o login burla o limite
    // e vira mail-bomb) — vive no SingleUseObjectProvider, chaveado por usuário.
    private static String cooldownKey(String userId) {
        return "mj-email-code-cd:" + userId;
    }

    private static String challengeKey(String userId) {
        return "mj-email-code:" + userId;
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        String state = authSession.getAuthNote(NOTE_STATE);
        if (STATE_CODE_SENT.equals(state)) {
            context.challenge(verifyScreen(context, "code", null));
            return;
        }
        if (STATE_CHOOSE.equals(state)) {
            context.challenge(verifyScreen(context, "password", null));
            return;
        }
        context.challenge(idScreen(context, null));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        String action = form.getFirst(FIELD_ACTION);
        if (action == null || action.isBlank()) {
            // Fallback defensivo: se o mjAction se perder (ex.: botão submit
            // desabilitado pelo React antes da serialização do form), inferimos
            // pelo campo PREENCHIDO (código e senha coexistem no mesmo form —
            // presença não basta, tem que ter valor).
            if (naoVazio(form.getFirst(FIELD_CODE))) {
                action = "verify";
            } else if (naoVazio(form.getFirst(FIELD_PASSWORD))) {
                action = "password";
            } else if (naoVazio(form.getFirst(FIELD_IDENTIFIER))) {
                action = "request";
            } else {
                action = "";
            }
        }
        switch (action) {
            case "request" -> handleRequest(context, form.getFirst(FIELD_IDENTIFIER));
            case "verify" -> handleVerify(context, form.getFirst(FIELD_CODE));
            case "password" -> handlePassword(context, form.getFirst(FIELD_PASSWORD));
            // sendcode = primeiro envio (tela de senha) e resend = reenvio (tela
            // do código) — mesma lógica, mesmo cooldown
            case "sendcode", "resend" -> handleSendCode(context);
            case "back" -> handleBack(context);
            default -> context.challenge(idScreen(context, null));
        }
    }

    // ------------------------------------------------------------------
    // Tela 1 → identifica e vai para a tela 2 (senha em primeiro plano).
    // NENHUM e-mail sai aqui — só quando o cliente pedir o código (sendcode).
    // ------------------------------------------------------------------

    private void handleRequest(AuthenticationFlowContext context, String rawIdentifier) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        CodeChallenge.Identifier id = CodeChallenge.classify(rawIdentifier);

        UserModel user = lookupUser(context, id);

        // O que a tela de código exibe: e-mail digitado (o cliente já o conhece)
        // ou texto genérico "e-mail cadastrado" quando entrou por CPF — a MESMA
        // regra vale para conta existente e inexistente (sem oráculo).
        String dest = id.kind() == CodeChallenge.Kind.EMAIL ? id.value() : "";
        authSession.setAuthNote(NOTE_DEST, dest);
        // identificador como digitado (normalizado) — exibido no "Entrando como"
        authSession.setAuthNote(NOTE_TYPED, id.value());

        // Vincula a sessão ao usuário para os caminhos de senha/código. Não é
        // gate de e-mail/bruteforce aqui: cada caminho valida o que lhe cabe,
        // sempre com resposta neutra.
        if (user != null && user.isEnabled()) {
            authSession.setAuthNote(NOTE_USER_ID, user.getId());
        }

        authSession.setAuthNote(NOTE_STATE, STATE_CHOOSE);
        context.challenge(verifyScreen(context, "password", null));
    }

    /**
     * Cliente pediu o código (tela de senha "entrar sem senha" ou reenvio na
     * tela do código). Resposta neutra sempre — conta inexistente também "vai".
     */
    private void handleSendCode(AuthenticationFlowContext context) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        String userId = authSession.getAuthNote(NOTE_USER_ID);
        if (userId != null) {
            UserModel user = context.getSession().users().getUserById(context.getRealm(), userId);
            if (user != null && eligible(context, user)) {
                sendCodeRespectingCooldown(context, user);
            }
        }
        authSession.setAuthNote(NOTE_STATE, STATE_CODE_SENT);
        context.challenge(verifyScreen(context, "code", null));
    }

    private UserModel lookupUser(AuthenticationFlowContext context, CodeChallenge.Identifier id) {
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();
        return switch (id.kind()) {
            case CPF -> session.users().getUserByUsername(realm, id.value());
            case EMAIL -> session.users().getUserByEmail(realm, id.value());
            case UNKNOWN -> {
                if (id.value().isEmpty()) {
                    yield null;
                }
                UserModel byUsername = session.users().getUserByUsername(realm, id.value());
                yield byUsername != null ? byUsername : session.users().getUserByEmail(realm, id.value());
            }
        };
    }

    private boolean eligible(AuthenticationFlowContext context, UserModel user) {
        // SEM gate de brute force aqui, de propósito: lock por senha não pode
        // estrangular o caminho de recuperação — o código vai pra caixa do
        // DONO (atacante não o lê) e é como o dono legítimo sai do lock. O
        // mail-bomb é contido pelo cooldown de 60s e o código tem as próprias
        // 5 tentativas. Senha continua bloqueada pelo lock (handlePassword).
        return user.isEnabled() && user.getEmail() != null && !user.getEmail().isBlank();
    }

    /** Gera + envia o código, respeitando o cooldown cross-session de 60 s por usuário. */
    private void sendCodeRespectingCooldown(AuthenticationFlowContext context, UserModel user) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        SingleUseObjectProvider suo = context.getSession().singleUseObjects();
        long now = Time.currentTime();

        String cdKey = cooldownKey(user.getId());
        Map<String, String> cd = suo.get(cdKey);
        if (cd != null) {
            // Dentro do cooldown: não reenvia. Se há um desafio vivo (o cliente
            // reiniciou o login), liga esta sessão a ele — o código já enviado
            // por e-mail continua valendo.
            if (suo.get(challengeKey(user.getId())) != null) {
                authSession.setAuthNote(NOTE_USER_ID, user.getId());
            }
            authSession.setAuthNote(NOTE_CD_UNTIL, cd.getOrDefault("until", String.valueOf(now)));
            return;
        }

        String code = CodeChallenge.generateCode();
        long until = now + CodeChallenge.RESEND_COOLDOWN_SECONDS;
        try {
            // map MUTÁVEL: o FreeMarkerEmailTemplateProvider dá put() nos
            // atributos (Map.of() imutável = UnsupportedOperationException)
            Map<String, Object> attrs = new java.util.HashMap<>();
            attrs.put("code", code);
            attrs.put("ttlMinutes", String.valueOf(CodeChallenge.TTL_MINUTES));
            context.getSession().getProvider(EmailTemplateProvider.class)
                    .setRealm(context.getRealm())
                    .setUser(user)
                    .send("mjEmailCodeSubject", TPL_EMAIL, attrs);
        } catch (EmailException e) {
            // Marcador estável para alerta no Grafana/Loki. Resposta segue neutra:
            // um erro distinto aqui seria, ele próprio, um oráculo de enumeração.
            LOG.errorf(e, "MJ_EMAIL_CODE_SMTP_FAIL realm=%s user=%s", context.getRealm().getName(), user.getId());
            return;
        }

        // novo desafio substitui o anterior (reenvio invalida o código velho)
        String chKey = challengeKey(user.getId());
        suo.remove(chKey);
        suo.put(chKey, CodeChallenge.TTL_SECONDS, Map.of(
                SUO_HASH, CodeChallenge.hash(code),
                SUO_EXPIRES, String.valueOf(now + CodeChallenge.TTL_SECONDS),
                SUO_TRIES, "0"));
        suo.put(cdKey, CodeChallenge.RESEND_COOLDOWN_SECONDS, Map.of("until", String.valueOf(until)));
        authSession.setAuthNote(NOTE_USER_ID, user.getId());
        authSession.setAuthNote(NOTE_CD_UNTIL, String.valueOf(until));
    }

    // ------------------------------------------------------------------
    // Tela 2 → verifica / reenvia / volta
    // ------------------------------------------------------------------

    private void handleVerify(AuthenticationFlowContext context, String typedCode) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        SingleUseObjectProvider suo = context.getSession().singleUseObjects();
        long now = Time.currentTime();

        // Código em branco não é tentativa: re-renderiza sem queimar tentativa
        // nem contar no brute force
        if (typedCode == null || typedCode.isBlank()) {
            context.challenge(verifyScreen(context, "code", "mjEcCodeRequired"));
            return;
        }

        String userId = authSession.getAuthNote(NOTE_USER_ID);
        Map<String, String> challenge = userId == null ? null : suo.get(challengeKey(userId));

        // Conta inexistente / nada enviado / desafio consumido: falha genérica
        // em tempo constante
        if (userId == null || challenge == null) {
            CodeChallenge.verify(null, typedCode);
            context.challenge(verifyScreen(context, "code", "mjEcInvalidCode"));
            return;
        }

        UserModel user = context.getSession().users().getUserById(context.getRealm(), userId);
        if (user == null || !user.isEnabled()) {
            CodeChallenge.verify(null, typedCode);
            context.challenge(verifyScreen(context, "code", "mjEcInvalidCode"));
            return;
        }

        if (CodeChallenge.isExpired(Long.parseLong(challenge.get(SUO_EXPIRES)), now)) {
            suo.remove(challengeKey(userId));
            authSession.setAuthNote(NOTE_STATE, STATE_CHOOSE);
            context.challenge(verifyScreen(context, "password", "mjEcExpired"));
            return;
        }

        if (!CodeChallenge.verify(challenge.get(SUO_HASH), typedCode)) {
            int tries = Integer.parseInt(challenge.getOrDefault(SUO_TRIES, "0")) + 1;
            context.getEvent().user(user).error(Errors.INVALID_USER_CREDENTIALS);
            registrarFalhaBruteForce(context, user);
            if (tries >= CodeChallenge.MAX_ATTEMPTS) {
                // código queimado: continua na tela 2 (senha à frente), peça outro
                suo.remove(challengeKey(userId));
                context.getAuthenticationSession().setAuthNote(NOTE_STATE, STATE_CHOOSE);
                context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                        verifyScreen(context, "password", "mjEcExpired"));
                return;
            }
            Map<String, String> updated = new java.util.HashMap<>(challenge);
            updated.put(SUO_TRIES, String.valueOf(tries));
            suo.replace(challengeKey(userId), updated);
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    verifyScreen(context, "code", "mjEcInvalidCode"));
            return;
        }

        // Sucesso: código correto prova posse da caixa de e-mail. remove() do
        // desafio garante uso único mesmo com outra sessão em paralelo.
        suo.remove(challengeKey(userId));
        user.setEmailVerified(true);
        // SÓ para CLIENTE: o balcão cria cliente com senha temporária +
        // UPDATE_PASSWORD; no login sem senha removemos a pendência (quem
        // quiser senha usa o "Recuperar acesso"). STAFF (backoffice usa o
        // mesmo flow) mantém a troca obrigatória do convite.
        if (user.getRealmRoleMappingsStream().anyMatch(r -> "CLIENTE".equals(r.getName()))) {
            user.removeRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
        }
        concluirLogin(context, user);
    }

    /**
     * Caminho alternativo da tela 2: entrar com a senha (mjAction=password).
     * Anti-enumeração: conta inexistente, sem senha (só-Google) ou senha errada
     * respondem com a MESMA mensagem genérica. Senha não prova posse do e-mail:
     * não marca emailVerified nem remove UPDATE_PASSWORD (cliente de balcão com
     * senha temporária cai na troca obrigatória — comportamento padrão).
     */
    private void handlePassword(AuthenticationFlowContext context, String password) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();

        // Senha em branco não é tentativa de credencial: re-renderiza sem
        // incrementar brute force (Enter no campo vazio estava TRAVANDO conta)
        if (password == null || password.isBlank()) {
            context.challenge(verifyScreen(context, "password", "mjEcPasswordRequired"));
            return;
        }

        String userId = authSession.getAuthNote(NOTE_USER_ID);
        UserModel user = userId == null ? null
                : context.getSession().users().getUserById(context.getRealm(), userId);

        boolean valido = user != null
                && user.isEnabled()
                && !bloqueadoPorBruteForce(context, user)
                && user.credentialManager().isValid(UserCredentialModel.password(password));

        if (!valido) {
            if (user != null) {
                context.getEvent().user(user).error(Errors.INVALID_USER_CREDENTIALS);
                registrarFalhaBruteForce(context, user);
            }
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    verifyScreen(context, "password", "mjEcBadCredentials"));
            return;
        }

        // higiene: código pendente morre junto com o desafio
        context.getSession().singleUseObjects().remove(challengeKey(userId));
        concluirLogin(context, user);
    }

    private void concluirLogin(AuthenticationFlowContext context, UserModel user) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        if (context.getRealm().isBruteForceProtected()) {
            context.getProtector().successfulLogin(context.getRealm(), user,
                    context.getConnection(), context.getUriInfo());
        }
        clearChallenge(authSession);
        authSession.removeAuthNote(NOTE_STATE);
        authSession.removeAuthNote(NOTE_DEST);
        context.setUser(user);
        context.success();
    }

    /**
     * Incrementa o contador de brute force do realm. Necessário explicitamente:
     * failureChallenge + event.error NÃO incrementam (verificado empiricamente
     * no 26.4 via /attack-detection).
     */
    private void registrarFalhaBruteForce(AuthenticationFlowContext context, UserModel user) {
        if (context.getRealm().isBruteForceProtected()) {
            context.getProtector().failedLogin(context.getRealm(), user,
                    context.getConnection(), context.getUriInfo());
        }
    }

    private boolean bloqueadoPorBruteForce(AuthenticationFlowContext context, UserModel user) {
        if (!context.getRealm().isBruteForceProtected()) {
            return false;
        }
        return context.getProtector().isPermanentlyLockedOut(context.getSession(), context.getRealm(), user)
                || context.getProtector().isTemporarilyDisabled(context.getSession(), context.getRealm(), user);
    }

    private void handleBack(AuthenticationFlowContext context) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        clearChallenge(authSession);
        authSession.removeAuthNote(NOTE_STATE);
        authSession.removeAuthNote(NOTE_DEST);
        authSession.removeAuthNote(NOTE_TYPED);
        context.challenge(idScreen(context, null));
    }

    private static boolean naoVazio(String v) {
        return v != null && !v.isBlank();
    }

    private void clearChallenge(AuthenticationSessionModel authSession) {
        authSession.removeAuthNote(NOTE_USER_ID);
    }

    // ------------------------------------------------------------------
    // Renderização
    // ------------------------------------------------------------------

    private Response idScreen(AuthenticationFlowContext context, String errorKey) {
        LoginFormsProvider form = context.form();
        form.setAttribute("mjSocial", socialProviders(context));
        if (errorKey != null) {
            form.setError(errorKey);
        }
        return form.createForm(TPL_ID);
    }

    /** @param mode "password" (senha à frente, código sob demanda) ou "code" */
    private Response verifyScreen(AuthenticationFlowContext context, String mode, String errorKey) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        long now = Time.currentTime();
        long cooldown = 0;
        String cdUntil = authSession.getAuthNote(NOTE_CD_UNTIL);
        if (cdUntil != null) {
            cooldown = Math.max(0, Long.parseLong(cdUntil) - now);
        }
        String dest = authSession.getAuthNote(NOTE_DEST);
        String typed = authSession.getAuthNote(NOTE_TYPED);

        LoginFormsProvider form = context.form();
        form.setAttribute("mjMode", mode);
        form.setAttribute("mjDest", dest == null ? "" : dest);
        form.setAttribute("mjTyped", typed == null ? "" : typed);
        form.setAttribute("mjCooldown", cooldown);
        // Google também na tela 2: quem entra com Google não tem senha e não
        // pode ficar preso no painel de senha (a dica estática + este botão
        // resolvem sem vazar se a conta existe/tem senha)
        form.setAttribute("mjSocial", socialProviders(context));
        if (errorKey != null) {
            form.setError(errorKey);
        }
        return form.createForm(TPL_VERIFY);
    }

    /**
     * Botões sociais na tela 1. createForm() de template custom não popula o
     * bean "social" — montamos a URL de broker na mão (mesmo formato que o
     * IdentityProviderBean gera para o login.ftl).
     */
    private List<Map<String, String>> socialProviders(AuthenticationFlowContext context) {
        List<Map<String, String>> providers = new ArrayList<>();
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();
        AuthenticationSessionModel authSession = context.getAuthenticationSession();

        session.identityProviders().getAllStream().forEach(idp -> {
            if (!idp.isEnabled() || idp.isLinkOnly() || idp.isHideOnLogin()) {
                return;
            }
            URI loginUrl = UriBuilder.fromUri(context.getUriInfo().getBaseUri())
                    .path("realms").path(realm.getName())
                    .path("broker").path(idp.getAlias()).path("login")
                    .queryParam(Constants.CLIENT_ID, authSession.getClient().getClientId())
                    .queryParam(Constants.TAB_ID, authSession.getTabId())
                    .queryParam("session_code", context.generateAccessCode())
                    .build();
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("alias", idp.getAlias());
            entry.put("displayName", displayName(idp));
            entry.put("loginUrl", loginUrl.toString());
            providers.add(entry);
        });
        return providers;
    }

    private String displayName(IdentityProviderModel idp) {
        if (idp.getDisplayName() != null && !idp.getDisplayName().isBlank()) {
            return idp.getDisplayName();
        }
        String alias = idp.getAlias();
        return Character.toUpperCase(alias.charAt(0)) + alias.substring(1);
    }

    // ------------------------------------------------------------------
    // Contratos do SPI
    // ------------------------------------------------------------------

    @Override
    public boolean requiresUser() {
        // identity-first: este authenticator estabelece o usuário sozinho
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // nenhuma
    }

    @Override
    public void close() {
        // stateless
    }
}
