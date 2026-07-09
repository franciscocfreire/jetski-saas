import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatCurrency(value: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(value)
}

// App single-region (Brasil): fixa o fuso para não exibir em UTC (ex.: SSR no
// container roda em UTC → horários 3h adiantados).
const TZ = 'America/Sao_Paulo'

export function formatDate(date: string | Date): string {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeZone: TZ,
  }).format(new Date(date))
}

export function formatDateTime(date: string | Date): string {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: TZ,
  }).format(new Date(date))
}

export function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  if (hours === 0) return `${mins}min`
  if (mins === 0) return `${hours}h`
  return `${hours}h ${mins}min`
}

/** Duração por extenso em pt-BR: "30 min", "1 hora", "2 horas", "2 horas e 20 min". */
export function formatDuracao(minutes: number): string {
  const m = Math.max(0, Math.round(minutes))
  const h = Math.floor(m / 60)
  const r = m % 60
  if (h === 0) return `${r} min`
  const hp = `${h} ${h === 1 ? 'hora' : 'horas'}`
  return r === 0 ? hp : `${hp} e ${r} min`
}

/**
 * Data/hora LOCAL sem zona ("YYYY-MM-DDTHH:mm:ss") para payloads de campos
 * LocalDateTime do backend. NUNCA use Date.toISOString() nesses campos: ele
 * converte para UTC (sufixo Z) e o backend descarta o Z — grava +3h errado.
 */
export function toLocalDateTime(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
