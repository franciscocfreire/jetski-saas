'use client'

import { AlertTriangle, Bell, Volume2, VolumeX } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { RentalAlert } from '@/lib/notifications/rental-notification-service'

interface RentalAlertBannerProps {
  alerts: RentalAlert[]
  soundEnabled: boolean
  onToggleSound: (enabled: boolean) => void
  onRequestPushPermission?: () => void
  className?: string
}

export function RentalAlertBanner({
  alerts,
  soundEnabled,
  onToggleSound,
  onRequestPushPermission,
  className,
}: RentalAlertBannerProps) {
  const expiredAlerts = alerts.filter((a) => a.type === 'expired')
  const warningAlerts = alerts.filter((a) => a.type === 'warning')

  if (alerts.length === 0) return null

  return (
    <div
      className={cn(
        'rounded-lg border p-4 mb-4',
        expiredAlerts.length > 0
          ? 'bg-destructive/10 border-destructive'
          : 'bg-yellow-500/10 border-yellow-500',
        className
      )}
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <AlertTriangle
            className={cn(
              'h-5 w-5',
              expiredAlerts.length > 0 ? 'text-destructive animate-pulse' : 'text-yellow-600'
            )}
          />
          <div>
            <h4 className="font-medium">
              {expiredAlerts.length > 0 ? (
                <>
                  {expiredAlerts.length} locação(ões) com tempo expirado!
                  {warningAlerts.length > 0 && (
                    <span className="text-yellow-600 ml-2">
                      + {warningAlerts.length} terminando
                    </span>
                  )}
                </>
              ) : (
                <>{warningAlerts.length} locação(ões) terminando em breve</>
              )}
            </h4>
            <div className="text-sm text-muted-foreground mt-1">
              {[...expiredAlerts, ...warningAlerts].slice(0, 3).map((alert) => (
                <span key={alert.locacaoId} className="inline-block mr-3">
                  <strong>{alert.jetskiSerie}</strong>
                  {alert.clienteNome && ` - ${alert.clienteNome}`}
                </span>
              ))}
              {alerts.length > 3 && (
                <span className="text-muted-foreground">
                  e mais {alerts.length - 3}...
                </span>
              )}
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => onToggleSound(!soundEnabled)}
            title={soundEnabled ? 'Desativar som' : 'Ativar som'}
          >
            {soundEnabled ? (
              <Volume2 className="h-4 w-4" />
            ) : (
              <VolumeX className="h-4 w-4 text-muted-foreground" />
            )}
          </Button>

          {onRequestPushPermission && (
            <Button
              variant="ghost"
              size="icon"
              onClick={onRequestPushPermission}
              title="Ativar notificações push"
            >
              <Bell className="h-4 w-4" />
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
