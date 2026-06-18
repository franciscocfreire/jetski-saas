'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { FileUpload } from '@/components/file-upload'
import { AddressForm, type Address } from '@/components/address-form'
import { clientesService } from '@/lib/api/services'
import type { Atendimento } from '../types'

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
  onDone: (patch: { endereco?: Address; temComprovanteResidencia: boolean; temCha: boolean }) => void
}) {
  const c = atendimento.cliente!
  const [temComprovante, setTemComprovante] = useState(atendimento.temComprovanteResidencia)
  const [endereco, setEndereco] = useState<Address | undefined>(atendimento.endereco)
  const [temCha, setTemCha] = useState(atendimento.temCha)
  // Dados pessoais dos anexos (preenchimento manual — sem OCR)
  const [rg, setRg] = useState(c.rg ?? '')
  const [orgaoEmissor, setOrgaoEmissor] = useState(c.orgaoEmissor ?? '')
  const [nacionalidade, setNacionalidade] = useState(c.nacionalidade ?? 'Brasileira')
  const [naturalidade, setNaturalidade] = useState(c.naturalidade ?? '')

  const salvarDados = useMutation({
    mutationFn: () =>
      clientesService.update(c.id, {
        nome: c.nome,
        rg: rg || undefined,
        orgaoEmissor: orgaoEmissor || undefined,
        nacionalidade: nacionalidade || undefined,
        naturalidade: naturalidade || undefined,
        enderecoJson: endereco ? JSON.stringify(endereco) : undefined,
      }),
    onSuccess: () => onDone({ endereco, temComprovanteResidencia: temComprovante, temCha }),
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
          <FileUpload label="Enviar RG/CNH" />
        </div>
        <div>
          <Label className="mb-1 block text-xs">CPF</Label>
          <FileUpload label="Enviar CPF" />
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
      </div>

      <div className="space-y-3 rounded-lg border p-4">
        <Label className="text-sm font-medium">Comprovante de residência</Label>
        <div className="flex gap-2">
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
        {temComprovante ? (
          <FileUpload label="Enviar comprovante de residência" />
        ) : (
          <div className="pt-2">
            <p className="mb-2 text-xs text-muted-foreground">
              Endereço declarado (Anexo 1-C / NORMAM-212):
            </p>
            <AddressForm value={endereco} onChange={setEndereco} />
          </div>
        )}
      </div>

      <div className="space-y-2 rounded-lg border p-4">
        <Label className="text-sm font-medium">Habilitação náutica</Label>
        <div className="flex gap-2">
          <Button
            type="button"
            variant={temCha ? 'default' : 'outline'}
            size="sm"
            onClick={() => setTemCha(true)}
          >
            Tem CHA/CHV
          </Button>
          <Button
            type="button"
            variant={!temCha ? 'default' : 'outline'}
            size="sm"
            onClick={() => setTemCha(false)}
          >
            Não tem → EMA + GRU
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">Detalhado no passo de Habilitação.</p>
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
