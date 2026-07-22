import { i18nBuilder } from "keycloakify/login";
import type { ThemeName } from "../kc.gen";

/**
 * Overrides de mensagem do tema Meu Jet — o que NÃO estiver aqui cai nas
 * messages nativas do Keycloak no locale corrente (pt-BR é o default do
 * realm). Manter tom do BRAND.md: claro, direto, sem juridiquês.
 * @see https://docs.keycloakify.dev/features/i18n
 */
const { useI18n, ofTypeI18n } = i18nBuilder
    .withThemeName<ThemeName>()
    .withCustomTranslations({
        "pt-BR": {
            // Login
            loginAccountTitle: "Entrar no Meu Jet",
            usernameOrEmail: "E-mail ou CPF (só números)",
            doLogIn: "Entrar",
            doForgotPassword: "Esqueceu sua senha?",
            rememberMe: "Continuar conectado",
            "identity-provider-login-label": "ou continue com",

            // Login sem senha por código de e-mail (SPI meujet-email-code).
            // As chaves mjEc* também são resolvidas server-side (form.setError
            // do SPI) — o Keycloakify grava estas traduções nos
            // messages_*.properties do JAR do tema.
            mjEcInstruction: "Digite seu e-mail ou CPF para continuar.",
            mjEcContinue: "Continuar",
            mjEcVerifyTitle: "Digite o código",
            mjEcSentTo:
                "Se existir uma conta para {0}, enviamos um código de 6 dígitos. " +
                "Confira também a caixa de spam.",
            mjEcSentGeneric:
                "Se existir uma conta para o CPF informado, enviamos um código de " +
                "6 dígitos ao e-mail cadastrado. Confira também a caixa de spam.",
            mjEcCodeLabel: "Código de 6 dígitos",
            mjEcResend: "Reenviar código",
            mjEcResendIn: "Reenviar em {0}s",
            mjEcBack: "Usar outro e-mail ou CPF",
            mjEcInvalidCode: "Código inválido ou expirado.",
            mjEcExpired: "Código expirado — peça um novo.",
            mjEcOrPassword: "ou, se preferir",
            mjEcPasswordSubmit: "Entrar com senha",
            mjEcBadCredentials: "Dados de acesso inválidos.",
            mjEcPasswordTitle: "Digite sua senha",
            mjEcTypedAs: "Entrando como",
            mjEcNoPassword: "sem senha ou esqueceu? entre por e-mail",
            mjEcSendCode: "Receber código por e-mail",
            mjEcPasswordRequired: "Digite sua senha para entrar.",
            mjEcCodeRequired: "Digite o código de 6 dígitos.",

            // Reset de senha (vale também para quem só entra com Google:
            // o link cria uma senha para a conta)
            emailForgotTitle: "Recuperar acesso",
            emailInstruction:
                "Informe seu e-mail (ou CPF) e enviaremos um link para você redefinir a senha. " +
                "Se você entra com Google, este passo cria uma senha para a sua conta.",
            mjEnviarLink: "Enviar link",
            mjSalvarSenha: "Salvar nova senha",
            backToLogin: "« Voltar para o login",

            // Nova senha (senha temporária do balcão / reset)
            updatePasswordTitle: "Defina sua nova senha",
            passwordNew: "Nova senha",
            passwordConfirm: "Confirme a nova senha",

            // Verificação de e-mail (signup do portal)
            emailVerifyTitle: "Confirme seu e-mail",
            emailVerifyInstruction1:
                "Enviamos um e-mail com o link de confirmação para {0}.",
            emailVerifyInstruction2:
                "Não recebeu? Confira o spam ou peça um novo link.",
            doClickHere: "Clique aqui",
            emailVerifyInstruction3: "para reenviar o e-mail.",

            // First-broker-login (Google): completar cadastro / conta já existe
            loginProfileTitle: "Complete seu cadastro",
            confirmLinkIdpTitle: "Conta já existe",
            federatedIdentityConfirmLinkMessage:
                "Já existe uma conta Meu Jet com este e-mail. Quer conectá-la ao seu login do Google?",
            confirmLinkIdpContinue: "Sim, conectar as contas",
            confirmLinkIdpReviewProfile: "Revisar os dados",
            emailLinkIdpTitle: "Confirme para conectar",
            emailLinkIdp1:
                "Enviamos um e-mail com o link para confirmar a conexão da conta {1} com a sua conta Meu Jet.",
            emailLinkIdp2: "Não recebeu? Confira o spam ou",
            emailLinkIdp3: "para reenviar.",
            emailLinkIdp4: "Se já confirmou o e-mail em outra aba,",
            emailLinkIdp5: "para continuar.",

            // Logout
            logoutConfirmTitle: "Sair do Meu Jet?",
            logoutConfirmHeader: "Quer mesmo encerrar a sessão?",
            doLogout: "Sair",

            // Telas de borda
            pageExpiredTitle: "Esta página expirou",
            pageExpiredMsg1: "Para recomeçar o login,",
            pageExpiredMsg2: "Para continuar de onde parou,",
            errorTitle: "Algo deu errado",

            // Termos do rodapé de required fields
            requiredFields: "Campos obrigatórios"
        },
        en: {
            loginAccountTitle: "Sign in to Meu Jet",
            usernameOrEmail: "E-mail or CPF (digits only)",
            doLogIn: "Sign in",
            doForgotPassword: "Forgot your password?",
            rememberMe: "Stay signed in",
            "identity-provider-login-label": "or continue with",

            mjEcInstruction: "Enter your e-mail or CPF to continue.",
            mjEcContinue: "Continue",
            mjEcVerifyTitle: "Enter the code",
            mjEcSentTo:
                "If an account exists for {0}, we sent a 6-digit code. " +
                "Also check your spam folder.",
            mjEcSentGeneric:
                "If an account exists for that CPF, we sent a 6-digit code to " +
                "the registered e-mail. Also check your spam folder.",
            mjEcCodeLabel: "6-digit code",
            mjEcResend: "Resend code",
            mjEcResendIn: "Resend in {0}s",
            mjEcBack: "Use another e-mail or CPF",
            mjEcInvalidCode: "Invalid or expired code.",
            mjEcExpired: "Code expired — request a new one.",
            mjEcOrPassword: "or, if you prefer",
            mjEcPasswordSubmit: "Sign in with password",
            mjEcBadCredentials: "Invalid credentials.",
            mjEcPasswordTitle: "Enter your password",
            mjEcTypedAs: "Signing in as",
            mjEcNoPassword: "no password or forgot it? sign in by e-mail",
            mjEcSendCode: "Get code by e-mail",
            mjEcPasswordRequired: "Enter your password to sign in.",
            mjEcCodeRequired: "Enter the 6-digit code.",

            emailForgotTitle: "Recover access",
            emailInstruction:
                "Enter your e-mail (or CPF) and we will send you a link to reset your password. " +
                "If you sign in with Google, this step creates a password for your account.",
            mjEnviarLink: "Send link",
            mjSalvarSenha: "Save new password",
            backToLogin: "« Back to login",

            updatePasswordTitle: "Set your new password",
            passwordNew: "New password",
            passwordConfirm: "Confirm new password",

            emailVerifyTitle: "Confirm your e-mail",
            emailVerifyInstruction1: "We sent an e-mail with a confirmation link to {0}.",
            emailVerifyInstruction2: "Didn't get it? Check your spam folder or request a new link.",
            doClickHere: "Click here",
            emailVerifyInstruction3: "to resend the e-mail.",

            loginProfileTitle: "Complete your profile",
            confirmLinkIdpTitle: "Account already exists",
            federatedIdentityConfirmLinkMessage:
                "A Meu Jet account with this e-mail already exists. Do you want to link it to your Google sign-in?",
            confirmLinkIdpContinue: "Yes, link the accounts",
            confirmLinkIdpReviewProfile: "Review profile",
            emailLinkIdpTitle: "Confirm to link",
            emailLinkIdp1: "We sent an e-mail with a link to confirm linking the {1} account with your Meu Jet account.",
            emailLinkIdp2: "Didn't get it? Check your spam folder or",
            emailLinkIdp3: "to resend.",
            emailLinkIdp4: "If you already confirmed in another tab,",
            emailLinkIdp5: "to continue.",

            logoutConfirmTitle: "Sign out of Meu Jet?",
            logoutConfirmHeader: "Do you really want to end the session?",
            doLogout: "Sign out",

            pageExpiredTitle: "This page has expired",
            pageExpiredMsg1: "To restart the login,",
            pageExpiredMsg2: "To continue where you left off,",
            errorTitle: "Something went wrong",

            requiredFields: "Required fields"
        }
    })
    .build();

type I18n = typeof ofTypeI18n;

export { useI18n, type I18n };
