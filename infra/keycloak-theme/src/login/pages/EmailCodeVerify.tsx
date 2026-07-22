/**
 * Tela 2 do login do portal (email-code-verify.ftl, SPI meujet-email-code).
 * Identifier-first com SENHA à frente; "Receber código por e-mail" só envia
 * o e-mail quando clicado (mjAction=sendcode). mjMode="code" = painel do
 * código (reenvio com countdown, toggle de volta pra senha).
 *
 * ARQUITETURA DOS FORMS: um <form> POR AÇÃO, cada um com hidden mjAction
 * ESTÁTICO. Nada de mutar hidden no onClick nem name/value no botão submit —
 * o re-render do React (setState no onSubmit/disabled) corre contra a
 * serialização do form e já produziu dois bugs reais em navegador que os
 * testes via curl não pegam. Estático não corre.
 */
import { useEffect, useState } from "react";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";

/** 11 dígitos = CPF: exibe formatado (333.333.333-33); resto volta como veio. */
function formatIdentifier(typed: string): string {
    return /^\d{11}$/.test(typed) ? typed.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, "$1.$2.$3-$4") : typed;
}

export default function EmailCodeVerify(props: PageProps<Extract<KcContext, { pageId: "email-code-verify.ftl" }>, I18n>) {
    const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;

    const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });

    const { url, realm, mjMode, mjSocial, mjDest, mjTyped, mjCooldown } = kcContext;

    const { msg, msgStr } = i18n;

    const serverMode: "code" | "password" = mjMode === "code" ? "code" : "password";
    const [pane, setPane] = useState<"code" | "password">(serverMode);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [cooldown, setCooldown] = useState(Math.max(0, Math.trunc(mjCooldown ?? 0)));

    useEffect(() => {
        if (cooldown <= 0) {
            return;
        }
        const timer = setInterval(() => setCooldown(c => Math.max(0, c - 1)), 1000);
        return () => clearInterval(timer);
    }, [cooldown > 0]);

    const marcarEnvio = () => {
        setIsSubmitting(true);
        return true;
    };

    return (
        <Template
            kcContext={kcContext}
            i18n={i18n}
            doUseDefaultCss={doUseDefaultCss}
            classes={classes}
            headerNode={pane === "code" ? msg("mjEcVerifyTitle") : msg("mjEcPasswordTitle")}
        >
            <div id="kc-form">
                <div id="kc-form-wrapper">
                    {mjTyped !== undefined && mjTyped !== "" && (
                        <p className="mj-instruction" id="mj-typed-as">
                            {msg("mjEcTypedAs")} <strong>{formatIdentifier(mjTyped)}</strong>
                        </p>
                    )}

                    {pane === "password" ? (
                        <>
                            {/* form 1: senha */}
                            <form id="mj-form-password" action={url.loginAction} method="post" onSubmit={marcarEnvio}>
                                <input type="hidden" name="mjAction" value="password" />
                                <div className={kcClsx("kcFormGroupClass")}>
                                    <label htmlFor="password" className={kcClsx("kcLabelClass")}>
                                        {msg("password")}
                                    </label>
                                    <input
                                        tabIndex={1}
                                        id="password"
                                        className={kcClsx("kcInputClass")}
                                        name="password"
                                        type="password"
                                        autoFocus
                                        required
                                        autoComplete="current-password"
                                    />
                                </div>
                                <div id="kc-form-buttons" className={kcClsx("kcFormGroupClass")}>
                                    <button
                                        tabIndex={2}
                                        disabled={isSubmitting}
                                        className={kcClsx("kcButtonClass", "kcButtonPrimaryClass", "kcButtonBlockClass", "kcButtonLargeClass")}
                                        id="mj-login-password"
                                        type="submit"
                                    >
                                        {msgStr("doLogIn")}
                                    </button>
                                </div>
                            </form>
                            <div className="mj-form-options-row">
                                {realm.resetPasswordAllowed && (
                                    <a tabIndex={3} href={url.loginResetCredentialsUrl}>
                                        {msg("doForgotPassword")}
                                    </a>
                                )}
                                {/* form 2: trocar identificação */}
                                <form action={url.loginAction} method="post" style={{ display: "inline" }} onSubmit={marcarEnvio}>
                                    <input type="hidden" name="mjAction" value="back" />
                                    <button tabIndex={4} disabled={isSubmitting} className="mj-link-button" id="mj-back" type="submit">
                                        {msgStr("mjEcBack")}
                                    </button>
                                </form>
                            </div>

                            {/* form 3: pedir o código — o e-mail só sai aqui */}
                            <div className="mj-social-divider">{msg("mjEcNoPassword")}</div>
                            <form action={url.loginAction} method="post" onSubmit={marcarEnvio}>
                                <input type="hidden" name="mjAction" value="sendcode" />
                                <button tabIndex={5} disabled={isSubmitting} className="mj-btn-secondary" id="mj-send-code" type="submit">
                                    {msgStr("mjEcSendCode")}
                                </button>
                            </form>
                            {mjSocial !== undefined && mjSocial.length !== 0 && (
                                <div id="kc-social-providers" className="mj-social">
                                    <div className="mj-social-divider">{msg("identity-provider-login-label")}</div>
                                    <ul className="mj-social-list">
                                        {mjSocial.map(p => (
                                            <li key={p.alias}>
                                                <a id={`social-${p.alias}`} className="mj-social-link" href={p.loginUrl}>
                                                    {p.alias === "google" && <GoogleIcon />}
                                                    <span
                                                        dangerouslySetInnerHTML={{
                                                            __html: kcSanitize(p.displayName)
                                                        }}
                                                    ></span>
                                                </a>
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                            )}
                        </>
                    ) : (
                        <>
                            <p className="mj-instruction">
                                {mjDest !== undefined && mjDest !== "" ? msg("mjEcSentTo", mjDest) : msg("mjEcSentGeneric")}
                            </p>
                            {/* form 1: verificar o código */}
                            <form id="mj-form-verify" action={url.loginAction} method="post" onSubmit={marcarEnvio}>
                                <input type="hidden" name="mjAction" value="verify" />
                                <div className={kcClsx("kcFormGroupClass")}>
                                    <label htmlFor="code" className={kcClsx("kcLabelClass")}>
                                        {msg("mjEcCodeLabel")}
                                    </label>
                                    <input
                                        tabIndex={1}
                                        id="code"
                                        className={kcClsx("kcInputClass")}
                                        name="code"
                                        type="text"
                                        autoFocus
                                        required
                                        inputMode="numeric"
                                        pattern="[0-9]{6}"
                                        maxLength={6}
                                        autoComplete="one-time-code"
                                        placeholder="••••••"
                                    />
                                </div>
                                <div id="kc-form-buttons" className={kcClsx("kcFormGroupClass")}>
                                    <button
                                        tabIndex={2}
                                        disabled={isSubmitting}
                                        className={kcClsx("kcButtonClass", "kcButtonPrimaryClass", "kcButtonBlockClass", "kcButtonLargeClass")}
                                        id="mj-verify-code"
                                        type="submit"
                                    >
                                        {msgStr("doLogIn")}
                                    </button>
                                </div>
                            </form>
                            <div className="mj-form-options-row">
                                {/* form 2: reenviar */}
                                <form action={url.loginAction} method="post" style={{ display: "inline" }} onSubmit={marcarEnvio}>
                                    <input type="hidden" name="mjAction" value="resend" />
                                    <button
                                        tabIndex={3}
                                        disabled={isSubmitting || cooldown > 0}
                                        className="mj-link-button"
                                        id="mj-resend-code"
                                        type="submit"
                                    >
                                        {cooldown > 0 ? msgStr("mjEcResendIn", String(cooldown)) : msgStr("mjEcResend")}
                                    </button>
                                </form>
                                {/* form 3: trocar identificação */}
                                <form action={url.loginAction} method="post" style={{ display: "inline" }} onSubmit={marcarEnvio}>
                                    <input type="hidden" name="mjAction" value="back" />
                                    <button tabIndex={4} disabled={isSubmitting} className="mj-link-button" id="mj-back" type="submit">
                                        {msgStr("mjEcBack")}
                                    </button>
                                </form>
                            </div>

                            <div className="mj-social-divider">{msg("mjEcOrPassword")}</div>
                            <button
                                tabIndex={5}
                                className="mj-btn-secondary"
                                id="mj-show-password"
                                type="button"
                                onClick={() => setPane("password")}
                            >
                                {msgStr("mjEcPasswordSubmit")}
                            </button>
                        </>
                    )}
                </div>
            </div>
        </Template>
    );
}

/** "G" oficial do Google — mesmo SVG do Login.tsx / EmailCodeId.tsx. */
function GoogleIcon() {
    return (
        <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true">
            <path
                fill="#4285F4"
                d="M23.52 12.27c0-.85-.08-1.66-.22-2.45H12v4.63h6.46a5.53 5.53 0 0 1-2.4 3.62v3h3.88c2.27-2.09 3.58-5.17 3.58-8.8z"
            />
            <path
                fill="#34A853"
                d="M12 24c3.24 0 5.96-1.07 7.94-2.91l-3.88-3c-1.07.72-2.45 1.15-4.06 1.15-3.13 0-5.78-2.11-6.72-4.95H1.27v3.1A11.99 11.99 0 0 0 12 24z"
            />
            <path
                fill="#FBBC05"
                d="M5.28 14.29A7.2 7.2 0 0 1 4.9 12c0-.8.14-1.57.38-2.29v-3.1H1.27A11.99 11.99 0 0 0 0 12c0 1.94.46 3.77 1.27 5.39l4.01-3.1z"
            />
            <path
                fill="#EA4335"
                d="M12 4.77c1.76 0 3.34.61 4.58 1.8l3.44-3.44C17.95 1.19 15.24 0 12 0A11.99 11.99 0 0 0 1.27 6.61l4.01 3.1C6.22 6.87 8.87 4.77 12 4.77z"
            />
        </svg>
    );
}
