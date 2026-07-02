import { ChevronDown } from 'lucide-react'

const FAQS = [
  {
    pergunta: 'Preciso de cartão de crédito para testar?',
    resposta:
      'Não. O trial de 14 dias dá acesso completo ao sistema sem cartão. Ao final, você escolhe um plano — ou simplesmente para de usar.',
  },
  {
    pergunta: 'Como funciona a emissão da documentação NORMAM-212?',
    resposta:
      'No atendimento de balcão, o sistema monta a documentação exigida pela Marinha (NORMAM-212/DPC) com os dados do cliente, gera a GRU automaticamente com pagamento por PIX ou boleto e emite o pacote em PDF — pronto para envio à Capitania e para o cliente.',
  },
  {
    pergunta: 'A assinatura digital tem validade jurídica?',
    resposta:
      'Sim. O termo é assinado eletronicamente (Lei nº 14.063/2020 e MP 2.200-2/2001) com trilha de auditoria anexada ao PDF, verificação por código OTP e carimbo de tempo. Opcionalmente, o PDF sai com assinatura digital PAdES, que evidencia qualquer alteração posterior.',
  },
  {
    pergunta: 'Posso usar minha própria marca?',
    resposta:
      'Sim. Você configura seu logo e suas cores no painel (white-label) e ganha um endereço próprio no formato sua-marca.meujet.com.br.',
  },
  {
    pergunta: 'E se eu quiser cancelar?',
    resposta:
      'Sem fidelidade e sem multa: você cancela quando quiser e seus dados ficam disponíveis para exportação durante o período de retenção.',
  },
]

export function FaqSection() {
  return (
    <section className="py-28 bg-premium-navy">
      <div className="container max-w-3xl">
        <div className="text-center mb-14">
          <div className="inline-flex items-center gap-3 mb-6">
            <div className="h-px w-8 bg-gold/60" />
            <span className="text-gold/80 text-sm tracking-[0.3em] uppercase">Dúvidas frequentes</span>
            <div className="h-px w-8 bg-gold/60" />
          </div>
          <h2 className="font-display text-4xl font-medium text-white">
            Perguntas <span className="text-gold-gradient">frequentes</span>
          </h2>
        </div>

        <div className="space-y-3">
          {FAQS.map((faq) => (
            <details
              key={faq.pergunta}
              className="group rounded-xl border border-white/[0.08] bg-white/[0.02] open:border-gold/20"
            >
              <summary className="flex cursor-pointer items-center justify-between gap-4 px-6 py-4 text-white/90 [&::-webkit-details-marker]:hidden">
                <span className="font-medium">{faq.pergunta}</span>
                <ChevronDown className="h-4 w-4 shrink-0 text-gold transition-transform duration-300 group-open:rotate-180" />
              </summary>
              <p className="px-6 pb-5 text-sm leading-relaxed text-white/50">{faq.resposta}</p>
            </details>
          ))}
        </div>
      </div>
    </section>
  )
}
