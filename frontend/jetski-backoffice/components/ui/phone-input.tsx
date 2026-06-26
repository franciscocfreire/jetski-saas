'use client'

import { useMemo } from 'react'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

export type Pais = { code: string; nome: string; dial: string; flag: string }

/** Lista curada (Brasil primeiro = default). Cobre a maioria dos turistas. */
export const PAISES: Pais[] = [
  { code: 'BR', nome: 'Brasil', dial: '55', flag: '🇧🇷' },
  { code: 'PT', nome: 'Portugal', dial: '351', flag: '🇵🇹' },
  { code: 'US', nome: 'EUA/Canadá', dial: '1', flag: '🇺🇸' },
  { code: 'AR', nome: 'Argentina', dial: '54', flag: '🇦🇷' },
  { code: 'UY', nome: 'Uruguai', dial: '598', flag: '🇺🇾' },
  { code: 'PY', nome: 'Paraguai', dial: '595', flag: '🇵🇾' },
  { code: 'CL', nome: 'Chile', dial: '56', flag: '🇨🇱' },
  { code: 'CO', nome: 'Colômbia', dial: '57', flag: '🇨🇴' },
  { code: 'ES', nome: 'Espanha', dial: '34', flag: '🇪🇸' },
  { code: 'IT', nome: 'Itália', dial: '39', flag: '🇮🇹' },
  { code: 'FR', nome: 'França', dial: '33', flag: '🇫🇷' },
  { code: 'DE', nome: 'Alemanha', dial: '49', flag: '🇩🇪' },
  { code: 'GB', nome: 'Reino Unido', dial: '44', flag: '🇬🇧' },
]

const DEFAULT = PAISES[0]

function maskNacionalBR(digits: string): string {
  const d = digits.slice(0, 11)
  const ddd = d.slice(0, 2)
  const num = d.slice(2)
  let r = ddd ? `(${ddd}` : ''
  if (ddd.length === 2) r += ') '
  if (num.length <= 4) r += num
  else if (num.length <= 8) r += `${num.slice(0, 4)}-${num.slice(4)}`
  else r += `${num.slice(0, 5)}-${num.slice(5)}`
  return r
}

/** Quebra um E.164 (+<dial><nacional>) no país conhecido + parte nacional. */
function parse(value: string): { pais: Pais; nacional: string } {
  const digits = (value ?? '').replace(/\D/g, '')
  const dials = [...new Set(PAISES.map((p) => p.dial))].sort((a, b) => b.length - a.length)
  for (const d of dials) {
    if (digits.startsWith(d)) {
      const pais = PAISES.find((p) => p.dial === d)!
      return { pais, nacional: digits.slice(d.length) }
    }
  }
  return { pais: DEFAULT, nacional: digits }
}

/**
 * Campo de telefone com seletor de país (Brasil = default, mas trocável).
 * `value`/`onChange` em E.164 (`+55119...`).
 */
export function PhoneInput({
  value,
  onChange,
  placeholder,
}: {
  value: string
  onChange: (v: string) => void
  placeholder?: string
}) {
  const { pais, nacional } = useMemo(() => parse(value), [value])

  const trocarPais = (code: string) => {
    const p = PAISES.find((x) => x.code === code) ?? DEFAULT
    onChange(nacional ? `+${p.dial}${nacional}` : '')
  }

  const setNumero = (input: string) => {
    const digits = input.replace(/\D/g, '').slice(0, 15)
    onChange(digits ? `+${pais.dial}${digits}` : '')
  }

  const display = pais.dial === '55' ? maskNacionalBR(nacional) : nacional

  return (
    <div className="flex gap-2">
      <Select value={pais.code} onValueChange={trocarPais}>
        <SelectTrigger className="w-[96px] shrink-0">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {PAISES.map((p) => (
            <SelectItem key={p.code} value={p.code}>
              {p.flag} +{p.dial}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Input
        inputMode="tel"
        value={display}
        onChange={(e) => setNumero(e.target.value)}
        placeholder={placeholder ?? (pais.dial === '55' ? '(11) 99999-9999' : 'número')}
      />
    </div>
  )
}
