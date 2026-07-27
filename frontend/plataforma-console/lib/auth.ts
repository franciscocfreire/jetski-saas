import NextAuth from "next-auth";
import Keycloak from "next-auth/providers/keycloak";
import type { JWT } from "next-auth/jwt";

/**
 * Auth do Console da Plataforma: Keycloak, client público próprio
 * `jetski-platform-console` (PKCE), mesmo realm do backoffice.
 *
 * Client SEPARADO de propósito: o console tem redirect URIs restritas ao
 * subdomínio admin.*, exige 2FA e (na F2) papéis de plataforma próprios. Um
 * token emitido para o backoffice não deve servir aqui, e vice-versa.
 *
 * A autorização real é do backend: o PlatformScopeInterceptor barra
 * /v1/platform/** para quem não é operador de plataforma. O que este arquivo
 * garante é identidade — não permissão.
 */

declare module "next-auth" {
  interface Session {
    accessToken: string;
    /** id_token p/ logout federado no Keycloak (id_token_hint). */
    idToken?: string;
    error?: string;
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    accessToken?: string;
    refreshToken?: string;
    expiresAt?: number;
    refreshExpiresAt?: number;
    idToken?: string;
    error?: string;
  }
}

async function refreshAccessToken(token: JWT): Promise<JWT> {
  try {
    const issuer = process.env.KEYCLOAK_ISSUER;
    if (!issuer) throw new Error("KEYCLOAK_ISSUER not configured");

    const params = new URLSearchParams({
      client_id: process.env.KEYCLOAK_CLIENT_ID!,
      grant_type: "refresh_token",
      refresh_token: token.refreshToken!,
    });

    const response = await fetch(`${issuer}/protocol/openid-connect/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: params.toString(),
    });
    const refreshed = await response.json();
    if (!response.ok) {
      throw new Error(refreshed.error_description || "Token refresh failed");
    }

    return {
      ...token,
      accessToken: refreshed.access_token,
      refreshToken: refreshed.refresh_token ?? token.refreshToken,
      expiresAt: Math.floor(Date.now() / 1000) + refreshed.expires_in,
      refreshExpiresAt: refreshed.refresh_expires_in
        ? Math.floor(Date.now() / 1000) + refreshed.refresh_expires_in
        : token.refreshExpiresAt,
      idToken: refreshed.id_token ?? token.idToken,
      error: undefined,
    };
  } catch (error) {
    console.error("[Auth] Error refreshing access token:", error);
    return { ...token, error: "RefreshAccessTokenError" };
  }
}

const secure = (process.env.NEXTAUTH_URL ?? "").startsWith("https");
const cookiePrefix = secure ? "__Secure-" : "";
const cookieDefaults = { httpOnly: true, sameSite: "lax" as const, path: "/", secure };

export const { handlers, signIn, signOut, auth } = NextAuth({
  trustHost: true,
  useSecureCookies: secure,
  // O console tem host próprio (admin.*), mas nomes de cookie próprios evitam
  // qualquer colisão com backoffice/portal se um dia dividirem domínio-pai.
  cookies: {
    sessionToken: { name: `${cookiePrefix}console.session-token`, options: cookieDefaults },
    callbackUrl: { name: `${cookiePrefix}console.callback-url`, options: { ...cookieDefaults, httpOnly: false } },
    csrfToken: { name: `${cookiePrefix}console.csrf-token`, options: cookieDefaults },
    pkceCodeVerifier: { name: `${cookiePrefix}console.pkce.code_verifier`, options: { ...cookieDefaults, maxAge: 1800 } },
    state: { name: `${cookiePrefix}console.state`, options: { ...cookieDefaults, maxAge: 1800 } },
    nonce: { name: `${cookiePrefix}console.nonce`, options: cookieDefaults },
  },
  providers: [
    Keycloak({
      clientId: process.env.KEYCLOAK_CLIENT_ID!,
      clientSecret: process.env.KEYCLOAK_CLIENT_SECRET || "",
      issuer: process.env.KEYCLOAK_ISSUER,
      allowDangerousEmailAccountLinking: false,
      authorization: { params: { scope: "openid profile email" } },
    }),
  ],
  callbacks: {
    async jwt({ token, account }) {
      if (account) {
        const refreshExpiresIn = account.refresh_expires_in as number | undefined;
        return {
          ...token,
          accessToken: account.access_token,
          refreshToken: account.refresh_token,
          expiresAt: account.expires_at,
          refreshExpiresAt: refreshExpiresIn
            ? Math.floor(Date.now() / 1000) + refreshExpiresIn
            : undefined,
          idToken: account.id_token,
        };
      }

      const expiresAt = token.expiresAt as number | undefined;
      if (expiresAt && Date.now() < (expiresAt - 60) * 1000) {
        return token;
      }
      // Refresh vencido: não chama o Keycloak — a chamada é condenada e só
      // geraria ruído de REFRESH_TOKEN_ERROR no log (mesma lição do portal).
      if (token.refreshExpiresAt && Date.now() >= token.refreshExpiresAt * 1000) {
        return { ...token, error: "RefreshAccessTokenError" };
      }
      return refreshAccessToken(token);
    },
    async session({ session, token }) {
      return {
        ...session,
        accessToken: token.accessToken as string,
        idToken: token.idToken as string | undefined,
        error: token.error as string | undefined,
      };
    },
  },
  pages: {
    signIn: "/login",
    error: "/login",
    signOut: "/login",
  },
  session: {
    strategy: "jwt",
    // Sessão curta: um console com poder de plataforma não fica aberto por dias.
    maxAge: 8 * 60 * 60,
  },
});
