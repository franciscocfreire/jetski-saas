/**
 * Tela 1 do login do portal (email-code-id.ftl, SPI meujet-email-code) —
 * padrão identifier-first: só e-mail/CPF + Continuar (+ Google). A escolha
 * entre código e senha acontece na tela 2, sem revelar se a conta existe.
 */
import { useState } from "react";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";

export default function EmailCodeId(props: PageProps<Extract<KcContext, { pageId: "email-code-id.ftl" }>, I18n>) {
    const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;

    const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });

    const { url, mjSocial } = kcContext;

    const { msg, msgStr } = i18n;

    const [isSubmitting, setIsSubmitting] = useState(false);

    return (
        <Template
            kcContext={kcContext}
            i18n={i18n}
            doUseDefaultCss={doUseDefaultCss}
            classes={classes}
            headerNode={msg("loginAccountTitle")}
            socialProvidersNode={
                <>
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
            }
        >
            <div id="kc-form">
                <div id="kc-form-wrapper">
                    <p className="mj-instruction">{msg("mjEcInstruction")}</p>
                    <form
                        id="mj-email-code-request-form"
                        action={url.loginAction}
                        method="post"
                        onSubmit={() => {
                            setIsSubmitting(true);
                            return true;
                        }}
                    >
                        {/* A ação vai em hidden input: o setIsSubmitting desabilita o
                            botão ANTES de o browser serializar o form, e botão
                            disabled não envia name/value. */}
                        <input type="hidden" name="mjAction" value="request" />
                        <div className={kcClsx("kcFormGroupClass")}>
                            <label htmlFor="identifier" className={kcClsx("kcLabelClass")}>
                                {msg("usernameOrEmail")}
                            </label>
                            <input
                                tabIndex={1}
                                id="identifier"
                                className={kcClsx("kcInputClass")}
                                name="identifier"
                                type="text"
                                autoFocus
                                autoComplete="username"
                                inputMode="email"
                            />
                        </div>
                        <div id="kc-form-buttons" className={kcClsx("kcFormGroupClass")}>
                            <button
                                tabIndex={2}
                                disabled={isSubmitting}
                                className={kcClsx("kcButtonClass", "kcButtonPrimaryClass", "kcButtonBlockClass", "kcButtonLargeClass")}
                                id="mj-send-code"
                                type="submit"
                            >
                                {msgStr("mjEcContinue")}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </Template>
    );
}

/** "G" oficial do Google — mesmo SVG do Login.tsx. */
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
