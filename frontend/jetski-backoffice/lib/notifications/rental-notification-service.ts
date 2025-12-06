import { playNotificationSound, SoundType } from './notification-sounds'

export interface RentalAlert {
  locacaoId: string
  jetskiSerie: string
  clienteNome?: string
  type: 'warning' | 'expired'
  remainingMinutes: number
  triggeredAt: Date
}

/**
 * Service for managing rental notifications
 */
class RentalNotificationService {
  private notifiedRentals: Map<string, Set<'warning' | 'expired'>> = new Map()
  private soundEnabled: boolean = true
  private pushEnabled: boolean = false

  /**
   * Check if a notification has already been sent for this rental
   */
  hasNotified(locacaoId: string, type: 'warning' | 'expired'): boolean {
    const notifications = this.notifiedRentals.get(locacaoId)
    return notifications?.has(type) ?? false
  }

  /**
   * Mark a notification as sent
   */
  markNotified(locacaoId: string, type: 'warning' | 'expired'): void {
    if (!this.notifiedRentals.has(locacaoId)) {
      this.notifiedRentals.set(locacaoId, new Set())
    }
    this.notifiedRentals.get(locacaoId)!.add(type)
  }

  /**
   * Clear notifications for a rental (e.g., when checked out)
   */
  clearNotifications(locacaoId: string): void {
    this.notifiedRentals.delete(locacaoId)
  }

  /**
   * Enable/disable sound notifications
   */
  setSoundEnabled(enabled: boolean): void {
    this.soundEnabled = enabled
  }

  isSoundEnabled(): boolean {
    return this.soundEnabled
  }

  /**
   * Enable/disable push notifications
   */
  setPushEnabled(enabled: boolean): void {
    this.pushEnabled = enabled
  }

  isPushEnabled(): boolean {
    return this.pushEnabled
  }

  /**
   * Play notification sound based on type
   */
  async playSound(type: 'warning' | 'expired'): Promise<void> {
    if (!this.soundEnabled) return

    const soundType: SoundType = type === 'warning' ? 'warning' : 'expired'
    await playNotificationSound(soundType)
  }

  /**
   * Request push notification permission
   */
  async requestPushPermission(): Promise<boolean> {
    if (!('Notification' in window)) {
      console.warn('Push notifications not supported')
      return false
    }

    if (Notification.permission === 'granted') {
      this.pushEnabled = true
      return true
    }

    if (Notification.permission === 'denied') {
      return false
    }

    const permission = await Notification.requestPermission()
    this.pushEnabled = permission === 'granted'
    return this.pushEnabled
  }

  /**
   * Show a push notification
   */
  showPushNotification(title: string, body: string, icon?: string): void {
    if (!this.pushEnabled || !('Notification' in window)) return
    if (Notification.permission !== 'granted') return

    try {
      new Notification(title, {
        body,
        icon: icon || '/icon.png',
        tag: 'rental-alert',
        requireInteraction: true,
      })
    } catch (error) {
      console.warn('Could not show push notification:', error)
    }
  }

  /**
   * Send notification for a rental alert
   */
  async notify(alert: RentalAlert): Promise<void> {
    // Skip if already notified
    if (this.hasNotified(alert.locacaoId, alert.type)) {
      return
    }

    // Mark as notified
    this.markNotified(alert.locacaoId, alert.type)

    // Play sound
    await this.playSound(alert.type)

    // Show push notification
    if (alert.type === 'warning') {
      this.showPushNotification(
        'Locação terminando!',
        `${alert.jetskiSerie}${alert.clienteNome ? ` - ${alert.clienteNome}` : ''} termina em ${alert.remainingMinutes} minutos`
      )
    } else {
      this.showPushNotification(
        'Tempo expirado!',
        `${alert.jetskiSerie}${alert.clienteNome ? ` - ${alert.clienteNome}` : ''} excedeu o tempo previsto`
      )
    }
  }
}

// Singleton instance
export const rentalNotificationService = new RentalNotificationService()
