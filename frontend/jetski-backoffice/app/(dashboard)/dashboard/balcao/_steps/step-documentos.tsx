'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { FileUpload } from '@/components/file-upload'
import { AddressForm, type Address } from '@/components/address-form'
import { clientesService } from '@/lib/api/services'
import type { Atendimento } from '../types'

/** Endereço salvo do cliente (enderecoJson) → Address, para reaproveitar. */
function parseEnderecoSalvo(json?: string): Address | undefined {
  if (!json) return undefined
  try {
    const e = JSON.parse(json)
    return e && (e.cep || e.logradouro) ? (e as Address) : undefined
  } catch {
    return undefined
  }
}

/**
 * Passo 2 — Documentos. Coleta RG/CPF (preview local) e o comprovante de
 * residência: "tem" (upload) ou "não tem" → endereço p/ a Declaração 1-C,
 * salvo em cliente.enderecoJson. Triagem de habilitação (tem CHA?).
 */
export function StepDocumentos({
  atendimento,
  onBack,
  onDone,
}: {
  atendimento: Atendimento
  onBack: () => void
  onDone: (patch: { endereco?: Address; temComprovanteResidencia: boolean }) => void
}) {
  const c = atendimento.cliente!
  const [temComprovante, setTemComprovante] = useState(atendimento.temComprovanteResidencia)
  // Reaproveita o endereço já salvo do cliente (enderecoJson) quando voltar numa próxima vez.
  const [endereco, setEndereco] = useState<Address | undefined>(
    atendimento.endereco ?? parseEnderecoSalvo(c.enderecoJson)
  )
  // Dados pessoais dos anexos (preenchimento manual — sem OCR)
  const [rg, setRg] = useState(c.rg ?? '')
  const [orgaoEmissor, setOrgaoEmissor] = useState(c.orgaoEmissor ?? '')
  const [nacionalidade, setNacionalidade] = useState(c.nacionalidade ?? 'Brasileira')
  const [naturalidade, setNaturalidade] = useState(c.naturalidade ?? '')
  const [estrangeiro, setEstrangeiro] = useState(c.estrangeiro ?? false)
  // Anexos capturados (dataURL) p/ incluir no PDF: identidade, comprovante, selfie.
  const [anexos, setAnexos] = useState<{
    IDENTIDADE?: string
    COMPROVANTE_RESIDENCIA?: string
    SELFIE?: string
  }>({})

  const salvarDados = useMutation({
    mutationFn: async () => {
      await clientesService.update(c.id, {
        nome: c.nome,
        rg: rg || undefined,
        orgaoEmissor: orgaoEmissor || undefined,
        nacionalidade: nacionalidade || undefined,
        naturalidade: naturalidade || undefined,
        estrangeiro,
        enderecoJson: endereco ? JSON.stringify(endereco) : undefined,
      })
      // sobe os anexos capturados (best-effort)
      for (const [tipo, dataUrl] of Object.entries(anexos)) {
        if (dataUrl) {
          await clientesService
            .uploadAnexo(c.id, tipo as 'IDENTIDADE' | 'COMPROVANTE_RESIDENCIA' | 'SELFIE', dataUrl)
            .catch(() => null)
        }
      }
    },
    onSuccess: () => onDone({ endereco, temComprovanteResidencia: temComprovante }),
    onError: () => toast.error('Falha ao salvar os dados do cliente.'),
  })

  function avancar() {
    if (!temComprovante && (!endereco?.cep || !endereco?.logradouro)) {
      toast.warning('Sem comprovante: informe o endereço para a Declaração 1-C.')
      return
    }
    salvarDados.mutate()
  }

  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <Label className="mb-1 block text-xs">Documento de identidade (RG/CNH)</Label>
          <FileUpload
            label="Enviar RG/CNH"
            onChange={(f) => setAnexos((a) => ({ ...a, IDENTIDADE: f?.dataUrl }))}
          />
        </div>
        <div>
          <Label className="mb-1 block text-xs">Selfie / foto do cliente</Label>
          <FileUpload
            label="Tirar/enviar selfie"
            accept="image/*"
            onChange={(f) => setAnexos((a) => ({ ...a, SELFIE: f?.dataUrl }))}
          />
        </div>
      </div>

      <div className="space-y-3 rounded-lg border p-4">
        <Label className="text-sm font-medium">Dados pessoais (anexos NORMAM-212)</Label>
        <div className="grid gap-3 sm:grid-cols-4">
          <div>
            <Label className="text-xs">RG (identidade)</Label>
            <Input value={rg} onChange={(e) => setRg(e.target.value)} />
          </div>
          <div>
            <Label className="text-xs">Órgão emissor</Label>
            <Input value={orgaoEmissor} onChange={(e) => setOrgaoEmissor(e.target.value)} placeholder="SSP/RJ" />
          </div>
          <div>
            <Label className="text-xs">Nacionalidade</Label>
            <Input value={nacionalidade} onChange={(e) => setNacionalidade(e.target.value)} />
          </div>
          <div>
            <Label className="text-xs">Naturalidade</Label>
            <Input value={naturalidade} onChange={(e) => setNaturalidade(e.target.value)} placeholder="Cidade/UF" />
          </div>
        </div>
        <label className="flex items-center gap-2 pt-1 text-sm">
          <Checkbox checked={estrangeiro} onCheckedChange={(v) => setEstrangeiro(!!v)} />
          Locatário estrangeiro (emite também os anexos 5-B em inglês)
        </label>
      </div>

      <div className="space-y-3 rounded-lg border p-4">
        <Label className="text-sm font-medium">Endereço residencial</Label>
        <p className="text-xs text-muted-foreground">
          Usado na Declaração de Residência (Anexo 1-C). Preencha sempre.
        </p>
        <AddressForm value={endereco} onChange={setEndereco} />

        <div className="flex flex-wrap items-center gap-2 pt-2">
          <span className="text-xs text-muted-foreground">Comprovante:</span>
          <Button
            type="button"
            variant={temComprovante ? 'default' : 'outline'}
            size="sm"
            onClick={() => setTemComprovante(true)}
          >
            Tem comprovante
          </Button>
          <Button
            type="button"
            variant={!temComprovante ? 'default' : 'outline'}
            size="sm"
            onClick={() => setTemComprovante(false)}
          >
            Não tem → Declaração 1-C
          </Button>
        </div>
        {temComprovante && (
          <FileUpload
            label="Enviar comprovante de residência"
            onChange={(f) => setAnexos((a) => ({ ...a, COMPROVANTE_RESIDENCIA: f?.dataUrl }))}
          />
        )}
      </div>

      <div className="flex justify-between">
        <Button type="button" variant="outline" onClick={onBack}>
          Voltar
        </Button>
        <Button type="button" disabled={salvarDados.isPending} onClick={avancar}>
          {salvarDados.isPending ? 'Salvando…' : 'Avançar'}
        </Button>
      </div>
    </div>
  )
}
