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

export const { handlers, signIn, signOut, auth } = NextAuth({
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
