'use client'

import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { rentalNotificationService, RentalAlert } from '@/lib/notifications/rental-notification-service'
import { initializeSounds } from '@/lib/notifications/notification-sounds'
import type { Locacao } from '@/lib/api/types'

export interface UseRentalNotificationsOptions {
  enabled?: boolean
  warningMinutes?: number // Default: 5
}

export interface RentalNotificationState {
  alerts: RentalAlert[]
  soundEnabled: boolean
  pushEnabled: boolean
}

export function useRentalNotifications(
  locacoes: Locacao[] | undefined,
  options: UseRentalNotificationsOptions = {}
) {
  const { enabled = true, warningMinutes = 5 } = options

  const [state, setState] = useState<RentalNotificationState>({
    alerts: [],
    soundEnabled: rentalNotificationService.isSoundEnabled(),
    pushEnabled: rentalNotificationService.isPushEnabled(),
  })

  const [soundInitialized, setSoundInitialized] = useState(false)

  // Initialize sound on first user interaction
  const initializeSound = useCallback(async () => {
    if (soundInitialized) return
    const success = await initializeSounds()
    if (success) {
      setSoundInitialized(true)
    }
  }, [soundInitialized])

  // Request push permission
  const requestPushPermission = useCallback(async () => {
    const granted = await rentalNotificationService.requestPushPermission()
    setState((prev) => ({ ...prev, pushEnabled: granted }))
    return granted
  }, [])

  // Toggle sound
  const toggleSound = useCallback((enabled: boolean) => {
    rentalNotificationService.setSoundEnabled(enabled)
    setState((prev) => ({ ...prev, soundEnabled: enabled }))
  }, [])

  // Handle warning notification
  const handleWarning = useCallback(
    async (locacao: Locacao) => {
      const alert: RentalAlert = {
        locacaoId: locacao.id,
        jetskiSerie: locacao.jetskiSerie || 'Jetski',
        clienteNome: locacao.clienteNome,
        type: 'warning',
        remainingMinutes: warningMinutes,
        triggeredAt: new Date(),
      }

      await rentalNotificationService.notify(alert)

      toast.warning('Locação terminando!', {
        description: `${locacao.jetskiSerie}${locacao.clienteNome ? ` - ${locacao.clienteNome}` : ''} termina em ${warningMinutes} minutos`,
        duration: 10000,
      })

      setState((prev) => ({
        ...prev,
        alerts: [...prev.alerts.filter((a) => a.locacaoId !== locacao.id), alert],
      }))
    },
    [warningMinutes]
  )

  // Handle expired notification
  const handleExpired = useCallback(async (locacao: Locacao) => {
    const alert: RentalAlert = {
      locacaoId: locacao.id,
      jetskiSerie: locacao.jetskiSerie || 'Jetski',
      clienteNome: locacao.clienteNome,
      type: 'expired',
      remainingMinutes: 0,
      triggeredAt: new Date(),
    }

    await rentalNotificationService.notify(alert)

    toast.error('Tempo expirado!', {
      description: `${locacao.jetskiSerie}${locacao.clienteNome ? ` - ${locacao.clienteNome}` : ''} excedeu o tempo previsto`,
      duration: 15000,
    })

    setState((prev) => ({
      ...prev,
      alerts: [...prev.alerts.filter((a) => a.locacaoId !== locacao.id), alert],
    }))
  }, [])

  // Clear alerts for checked-out rentals
  useEffect(() => {
    if (!locacoes) return

    const activeIds = new Set(
      locacoes.filter((l) => l.status === 'EM_CURSO').map((l) => l.id)
    )

    // Clear notifications for rentals that are no longer active
    setState((prev) => ({
      ...prev,
      alerts: prev.alerts.filter((a) => activeIds.has(a.locacaoId)),
    }))
  }, [locacoes])

  // Get active rentals that are ending soon or expired
  const getUrgentRentals = useCallback((): Locacao[] => {
    if (!locacoes) return []

    return locacoes.filter((locacao) => {
      if (locacao.status !== 'EM_CURSO' || !locacao.duracaoPrevista) return false

      const checkIn = new Date(locacao.dataCheckIn).getTime()
      const endTime = checkIn + locacao.duracaoPrevista * 60 * 1000
      const remaining = Math.ceil((endTime - Date.now()) / 60000)

      return remaining <= 10 // Within 10 minutes or expired
    })
  }, [locacoes])

  // Count of expired rentals
  const expiredCount = state.alerts.filter((a) => a.type === 'expired').length

  // Count of warning rentals
  const warningCount = state.alerts.filter((a) => a.type === 'warning').length

  return {
    alerts: state.alerts,
    soundEnabled: state.soundEnabled,
    pushEnabled: state.pushEnabled,
    expiredCount,
    warningCount,
    urgentRentals: getUrgentRentals(),
    initializeSound,
    requestPushPermission,
    toggleSound,
    handleWarning,
    handleExpired,
  }
}
