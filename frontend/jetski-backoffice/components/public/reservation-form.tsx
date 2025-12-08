'use client'

import { useState } from 'react'
import { X, Calendar, Clock, User, Phone, MessageCircle, Check } from 'lucide-react'

interface Offering {
  id: string
  modelo: string
  tipo: 'JETSKI' | 'LANCHA'
  empresa: string
  empresaWhatsapp: string
  precoHora?: number
  precoPacote30min?: number
  precoMeiaDiaria?: number
  precoDiaria?: number
  localizacao: string
  horarios: string[]
}

interface ReservationFormProps {
  offering: Offering
  onClose: () => void
}

export function ReservationForm({ offering, onClose }: ReservationFormProps) {
  const [step, setStep] = useState(1)
  const [formData, setFormData] = useState({
    data: '',
    horario: '',
    duracao: offering.tipo === 'JETSKI' ? '30min' : 'meia-diaria',
    nome: '',
    telefone: '',
    email: '',
    observacoes: '',
  })

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    }).format(value)
  }

  // Calcular preço baseado na duração
  const calculatePrice = () => {
    if (offering.tipo === 'LANCHA') {
      if (formData.duracao === 'meia-diaria') {
        return offering.precoMeiaDiaria || 0
      }
      return offering.precoDiaria || 0
    }
    // Jetski
    if (formData.duracao === '30min') {
      return offering.precoPacote30min || 0
    }
    if (formData.duracao === '1h') {
      return offering.precoHora || 0
    }
    if (formData.duracao === '2h') {
      return (offering.precoHora || 0) * 1.8 // 10% desconto
    }
    return 0
  }

  // Gerar mensagem do WhatsApp
  const generateWhatsAppMessage = () => {
    const preco = calculatePrice()
    const duracaoLabel =
      formData.duracao === '30min'
        ? '30 minutos'
        : formData.duracao === '1h'
        ? '1 hora'
        : formData.duracao === '2h'
        ? '2 horas'
        : formData.duracao === 'meia-diaria'
        ? 'Meia diária'
        : 'Diária completa'

    const message = `🚤 *NOVA RESERVA - Pega o Jet*

📋 *Embarcação:* ${offering.modelo}
📍 *Local:* ${offering.localizacao}

📅 *Data:* ${formatDate(formData.data)}
⏰ *Horário:* ${formData.horario}
⏱️ *Duração:* ${duracaoLabel}
💰 *Valor:* ${formatCurrency(preco)}

👤 *Cliente:* ${formData.nome}
📱 *Telefone:* ${formData.telefone}
📧 *Email:* ${formData.email}
${formData.observacoes ? `\n📝 *Observações:* ${formData.observacoes}` : ''}

_Aguardo confirmação da disponibilidade._`

    return encodeURIComponent(message)
  }

  const formatDate = (dateStr: string) => {
    if (!dateStr) return ''
    const date = new Date(dateStr + 'T00:00:00')
    return date.toLocaleDateString('pt-BR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    })
  }

  // Gerar datas disponíveis (próximos 30 dias)
  const getAvailableDates = () => {
    const dates = []
    const today = new Date()
    for (let i = 1; i <= 30; i++) {
      const date = new Date(today)
      date.setDate(today.getDate() + i)
      dates.push(date.toISOString().split('T')[0])
    }
    return dates
  }

  const handleSubmit = () => {
    const message = generateWhatsAppMessage()
    const whatsappUrl = `https://wa.me/${offering.empresaWhatsapp}?text=${message}`
    window.open(whatsappUrl, '_blank')
    setStep(3) // Mostrar confirmação
  }

  const isStep1Valid = formData.data && formData.horario && formData.duracao
  const isStep2Valid = formData.nome && formData.telefone

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/80 backdrop-blur-sm"
        onClick={onClose}
      />

      {/* Modal - Full height on mobile, centered on desktop */}
      <div className="relative w-full sm:max-w-lg max-h-[95vh] sm:max-h-[90vh] bg-zinc-900 rounded-t-2xl sm:rounded-2xl overflow-hidden flex flex-col sm:m-4">
        {/* Header - Fixed */}
        <div className="flex-shrink-0 flex items-center justify-between p-4 sm:p-6 border-b border-white/10">
          <div>
            <h2 className="text-xl font-display text-white">Fazer Reserva</h2>
            <p className="text-white/50 text-sm">{offering.modelo}</p>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-white/50 hover:text-white transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Progress Steps - Fixed */}
        <div className="flex-shrink-0 flex items-center justify-center gap-2 py-3 sm:py-4 border-b border-white/10">
          {[1, 2, 3].map((s) => (
            <div key={s} className="flex items-center gap-2">
              <div
                className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium transition-colors ${
                  step >= s
                    ? 'bg-gold text-black'
                    : 'bg-white/10 text-white/50'
                }`}
              >
                {step > s ? <Check className="h-4 w-4" /> : s}
              </div>
              {s < 3 && (
                <div
                  className={`w-12 h-0.5 ${
                    step > s ? 'bg-gold' : 'bg-white/10'
                  }`}
                />
              )}
            </div>
          ))}
        </div>

        {/* Content - Scrollable */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-6">
          {step === 1 && (
            <div className="space-y-6">
              <h3 className="text-white font-medium flex items-center gap-2">
                <Calendar className="h-5 w-5 text-gold" />
                Escolha a data e horário
              </h3>

              {/* Data */}
              <div>
                <label className="block text-white/60 text-sm mb-2">
                  Data do passeio
                </label>
                <input
                  type="date"
                  value={formData.data}
                  min={getAvailableDates()[0]}
                  max={getAvailableDates()[29]}
                  onChange={(e) =>
                    setFormData({ ...formData, data: e.target.value })
                  }
                  className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-gold transition-colors"
                />
              </div>

              {/* Horário */}
              <div>
                <label className="block text-white/60 text-sm mb-2">
                  Horário de início
                </label>
                <div className="grid grid-cols-4 gap-2">
                  {offering.horarios.map((horario) => (
                    <button
                      key={horario}
                      onClick={() =>
                        setFormData({ ...formData, horario: horario })
                      }
                      className={`px-3 py-2 rounded-lg text-sm font-medium transition-all ${
                        formData.horario === horario
                          ? 'bg-gold text-black'
                          : 'bg-white/5 text-white/70 hover:bg-white/10'
                      }`}
                    >
                      {horario.length > 5 ? horario.split(' ')[0] : horario}
                    </button>
                  ))}
                </div>
              </div>

              {/* Duração */}
              <div>
                <label className="block text-white/60 text-sm mb-2">
                  Duração
                </label>
                <div className="grid grid-cols-2 gap-2">
                  {offering.tipo === 'JETSKI' ? (
                    <>
                      <button
                        onClick={() =>
                          setFormData({ ...formData, duracao: '30min' })
                        }
                        className={`px-4 py-3 rounded-lg text-sm font-medium transition-all ${
                          formData.duracao === '30min'
                            ? 'bg-gold text-black'
                            : 'bg-white/5 text-white/70 hover:bg-white/10'
                        }`}
                      >
                        <div>30 minutos</div>
                        <div className="text-xs opacity-70">
                          {formatCurrency(offering.precoPacote30min || 0)}
                        </div>
                      </button>
                      <button
                        onClick={() =>
                          setFormData({ ...formData, duracao: '1h' })
                        }
                        className={`px-4 py-3 rounded-lg text-sm font-medium transition-all ${
                          formData.duracao === '1h'
                            ? 'bg-gold text-black'
                            : 'bg-white/5 text-white/70 hover:bg-white/10'
                        }`}
                      >
                        <div>1 hora</div>
                        <div className="text-xs opacity-70">
                          {formatCurrency(offering.precoHora || 0)}
                        </div>
                      </button>
                      <button
                        onClick={() =>
                          setFormData({ ...formData, duracao: '2h' })
                        }
                        className={`px-4 py-3 rounded-lg text-sm font-medium transition-all col-span-2 ${
                          formData.duracao === '2h'
                            ? 'bg-gold text-black'
                            : 'bg-white/5 text-white/70 hover:bg-white/10'
                        }`}
                      >
                        <div>2 horas</div>
                        <div className="text-xs opacity-70">
                          {formatCurrency((offering.precoHora || 0) * 1.8)} (10%
                          off)
                        </div>
                      </button>
                    </>
                  ) : (
                    <>
                      <button
                        onClick={() =>
                          setFormData({ ...formData, duracao: 'meia-diaria' })
                        }
                        className={`px-4 py-3 rounded-lg text-sm font-medium transition-all ${
                          formData.duracao === 'meia-diaria'
                            ? 'bg-gold text-black'
                            : 'bg-white/5 text-white/70 hover:bg-white/10'
                        }`}
                      >
                        <div>Meia diária</div>
                        <div className="text-xs opacity-70">
                          {formatCurrency(offering.precoMeiaDiaria || 0)}
                        </div>
                      </button>
                      <button
                        onClick={() =>
                          setFormData({ ...formData, duracao: 'diaria' })
                        }
                        className={`px-4 py-3 rounded-lg text-sm font-medium transition-all ${
                          formData.duracao === 'diaria'
                            ? 'bg-gold text-black'
                            : 'bg-white/5 text-white/70 hover:bg-white/10'
                        }`}
                      >
                        <div>Diária completa</div>
                        <div className="text-xs opacity-70">
                          {formatCurrency(offering.precoDiaria || 0)}
                        </div>
                      </button>
                    </>
                  )}
                </div>
              </div>
            </div>
          )}

          {step === 2 && (
            <div className="space-y-6">
              <h3 className="text-white font-medium flex items-center gap-2">
                <User className="h-5 w-5 text-gold" />
                Seus dados
              </h3>

              <div>
                <label className="block text-white/60 text-sm mb-2">
                  Nome completo *
                </label>
                <input
                  type="text"
                  value={formData.nome}
                  onChange={(e) =>
                    setFormData({ ...formData, nome: e.target.value })
                  }
                  placeholder="Digite seu nome"
                  className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-3 text-white placeholder:text-white/30 focus:outline-none focus:border-gold transition-colors"
                />
              </div>

              <div>
                <label className="block text-white/60 text-sm mb-2">
                  WhatsApp / Telefone *
                </label>
                <input
                  type="tel"
                  value={formData.telefone}
                  onChange={(e) =>
                    setFormData({ ...formData, telefone: e.target.value })
                  }
                  placeholder="(00) 00000-0000"
                  className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-3 text-white placeholder:text-white/30 focus:outline-none focus:border-gold transition-colors"
                />
              </div>

              <div>
                <label className="block text-white/60 text-sm mb-2">
                  Email (opcional)
                </label>
                <input
                  type="email"
                  value={formData.email}
                  onChange={(e) =>
                    setFormData({ ...formData, email: e.target.value })
                  }
                  placeholder="seu@email.com"
                  className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-3 text-white placeholder:text-white/30 focus:outline-none focus:border-gold transition-colors"
                />
              </div>

              <div>
                <label className="block text-white/60 text-sm mb-2">
                  Observações (opcional)
                </label>
                <textarea
                  value={formData.observacoes}
                  onChange={(e) =>
                    setFormData({ ...formData, observacoes: e.target.value })
                  }
                  placeholder="Alguma informação adicional..."
                  rows={3}
                  className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-3 text-white placeholder:text-white/30 focus:outline-none focus:border-gold transition-colors resize-none"
                />
              </div>

              {/* Resumo */}
              <div className="bg-white/5 rounded-lg p-4 space-y-2">
                <h4 className="text-white/50 text-xs uppercase tracking-wider">
                  Resumo
                </h4>
                <div className="flex justify-between text-sm">
                  <span className="text-white/70">Data</span>
                  <span className="text-white">{formatDate(formData.data)}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-white/70">Horário</span>
                  <span className="text-white">{formData.horario}</span>
                </div>
                <div className="flex justify-between text-sm pt-2 border-t border-white/10">
                  <span className="text-white font-medium">Total</span>
                  <span className="text-gold font-medium">
                    {formatCurrency(calculatePrice())}
                  </span>
                </div>
              </div>
            </div>
          )}

          {step === 3 && (
            <div className="text-center py-8">
              <div className="w-16 h-16 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-6">
                <Check className="h-8 w-8 text-green-500" />
              </div>
              <h3 className="text-xl font-display text-white mb-2">
                Reserva enviada!
              </h3>
              <p className="text-white/60 mb-6">
                Sua solicitação foi enviada via WhatsApp para {offering.empresa}.
                Aguarde a confirmação de disponibilidade.
              </p>
              <button
                onClick={onClose}
                className="px-8 py-3 bg-gold text-black font-medium hover:bg-gold/90 transition-colors"
              >
                Fechar
              </button>
            </div>
          )}
        </div>

        {/* Footer - Fixed */}
        {step < 3 && (
          <div className="flex-shrink-0 flex gap-3 p-4 sm:p-6 border-t border-white/10 bg-zinc-900">
            {step > 1 && (
              <button
                onClick={() => setStep(step - 1)}
                className="flex-1 px-6 py-3 border border-white/20 text-white font-medium hover:bg-white/5 transition-colors rounded-lg"
              >
                Voltar
              </button>
            )}
            {step === 1 && (
              <button
                onClick={() => setStep(2)}
                disabled={!isStep1Valid}
                className="flex-1 px-6 py-3 bg-gold text-black font-medium hover:bg-gold/90 transition-colors rounded-lg disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Continuar
              </button>
            )}
            {step === 2 && (
              <button
                onClick={handleSubmit}
                disabled={!isStep2Valid}
                className="flex-1 px-6 py-3 bg-green-600 text-white font-medium hover:bg-green-700 transition-colors rounded-lg disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
              >
                <MessageCircle className="h-5 w-5" />
                Enviar via WhatsApp
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
