'use client'

import { useState } from 'react'
import { Search, Loader2, Check, AlertTriangle } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export type Address = {
  cep: string
  logradouro: string
  numero: string
  complemento: string
  bairro: string
  cidade: string
  uf: string
}

const EMPTY: Address = {
  cep: '',
  logradouro: '',
  numero: '',
  complemento: '',
  bairro: '',
  cidade: '',
  uf: '',
}

function maskCep(v: string) {
  const d = v.replace(/\D/g, '').slice(0, 8)
  return d.length > 5 ? `${d.slice(0, 5)}-${d.slice(5)}` : d
}

/** Form de endereço com autopreenchimento por CEP (ViaCEP) + fallback manual. */
export function AddressForm({
  value,
  onChange,
}: {
  value?: Address
  onChange?: (a: Address) => void
}) {
  const [addr, setAddr] = useState<Address>(value ?? EMPTY)
  const [loading, setLoading] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [preenchido, setPreenchido] = useState(false)

  function up(patch: Partial<Address>) {
    setAddr((a) => {
      const next = { ...a, ...patch }
      onChange?.(next)
      return next
    })
  }

  async function buscarCep(raw: string) {
    const cep = raw.replace(/\D/g, '')
    if (cep.length !== 8) return
    setLoading(true)
    setErro(null)
    setPreenchido(false)
    try {
      const r = await fetch(`https://viacep.com.br/ws/${cep}/json/`)
      const d = await r.json()
      if (d.erro) {
        setErro('CEP não encontrado. Preencha manualmente.')
      } else {
        up({
          logradouro: d.logradouro || '',
          bairro: d.bairro || '',
          cidade: d.localidade || '',
          uf: d.uf || '',
        })
        setPreenchido(true)
      }
    } catch {
      setErro('Não foi possível consultar o CEP — preencha manualmente.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="grid gap-3 sm:grid-cols-6">
      <div className="sm:col-span-2">
        <Label className="text-xs">CEP</Label>
        <div className="relative">
          <Input
            inputMode="numeric"
            placeholder="00000-000"
            className="pr-9"
            value={addr.cep}
            onChange={(e) => {
              const masked = maskCep(e.target.value)
              up({ cep: masked })
              if (masked.replace(/\D/g, '').length === 8) buscarCep(masked)
            }}
            onBlur={() => buscarCep(addr.cep)}
          />
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground">
            {loading ? (
              <Loader2 size={16} className="animate-spin" />
            ) : preenchido ? (
              <Check size={16} className="text-emerald-500" />
            ) : (
              <Search size={16} />
            )}
          </span>
        </div>
      </div>

      <div className="sm:col-span-4">
        <Label className="text-xs">Logradouro</Label>
        <Input
          value={addr.logradouro}
          onChange={(e) => up({ logradouro: e.target.value })}
          placeholder="Rua / Avenida"
        />
      </div>

      <div className="sm:col-span-2">
        <Label className="text-xs">Número</Label>
        <Input value={addr.numero} onChange={(e) => up({ numero: e.target.value })} />
      </div>
      <div className="sm:col-span-4">
        <Label className="text-xs">Complemento</Label>
        <Input
          value={addr.complemento}
          onChange={(e) => up({ complemento: e.target.value })}
          placeholder="Apto, bloco… (opcional)"
        />
      </div>

      <div className="sm:col-span-3">
        <Label className="text-xs">Bairro</Label>
        <Input value={addr.bairro} onChange={(e) => up({ bairro: e.target.value })} />
      </div>
      <div className="sm:col-span-2">
        <Label className="text-xs">Cidade</Label>
        <Input value={addr.cidade} onChange={(e) => up({ cidade: e.target.value })} />
      </div>
      <div className="sm:col-span-1">
        <Label className="text-xs">UF</Label>
        <Input
          maxLength={2}
          value={addr.uf}
          onChange={(e) => up({ uf: e.target.value.toUpperCase() })}
        />
      </div>

      {erro && (
        <p className="flex items-center gap-1 text-xs text-amber-600 sm:col-span-6">
          <AlertTriangle size={13} /> {erro}
        </p>
      )}
      {preenchido && !erro && (
        <p className="flex items-center gap-1 text-xs text-emerald-600 sm:col-span-6">
          <Check size={13} /> Endereço preenchido pelo CEP — confira e complete o número.
        </p>
      )}
    </div>
  )
}
