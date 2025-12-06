import NextAuth from "next-auth"
import Keycloak from "next-auth/providers/keycloak"

declare module "next-auth" {
  interface Session {
    accessToken: string
    refreshToken: string
    idToken?: string
    tenantId?: string
    error?: string
  }
}

// Disable __Secure- cookie prefix for E2E testing with Playwright
// When NEXTAUTH_E2E_TESTING is set, cookies won't have the __Secure- prefix
// which allows Playwright to properly handle them across page navigations
const isE2ETesting = process.env.NEXTAUTH_E2E_TESTING === 'true'

export const { handlers, signIn, signOut, auth } = NextAuth({
  trustHost: true, // Required for ngrok/reverse proxy
  // In E2E mode, use non-secure cookies to work around Playwright limitations
  // See: https://github.com/nextauthjs/next-auth/issues/8914
  useSecureCookies: !isE2ETesting,
  providers: [
    Keycloak({
      clientId: process.env.KEYCLOAK_CLIENT_ID!,
      clientSecret: process.env.KEYCLOAK_CLIENT_SECRET || "", // Empty for public clients
      issuer: process.env.KEYCLOAK_ISSUER,
      // PKCE configuration for public clients (SPA)
      allowDangerousEmailAccountLinking: false,
      // Keycloak OAuth2 parameters for PKCE
      authorization: {
        params: {
          scope: "openid profile email",
        },
      },
    }),
  ],
  callbacks: {
    async jwt({ token, account, profile }) {
      if (account) {
        token.accessToken = account.access_token
        token.refreshToken = account.refresh_token
        token.expiresAt = account.expires_at
        token.idToken = account.id_token
      }

      // Extract tenant_id from token if available
      if (profile && typeof profile === 'object' && 'tenant_id' in profile) {
        token.tenantId = profile.tenant_id as string
      }

      return token
    },
    async session({ session, token }) {
      return {
        ...session,
        accessToken: token.accessToken as string,
        refreshToken: token.refreshToken as string,
        idToken: token.idToken as string | undefined,
        tenantId: token.tenantId as string | undefined,
        error: token.error as string | undefined,
      }
    },
  },
  pages: {
    signIn: "/login",
    error: "/login",
    signOut: "/login",
  },
  session: {
    strategy: "jwt",
  },
})
