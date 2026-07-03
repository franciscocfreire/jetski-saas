"use client";

import { SessionProvider } from "next-auth/react";
import { withBase } from "@/lib/base";

export function Providers({ children }: { children: React.ReactNode }) {
  // basePath explícito: sem ele o client do NextAuth (signIn/useSession) chama
  // /api/auth na raiz do host — que pertence ao backoffice.
  return (
    <SessionProvider basePath={withBase("/api/auth")}>
      {children}
    </SessionProvider>
  );
}
