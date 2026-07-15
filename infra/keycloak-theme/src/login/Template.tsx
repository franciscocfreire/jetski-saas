import { useEffect } from "react";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import type { TemplateProps } from "keycloakify/login/TemplateProps";
import { useSetClassName } from "keycloakify/tools/useSetClassName";
import { useInitialize } from "keycloakify/login/Template.useInitialize";
import type { I18n } from "./i18n";
import type { KcContext } from "./KcContext";

// Fontes self-hosted (Vite embute os woff2 no JAR — sem CDN em runtime)
import "@fontsource/playfair-display/600.css";
import "@fontsource/geist-sans/400.css";
import "@fontsource/geist-sans/500.css";
import "@fontsource/geist-sans/600.css";

/**
 * Template Meu Jet: fundo navy abissal com a onda da Crista Dupla em marca
 * d'água, card claro (sand-50) com filete dourado e wordmark no topo.
 * Envolve TODAS as páginas do tema (ejetadas ou default) — ver BRAND.md.
 *
 * Estrutura funcional preservada do Template default do Keycloakify:
 * título do documento, useInitialize, seletor de idioma, attemptedUsername +
 * restart do fluxo, campos obrigatórios, banner de mensagens, "try another
 * way", socialProvidersNode e infoNode.
 */
export default function Template(props: TemplateProps<KcContext, I18n>) {
    const {
        displayInfo = false,
        displayMessage = true,
        displayRequiredFields = false,
        headerNode,
        socialProvidersNode = null,
        infoNode = null,
        documentTitle,
        bodyClassName,
        kcContext,
        i18n,
        doUseDefaultCss,
        children
    } = props;

    const { msg, msgStr, currentLanguage, enabledLanguages } = i18n;

    const { realm, auth, url, message, isAppInitiatedAction } = kcContext;

    useEffect(() => {
        document.title = documentTitle ?? msgStr("loginTitle", realm.displayName || realm.name);
    }, []);

    useSetClassName({ qualifiedName: "html", className: "mj-html" });
    useSetClassName({ qualifiedName: "body", className: bodyClassName ?? "mj-body" });

    const { isReadyToRender } = useInitialize({ kcContext, doUseDefaultCss });

    if (!isReadyToRender) {
        return null;
    }

    return (
        <div className="mj-page">
            {enabledLanguages.length > 1 && (
                <div className="mj-locale" id="kc-locale">
                    <details className="mj-locale-dropdown" id="kc-locale-dropdown">
                        <summary
                            tabIndex={1}
                            id="kc-current-locale-link"
                            aria-label={msgStr("languages")}
                            aria-haspopup="true"
                            aria-controls="language-switch1"
                        >
                            {currentLanguage.label}
                        </summary>
                        <ul role="menu" tabIndex={-1} aria-labelledby="kc-current-locale-link" id="language-switch1" className="mj-locale-list">
                            {enabledLanguages.map(({ languageTag, label, href }, i) => (
                                <li key={languageTag} role="none">
                                    <a role="menuitem" id={`language-${i + 1}`} href={href}>
                                        {label}
                                    </a>
                                </li>
                            ))}
                        </ul>
                    </details>
                </div>
            )}

            <main className="mj-card">
                <div className="mj-brand">
                    <CristaDupla />
                    <span className="mj-wordmark">MEU JET</span>
                </div>

                <header className="mj-header">
                    {(() => {
                        const node = !(auth !== undefined && auth.showUsername && !auth.showResetCredentials) ? (
                            <h1 id="kc-page-title" className="mj-title">
                                {headerNode}
                            </h1>
                        ) : (
                            <div id="kc-username" className="mj-attempted-username">
                                <label id="kc-attempted-username">{auth.attemptedUsername}</label>
                                <a
                                    id="reset-login"
                                    href={url.loginRestartFlowUrl}
                                    aria-label={msgStr("restartLoginTooltip")}
                                    title={msgStr("restartLoginTooltip")}
                                >
                                    ↺ {msg("restartLoginTooltip")}
                                </a>
                            </div>
                        );

                        if (displayRequiredFields) {
                            return (
                                <>
                                    {node}
                                    <p className="mj-required-hint">
                                        <span className="mj-required">*</span> {msg("requiredFields")}
                                    </p>
                                </>
                            );
                        }

                        return node;
                    })()}
                </header>

                <div id="kc-content">
                    <div id="kc-content-wrapper">
                        {/* App-initiated actions não veem warnings sobre completar a ação */}
                        {displayMessage && message !== undefined && (message.type !== "warning" || !isAppInitiatedAction) && (
                            <div className={`mj-alert mj-alert--${message.type}`}>
                                <span
                                    className="mj-alert-text"
                                    dangerouslySetInnerHTML={{
                                        __html: kcSanitize(message.summary)
                                    }}
                                />
                            </div>
                        )}
                        {children}
                        {auth !== undefined && auth.showTryAnotherWayLink && (
                            <form id="kc-select-try-another-way-form" action={url.loginAction} method="post">
                                <input type="hidden" name="tryAnotherWay" value="on" />
                                <a
                                    href="#"
                                    id="try-another-way"
                                    className="mj-link"
                                    onClick={event => {
                                        document.forms["kc-select-try-another-way-form" as never].requestSubmit();
                                        event.preventDefault();
                                        return false;
                                    }}
                                >
                                    {msg("doTryAnotherWay")}
                                </a>
                            </form>
                        )}
                        {socialProvidersNode}
                        {displayInfo && (
                            <div id="kc-info" className="mj-info">
                                <div id="kc-info-wrapper">{infoNode}</div>
                            </div>
                        )}
                    </div>
                </div>
            </main>

            <footer className="mj-footer">
                <span>powered by Meu Jet</span>
                <nav>
                    <a href="https://www.meujet.com.br/ajuda">Ajuda</a>
                    <a href="https://www.meujet.com.br/termos">Termos</a>
                    <a href="https://www.meujet.com.br/privacidade">Privacidade</a>
                </nav>
            </footer>
        </div>
    );
}

/** Logo Crista Dupla (versão fundo claro: onda dourada + onda navy) — paths canônicos do BRAND.md. */
function CristaDupla() {
    return (
        <svg className="mj-logo" viewBox="0 0 64 40" role="img" aria-label="Meu Jet" fill="none">
            <path
                d="M5 15.5 C 15 15.5, 19 6, 30 6 C 39.5 6, 42 12.5, 59 10.5"
                stroke="#C9A24B"
                strokeWidth="4.4"
                strokeLinecap="round"
            />
            <path
                d="M5 29 C 13 29, 18.5 20.5, 28 20.5 C 37 20.5, 42.5 27.5, 59 25"
                stroke="#1E4266"
                strokeWidth="3.6"
                strokeLinecap="round"
            />
        </svg>
    );
}
