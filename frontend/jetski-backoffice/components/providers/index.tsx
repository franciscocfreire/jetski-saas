'use client'

import { QueryProvider } from './query-provider'
import { SessionProvider } from './session-provider'

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <SessionProvider>
      <QueryProvider>{children}</QueryProvider>
    </SessionProvider>
  )
}
