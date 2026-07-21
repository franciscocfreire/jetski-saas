import { withBase } from "@/lib/base";

/**
 * Logout FEDERADO (fix do "Sair que não sai"): encerrar SÓ o cookie do
 * portal deixa a sessão SSO do Keycloak viva — o próximo "Entrar" loga de
 * volta sem senha. A montagem da URL de end_session vive no servidor
 * (/api/logout): o issuer está no host dedicado sso.* e só o servidor o
 * conhece (KEYCLOAK_ISSUER). A rota limpa os cookies e redireciona ao
 * Keycloak com id_token_hint.
 */
export function sairDaConta(_idToken?: string) {
  window.location.href = withBase("/api/logout");
}
