import type { Metadata } from 'next'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Central de Ajuda | Meu Jet',
  description:
    'Guias e perguntas frequentes do Meu Jet — para locadoras (painel, balcão, GRU, financeiro) e clientes (portal, reservas, habilitação).',
  robots: { index: true, follow: true },
}

/** Título de seção da central de ajuda. */
function H2({ children, id }: { children: React.ReactNode; id?: string }) {
  return (
    <h2 id={id} className="mt-12 mb-4 border-b pb-2 text-2xl font-semibold text-slate-900">
      {children}
    </h2>
  )
}

function Guia({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <div className="mb-6">
      <h3 className="mb-2 text-lg font-medium text-slate-900">{titulo}</h3>
      <div className="space-y-2 text-[15px] leading-relaxed text-slate-700">{children}</div>
    </div>
  )
}

function Pergunta({ q, children }: { q: string; children: React.ReactNode }) {
  return (
    <details className="group rounded-lg border p-4">
      <summary className="cursor-pointer font-medium text-slate-900 marker:content-none">
        {q}
      </summary>
      <div className="mt-2 text-[15px] leading-relaxed text-slate-700">{children}</div>
    </details>
  )
}

export default function AjudaPage() {
  return (
    <article className="bg-white">
      <div className="mx-auto max-w-3xl px-6 py-16">
        <h1 className="text-3xl font-bold text-slate-900">Central de Ajuda</h1>
        <p className="mt-2 text-slate-600">
          Guias rápidos do Meu Jet. Não achou o que precisava? Fale com a gente:{' '}
          <a href="mailto:suporte@meujet.com.br" className="text-sky-700 underline">
            suporte@meujet.com.br
          </a>
          .
        </p>

        <nav className="mt-6 flex flex-wrap gap-3 text-sm">
          <a href="#locadoras" className="rounded-full border px-3 py-1 hover:bg-slate-50">Para locadoras</a>
          <a href="#clientes" className="rounded-full border px-3 py-1 hover:bg-slate-50">Para clientes</a>
          <a href="#faq" className="rounded-full border px-3 py-1 hover:bg-slate-50">Perguntas frequentes</a>
        </nav>

        <H2 id="locadoras">Para locadoras (painel da empresa)</H2>

        <Guia titulo="Primeiros passos">
          <p>
            Depois da aprovação do cadastro, entre em <b>app.meujet.com.br</b> e siga o checklist
            &ldquo;Primeiros passos&rdquo; do painel: cadastre um <b>modelo</b> (com preço/hora),
            um <b>jetski</b>, um <b>instrutor</b> (necessário para emissões EMA), os dados da{' '}
            <b>Capitania</b>, a sua <b>chave PIX</b> (Configurações → Geral — é ela que recebe os
            pagamentos dos clientes) e convide sua <b>equipe</b>. Com isso, o balcão já opera.
          </p>
        </Guia>

        <Guia titulo="Atendimento de balcão (7 passos)">
          <p>
            Menu <b>Balcão</b>: identifique o <b>cliente</b> (ou cadastre na hora), escolha{' '}
            <b>passeio &amp; preço</b>, resolva a <b>habilitação</b> (CHA própria, reuso de
            temporária vigente ou emissão EMA com GRU), colete os <b>documentos</b>, faça o aceite
            dos <b>termos</b> (assinatura eletrônica com OTP), registre o <b>pagamento</b> e{' '}
            <b>emita</b> a documentação. Cada passo salva sozinho — dá para pausar e retomar pela
            página Pendências.
          </p>
        </Guia>

        <Guia titulo="Cobrança PIX no balcão">
          <p>
            No passo Pagamento, escolhendo <b>PIX</b> você pode gerar o <b>QR Code</b> na tela,
            enviar o copia-e-cola por <b>e-mail</b> ou por <b>WhatsApp</b>. O valor cai direto na
            chave PIX da sua loja. Depois de confirmar o recebimento no seu banco, clique em{' '}
            <b>Registrar pagamento</b> — isso alimenta o caixa e o fechamento do dia.
          </p>
        </Guia>

        <Guia titulo="GRU e emissão à Marinha (EMA)">
          <p>
            Para cliente sem habilitação, o fluxo EMA gera a <b>GRU</b> automaticamente (PIX ou
            boleto), acompanha o pagamento e monta a documentação NORMAM-212. Cada emissão consome{' '}
            <b>1 crédito</b> (compre mais em Plataforma → Créditos, via PIX). Quando a Marinha
            responder por e-mail, anexe a <b>devolutiva</b> na reserva — é ela que torna a
            habilitação temporária reutilizável em novas locações (em qualquer loja Meu Jet).
          </p>
        </Guia>

        <Guia titulo="Financeiro e fechamentos">
          <p>
            Cada reserva/locação tem um <b>extrato (folio)</b> com pagamentos, estornos e
            cobranças. O <b>fechamento diário</b> consolida o caixa por forma de pagamento
            (dinheiro/PIX/cartão) e o <b>mensal</b> calcula as comissões dos vendedores. Depois de
            fechado, o período trava — edições retroativas não são permitidas.
          </p>
        </Guia>

        <Guia titulo="Plano, faturas e limites">
          <p>
            Em <b>Plano e faturas</b> você acompanha o uso dos limites do seu plano (jetskis,
            locações do mês, usuários) e paga a mensalidade: a fatura chega por e-mail no início do
            mês com PIX copia-e-cola; depois de pagar, informe o número da transação e nossa equipe
            confirma. Fatura vencida além da carência suspende o acesso automaticamente — é só
            regularizar para reativar.
          </p>
        </Guia>

        <H2 id="clientes">Para clientes (portal)</H2>

        <Guia titulo="Reservar um passeio online">
          <p>
            No site da loja (ou em <b>meujet.com.br → Marketplace</b>), escolha a embarcação e o
            horário. A reserva é garantida com um <b>sinal de 30% via PIX</b> — pague pelo QR Code
            ou copia-e-cola e anexe o comprovante; a loja confirma em seguida. O restante é pago na
            loja, no dia do passeio.
          </p>
        </Guia>

        <Guia titulo="Habilitação para pilotar">
          <p>
            Não tem habilitação náutica? Dá para tirar a <b>CHA-MTA-E temporária</b> pela própria
            loja (fluxo EMA): você paga a taxa da Marinha (GRU), envia os documentos pelo portal e
            faz uma aula de demonstração. A temporária vale <b>30 dias</b> e pode ser reaproveitada
            em qualquer loja Meu Jet — seus documentos e habilitações ficam na sua conta, em{' '}
            <b>Minhas habilitações</b>.
          </p>
        </Guia>

        <Guia titulo="Sua conta e seus dados">
          <p>
            O login do portal funciona por e-mail ou CPF. Em <b>Perfil</b> você gerencia seus dados
            e documentos por loja. Seus direitos sobre dados pessoais estão descritos na{' '}
            <Link href="/privacidade" className="text-sky-700 underline">Política de Privacidade</Link>
            {' '}— pedidos de acesso/correção podem ser feitos à loja ou pelo nosso suporte.
          </p>
        </Guia>

        <H2 id="faq">Perguntas frequentes</H2>
        <div className="space-y-3">
          <Pergunta q="Esqueci minha senha — como recupero?">
            Na tela de login, use &ldquo;Esqueci minha senha&rdquo;. O link de redefinição chega
            por e-mail (verifique o spam). Se o e-mail cadastrado mudou, fale com o administrador
            da sua empresa (equipe) ou com a loja (clientes).
          </Pergunta>
          <Pergunta q="O QR Code PIX não é aceito pelo banco. E agora?">
            Confirme se a chave PIX da loja está correta em Configurações → Geral (CPF/CNPJ apenas
            números; telefone no formato +55DDD…). O QR gerado usa o padrão oficial do Banco
            Central — se a chave estiver registrada, qualquer banco aceita.
          </Pergunta>
          <Pergunta q="Quanto custa emitir a documentação à Marinha?">
            Cada emissão consome 1 crédito Meu Jet (comprado via PIX no painel) + a taxa da GRU,
            que é um tributo federal pago diretamente à União pelo interessado.
          </Pergunta>
          <Pergunta q="A GRU não gerou automaticamente. O que fazer?">
            O site da Marinha às vezes fica indisponível. O painel oferece o fluxo manual: gere a
            GRU no site do governo e digite o número/valor no passo Habilitação — o restante do
            fluxo segue igual.
          </Pergunta>
          <Pergunta q="Atingi o limite do meu plano. Como faço upgrade?">
            A página Plano e faturas mostra seu uso. Para mudar de plano, fale com o Meu Jet
            (suporte@meujet.com.br) — a troca é imediata e a próxima fatura já sai no novo valor.
          </Pergunta>
          <Pergunta q="Minha empresa foi suspensa. Por quê?">
            As causas comuns são: fim do período de teste sem contratação ou fatura vencida além
            da carência. Regularize o pagamento (ou contrate um plano) e o acesso é reativado.
          </Pergunta>
          <Pergunta q="Cliente não compareceu (no-show). Como registro?">
            Na agenda ou na página da reserva, use a ação &ldquo;Não compareceu&rdquo;. O valor já
            pago fica no folio — a devolução (se a sua política previr) é registrada como estorno
            manual.
          </Pergunta>
          <Pergunta q="Posso zerar os dados de teste antes de operar de verdade?">
            Sim — peça ao suporte o <b>reset da empresa</b>: apagamos reservas/clientes de teste e
            mantemos frota, equipe e configurações. Um arquivo de backup é gerado antes, sempre.
          </Pergunta>
          <Pergunta q="A assinatura eletrônica dos termos tem validade jurídica?">
            Sim. O aceite registra data/hora, IP, dispositivo e código OTP, com carimbo de tempo e
            hash do documento (Lei 14.063/2020 e MP 2.200-2/2001). A loja pode ainda ativar o PDF
            assinado digitalmente (PAdES) nas configurações.
          </Pergunta>
          <Pergunta q="Como excluo minha conta ou meus dados?">
            Clientes: solicite à loja (controladora dos seus dados de locação) ou ao nosso
            suporte. Empresas: a exclusão da conta é feita pelo suporte, com exportação dos dados
            e prazo de 30 dias, conforme os <Link href="/termos" className="text-sky-700 underline">Termos de Uso</Link>.
          </Pergunta>
        </div>

        <H2>Contato</H2>
        <p className="text-[15px] text-slate-700">
          Suporte:{' '}
          <a href="mailto:suporte@meujet.com.br" className="text-sky-700 underline">
            suporte@meujet.com.br
          </a>{' '}
          (respondemos em horário comercial). Documentos:{' '}
          <Link href="/termos" className="text-sky-700 underline">Termos de Uso</Link> ·{' '}
          <Link href="/privacidade" className="text-sky-700 underline">Política de Privacidade</Link>.
        </p>
      </div>
    </article>
  )
}
