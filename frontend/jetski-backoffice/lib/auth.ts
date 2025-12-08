import NextAuth from "next-auth"
import Keycloak from "next-auth/providers/keycloak"
import type { JWT } from "next-auth/jwt"

declare module "next-auth" {
  interface Session {
    accessToken: string
    refreshToken: string
    idToken?: string
    tenantId?: string
    error?: string
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    accessToken?: string
    refreshToken?: string
    expiresAt?: number
    idToken?: string
    tenantId?: string
    error?: string
  }
}

// Disable __Secure- cookie prefix for E2E testing with Playwright
// When NEXTAUTH_E2E_TESTING is set, cookies won't have the __Secure- prefix
// which allows Playwright to properly handle them across page navigations
const isE2ETesting = process.env.NEXTAUTH_E2E_TESTING === 'true'

/**
 * Refresh the access token using the refresh token
 * Called when the access token has expired
 */
async function refreshAccessToken(token: JWT): Promise<JWT> {
  try {
    const issuer = process.env.KEYCLOAK_ISSUER
    if (!issuer) {
      throw new Error("KEYCLOAK_ISSUER not configured")
    }

    // Keycloak token endpoint
    const tokenEndpoint = `${issuer}/protocol/openid-connect/token`

    const params = new URLSearchParams({
      client_id: process.env.KEYCLOAK_CLIENT_ID!,
      grant_type: "refresh_token",
      refresh_token: token.refreshToken!,
    })

    // Add client_secret if configured (confidential client)
    if (process.env.KEYCLOAK_CLIENT_SECRET) {
      params.append("client_secret", process.env.KEYCLOAK_CLIENT_SECRET)
    }

    console.log("[Auth] Refreshing access token...")

    const response = await fetch(tokenEndpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: params.toString(),
    })

    const refreshedTokens = await response.json()

    if (!response.ok) {
      console.error("[Auth] Token refresh failed:", refreshedTokens)
      throw new Error(refreshedTokens.error_description || "Token refresh failed")
    }

    console.log("[Auth] Token refreshed successfully, expires in:", refreshedTokens.expires_in, "seconds")

    return {
      ...token,
      accessToken: refreshedTokens.access_token,
      refreshToken: refreshedTokens.refresh_token ?? token.refreshToken, // Fall back to old refresh token
      expiresAt: Math.floor(Date.now() / 1000) + refreshedTokens.expires_in,
      idToken: refreshedTokens.id_token ?? token.idToken,
      error: undefined, // Clear any previous error
    }
  } catch (error) {
    console.error("[Auth] Error refreshing access token:", error)

    return {
      ...token,
      error: "RefreshAccessTokenError",
    }
  }
}

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
          scope: "openid profile email offline_access", // Added offline_access for refresh tokens
        },
      },
    }),
  ],
  callbacks: {
    async jwt({ token, account, profile }) {
      // Initial sign in - store tokens from account
      if (account) {
        console.log("[Auth] Initial sign in, storing tokens. Expires at:", account.expires_at)
        return {
          ...token,
          accessToken: account.access_token,
          refreshToken: account.refresh_token,
          expiresAt: account.expires_at,
          idToken: account.id_token,
          tenantId: profile && typeof profile === 'object' && 'tenant_id' in profile
            ? (profile.tenant_id as string)
            : undefined,
        }
      }

      // Subsequent requests - check if token needs refresh
      // Refresh if less than 60 seconds until expiration (buffer time)
      const expiresAt = token.expiresAt as number | undefined
      const bufferSeconds = 60 // Refresh 60 seconds before expiry

      if (expiresAt && Date.now() < (expiresAt - bufferSeconds) * 1000) {
        // Token is still valid
        return token
      }

      // Token has expired or is about to expire - refresh it
      console.log("[Auth] Token expired or expiring soon, attempting refresh...")
      return refreshAccessToken(token)
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
    // Extend session max age to allow longer sessions with refresh
    maxAge: 30 * 24 * 60 * 60, // 30 days
  },
})
