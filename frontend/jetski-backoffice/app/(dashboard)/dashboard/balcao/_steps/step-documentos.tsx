'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { FileUpload } from '@/components/file-upload'
import { AddressForm, type Address } from '@/components/address-form'
import { clientesService, habilitacaoService } from '@/lib/api/services'
import type { Cliente } from '@/lib/api/types'
import type { Atendimento } from '../types'

/** UFs com a capital (a capital vai para o topo da lista de cidades). */
const UFS: { uf: string; capital: string }[] = [
  { uf: 'SP', capital: 'São Paulo' },
  { uf: 'RJ', capital: 'Rio de Janeiro' },
  { uf: 'MG', capital: 'Belo Horizonte' },
  { uf: 'PR', capital: 'Curitiba' },
  { uf: 'SC', capital: 'Florianópolis' },
  { uf: 'RS', capital: 'Porto Alegre' },
  { uf: 'ES', capital: 'Vitória' },
  { uf: 'BA', capital: 'Salvador' },
  { uf: 'DF', capital: 'Brasília' },
  { uf: 'GO', capital: 'Goiânia' },
  { uf: 'MS', capital: 'Campo Grande' },
  { uf: 'MT', capital: 'Cuiabá' },
  { uf: 'PE', capital: 'Recife' },
  { uf: 'CE', capital: 'Fortaleza' },
  { uf: 'RN', capital: 'Natal' },
  { uf: 'PB', capital: 'João Pessoa' },
  { uf: 'AL', capital: 'Maceió' },
  { uf: 'SE', capital: 'Aracaju' },
  { uf: 'PI', capital: 'Teresina' },
  { uf: 'MA', capital: 'São Luís' },
  { uf: 'PA', capital: 'Belém' },
  { uf: 'AM', capital: 'Manaus' },
  { uf: 'TO', capital: 'Palmas' },
  { uf: 'RO', capital: 'Porto Velho' },
  { uf: 'AC', capital: 'Rio Branco' },
  { uf: 'RR', capital: 'Boa Vista' },
  { uf: 'AP', capital: 'Macapá' },
]

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
  onDone: (patch: {
    endereco?: Address
    temComprovanteResidencia: boolean
    cliente?: Cliente
  }) => void
}) {
  const qc = useQueryClient()
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
  // Naturalidade guardada como "Cidade/UF" (formato dos anexos NORMAM) —
  // na UI é um par de selects encadeados: UF → cidades (IBGE), capital no topo
  const partesNat = (c.naturalidade ?? '').split('/')
  const [natUf, setNatUf] = useState(partesNat.length === 2 ? partesNat[1].trim().toUpperCase() : 'SP')
  const [natCidade, setNatCidade] = useState(partesNat.length >= 1 ? partesNat[0].trim() : '')
  const naturalidade = natCidade && natUf ? `${natCidade}/${natUf}` : ''

  // Municípios do estado escolhido — API pública do IBGE, cacheada por UF
  const { data: cidadesUf, isError: cidadesErro } = useQuery({
    queryKey: ['ibge-municipios', natUf],
    queryFn: async (): Promise<string[]> => {
      const res = await fetch(
        `https://servicodados.ibge.gov.br/api/v1/localidades/estados/${natUf}/municipios?orderBy=nome`
      )
      if (!res.ok) throw new Error('IBGE indisponível')
      const rows = (await res.json()) as { nome: string }[]
      const nomes = rows.map((r) => r.nome)
      // capital primeiro (o caso mais comum), demais em ordem alfabética
      const capital = UFS.find((u) => u.uf === natUf)?.capital
      return capital && nomes.includes(capital)
        ? [capital, ...nomes.filter((n) => n !== capital)]
        : nomes
    },
    enabled: !!natUf,
    staleTime: 24 * 60 * 60 * 1000, // lista de municípios não muda — cache de 1 dia
    retry: 1,
  })
  const [estrangeiro, setEstrangeiro] = useState(c.estrangeiro ?? false)
  // Anexos capturados (dataURL) p/ incluir no PDF: identidade, comprovante, selfie.
  const [anexos, setAnexos] = useState<{
    IDENTIDADE?: string
    COMPROVANTE_RESIDENCIA?: string
    SELFIE?: string
  }>({})

  // Pré-carrega as fotos já enviadas do cliente (carrega automático; pode trocar).
  const { data: anexosUrls } = useQuery({
    queryKey: ['cliente-anexos', c.id],
    queryFn: async () => {
      const lista = await clientesService.listarAnexos(c.id)
      const urls: Record<string, string> = {}
      for (const a of lista) {
        const blob = await clientesService.baixarAnexo(c.id, a.tipo).catch(() => null)
        if (blob) urls[a.tipo] = URL.createObjectURL(blob)
      }
      return urls
    },
    enabled: !!c.id,
  })

  const salvarDados = useMutation({
    mutationFn: async () => {
      // Guarda o cliente atualizado p/ devolver ao fluxo (senão, ao voltar, o
      // formulário reinicializa do cliente antigo e parece "não ter salvo").
      const atualizado = await clientesService.update(c.id, {
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
      // EMA: residência resolvida aqui (comprovante OU Declaração 1-C) → marca o anexo.
      if (!atendimento.temCha && atendimento.reserva?.id) {
        await habilitacaoService
          .registrar(atendimento.reserva.id, { via: 'EMA', anexoResidencia: true })
          .catch(() => null)
      }
      return atualizado
    },
    onSuccess: (cliente) => {
      // Recarrega as fotos do cliente (recém-enviadas) ao reabrir o passo.
      qc.invalidateQueries({ queryKey: ['cliente-anexos', c.id] })
      onDone({ endereco, temComprovanteResidencia: temComprovante, cliente })
    },
    onError: () => toast.error('Falha ao salvar os dados do cliente.'),
  })

  function avancar() {
    // Dados NORMAM obrigatórios para avançar (e para o envio à Marinha).
    if (!nacionalidade.trim() || !naturalidade.trim()) {
      toast.warning('Preencha nacionalidade e naturalidade (anexos NORMAM-212).')
      return
    }
    if (!temComprovante && (!endereco?.cep || !endereco?.logradouro)) {
      toast.warning('Sem comprovante: informe o endereço para a Declaração 1-C.')
      return
    }
    // "Tem comprovante" → a foto/arquivo é obrigatória.
    if (temComprovante && !anexos.COMPROVANTE_RESIDENCIA && !anexosUrls?.COMPROVANTE_RESIDENCIA) {
      toast.warning('Anexe a foto/arquivo do comprovante de residência.')
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
            tipoDocumento="IDENTIDADE"
            initialUrl={anexosUrls?.IDENTIDADE}
            onChange={(f) => setAnexos((a) => ({ ...a, IDENTIDADE: f?.dataUrl }))}
          />
        </div>
        <div>
          <Label className="mb-1 block text-xs">Selfie / foto do cliente</Label>
          <FileUpload
            label="Tirar/enviar selfie"
            accept="image/*"
            tipoDocumento="SELFIE"
            initialUrl={anexosUrls?.SELFIE}
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
            <Label className="text-xs">
              Nacionalidade <span className="text-red-500">*</span>
            </Label>
            <Input value={nacionalidade} onChange={(e) => setNacionalidade(e.target.value)} />
          </div>
          <div>
            <Label className="text-xs">
              Naturalidade <span className="text-red-500">*</span>
            </Label>
            <div className="flex gap-2">
              <Select
                value={natUf}
                onValueChange={(uf) => {
                  setNatUf(uf)
                  setNatCidade('') // estado mudou → escolher a cidade de novo
                }}
              >
                <SelectTrigger className="w-24 shrink-0">
                  <SelectValue placeholder="UF" />
                </SelectTrigger>
                <SelectContent>
                  {UFS.map(({ uf }) => (
                    <SelectItem key={uf} value={uf}>
                      {uf}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {cidadesErro ? (
                // IBGE fora do ar → não trava o atendimento: cidade em texto livre
                <Input
                  value={natCidade}
                  onChange={(e) => setNatCidade(e.target.value)}
                  placeholder="Cidade"
                />
              ) : (
                <Select value={natCidade} onValueChange={setNatCidade}>
                  <SelectTrigger>
                    <SelectValue placeholder={cidadesUf ? 'Cidade' : 'Carregando…'} />
                  </SelectTrigger>
                  <SelectContent>
                    {/* cidade vinda do cadastro pode não estar na lista (grafia antiga) */}
                    {natCidade && !(cidadesUf ?? []).includes(natCidade) && (
                      <SelectItem value={natCidade}>{natCidade}</SelectItem>
                    )}
                    {(cidadesUf ?? []).map((nome) => (
                      <SelectItem key={nome} value={nome}>
                        {nome}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </div>
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
            tipoDocumento="COMPROVANTE_RESIDENCIA"
            initialUrl={anexosUrls?.COMPROVANTE_RESIDENCIA}
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
