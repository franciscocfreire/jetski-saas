import NextAuth from "next-auth";
import { withBase } from "./base";
import Keycloak from "next-auth/providers/keycloak";
import type { JWT } from "next-auth/jwt";

/**
 * Auth real do portal do cliente: Keycloak (client público `jetski-customer-portal`,
 * PKCE), mesmo realm do backoffice porém SEM Membro — o backend só aceita este
 * token no escopo /v1/customers/** (role CLIENTE).
 */

declare module "next-auth" {
  interface Session {
    accessToken: string;
    emailVerified?: boolean;
    /** Papéis do realm — usados p/ mostrar o acesso staff no Perfil. */
    roles?: string[];
    error?: string;
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    accessToken?: string;
    refreshToken?: string;
    expiresAt?: number;
    idToken?: string;
    emailVerified?: boolean;
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

// Opções padrão de cookie (mesmas do NextAuth) — só os NOMES mudam.
const cookieDefaults = { httpOnly: true, sameSite: "lax" as const, path: "/", secure };

export const { handlers, signIn, signOut, auth } = NextAuth({
  trustHost: true,
  // O app vive sob /portal (basePath do Next) — sem isto o client do NextAuth
  // chama /api/auth na RAIZ do host, que o nginx roteia para o BACKOFFICE
  // (login sairia pelo client errado, azp=jetski-backoffice).
  basePath: withBase("/api/auth"),
  // Dev local roda em http://localhost:3003 — cookies __Secure- só em https
  useSecureCookies: secure,
  // Portal e backoffice dividem o MESMO host: nomes de cookie próprios para as
  // sessões não se sobrescreverem (authjs.session-token é o default de ambos).
  cookies: {
    sessionToken: { name: `${cookiePrefix}portal.session-token`, options: cookieDefaults },
    callbackUrl: { name: `${cookiePrefix}portal.callback-url`, options: { ...cookieDefaults, httpOnly: false } },
    csrfToken: { name: `${cookiePrefix}portal.csrf-token`, options: cookieDefaults },
    pkceCodeVerifier: { name: `${cookiePrefix}portal.pkce.code_verifier`, options: { ...cookieDefaults, maxAge: 1800 } },
    state: { name: `${cookiePrefix}portal.state`, options: { ...cookieDefaults, maxAge: 1800 } },
    nonce: { name: `${cookiePrefix}portal.nonce`, options: cookieDefaults },
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
    async jwt({ token, account, profile }) {
      if (account) {
        return {
          ...token,
          accessToken: account.access_token,
          refreshToken: account.refresh_token,
          expiresAt: account.expires_at,
          idToken: account.id_token,
          emailVerified:
            profile && typeof profile === "object" && "email_verified" in profile
              ? Boolean(profile.email_verified)
              : undefined,
        };
      }

      const expiresAt = token.expiresAt as number | undefined;
      if (expiresAt && Date.now() < (expiresAt - 60) * 1000) {
        return token;
      }
      return refreshAccessToken(token);
    },
    async session({ session, token }) {
      // papéis do realm (p/ mostrar acesso ao Backoffice só a quem é staff)
      let roles: string[] = [];
      try {
        const payload = JSON.parse(
          Buffer.from((token.accessToken as string).split(".")[1], "base64").toString()
        );
        roles = payload?.realm_access?.roles ?? [];
      } catch {
        // token ausente/expirado — sem papéis
      }
      return {
        ...session,
        accessToken: token.accessToken as string,
        emailVerified: token.emailVerified as boolean | undefined,
        roles,
        error: token.error as string | undefined,
      };
    },
  },
  pages: {
    signIn: withBase("/login"),
    error: withBase("/login"),
    signOut: withBase("/login"),
  },
  session: {
    strategy: "jwt",
    maxAge: 30 * 24 * 60 * 60,
  },
});
