'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Search, UserPlus, CheckCircle2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PhoneInput } from '@/components/ui/phone-input'
import { clientesService } from '@/lib/api/services'
import type { Cliente } from '@/lib/api/types'

export function StepCliente({ onDone }: { onDone: (cliente: Cliente) => void }) {
  const [cpf, setCpf] = useState('')
  const [buscou, setBuscou] = useState(false)
  const [encontrado, setEncontrado] = useState<Cliente | null>(null)
  const [form, setForm] = useState({ nome: '', email: '', celular: '' })

  const buscar = useMutation({
    mutationFn: async () => {
      const c = await clientesService.buscarPorCpf(cpf.trim())
      if (c) return { cliente: c, nomeMarinha: null as string | null }
      // Não está na base local → tenta o nome na Marinha pelo CPF (pré-preenchimento)
      const nomeMarinha = await clientesService.consultarNomeMarinha(cpf.trim()).catch(() => null)
      return { cliente: null, nomeMarinha }
    },
    onSuccess: ({ cliente, nomeMarinha }) => {
      setBuscou(true)
      setEncontrado(cliente)
      if (cliente) {
        if (cliente.statusConta === 'ATIVA') {
          toast.warning('Cliente com conta ativa — verificação (OTP) necessária antes de vincular.')
        }
        setForm((f) => ({
          ...f,
          nome: cliente.nome,
          email: cliente.email ?? '',
          celular: cliente.whatsapp || cliente.telefone || '',
        }))
      } else if (nomeMarinha) {
        setForm((f) => ({ ...f, nome: nomeMarinha }))
        toast.info('Nome encontrado na base da Marinha pelo CPF.')
      }
    },
    onError: () => toast.error('Falha ao buscar cliente.'),
  })

  const criar = useMutation({
    mutationFn: () =>
      clientesService.criarPreConta({
        nome: form.nome.trim(),
        documento: cpf.trim() || undefined,
        email: form.email.trim() || undefined,
        telefone: form.celular || undefined,
        whatsapp: form.celular || undefined,
      }),
    onSuccess: (c) => {
      toast.success('Pré-conta criada.')
      onDone(c)
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? 'Falha ao criar pré-conta.')
    },
  })

  return (
    <div className="space-y-5">
      <div>
        <Label className="text-xs">CPF do cliente</Label>
        <div className="flex gap-2">
          <Input
            value={cpf}
            onChange={(e) => setCpf(e.target.value)}
            placeholder="000.000.000-00"
            inputMode="numeric"
          />
          <Button
            type="button"
            variant="outline"
            disabled={!cpf.trim() || buscar.isPending}
            onClick={() => buscar.mutate()}
          >
            <Search size={16} className="mr-1" /> Buscar
          </Button>
        </div>
      </div>

      {buscou && encontrado && (
        <div className="flex items-start gap-3 rounded-lg border bg-emerald-50 p-3 dark:bg-emerald-950/30">
          <CheckCircle2 className="mt-0.5 h-5 w-5 text-emerald-600" />
          <div className="flex-1">
            <p className="font-medium">{encontrado.nome}</p>
            <p className="text-sm text-muted-foreground">
              {encontrado.email ?? 'sem e-mail'} · status: {encontrado.statusConta ?? '—'}
            </p>
          </div>
          <Button type="button" onClick={() => onDone(encontrado)}>
            Usar este cliente
          </Button>
        </div>
      )}

      {buscou && !encontrado && (
        <div className="space-y-4 rounded-lg border p-4">
          <p className="flex items-center gap-2 text-sm text-muted-foreground">
            <UserPlus size={16} /> Nenhum cliente com este CPF. Criar pré-conta de balcão:
          </p>
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <Label className="text-xs">Nome *</Label>
              <Input value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} />
            </div>
            <div>
              <Label className="text-xs">E-mail</Label>
              <Input
                type="email"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
              />
            </div>
            <div>
              <Label className="text-xs">Celular / WhatsApp</Label>
              <PhoneInput
                value={form.celular}
                onChange={(v) => setForm({ ...form, celular: v })}
              />
            </div>
          </div>
          <Button
            type="button"
            disabled={!form.nome.trim() || criar.isPending}
            onClick={() => criar.mutate()}
          >
            {criar.isPending ? 'Criando…' : 'Criar pré-conta e avançar'}
          </Button>
        </div>
      )}
    </div>
  )
}
