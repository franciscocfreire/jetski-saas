/* eslint-disable @typescript-eslint/no-empty-object-type */
import type { ExtendKcContext } from "keycloakify/login";
import type { KcEnvName, ThemeName } from "../kc.gen";

export type KcContextExtension = {
    themeName: ThemeName;
    properties: Record<KcEnvName, string> & {};
    // NOTE: Here you can declare more properties to extend the KcContext
    // See: https://docs.keycloakify.dev/faq-and-help/some-values-you-need-are-missing-from-in-kccontext
};

/**
 * Páginas custom renderizadas pelo SPI meujet-email-code
 * (infra/keycloak-extensions/email-code) — atributos setados via
 * form.setAttribute() no EmailCodeAuthenticator aparecem no topo do kcContext.
 */
export type KcContextExtensionPerPage = {
    "email-code-id.ftl": {
        mjSocial?: Array<{ alias: string; displayName: string; loginUrl: string }>;
    };
    "email-code-verify.ftl": {
        /** "password" = senha à frente (default) | "code" = código já pedido. */
        mjMode?: string;
        mjSocial?: Array<{ alias: string; displayName: string; loginUrl: string }>;
        /** E-mail digitado na tela 1 ("" quando o cliente entrou por CPF). */
        mjDest?: string;
        /** Identificador digitado (e-mail ou CPF) — exibido no "Entrando como". */
        mjTyped?: string;
        /** Segundos restantes do cooldown de reenvio. */
        mjCooldown?: number;
        /** Presentes no kcContext em runtime (UrlBean/RealmBean), mas fora do
         *  tipo base das páginas custom do Keycloakify. */
        realm: { resetPasswordAllowed: boolean };
        url: { loginResetCredentialsUrl: string };
    };
};

export type KcContext = ExtendKcContext<KcContextExtension, KcContextExtensionPerPage>;
