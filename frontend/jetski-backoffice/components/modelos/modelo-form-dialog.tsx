'use client'

import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { modelosService, type ModeloCreateRequest } from '@/lib/api/services/modelos'
import type { Modelo } from '@/lib/api/types'
import { cn, formatCurrency } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

/**
 * Campos numéricos ficam como string no estado: vazio é vazio e 0 é 0.
 * (A versão anterior usava `valor || padrão`, que trocava um 0 salvo pelo
 * padrão — tolerância 0 abria como 5, caução 0 como 300 — e regravava.)
 */
type FormState = {
  nome: string
  fabricante: string
  potenciaHp: string
  capacidadePessoas: string
  precoBaseHora: string
  taxaHoraExtra: string
  toleranciaMin: string
  caucao: string
  duracaoMinimaMin: string
  incluiCombustivel: boolean
  descricao: string
}

const num = (v: number | null | undefined) => (v === null || v === undefined ? '' : String(v))

function toFormState(modelo?: Modelo): FormState {
  if (!modelo) {
    return {
      nome: '',
      fabricante: '',
      potenciaHp: '',
      capacidadePessoas: '2',
      precoBaseHora: '',
      taxaHoraExtra: '',
      toleranciaMin: '5',
      caucao: '',
      duracaoMinimaMin: '',
      incluiCombustivel: false,
      descricao: '',
    }
  }
  return {
    nome: modelo.nome ?? '',
    fabricante: modelo.fabricante ?? '',
    potenciaHp: num(modelo.potenciaHp),
    capacidadePessoas: num(modelo.capacidadePessoas),
    precoBaseHora: num(modelo.precoBaseHora),
    taxaHoraExtra: num(modelo.taxaHoraExtra),
    toleranciaMin: num(modelo.toleranciaMin),
    caucao: num(modelo.caucao),
    duracaoMinimaMin: modelo.duracaoMinimaMin ? String(modelo.duracaoMinimaMin) : '',
    incluiCombustivel: modelo.incluiCombustivel ?? false,
    descricao: modelo.descricao ?? '',
  }
}

/** Aceita vírgula decimal; vazio/ inválido → undefined. */
function parseNum(v: string): number | undefined {
  const t = v.trim().replace(',', '.')
  if (t === '') return undefined
  const n = Number(t)
  return Number.isFinite(n) ? n : undefined
}

function toRequest(f: FormState): ModeloCreateRequest {
  return {
    nome: f.nome.trim(),
    fabricante: f.fabricante.trim(),
    // Backend exige potência >= 1; vazio = não informar (mantém o atual na edição)
    potenciaHp: parseNum(f.potenciaHp),
    capacidadePessoas: parseNum(f.capacidadePessoas) ?? 1,
    precoBaseHora: parseNum(f.precoBaseHora) ?? 0,
    // Opcionais: vazio = zero explícito (sem taxa / sem tolerância / sem caução)
    taxaHoraExtra: parseNum(f.taxaHoraExtra) ?? 0,
    toleranciaMin: parseNum(f.toleranciaMin) ?? 0,
    caucao: parseNum(f.caucao) ?? 0,
    duracaoMinimaMin: parseNum(f.duracaoMinimaMin) ?? 0,
    incluiCombustivel: f.incluiCombustivel,
    descricao: f.descricao,
  }
}

function extractErrorMessage(e: unknown): string | undefined {
  const data = (e as { response?: { data?: { message?: string; errors?: Record<string, string> } } })
    ?.response?.data
  if (data?.errors) {
    const first = Object.values(data.errors)[0]
    if (first) return first
  }
  return data?.message
}

function Section({ title, description, children }: {
  title: string
  description?: string
  children: React.ReactNode
}) {
  return (
    <section className="space-y-3">
      <div>
        <h3 className="text-sm font-semibold">{title}</h3>
        {description && <p className="text-xs text-muted-foreground">{description}</p>}
      </div>
      {children}
    </section>
  )
}

/** Input numérico com unidade fixa à esquerda (R$) ou à direita (min, HP). */
function NumberField({
  id,
  label,
  value,
  onChange,
  prefix,
  suffix,
  hint,
  required,
  min,
  max,
  step,
  placeholder,
  decimal,
}: {
  id: string
  label: string
  value: string
  onChange: (v: string) => void
  prefix?: string
  suffix?: string
  hint?: string
  required?: boolean
  min?: number
  max?: number
  step?: number | 'any'
  placeholder?: string
  decimal?: boolean
}) {
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>
        {label}
        {required && <span className="text-destructive"> *</span>}
      </Label>
      <div className="relative">
        {prefix && (
          <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-sm text-muted-foreground">
            {prefix}
          </span>
        )}
        <Input
          id={id}
          type="number"
          inputMode={decimal ? 'decimal' : 'numeric'}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onWheel={(e) => (e.target as HTMLInputElement).blur()}
          min={min}
          max={max}
          step={step}
          required={required}
          placeholder={placeholder}
          className={cn(prefix && 'pl-9', suffix && 'pr-12')}
        />
        {suffix && (
          <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-sm text-muted-foreground">
            {suffix}
          </span>
        )}
      </div>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}

export function ModeloFormDialog({
  modelo,
  open,
  onOpenChange,
}: {
  /** Ausente = cadastro de novo modelo. */
  modelo?: Modelo
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(() => toFormState(modelo))
  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((f) => ({ ...f, [key]: value }))

  // Recarrega a cada abertura: o diálogo da lista é reaproveitado entre modelos
  // e a edição anterior (cancelada) não pode vazar para a próxima. Depende do id
  // (não do objeto) para um refetch em segundo plano não apagar o que se digita.
  useEffect(() => {
    if (open) setForm(toFormState(modelo))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, modelo?.id])

  const onSuccess = () => {
    queryClient.invalidateQueries({ queryKey: ['modelos'] })
    if (modelo) queryClient.invalidateQueries({ queryKey: ['modelo', modelo.id] })
    toast.success(modelo ? 'Modelo atualizado.' : 'Modelo cadastrado.')
    onOpenChange(false)
  }

  const mutation = useMutation({
    mutationFn: (data: ModeloCreateRequest) =>
      modelo ? modelosService.update(modelo.id, data) : modelosService.create(data),
    onSuccess,
    onError: (e: unknown) =>
      toast.error(extractErrorMessage(e) ?? 'Falha ao salvar o modelo.'),
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    mutation.mutate(toRequest(form))
  }

  const preco = parseNum(form.precoBaseHora)
  const taxa = parseNum(form.taxaHoraExtra) ?? 0
  const tolerancia = parseNum(form.toleranciaMin) ?? 0
  const caucao = parseNum(form.caucao) ?? 0
  const minimo = parseNum(form.duracaoMinimaMin) ?? 0

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[90vh] flex-col gap-0 p-0 sm:max-w-[640px]">
        <form onSubmit={handleSubmit} className="flex min-h-0 flex-1 flex-col">
          <DialogHeader className="border-b px-6 py-4">
            <DialogTitle>{modelo ? 'Editar modelo' : 'Novo modelo'}</DialogTitle>
            <DialogDescription>
              {modelo ? 'Atualize os dados do modelo' : 'Cadastre um novo modelo de jetski'}
            </DialogDescription>
          </DialogHeader>

          <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 py-5">
            <Section title="Identificação">
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="grid gap-1.5">
                  <Label htmlFor="nome">
                    Nome<span className="text-destructive"> *</span>
                  </Label>
                  <Input
                    id="nome"
                    value={form.nome}
                    onChange={(e) => set('nome', e.target.value)}
                    placeholder="Ex: Sea-Doo GTI 130"
                    minLength={2}
                    required
                  />
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="fabricante">Fabricante</Label>
                  <Input
                    id="fabricante"
                    value={form.fabricante}
                    onChange={(e) => set('fabricante', e.target.value)}
                    placeholder="Ex: Sea-Doo"
                  />
                </div>
                <NumberField
                  id="potenciaHp"
                  label="Potência"
                  suffix="HP"
                  value={form.potenciaHp}
                  onChange={(v) => set('potenciaHp', v)}
                  min={1}
                  placeholder="Ex: 130"
                />
                <NumberField
                  id="capacidadePessoas"
                  label="Capacidade"
                  suffix="pessoas"
                  value={form.capacidadePessoas}
                  onChange={(v) => set('capacidadePessoas', v)}
                  min={1}
                  max={4}
                  required
                />
              </div>
            </Section>

            <Section title="Preços" description="Deixe em branco o que não se aplica — vale como zero.">
              <div className="grid gap-4 sm:grid-cols-3">
                <NumberField
                  id="precoBaseHora"
                  label="Preço por hora"
                  prefix="R$"
                  value={form.precoBaseHora}
                  onChange={(v) => set('precoBaseHora', v)}
                  min={0.01}
                  step={0.01}
                  decimal
                  required
                  placeholder="0,00"
                />
                <NumberField
                  id="taxaHoraExtra"
                  label="Hora extra"
                  prefix="R$"
                  value={form.taxaHoraExtra}
                  onChange={(v) => set('taxaHoraExtra', v)}
                  min={0}
                  step={0.01}
                  decimal
                  placeholder="Sem taxa"
                />
                <NumberField
                  id="caucao"
                  label="Caução"
                  prefix="R$"
                  value={form.caucao}
                  onChange={(v) => set('caucao', v)}
                  min={0}
                  step={0.01}
                  decimal
                  placeholder="Sem caução"
                />
              </div>

              <div className="flex items-center justify-between gap-4 rounded-lg border p-3">
                <div className="space-y-0.5">
                  <Label htmlFor="incluiCombustivel">Combustível incluso</Label>
                  <p className="text-xs text-muted-foreground">
                    Ligado: o combustível já está no preço da hora.
                  </p>
                </div>
                <Switch
                  id="incluiCombustivel"
                  checked={form.incluiCombustivel}
                  onCheckedChange={(checked) => set('incluiCombustivel', checked)}
                />
              </div>
            </Section>

            <Section title="Regras da locação">
              <div className="grid gap-4 sm:grid-cols-2">
                <NumberField
                  id="toleranciaMin"
                  label="Tolerância"
                  suffix="min"
                  value={form.toleranciaMin}
                  onChange={(v) => set('toleranciaMin', v)}
                  min={0}
                  placeholder="0"
                  hint="Minutos descontados antes de cobrar."
                />
                <NumberField
                  id="duracaoMinimaMin"
                  label="Locação mínima"
                  suffix="min"
                  value={form.duracaoMinimaMin}
                  onChange={(v) => set('duracaoMinimaMin', v)}
                  min={0}
                  step={15}
                  placeholder="Sem mínimo"
                  hint="Aparece no marketplace e limita a reserva no portal."
                />
              </div>
            </Section>

            <Section title="Marketplace">
              <div className="grid gap-1.5">
                <Label htmlFor="descricao">Descrição</Label>
                <Textarea
                  id="descricao"
                  value={form.descricao}
                  onChange={(e) => set('descricao', e.target.value)}
                  placeholder="Conte o que o cliente precisa saber: diferenciais do jet, o que está incluso, ponto de saída..."
                  maxLength={2000}
                  rows={4}
                />
                <p className="flex justify-between gap-2 text-xs text-muted-foreground">
                  <span>Aparece em &quot;Sobre&quot; na página pública. Em branco, usamos um texto padrão.</span>
                  <span className="shrink-0 tabular-nums">{form.descricao.length}/2000</span>
                </p>
              </div>
            </Section>
          </div>

          <div className="border-t bg-muted/40 px-6 py-3 text-xs text-muted-foreground">
            <span className="font-medium text-foreground">
              {preco !== undefined ? `${formatCurrency(preco)}/h` : 'Preço não definido'}
            </span>
            {' · '}
            {taxa > 0 ? `hora extra ${formatCurrency(taxa)}` : 'sem taxa de hora extra'}
            {' · '}
            {tolerancia > 0 ? `tolerância ${tolerancia} min` : 'sem tolerância'}
            {' · '}
            {caucao > 0 ? `caução ${formatCurrency(caucao)}` : 'sem caução'}
            {minimo > 0 && ` · mínimo ${minimo} min`}
            {form.incluiCombustivel && ' · combustível incluso'}
          </div>

          <DialogFooter className="gap-2 border-t px-6 py-4">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Salvando...' : modelo ? 'Salvar' : 'Criar'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
