'use client'

import { createContext, useContext, useEffect, useRef, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { locacoesService } from '@/lib/api/services'
import { playNotificationSound, initializeSounds } from '@/lib/notifications/notification-sounds'
import { useTenantStore } from '@/lib/store/tenant-store'
import type { Locacao } from '@/lib/api/types'

interface RentalNotificationContextType {
  activeRentals: Locacao[]
  warningRentals: Locacao[]
  expiredRentals: Locacao[]
  alertCount: number
}

const RentalNotificationContext = createContext<RentalNotificationContextType>({
  activeRentals: [],
  warningRentals: [],
  expiredRentals: [],
  alertCount: 0
})

export function RentalNotificationProvider({ children }: { children: React.ReactNode }) {
  const { currentTenant } = useTenantStore()
  const notifiedWarningRef = useRef<Set<string>>(new Set())
  const notifiedExpiredRef = useRef<Set<string>>(new Set())
  const soundInitializedRef = useRef(false)

  // Poll for active rentals every 30 seconds
  const { data: activeRentals = [] } = useQuery({
    queryKey: ['locacoes', currentTenant?.id, 'EM_CURSO'],
    queryFn: () => locacoesService.list({ status: 'EM_CURSO' }),
    enabled: !!currentTenant,
    refetchInterval: 30000, // 30 seconds
    staleTime: 10000, // 10 seconds
  })

  // Initialize sounds on first user interaction
  useEffect(() => {
    const handleInteraction = () => {
      if (!soundInitializedRef.current) {
        initializeSounds()
        soundInitializedRef.current = true
        console.log('🔊 Audio context initialized')
      }
    }
    document.addEventListener('click', handleInteraction, { once: true })
    document.addEventListener('keydown', handleInteraction, { once: true })
    return () => {
      document.removeEventListener('click', handleInteraction)
      document.removeEventListener('keydown', handleInteraction)
    }
  }, [])

  // Check rentals every second for warning/expired
  useEffect(() => {
    if (!activeRentals.length) return

    const checkRentals = () => {
      const now = Date.now()

      activeRentals.forEach((rental) => {
        if (!rental.duracaoPrevista) return

        const endTime = new Date(rental.dataCheckIn).getTime() + rental.duracaoPrevista * 60 * 1000
        const remainingMs = endTime - now
        const remainingMins = Math.ceil(remainingMs / 60000)

        // Warning at 5 minutes
        if (remainingMins <= 5 && remainingMins > 0 && !notifiedWarningRef.current.has(rental.id)) {
          notifiedWarningRef.current.add(rental.id)
          console.log('🔔 Global warning notification for:', rental.jetskiSerie)
          playNotificationSound('warning')
          toast.warning(`⚠️ Locação ${rental.jetskiSerie || 'Jetski'} expira em ${remainingMins} minutos!`, {
            duration: 10000,
            action: {
              label: 'Ver',
              onClick: () => window.location.href = '/dashboard/locacoes'
            }
          })
        }

        // Expired
        if (remainingMs <= 0 && !notifiedExpiredRef.current.has(rental.id)) {
          notifiedExpiredRef.current.add(rental.id)
          console.log('🚨 Global expired notification for:', rental.jetskiSerie)
          playNotificationSound('expired')
          toast.error(`🚨 Locação ${rental.jetskiSerie || 'Jetski'} EXPIRADA!`, {
            duration: 0, // Won't auto-dismiss
            action: {
              label: 'Ver',
              onClick: () => window.location.href = '/dashboard/locacoes'
            }
          })
        }
      })
    }

    const interval = setInterval(checkRentals, 1000)
    checkRentals() // Run immediately

    return () => clearInterval(interval)
  }, [activeRentals])

  // Reset notifications when rentals change (e.g., rental completed)
  useEffect(() => {
    const currentIds = new Set(activeRentals.map(r => r.id))

    // Remove notifications for rentals that are no longer active
    notifiedWarningRef.current = new Set(
      [...notifiedWarningRef.current].filter(id => currentIds.has(id))
    )
    notifiedExpiredRef.current = new Set(
      [...notifiedExpiredRef.current].filter(id => currentIds.has(id))
    )
  }, [activeRentals])

  // Calculate warning and expired rentals for context
  const { warningRentals, expiredRentals } = useMemo(() => {
    const now = Date.now()

    const warning = activeRentals.filter(r => {
      if (!r.duracaoPrevista) return false
      const endTime = new Date(r.dataCheckIn).getTime() + r.duracaoPrevista * 60 * 1000
      const remainingMins = Math.ceil((endTime - now) / 60000)
      return remainingMins <= 5 && remainingMins > 0
    })

    const expired = activeRentals.filter(r => {
      if (!r.duracaoPrevista) return false
      const endTime = new Date(r.dataCheckIn).getTime() + r.duracaoPrevista * 60 * 1000
      return endTime <= now
    })

    return { warningRentals: warning, expiredRentals: expired }
  }, [activeRentals])

  const alertCount = warningRentals.length + expiredRentals.length

  return (
    <RentalNotificationContext.Provider value={{
      activeRentals,
      warningRentals,
      expiredRentals,
      alertCount
    }}>
      {children}
    </RentalNotificationContext.Provider>
  )
}

export const useRentalNotifications = () => useContext(RentalNotificationContext)
