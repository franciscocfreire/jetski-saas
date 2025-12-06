'use client'

import { Toaster } from 'sonner'
import { QueryProvider } from './query-provider'
import { SessionProvider } from './session-provider'

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <SessionProvider>
      <QueryProvider>
        {children}
        <Toaster position="top-right" richColors closeButton />
      </QueryProvider>
    </SessionProvider>
  )
}
