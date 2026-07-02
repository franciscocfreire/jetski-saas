'use client'

import { Toaster } from 'sonner'
import { QueryProvider } from './query-provider'
import { SessionProvider } from './session-provider'
import { ThemeProvider } from './theme-provider'

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <ThemeProvider>
      <SessionProvider>
        <QueryProvider>
          {children}
          <Toaster position="top-right" richColors closeButton />
        </QueryProvider>
      </SessionProvider>
    </ThemeProvider>
  )
}
