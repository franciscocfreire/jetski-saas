import type { DeepPartial } from "keycloakify/tools/DeepPartial";
import type { KcContext } from "./KcContext";
import KcPage from "./KcPage";
import { createGetKcContextMock } from "keycloakify/login/KcContext";
import type { KcContextExtension, KcContextExtensionPerPage } from "./KcContext";
import { themeNames, kcEnvDefaults } from "../kc.gen";

const kcContextExtension: KcContextExtension = {
    themeName: themeNames[0],
    properties: {
        ...kcEnvDefaults
    }
};
const kcContextExtensionPerPage: KcContextExtensionPerPage = {
    "email-code-id.ftl": {
        mjSocial: [
            {
                alias: "google",
                displayName: "Google",
                loginUrl: "#"
            }
        ],
        client: { baseUrl: undefined }
    },
    "trusted-device-enroll.ftl": {},
    "email-code-verify.ftl": {
        mjMode: "code",
        mjSocial: [{ alias: "google", displayName: "Google", loginUrl: "#" }],
        mjDest: "cliente@exemplo.com.br",
        mjTyped: "cliente@exemplo.com.br",
        mjCooldown: 42,
        realm: { resetPasswordAllowed: true },
        url: { loginResetCredentialsUrl: "#" }
    }
};

export const { getKcContextMock } = createGetKcContextMock({
    kcContextExtension,
    kcContextExtensionPerPage,
    overrides: {},
    overridesPerPage: {}
});

export function createKcPageStory<PageId extends KcContext["pageId"]>(params: {
    pageId: PageId;
}) {
    const { pageId } = params;

    function KcPageStory(props: {
        kcContext?: DeepPartial<Extract<KcContext, { pageId: PageId }>>;
    }) {
        const { kcContext: overrides } = props;

        const kcContextMock = getKcContextMock({
            pageId,
            overrides
        });

        return <KcPage kcContext={kcContextMock} />;
    }

    return { KcPageStory };
}
