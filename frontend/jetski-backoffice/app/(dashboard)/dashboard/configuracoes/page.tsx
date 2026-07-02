'use client'

import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { configuracoesService } from '@/lib/api/services'
import type {
  AssinaturaConfig,
  ComissaoConfigRequest,
  DocumentoConfig,
  DocumentoConfigDestino,
  DocumentoObrigatoriosMarinha,
  TenantGeralConfigRequest,
} from '@/lib/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useToast } from '@/hooks/use-toast'
import { Loader2, Settings, Percent, Gift, Save, AlertCircle, Building2, Mail, FileText, ShieldCheck } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'

export default function ConfiguracoesPage() {
  const { toast } = useToast()
  const queryClient = useQueryClient()

  // Form state
  const [percentualPadrao, setPercentualPadrao] = useState<string>('10')
  const [percentualAbaixoBase, setPercentualAbaixoBase] = useState<string>('5')
  const [bonusAtivo, setBonusAtivo] = useState<boolean>(true)
  const [bonusMetaVendas, setBonusMetaVendas] = useState<string>('50')
  const [bonusValor, setBonusValor] = useState<string>('500')
  const [hasChanges, setHasChanges] = useState(false)

  // Query for current config
  const { data: config, isLoading, error } = useQuery({
    queryKey: ['comissao-config'],
    queryFn: () => configuracoesService.getComissaoConfig(),
  })

  // Dados gerais / e-mail da empresa
  const [razaoSocial, setRazaoSocial] = useState('')
  const [cidade, setCidade] = useState('')
  const [marinhaEmail, setMarinhaEmail] = useState('')
  const [emailRemetente, setEmailRemetente] = useState('')
  // SMTP por tenant
  const [smtpHost, setSmtpHost] = useState('')
  const [smtpPort, setSmtpPort] = useState('587')
  const [smtpUsername, setSmtpUsername] = useState('')
  const [smtpPassword, setSmtpPassword] = useState('')
  const [smtpFrom, setSmtpFrom] = useState('')
  const [smtpStarttls, setSmtpStarttls] = useState(true)
  const [smtpConfigurado, setSmtpConfigurado] = useState(false)

  const { data: geral } = useQuery({
    queryKey: ['tenant-geral-config'],
    queryFn: () => configuracoesService.getTenantConfig(),
  })
  useEffect(() => {
    if (geral) {
      setRazaoSocial(geral.razaoSocial ?? '')
      setCidade(geral.cidade ?? '')
      setMarinhaEmail(geral.marinhaEmail ?? '')
      setEmailRemetente(geral.emailRemetente ?? '')
      setSmtpHost(geral.smtpHost ?? '')
      setSmtpPort(geral.smtpPort?.toString() ?? '587')
      setSmtpUsername(geral.smtpUsername ?? '')
      setSmtpFrom(geral.smtpFrom ?? '')
      setSmtpStarttls(geral.smtpStarttls ?? true)
      setSmtpConfigurado(!!geral.smtpConfigurado)
      setSmtpPassword('') // senha nunca volta — write-only
    }
  }, [geral])

  const updateGeral = useMutation({
    mutationFn: (req: TenantGeralConfigRequest) => configuracoesService.updateTenantConfig(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenant-geral-config'] })
      setSmtpPassword('')
      toast({ title: 'Dados da empresa salvos', description: 'E-mail e SMTP atualizados.' })
    },
    onError: (e: Error) =>
      toast({ title: 'Erro ao salvar', description: e.message, variant: 'destructive' }),
  })

  const salvarGeral = () =>
    updateGeral.mutate({
      razaoSocial,
      cidade,
      marinhaEmail,
      emailRemetente,
      smtpHost,
      smtpPort: smtpPort ? Number(smtpPort) : undefined,
      smtpUsername,
      smtpFrom,
      smtpStarttls,
      ...(smtpPassword ? { smtpPassword } : {}),
    })

  // Parametrização da emissão: o que vai para Marinha vs Cliente (por seção).
  const [docCfg, setDocCfg] = useState<DocumentoConfig | null>(null)
  const { data: docConfig } = useQuery({
    queryKey: ['documento-config'],
    queryFn: () => configuracoesService.getDocumentoConfig(),
  })
  useEffect(() => {
    if (docConfig) setDocCfg(docConfig)
  }, [docConfig])

  const updateDoc = useMutation({
    mutationFn: (req: DocumentoConfig) => configuracoesService.updateDocumentoConfig(req),
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['documento-config'] })
      setDocCfg(saved)
      toast({ title: 'Configuração de documentos salva', description: 'Recorte por destino atualizado.' })
    },
    onError: (e: Error) =>
      toast({ title: 'Erro ao salvar', description: e.message, variant: 'destructive' }),
  })

  const toggleDoc = (destino: 'marinha' | 'cliente', secao: keyof DocumentoConfigDestino) =>
    setDocCfg((prev) =>
      prev
        ? { ...prev, [destino]: { ...prev[destino], [secao]: !prev[destino][secao] } }
        : prev
    )

  const toggleObrig = (item: keyof DocumentoObrigatoriosMarinha) =>
    setDocCfg((prev) =>
      prev
        ? {
            ...prev,
            obrigatoriosMarinha: {
              ...prev.obrigatoriosMarinha,
              [item]: !prev.obrigatoriosMarinha[item],
            },
          }
        : prev
    )

  // Reforço jurídico da assinatura: página de auditoria + carimbo de tempo.
  const [assCfg, setAssCfg] = useState<AssinaturaConfig | null>(null)
  const { data: assConfig } = useQuery({
    queryKey: ['assinatura-config'],
    queryFn: () => configuracoesService.getAssinaturaConfig(),
  })
  useEffect(() => {
    if (assConfig) setAssCfg(assConfig)
  }, [assConfig])

  const updateAss = useMutation({
    mutationFn: (req: AssinaturaConfig) => configuracoesService.updateAssinaturaConfig(req),
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['assinatura-config'] })
      setAssCfg(saved)
      toast({ title: 'Configuração de assinatura salva', description: 'Reforço jurídico atualizado.' })
    },
    onError: (e: Error) =>
      toast({ title: 'Erro ao salvar', description: e.message, variant: 'destructive' }),
  })

  // Populate form when config loads
  useEffect(() => {
    if (config) {
      setPercentualPadrao(config.percentualPadrao?.toString() || '10')
      setPercentualAbaixoBase(config.percentualAbaixoBase?.toString() || '5')
      setBonusAtivo(config.bonusAtivo ?? true)
      setBonusMetaVendas(config.bonusMetaVendas?.toString() || '50')
      setBonusValor(config.bonusValor?.toString() || '500')
      setHasChanges(false)
    }
  }, [config])

  // Track changes
  useEffect(() => {
    if (config) {
      const changed =
        percentualPadrao !== config.percentualPadrao?.toString() ||
        percentualAbaixoBase !== config.percentualAbaixoBase?.toString() ||
        bonusAtivo !== config.bonusAtivo ||
        bonusMetaVendas !== config.bonusMetaVendas?.toString() ||
        bonusValor !== config.bonusValor?.toString()
      setHasChanges(changed)
    }
  }, [percentualPadrao, percentualAbaixoBase, bonusAtivo, bonusMetaVendas, bonusValor, config])

  // Mutation for updating config
  const updateMutation = useMutation({
    mutationFn: (request: ComissaoConfigRequest) =>
      configuracoesService.updateComissaoConfig(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['comissao-config'] })
      toast({
        title: 'Configurações salvas',
        description: 'As configurações de comissões e bônus foram atualizadas com sucesso.',
      })
      setHasChanges(false)
    },
    onError: (error: Error) => {
      toast({
        title: 'Erro ao salvar',
        description: error.message || 'Não foi possível salvar as configurações.',
        variant: 'destructive',
      })
    },
  })

  const handleSave = () => {
    // Validate inputs
    const percPadrao = parseFloat(percentualPadrao)
    const percAbaixo = parseFloat(percentualAbaixoBase)
    const metaVendas = parseInt(bonusMetaVendas)
    const valorBonus = parseFloat(bonusValor)

    if (isNaN(percPadrao) || percPadrao < 0 || percPadrao > 100) {
      toast({
        title: 'Erro de validação',
        description: 'Percentual padrão deve ser entre 0 e 100.',
        variant: 'destructive',
      })
      return
    }

    if (isNaN(percAbaixo) || percAbaixo < 0 || percAbaixo > 100) {
      toast({
        title: 'Erro de validação',
        description: 'Percentual abaixo base deve ser entre 0 e 100.',
        variant: 'destructive',
      })
      return
    }

    if (bonusAtivo) {
      if (isNaN(metaVendas) || metaVendas < 1) {
        toast({
          title: 'Erro de validação',
          description: 'Meta de vendas deve ser maior ou igual a 1.',
          variant: 'destructive',
        })
        return
      }

      if (isNaN(valorBonus) || valorBonus < 0) {
        toast({
          title: 'Erro de validação',
          description: 'Valor do bônus deve ser maior ou igual a 0.',
          variant: 'destructive',
        })
        return
      }
    }

    const request: ComissaoConfigRequest = {
      percentualPadrao: percPadrao,
      percentualAbaixoBase: percAbaixo,
      bonusAtivo,
      bonusMetaVendas: bonusAtivo ? metaVendas : null,
      bonusValor: bonusAtivo ? valorBonus : null,
    }

    updateMutation.mutate(request)
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="container mx-auto p-6">
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>
            Erro ao carregar configurações. Você precisa ter permissão de ADMIN_TENANT ou GERENTE.
          </AlertDescription>
        </Alert>
      </div>
    )
  }

  return (
    <div className="container mx-auto p-6 max-w-4xl">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <Settings className="h-8 w-8 text-primary" />
          <div>
            <h1 className="text-2xl font-bold">Configurações</h1>
            <p className="text-muted-foreground">Gerencie as configurações do seu tenant</p>
          </div>
        </div>
        <Button onClick={handleSave} disabled={!hasChanges || updateMutation.isPending}>
          {updateMutation.isPending ? (
            <Loader2 className="h-4 w-4 animate-spin mr-2" />
          ) : (
            <Save className="h-4 w-4 mr-2" />
          )}
          Salvar Alterações
        </Button>
      </div>

      {/* Tabs */}
      <Tabs defaultValue="comissoes" className="space-y-6">
        <TabsList>
          <TabsTrigger value="comissoes" className="gap-2">
            <Percent className="h-4 w-4" />
            Comissões e Bônus
          </TabsTrigger>
          <TabsTrigger value="empresa" className="gap-2">
            <Building2 className="h-4 w-4" />
            Empresa &amp; E-mail
          </TabsTrigger>
          <TabsTrigger value="documentos" className="gap-2">
            <FileText className="h-4 w-4" />
            Documentos
          </TabsTrigger>
          <TabsTrigger value="assinatura" className="gap-2">
            <ShieldCheck className="h-4 w-4" />
            Assinatura
          </TabsTrigger>
        </TabsList>

        <TabsContent value="empresa" className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Mail className="h-5 w-5" />
                E-mail da empresa
              </CardTitle>
              <CardDescription>
                Destino dos documentos NORMAM-212 (Capitania/Marinha) e identidade de remetente — por
                empresa.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="razaoSocial">Razão social</Label>
                  <Input
                    id="razaoSocial"
                    value={razaoSocial}
                    onChange={(e) => setRazaoSocial(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="cidade">Cidade</Label>
                  <Input id="cidade" value={cidade} onChange={(e) => setCidade(e.target.value)} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="marinhaEmail">E-mail da Marinha (Capitania)</Label>
                  <Input
                    id="marinhaEmail"
                    type="email"
                    placeholder="capitania@marinha.mil.br"
                    value={marinhaEmail}
                    onChange={(e) => setMarinhaEmail(e.target.value)}
                  />
                  <p className="text-sm text-muted-foreground">
                    Recebe o PDF consolidado quando a documentação está completa.
                  </p>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="emailRemetente">E-mail remetente (responder-para)</Label>
                  <Input
                    id="emailRemetente"
                    type="email"
                    placeholder="contato@suaempresa.com.br"
                    value={emailRemetente}
                    onChange={(e) => setEmailRemetente(e.target.value)}
                  />
                  <p className="text-sm text-muted-foreground">
                    E-mail da empresa usado como remetente/responder-para nos envios.
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* SMTP por tenant */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Mail className="h-5 w-5" />
                Servidor de e-mail (SMTP) próprio
              </CardTitle>
              <CardDescription>
                Envie com o e-mail real da sua empresa. Configure o SMTP (ex.: Gmail:
                smtp.gmail.com, porta 587, com uma <strong>senha de app</strong>). Sem isso, os
                e-mails saem pelo remetente padrão da plataforma.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="smtpHost">Host SMTP</Label>
                  <Input
                    id="smtpHost"
                    placeholder="smtp.gmail.com"
                    value={smtpHost}
                    onChange={(e) => setSmtpHost(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="smtpPort">Porta</Label>
                  <Input
                    id="smtpPort"
                    type="number"
                    placeholder="587"
                    value={smtpPort}
                    onChange={(e) => setSmtpPort(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="smtpUsername">Usuário (conta SMTP)</Label>
                  <Input
                    id="smtpUsername"
                    placeholder="suaempresa@gmail.com"
                    value={smtpUsername}
                    onChange={(e) => setSmtpUsername(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="smtpPassword">
                    Senha / senha de app {smtpConfigurado && '(já configurada)'}
                  </Label>
                  <Input
                    id="smtpPassword"
                    type="password"
                    placeholder={smtpConfigurado ? '•••••••• (deixe em branco p/ manter)' : 'senha de app'}
                    value={smtpPassword}
                    onChange={(e) => setSmtpPassword(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="smtpFrom">From (remetente exibido)</Label>
                  <Input
                    id="smtpFrom"
                    type="email"
                    placeholder="(padrão: a conta SMTP)"
                    value={smtpFrom}
                    onChange={(e) => setSmtpFrom(e.target.value)}
                  />
                </div>
                <div className="flex items-center justify-between pt-6">
                  <div className="space-y-0.5">
                    <Label htmlFor="smtpStarttls">STARTTLS</Label>
                    <p className="text-sm text-muted-foreground">Recomendado (porta 587)</p>
                  </div>
                  <Switch id="smtpStarttls" checked={smtpStarttls} onCheckedChange={setSmtpStarttls} />
                </div>
              </div>
              <Alert>
                <AlertCircle className="h-4 w-4" />
                <AlertDescription>
                  A senha é gravada de forma <strong>write-only</strong> (nunca é exibida de volta).
                  Para Gmail, gere uma <strong>Senha de app</strong> em
                  myaccount.google.com/apppasswords (requer verificação em duas etapas).
                </AlertDescription>
              </Alert>
            </CardContent>
          </Card>

          <div className="flex justify-end">
            <Button onClick={salvarGeral} disabled={updateGeral.isPending}>
              {updateGeral.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Save className="mr-2 h-4 w-4" />
              )}
              Salvar dados da empresa
            </Button>
          </div>
        </TabsContent>

        <TabsContent value="comissoes" className="space-y-6">
          {/* Commission Percentages */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Percent className="h-5 w-5" />
                Percentuais de Comissão
              </CardTitle>
              <CardDescription>
                Configure os percentuais de comissão para vendedores. A comissão diferenciada
                é aplicada quando o valor da venda está abaixo do preço base do modelo.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="space-y-2">
                  <Label htmlFor="percentualPadrao">Comissão Padrão (%)</Label>
                  <div className="relative">
                    <Input
                      id="percentualPadrao"
                      type="number"
                      min="0"
                      max="100"
                      step="0.5"
                      value={percentualPadrao}
                      onChange={(e) => setPercentualPadrao(e.target.value)}
                      className="pr-8"
                    />
                    <span className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground">
                      %
                    </span>
                  </div>
                  <p className="text-sm text-muted-foreground">
                    Aplicado quando a venda é feita no preço base ou acima
                  </p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="percentualAbaixoBase">Comissão Reduzida (%)</Label>
                  <div className="relative">
                    <Input
                      id="percentualAbaixoBase"
                      type="number"
                      min="0"
                      max="100"
                      step="0.5"
                      value={percentualAbaixoBase}
                      onChange={(e) => setPercentualAbaixoBase(e.target.value)}
                      className="pr-8"
                    />
                    <span className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground">
                      %
                    </span>
                  </div>
                  <p className="text-sm text-muted-foreground">
                    Aplicado quando a venda é feita abaixo do preço base
                  </p>
                </div>
              </div>

              <Alert>
                <AlertCircle className="h-4 w-4" />
                <AlertDescription>
                  <strong>Como funciona:</strong> Se o vendedor vende pelo preço padrão (ex: R$150/hora),
                  ele recebe {percentualPadrao}%. Se vende com desconto (ex: R$120/hora), recebe {percentualAbaixoBase}%.
                  Isso incentiva vendas pelo preço cheio.
                </AlertDescription>
              </Alert>
            </CardContent>
          </Card>

          {/* Bonus System */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Gift className="h-5 w-5" />
                Sistema de Bônus
              </CardTitle>
              <CardDescription>
                Configure bonificações para vendedores que atingirem metas de vendas.
                O bônus é acumulativo e nunca reseta.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label htmlFor="bonusAtivo">Ativar sistema de bônus</Label>
                  <p className="text-sm text-muted-foreground">
                    Habilita a geração automática de bônus ao atingir metas
                  </p>
                </div>
                <Switch
                  id="bonusAtivo"
                  checked={bonusAtivo}
                  onCheckedChange={setBonusAtivo}
                />
              </div>

              {bonusAtivo && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-4 border-t">
                  <div className="space-y-2">
                    <Label htmlFor="bonusMetaVendas">Meta de Vendas</Label>
                    <Input
                      id="bonusMetaVendas"
                      type="number"
                      min="1"
                      value={bonusMetaVendas}
                      onChange={(e) => setBonusMetaVendas(e.target.value)}
                    />
                    <p className="text-sm text-muted-foreground">
                      Quantidade de vendas acima do preço base para ganhar bônus
                    </p>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="bonusValor">Valor do Bônus (R$)</Label>
                    <div className="relative">
                      <span className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
                        R$
                      </span>
                      <Input
                        id="bonusValor"
                        type="number"
                        min="0"
                        step="10"
                        value={bonusValor}
                        onChange={(e) => setBonusValor(e.target.value)}
                        className="pl-10"
                      />
                    </div>
                    <p className="text-sm text-muted-foreground">
                      Valor pago ao vendedor a cada meta atingida
                    </p>
                  </div>
                </div>
              )}

              {bonusAtivo && (
                <Alert>
                  <Gift className="h-4 w-4" />
                  <AlertDescription>
                    <strong>Exemplo:</strong> Com meta de {bonusMetaVendas} vendas e bônus de R$ {bonusValor},
                    o vendedor ganha R$ {bonusValor} ao atingir {bonusMetaVendas} vendas acima do preço base,
                    mais R$ {bonusValor} ao atingir {parseInt(bonusMetaVendas) * 2} vendas, e assim por diante.
                    O contador nunca reseta.
                  </AlertDescription>
                </Alert>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Documentos: o que vai em cada destino (Marinha vs Cliente) na emissão */}
        <TabsContent value="documentos" className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <FileText className="h-5 w-5" />
                Emissão — o que vai para cada destino
              </CardTitle>
              <CardDescription>
                Marque as seções enviadas à Marinha (Capitania) e ao cliente. Por padrão, o
                <strong> Termo de Responsabilidade pelo uso da moto aquática</strong> vai ao cliente, mas
                não à Marinha (é um instrumento privado entre loja e cliente).
              </CardDescription>
            </CardHeader>
            <CardContent>
              {!docCfg ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" /> Carregando…
                </div>
              ) : (
                <div className="space-y-4">
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b text-left text-muted-foreground">
                          <th className="py-2 pr-4 font-medium">Seção</th>
                          <th className="px-3 py-2 text-center font-medium">Marinha</th>
                          <th className="px-3 py-2 text-center font-medium">Cliente</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(
                          [
                            ['residencia', 'Anexo 1-C — Declaração de residência'],
                            ['saude', 'Anexo 5-C — Autodeclaração de saúde'],
                            ['instrutor', 'Anexo 5-B — Atestado de demonstração (instrutor)'],
                            ['termo', 'Termo de Responsabilidade (uso da moto aquática)'],
                            ['anexoIdentidade', 'Documento de identidade (RG/CNH)'],
                            ['anexoComprovante', 'Comprovante de residência'],
                            ['anexoSelfie', 'Selfie / foto do cliente'],
                            ['comprovanteGru', 'Comprovante de pagamento da GRU'],
                          ] as [keyof DocumentoConfigDestino, string][]
                        ).map(([secao, label]) => (
                          <tr key={secao} className="border-b last:border-0">
                            <td className="py-3 pr-4">{label}</td>
                            <td className="px-3 py-3 text-center">
                              <Switch
                                checked={docCfg.marinha[secao]}
                                onCheckedChange={() => toggleDoc('marinha', secao)}
                              />
                            </td>
                            <td className="px-3 py-3 text-center">
                              <Switch
                                checked={docCfg.cliente[secao]}
                                onCheckedChange={() => toggleDoc('cliente', secao)}
                              />
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  <Alert>
                    <AlertCircle className="h-4 w-4" />
                    <AlertDescription>
                      As seções 1-C/5-C/5-B só aparecem em habilitações via <strong>EMA</strong> (escola).
                      Use a <strong>Prévia Marinha/Cliente</strong> na reserva para conferir o resultado.
                    </AlertDescription>
                  </Alert>

                  {/* Obrigatórios para liberar o e-mail à Marinha */}
                  <div className="space-y-3 rounded-lg border p-4">
                    <div>
                      <h4 className="text-sm font-medium">Obrigatório para envio à Marinha</h4>
                      <p className="text-xs text-muted-foreground">
                        Itens exigidos (EMA) para liberar o e-mail à Capitania. Faltando algum, a reserva
                        fica pendente e a Marinha não é notificada — até completar e reenviar.
                      </p>
                    </div>
                    {(
                      [
                        ['identidade', 'Documento de identidade (RG/CNH) anexado'],
                        ['selfie', 'Selfie / foto do cliente anexada'],
                        ['saude', 'Autodeclaração de saúde (5-C)'],
                        ['regras', 'Anexo de regras de navegação'],
                        ['residencia', 'Comprovante/Declaração de residência'],
                        ['instrutor', 'Instrutor (atestado de demonstração)'],
                        ['nacionalidade', 'Nacionalidade preenchida'],
                        ['naturalidade', 'Naturalidade preenchida'],
                      ] as [keyof DocumentoObrigatoriosMarinha, string][]
                    ).map(([item, label]) => (
                      <label key={item} className="flex items-center justify-between gap-3 text-sm">
                        <span>{label}</span>
                        <Switch
                          checked={docCfg.obrigatoriosMarinha[item]}
                          onCheckedChange={() => toggleObrig(item)}
                        />
                      </label>
                    ))}
                  </div>

                  <div className="flex justify-end">
                    <Button onClick={() => docCfg && updateDoc.mutate(docCfg)} disabled={updateDoc.isPending}>
                      {updateDoc.isPending ? (
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      ) : (
                        <Save className="mr-2 h-4 w-4" />
                      )}
                      Salvar
                    </Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="assinatura" className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <ShieldCheck className="h-5 w-5" />
                Reforço jurídico da assinatura
              </CardTitle>
              <CardDescription>
                Fortalece o valor probatório da assinatura eletrônica (Lei nº 14.063/2020; MP 2.200-2/2001)
                sem custo e sem fricção para o cliente.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-5">
              {!assCfg ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" /> Carregando…
                </div>
              ) : (
                <>
                  <div className="flex items-start justify-between gap-4 rounded-lg border p-4">
                    <div className="space-y-1">
                      <Label className="text-sm font-medium">Página de trilha de auditoria</Label>
                      <p className="text-xs text-muted-foreground">
                        Anexa ao PDF uma página com as evidências do aceite (identificação, data/hora, IP,
                        dispositivo, consentimentos, hash) e a base legal.
                      </p>
                    </div>
                    <Switch
                      checked={assCfg.paginaAuditoria}
                      onCheckedChange={(v) => setAssCfg({ ...assCfg, paginaAuditoria: v })}
                    />
                  </div>

                  <div className="flex items-start justify-between gap-4 rounded-lg border p-4">
                    <div className="space-y-1">
                      <Label className="text-sm font-medium">Carimbo de tempo</Label>
                      <p className="text-xs text-muted-foreground">
                        Carimba o hash do documento numa autoridade RFC 3161 gratuita (prova de anterioridade
                        e integridade). Se indisponível, usa uma âncora interna. Sem custo.
                      </p>
                    </div>
                    <Switch
                      checked={assCfg.carimboTempo.ativo}
                      onCheckedChange={(v) =>
                        setAssCfg({ ...assCfg, carimboTempo: { ...assCfg.carimboTempo, ativo: v } })
                      }
                    />
                  </div>

                  {assCfg.carimboTempo.ativo && (
                    <div className="space-y-2">
                      <Label htmlFor="tsaUrl">URL da TSA (RFC 3161)</Label>
                      <Input
                        id="tsaUrl"
                        value={assCfg.carimboTempo.tsaUrl ?? ''}
                        placeholder="https://freetsa.org/tsr"
                        onChange={(e) =>
                          setAssCfg({ ...assCfg, carimboTempo: { ...assCfg.carimboTempo, tsaUrl: e.target.value } })
                        }
                      />
                      <p className="text-xs text-muted-foreground">
                        Padrão: freetsa.org (gratuita, sem fé pública ICP-Brasil). Deixe em branco para usar o padrão.
                      </p>
                    </div>
                  )}

                  <div className="flex items-start justify-between gap-4 rounded-lg border p-4">
                    <div className="space-y-1">
                      <Label className="text-sm font-medium">Confirmação por código (OTP) no aceite</Label>
                      <p className="text-xs text-muted-foreground">
                        Antes de assinar, o cliente confirma um código enviado ao seu canal (vincula a
                        assinatura à posse do e-mail/telefone). Adiciona uma etapa ao atendimento.
                      </p>
                    </div>
                    <Switch
                      checked={assCfg.otp.ativo}
                      onCheckedChange={(v) => setAssCfg({ ...assCfg, otp: { ...assCfg.otp, ativo: v } })}
                    />
                  </div>

                  {assCfg.otp.ativo && (
                    <div className="space-y-2">
                      <Label>Canal do código</Label>
                      <div className="flex gap-2">
                        <Button
                          type="button"
                          variant={assCfg.otp.canal === 'EMAIL' ? 'default' : 'outline'}
                          size="sm"
                          onClick={() => setAssCfg({ ...assCfg, otp: { ...assCfg.otp, canal: 'EMAIL' } })}
                        >
                          E-mail
                        </Button>
                        <Button
                          type="button"
                          variant={assCfg.otp.canal === 'WHATSAPP' ? 'default' : 'outline'}
                          size="sm"
                          onClick={() => setAssCfg({ ...assCfg, otp: { ...assCfg.otp, canal: 'WHATSAPP' } })}
                        >
                          WhatsApp
                        </Button>
                      </div>
                      <p className="text-xs text-muted-foreground">
                        E-mail: enviado direto ao cliente (canal mais forte). WhatsApp: gera um link para o
                        operador enviar o código (sem custo, requer o operador enviar).
                      </p>
                    </div>
                  )}

                  <div className="flex items-start justify-between gap-4 rounded-lg border p-4">
                    <div className="space-y-1">
                      <Label className="text-sm font-medium">Assinatura digital do PDF (PAdES)</Label>
                      <p className="text-xs text-muted-foreground">
                        Assina criptograficamente o PDF emitido → documento <strong>à prova de
                        adulteração</strong> (qualquer alteração é detectada no próprio arquivo, verificável
                        no Adobe Reader). Sem custo e sem mudar nada para o cliente. Usa um certificado
                        auto-assinado da plataforma (o Adobe indica a identidade como &quot;não
                        verificada&quot;, mas a integridade é garantida).
                      </p>
                    </div>
                    <Switch
                      checked={assCfg.pades.ativo}
                      onCheckedChange={(v) => setAssCfg({ ...assCfg, pades: { ...assCfg.pades, ativo: v } })}
                    />
                  </div>

                  <Button onClick={() => updateAss.mutate(assCfg)} disabled={updateAss.isPending} className="gap-2">
                    {updateAss.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                    Salvar configuração de assinatura
                  </Button>
                </>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* Unsaved changes indicator */}
      {hasChanges && (
        <div className="fixed bottom-4 right-4 bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200 px-4 py-2 rounded-md shadow-lg flex items-center gap-2">
          <AlertCircle className="h-4 w-4" />
          Você tem alterações não salvas
        </div>
      )}
    </div>
  )
}
