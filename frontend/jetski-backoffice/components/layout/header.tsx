'use client'

import Link from 'next/link'
import { Bell, Search } from 'lucide-react'
import { SidebarTrigger } from '@/components/ui/sidebar'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Separator } from '@/components/ui/separator'
import { useRentalNotifications } from '@/components/providers/rental-notification-provider'
import { cn } from '@/lib/utils'

interface HeaderProps {
  title?: string
}

export function Header({ title }: HeaderProps) {
  const { alertCount, expiredRentals } = useRentalNotifications()
  const hasExpired = expiredRentals.length > 0

  return (
    <header className="flex h-16 shrink-0 items-center gap-2 border-b px-4">
      <SidebarTrigger className="-ml-1" />
      <Separator orientation="vertical" className="mr-2 h-4" />

      {title && <h1 className="text-lg font-semibold">{title}</h1>}

      <div className="ml-auto flex items-center gap-4">
        <div className="relative hidden md:block">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            type="search"
            placeholder="Buscar..."
            className="w-64 pl-8"
          />
        </div>

        <Link href="/dashboard/locacoes">
          <Button
            variant="ghost"
            size="icon"
            className={cn(
              "relative",
              hasExpired && "text-destructive"
            )}
          >
            <Bell className={cn("h-5 w-5", hasExpired && "animate-pulse")} />
            {alertCount > 0 && (
              <span className={cn(
                "absolute -top-1 -right-1 h-4 w-4 rounded-full text-[10px] font-medium flex items-center justify-center",
                hasExpired
                  ? "bg-destructive text-destructive-foreground animate-pulse"
                  : "bg-warning text-warning-foreground"
              )}>
                {alertCount > 9 ? '9+' : alertCount}
              </span>
            )}
          </Button>
        </Link>
      </div>
    </header>
  )
}
