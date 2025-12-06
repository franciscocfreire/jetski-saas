'use client'

import { useState, useEffect, useRef } from 'react'
import { Badge } from '@/components/ui/badge'
import { Clock, AlertTriangle } from 'lucide-react'
import { cn } from '@/lib/utils'

interface RentalCountdownProps {
  dataCheckIn: string
  duracaoPrevista?: number // in minutes
  onWarning?: () => void
  onExpired?: () => void
  className?: string
}

/**
 * Calculates remaining time and displays a countdown badge
 * with color-coded status
 */
export function RentalCountdown({
  dataCheckIn,
  duracaoPrevista,
  onWarning,
  onExpired,
  className,
}: RentalCountdownProps) {
  // Store remaining time in SECONDS for precision when under 5 minutes
  const [remainingSeconds, setRemainingSeconds] = useState<number>(0)

  // Use refs for callbacks to avoid dependency issues
  const onWarningRef = useRef(onWarning)
  const onExpiredRef = useRef(onExpired)
  const hasTriggeredWarningRef = useRef(false)
  const hasTriggeredExpiredRef = useRef(false)

  // Update refs when callbacks change
  useEffect(() => {
    onWarningRef.current = onWarning
    onExpiredRef.current = onExpired
  }, [onWarning, onExpired])

  // Reset triggers when rental data changes
  useEffect(() => {
    hasTriggeredWarningRef.current = false
    hasTriggeredExpiredRef.current = false
  }, [dataCheckIn, duracaoPrevista])

  useEffect(() => {
    if (!duracaoPrevista) return

    const calculateRemainingSeconds = () => {
      const checkIn = new Date(dataCheckIn).getTime()
      const endTime = checkIn + duracaoPrevista * 60 * 1000
      const now = Date.now()
      const remainingMs = endTime - now
      return Math.ceil(remainingMs / 1000)
    }

    // Initial calculation
    const initialSecs = calculateRemainingSeconds()
    setRemainingSeconds(initialSecs)

    // Check initial state for already expired/warning
    const initialMins = Math.ceil(initialSecs / 60)
    if (initialSecs <= 0 && !hasTriggeredExpiredRef.current) {
      hasTriggeredExpiredRef.current = true
      onExpiredRef.current?.()
    } else if (initialMins <= 5 && initialMins > 0 && !hasTriggeredWarningRef.current) {
      hasTriggeredWarningRef.current = true
      onWarningRef.current?.()
    }

    const interval = setInterval(() => {
      const secs = calculateRemainingSeconds()
      setRemainingSeconds(secs)
      const mins = Math.ceil(secs / 60)

      // Trigger warning at 5 minutes (only once)
      if (mins <= 5 && mins > 0 && !hasTriggeredWarningRef.current) {
        hasTriggeredWarningRef.current = true
        console.log('🔔 Triggering warning notification')
        onWarningRef.current?.()
      }

      // Trigger expired when time runs out (only once)
      if (secs <= 0 && !hasTriggeredExpiredRef.current) {
        hasTriggeredExpiredRef.current = true
        console.log('🚨 Triggering expired notification')
        onExpiredRef.current?.()
      }
    }, 1000) // Update every second

    return () => clearInterval(interval)
  }, [dataCheckIn, duracaoPrevista])

  // Convert to minutes for comparison
  const remainingMinutes = Math.ceil(remainingSeconds / 60)

  if (!duracaoPrevista) {
    return (
      <Badge variant="outline" className={cn('gap-1', className)}>
        <Clock className="h-3 w-3" />
        Em uso
      </Badge>
    )
  }

  // Format remaining time - shows seconds when under 5 minutes
  const formatRemaining = (totalSeconds: number): string => {
    if (totalSeconds <= 0) {
      // Overdue - show how much time has passed
      const overdueSeconds = Math.abs(totalSeconds)
      const overdueMins = Math.floor(overdueSeconds / 60)
      const overdueSecs = overdueSeconds % 60

      if (overdueMins < 5) {
        // Show mm:ss for recent overdue
        return `+${overdueMins}:${String(overdueSecs).padStart(2, '0')}`
      }
      if (overdueMins < 60) {
        return `+${overdueMins}min`
      }
      const hours = Math.floor(overdueMins / 60)
      const minutes = overdueMins % 60
      return `+${hours}h${minutes > 0 ? ` ${minutes}min` : ''}`
    }

    const mins = Math.floor(totalSeconds / 60)
    const secs = totalSeconds % 60

    // Show mm:ss when under 5 minutes
    if (mins < 5) {
      return `${mins}:${String(secs).padStart(2, '0')}`
    }

    // Show just minutes when 5+ minutes
    if (mins < 60) {
      return `${mins}min`
    }

    // Show hours and minutes
    const hours = Math.floor(mins / 60)
    const minutes = mins % 60
    return `${hours}h${minutes > 0 ? ` ${minutes}min` : ''}`
  }

  // Color coding based on remaining time
  const getVariant = (): 'success' | 'warning' | 'destructive' | 'outline' => {
    if (remainingSeconds <= 0) return 'destructive'
    if (remainingMinutes <= 5) return 'destructive'
    if (remainingMinutes <= 10) return 'warning'
    return 'success'
  }

  const isExpired = remainingSeconds <= 0
  const isUrgent = remainingMinutes <= 5 && remainingSeconds > 0

  return (
    <Badge
      variant={getVariant()}
      className={cn(
        'gap-1 transition-colors font-mono',
        isExpired && 'animate-pulse',
        className
      )}
    >
      {isExpired || isUrgent ? (
        <AlertTriangle className="h-3 w-3" />
      ) : (
        <Clock className="h-3 w-3" />
      )}
      {formatRemaining(remainingSeconds)}
    </Badge>
  )
}
