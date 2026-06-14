'use client'

import { toast as sonnerToast } from 'sonner'

type ToastProps = {
  title?: string
  description?: string
  variant?: 'default' | 'destructive'
  duration?: number
}

function toast({ title, description, variant, duration = 4000 }: ToastProps) {
  if (variant === 'destructive') {
    sonnerToast.error(title, {
      description,
      duration,
    })
  } else {
    sonnerToast.success(title, {
      description,
      duration,
    })
  }
}

export function useToast() {
  return {
    toast,
  }
}
