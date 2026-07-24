/**
 * Tela pós-2FA (trusted-device-enroll.ftl, SPI mj-trusted-device-enroll):
 * oferece "confiar neste navegador por 30 dias" (checkbox opt-in). Marcado →
 * o authenticator grava o cookie + credential e o 2FA é pulado neste navegador
 * nas próximas vezes. Não marcado → só continua.
 */
import { useState } from "react";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";

export default function TrustedDeviceEnroll(props: PageProps<Extract<KcContext, { pageId: "trusted-device-enroll.ftl" }>, I18n>) {
    const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;

    const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });

    const { url } = kcContext;

    const { msg, msgStr } = i18n;

    const [isSubmitting, setIsSubmitting] = useState(false);

    return (
        <Template
            kcContext={kcContext}
            i18n={i18n}
            doUseDefaultCss={doUseDefaultCss}
            classes={classes}
            headerNode={msg("mjTdTitle")}
        >
            <div id="kc-form">
                <div id="kc-form-wrapper">
                    <p className="mj-instruction">{msg("mjTdInstruction")}</p>
                    <form
                        id="mj-trusted-device-form"
                        action={url.loginAction}
                        method="post"
                        onSubmit={() => {
                            setIsSubmitting(true);
                            return true;
                        }}
                    >
                        <label className="mj-trust-check">
                            <input type="checkbox" name="trustDevice" defaultChecked />
                            <span>{msg("mjTdCheckbox")}</span>
                        </label>
                        <div id="kc-form-buttons" className={kcClsx("kcFormGroupClass")}>
                            <button
                                tabIndex={1}
                                disabled={isSubmitting}
                                className={kcClsx("kcButtonClass", "kcButtonPrimaryClass", "kcButtonBlockClass", "kcButtonLargeClass")}
                                id="mj-trusted-continue"
                                type="submit"
                            >
                                {msgStr("doContinue")}
                            </button>
                        </div>
                        <p className="mj-trust-hint">{msg("mjTdHint")}</p>
                    </form>
                </div>
            </div>
        </Template>
    );
}
