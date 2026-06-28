'use client'

import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { configuracoesService } from '@/lib/api/services'
import type { ComissaoConfigRequest, TenantGeralConfigRequest } from '@/lib/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useToast } from '@/hooks/use-toast'
import { Loader2, Settings, Percent, Gift, Save, AlertCircle, Building2, Mail } from 'lucide-react'
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
