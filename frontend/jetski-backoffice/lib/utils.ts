import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/** Formata um número brasileiro como `+55 (DD) NNNNN-NNNN` enquanto o usuário digita. */
export function formatTelefoneBR(value: string): string {
  let d = (value ?? '').replace(/\D/g, '')
  if (d.startsWith('55')) d = d.slice(2)
  d = d.slice(0, 11)
  if (!d) return ''
  const ddd = d.slice(0, 2)
  const num = d.slice(2)
  let r = `(${ddd}`
  if (ddd.length === 2) r += ') '
  if (num) {
    if (num.length <= 4) r += num
    else if (num.length <= 8) r += `${num.slice(0, 4)}-${num.slice(4)}`
    else r += `${num.slice(0, 5)}-${num.slice(5)}`
  }
  return `+55 ${r}`.trimEnd()
}

/** Normaliza para E.164 BR (`+55DDNNNNNNNNN`); undefined se vazio. */
export function telefoneToE164BR(value: string): string | undefined {
  const d = (value ?? '').replace(/\D/g, '').replace(/^55/, '')
  return d ? `+55${d}` : undefined
}

export function formatCurrency(value: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(value)
}

export function formatDate(date: string | Date): string {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
  }).format(new Date(date))
}

export function formatDateTime(date: string | Date): string {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(date))
}

export function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  if (hours === 0) return `${mins}min`
  if (mins === 0) return `${hours}h`
  return `${hours}h ${mins}min`
}
