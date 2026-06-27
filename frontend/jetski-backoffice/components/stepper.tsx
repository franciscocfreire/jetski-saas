'use client'

import { Check } from 'lucide-react'
import { cn } from '@/lib/utils'

export type Step = { key: string; label: string }

/**
 * Stepper horizontal reutilizável. `current` é o índice (0-based) do passo ativo.
 * Passos anteriores ficam "concluídos" (check); clicar num passo já visitado
 * dispara `onStepClick` (se fornecido).
 */
export function Stepper({
  steps,
  current,
  maxStep,
  onStepClick,
}: {
  steps: Step[]
  current: number
  /** Maior passo já alcançado — permite navegar (ida e volta) até ele. Default: current. */
  maxStep?: number
  onStepClick?: (index: number) => void
}) {
  const reach = maxStep ?? current
  return (
    <ol className="flex w-full items-center">
      {steps.map((step, i) => {
        const done = i < current
        const active = i === current
        const clickable = !!onStepClick && i <= reach
        return (
          <li key={step.key} className={cn('flex items-center', i < steps.length - 1 && 'flex-1')}>
            <button
              type="button"
              disabled={!clickable}
              onClick={() => clickable && onStepClick?.(i)}
              className={cn(
                'flex items-center gap-2 text-sm',
                clickable && 'cursor-pointer',
                !clickable && 'cursor-default'
              )}
            >
              <span
                className={cn(
                  'flex h-8 w-8 shrink-0 items-center justify-center rounded-full border text-xs font-semibold transition-colors',
                  done && 'border-primary bg-primary text-primary-foreground',
                  active && 'border-primary text-primary',
                  !done && !active && 'border-muted-foreground/30 text-muted-foreground'
                )}
              >
                {done ? <Check size={16} /> : i + 1}
              </span>
              <span
                className={cn(
                  'hidden whitespace-nowrap sm:inline',
                  active ? 'font-medium text-foreground' : 'text-muted-foreground'
                )}
              >
                {step.label}
              </span>
            </button>
            {i < steps.length - 1 && (
              <span
                className={cn(
                  'mx-2 h-px flex-1 transition-colors',
                  i < current ? 'bg-primary' : 'bg-muted-foreground/20'
                )}
              />
            )}
          </li>
        )
      })}
    </ol>
  )
}
