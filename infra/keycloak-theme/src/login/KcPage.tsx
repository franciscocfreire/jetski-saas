import { Suspense, lazy } from "react";
import type { ClassKey } from "keycloakify/login";
import type { KcContext } from "./KcContext";
import { useI18n } from "./i18n";
import DefaultPage from "keycloakify/login/DefaultPage";
import Template from "./Template";
import "./main.css";

const UserProfileFormFields = lazy(
    () => import("keycloakify/login/UserProfileFormFields")
);

const Login = lazy(() => import("./pages/Login"));
const LoginUpdatePassword = lazy(() => import("./pages/LoginUpdatePassword"));
const LoginResetPassword = lazy(() => import("./pages/LoginResetPassword"));
const LoginVerifyEmail = lazy(() => import("./pages/LoginVerifyEmail"));
const LoginUpdateProfile = lazy(() => import("./pages/LoginUpdateProfile"));
const IdpReviewUserProfile = lazy(() => import("./pages/IdpReviewUserProfile"));
const LoginIdpLinkConfirm = lazy(() => import("./pages/LoginIdpLinkConfirm"));
const LoginIdpLinkEmail = lazy(() => import("./pages/LoginIdpLinkEmail"));
const LogoutConfirm = lazy(() => import("./pages/LogoutConfirm"));
const Info = lazy(() => import("./pages/Info"));
const ErrorPage = lazy(() => import("./pages/Error"));
const LoginPageExpired = lazy(() => import("./pages/LoginPageExpired"));

const doMakeUserConfirmPassword = true;

// CSS default do Keycloak (PatternFly) DESLIGADO — o visual inteiro vem do
// main.css (tokens --mj-* da marca Meu Jet). Vale para todas as páginas,
// inclusive as não ejetadas (DefaultPage), que herdam o Template custom.
const doUseDefaultCss = false;

export default function KcPage(props: { kcContext: KcContext }) {
    const { kcContext } = props;

    const { i18n } = useI18n({ kcContext });

    return (
        <Suspense>
            {(() => {
                const common = {
                    i18n,
                    classes,
                    Template,
                    doUseDefaultCss
                } as const;

                switch (kcContext.pageId) {
                    case "login.ftl":
                        return <Login kcContext={kcContext} {...common} />;
                    case "login-update-password.ftl":
                        return <LoginUpdatePassword kcContext={kcContext} {...common} />;
                    case "login-reset-password.ftl":
                        return <LoginResetPassword kcContext={kcContext} {...common} />;
                    case "login-verify-email.ftl":
                        return <LoginVerifyEmail kcContext={kcContext} {...common} />;
                    case "login-update-profile.ftl":
                        return (
                            <LoginUpdateProfile
                                kcContext={kcContext}
                                {...common}
                                UserProfileFormFields={UserProfileFormFields}
                                doMakeUserConfirmPassword={doMakeUserConfirmPassword}
                            />
                        );
                    case "idp-review-user-profile.ftl":
                        return (
                            <IdpReviewUserProfile
                                kcContext={kcContext}
                                {...common}
                                UserProfileFormFields={UserProfileFormFields}
                                doMakeUserConfirmPassword={doMakeUserConfirmPassword}
                            />
                        );
                    case "login-idp-link-confirm.ftl":
                        return <LoginIdpLinkConfirm kcContext={kcContext} {...common} />;
                    case "login-idp-link-email.ftl":
                        return <LoginIdpLinkEmail kcContext={kcContext} {...common} />;
                    case "logout-confirm.ftl":
                        return <LogoutConfirm kcContext={kcContext} {...common} />;
                    case "info.ftl":
                        return <Info kcContext={kcContext} {...common} />;
                    case "error.ftl":
                        return <ErrorPage kcContext={kcContext} {...common} />;
                    case "login-page-expired.ftl":
                        return <LoginPageExpired kcContext={kcContext} {...common} />;
                    default:
                        return (
                            <DefaultPage
                                kcContext={kcContext}
                                {...common}
                                UserProfileFormFields={UserProfileFormFields}
                                doMakeUserConfirmPassword={doMakeUserConfirmPassword}
                            />
                        );
                }
            })()}
        </Suspense>
    );
}

const classes = {} satisfies { [key in ClassKey]?: string };
