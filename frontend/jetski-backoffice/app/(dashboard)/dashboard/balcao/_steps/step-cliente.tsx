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
import { formatarDocumento, rotuloDocumento } from '@/lib/documento'
import type { Cliente, DocumentoTipo } from '@/lib/api/types'
import type { Atendimento } from '../types'

export function StepCliente({
  atendimento,
  onDone,
}: {
  atendimento: Atendimento
  onDone: (cliente: Cliente) => void
}) {
  const clienteAtual = atendimento.cliente
  const [cpf, setCpf] = useState('')
  // O tipo é escolhido AQUI, no passo 1, porque é aqui que a identificação
  // acontece — antes o campo assumia CPF (inputMode numérico, máscara de CPF)
  // e um estrangeiro simplesmente não era localizável: `estrangeiro` só existia
  // três passos adiante, no passo Documentos.
  const [tipo, setTipo] = useState<DocumentoTipo>('CPF')
  const ehPassaporte = tipo === 'PASSAPORTE'
  const [buscou, setBuscou] = useState(false)
  const [encontrado, setEncontrado] = useState<Cliente | null>(null)
  const [form, setForm] = useState({ nome: '', email: '', celular: '' })

  const buscar = useMutation({
    mutationFn: async () => {
      const c = await clientesService.buscarPorCpf(cpf.trim())
      if (c) return { cliente: c, nomeMarinha: null as string | null }
      // Não está na base local → tenta o nome na Marinha pelo CPF. A consulta é
      // por CPF na origem; para passaporte não há o que perguntar.
      const nomeMarinha = ehPassaporte
        ? null
        : await clientesService.consultarNomeMarinha(cpf.trim()).catch(() => null)
      return { cliente: null, nomeMarinha }
    },
    onSuccess: ({ cliente, nomeMarinha }) => {
      setBuscou(true)
      setEncontrado(cliente)
      if (cliente) {
        if (cliente.statusConta === 'ATIVA') {
          // Anti-takeover (F2.2): conta ATIVA pertence ao cliente — o balcão pode
          // usá-la em atendimentos, mas não recriar/editar a identidade dela.
          toast.info(
            'Este cliente já tem conta ativa no portal — pode usá-lo normalmente; os dados da conta são gerenciados pelo próprio cliente.'
          )
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
        documentoTipo: cpf.trim() ? tipo : undefined,
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
      {clienteAtual && (
        <div className="flex flex-wrap items-start gap-3 rounded-lg border bg-emerald-50 p-3 dark:bg-emerald-950/30">
          <CheckCircle2 className="mt-0.5 h-5 w-5 text-emerald-600" />
          <div className="min-w-[8rem] flex-1">
            <p className="font-medium">{clienteAtual.nome}</p>
            <p className="text-sm text-muted-foreground">
              {clienteAtual.documento
                ? `${rotuloDocumento(clienteAtual.documentoTipo)} ${formatarDocumento(clienteAtual.documento, clienteAtual.documentoTipo)}`
                : 'sem documento'}{' '}
              ·{' '}
              {clienteAtual.email ?? 'sem e-mail'}
            </p>
          </div>
          <Button data-testid="balcao-cliente-continuar" type="button" className="w-full sm:w-auto" onClick={() => onDone(clienteAtual)}>
            Continuar
          </Button>
        </div>
      )}

      <div>
        <Label className="text-xs">
          {clienteAtual
            ? `Trocar cliente (buscar outro ${ehPassaporte ? 'passaporte' : 'CPF'})`
            : 'Documento do cliente'}
        </Label>
        <div className="flex gap-2">
          <div className="flex rounded-md border p-0.5">
            {(['CPF', 'PASSAPORTE'] as const).map((t) => (
              <Button
                key={t}
                data-testid={`balcao-cliente-tipo-${t}`}
                type="button"
                size="sm"
                variant={tipo === t ? 'default' : 'ghost'}
                className="h-8 px-3"
                onClick={() => {
                  setTipo(t)
                  setBuscou(false)
                  setEncontrado(null)
                }}
              >
                {t === 'CPF' ? 'CPF' : 'Passaporte'}
              </Button>
            ))}
          </div>
          <Input
            data-testid="balcao-cliente-documento"
            value={cpf}
            onChange={(e) => setCpf(e.target.value)}
            placeholder={ehPassaporte ? 'AB123456' : '000.000.000-00'}
            // Passaporte tem letra: teclado numérico no celular impediria digitar.
            inputMode={ehPassaporte ? 'text' : 'numeric'}
            autoCapitalize={ehPassaporte ? 'characters' : 'off'}
          />
          <Button
            data-testid="balcao-cliente-buscar"
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
        <div className="flex flex-wrap items-start gap-3 rounded-lg border bg-emerald-50 p-3 dark:bg-emerald-950/30">
          <CheckCircle2 className="mt-0.5 h-5 w-5 text-emerald-600" />
          <div className="min-w-[8rem] flex-1">
            <p className="font-medium">{encontrado.nome}</p>
            <p className="text-sm text-muted-foreground">
              {rotuloDocumento(encontrado.documentoTipo)}{' '}
              {formatarDocumento(encontrado.documento, encontrado.documentoTipo)} ·{' '}
              {encontrado.email ?? 'sem e-mail'} · status: {encontrado.statusConta ?? '—'}
            </p>
          </div>
          <Button data-testid="balcao-cliente-usar" type="button" className="w-full sm:w-auto" onClick={() => onDone(encontrado)}>
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
              <Input data-testid="balcao-cliente-nome" value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} />
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
                data-testid="balcao-cliente-celular"
                value={form.celular}
                onChange={(v) => setForm({ ...form, celular: v })}
              />
            </div>
          </div>
          <Button
            data-testid="balcao-cliente-criar"
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
