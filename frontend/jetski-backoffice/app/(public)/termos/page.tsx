import type { Metadata } from 'next'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Termos de Uso | Meu Jet',
  description: 'Termos de Uso da plataforma Meu Jet — sistema de gestão para locadoras de motos aquáticas.',
  robots: { index: true, follow: true },
}

/** Título de seção dos documentos legais. */
function H2({ children }: { children: React.ReactNode }) {
  return <h2 className="mt-10 mb-3 text-xl font-semibold text-slate-900">{children}</h2>
}

export default function TermosPage() {
  return (
    <article className="bg-white">
      <div className="mx-auto max-w-3xl px-6 py-16 text-[15px] leading-relaxed text-slate-700">
        <h1 className="text-3xl font-bold text-slate-900">Termos de Uso</h1>
        <p className="mt-2 text-sm text-slate-500">Última atualização: 10 de julho de 2026</p>

        <p className="mt-6">
          Estes Termos de Uso (&ldquo;Termos&rdquo;) regem o acesso e a utilização da plataforma{' '}
          <strong>Meu Jet</strong> (&ldquo;Plataforma&rdquo;), disponível em meujet.com.br, operada
          por <strong>Fcf Tecnologia Ltda</strong>, inscrita no CNPJ sob o nº{' '}
          <strong>93.365.124/0001-51</strong> (&ldquo;Meu Jet&rdquo;, &ldquo;nós&rdquo;). Ao criar uma conta ou
          utilizar a Plataforma, você declara ter lido, compreendido e aceito estes Termos e a{' '}
          <Link href="/privacidade" className="text-sky-700 underline">
            Política de Privacidade
          </Link>
          .
        </p>

        <H2>1. Definições</H2>
        <ul className="list-disc space-y-1 pl-6">
          <li>
            <strong>Locadora</strong>: pessoa jurídica que contrata a Plataforma para gerir sua
            operação de locação de motos aquáticas (jetskis).
          </li>
          <li>
            <strong>Usuário</strong>: pessoa física autorizada pela Locadora a operar a Plataforma
            (administrador, gerente, operador, vendedor, mecânico ou financeiro).
          </li>
          <li>
            <strong>Cliente Final</strong>: pessoa física que aluga embarcações da Locadora e pode
            acessar o Portal do Cliente.
          </li>
          <li>
            <strong>Portal do Cliente</strong>: área de autoatendimento em que o Cliente Final
            acompanha reservas, documentos e pagamentos junto à Locadora.
          </li>
          <li>
            <strong>Créditos de Emissão</strong>: unidades adquiridas pela Locadora, consumidas a
            cada emissão de documentação à Marinha do Brasil por meio da Plataforma.
          </li>
        </ul>

        <H2>2. Objeto</H2>
        <p>
          A Plataforma é um sistema de gestão para locadoras de motos aquáticas, que inclui: agenda
          e reservas; atendimento de balcão; check-in e check-out com registro fotográfico;
          documentação exigida pela NORMAM-212 (incluindo geração de GRU e envio de documentação de
          habilitação à Marinha do Brasil); assinatura eletrônica de termos; controle de
          combustível, manutenção, comissões e fechamentos financeiros; vitrine pública
          (marketplace) e Portal do Cliente. A Plataforma é fornecida no modelo de software como
          serviço (SaaS), acessada pela internet.
        </p>

        <H2>3. Cadastro, conta e acesso</H2>
        <p>
          O cadastro da Locadora está sujeito a análise e aprovação pelo Meu Jet. A Locadora declara
          que as informações fornecidas são verdadeiras, completas e atualizadas, e que possui
          capacidade legal para contratar. As credenciais de acesso são pessoais e intransferíveis;
          a Locadora responde pelos atos praticados pelos seus Usuários na Plataforma, cabendo a ela
          conceder e revogar acessos conforme sua própria política interna.
        </p>

        <H2>4. Período de teste e planos</H2>
        <p>
          Após a aprovação, a Locadora tem acesso a um período de teste gratuito de 14 (quatorze)
          dias. Encerrado o teste sem contratação de plano, o acesso é suspenso automaticamente —
          os dados são preservados conforme a seção 12. Os valores, limites e condições dos planos
          são os divulgados na Plataforma ou acordados diretamente com o Meu Jet.
        </p>

        <H2>5. Créditos de Emissão</H2>
        <p>
          Cada emissão de documentação à Marinha do Brasil consome 1 (um) Crédito de Emissão. Os
          créditos são adquiridos mediante pagamento via PIX e liberados após a confirmação do
          pagamento. Créditos não expiram enquanto a conta estiver ativa e não são conversíveis em
          dinheiro; valores pagos não são reembolsáveis, salvo erro de cobrança imputável ao Meu
          Jet. As taxas da GRU (Guia de Recolhimento da União) são tributos federais pagos pelo
          interessado diretamente à União e não se confundem com os Créditos de Emissão.
        </p>

        <H2>6. Responsabilidades da Locadora</H2>
        <ul className="list-disc space-y-1 pl-6">
          <li>
            Garantir a veracidade e a qualidade dos dados inseridos na Plataforma, inclusive os
            dados pessoais dos seus Clientes Finais;
          </li>
          <li>
            Cumprir a legislação aplicável à sua atividade, em especial as normas da Autoridade
            Marítima (NORMAM-212) e a legislação de proteção de dados (LGPD), na qualidade de
            controladora dos dados dos seus Clientes Finais;
          </li>
          <li>
            Conferir os documentos e informações antes de solicitar emissões à Marinha — a
            Plataforma automatiza o preenchimento e o envio, mas o conteúdo é de responsabilidade da
            Locadora e do Cliente Final;
          </li>
          <li>
            Utilizar a Plataforma apenas para fins lícitos, sem tentar burlar controles de acesso,
            realizar engenharia reversa ou sobrecarregar a infraestrutura.
          </li>
        </ul>

        <H2>7. Responsabilidades do Meu Jet</H2>
        <p>
          O Meu Jet empregará esforços comercialmente razoáveis para manter a Plataforma disponível
          e segura, realizando cópias de segurança diárias. A Plataforma é fornecida &ldquo;no
          estado em que se encontra&rdquo;, sem garantia de disponibilidade ininterrupta. Rotinas
          que dependem de sistemas de terceiros — em especial os sistemas do Governo Federal para
          geração de GRU e o recebimento de e-mails pela Marinha — podem ficar indisponíveis por
          causas alheias ao Meu Jet; nesses casos, a Plataforma oferece, quando possível, fluxo
          manual alternativo.
        </p>

        <H2>8. Pagamentos entre Locadora e Cliente Final</H2>
        <p>
          A Plataforma não intermedeia nem processa pagamentos entre a Locadora e seus Clientes
          Finais. Cobranças via PIX geradas pela Plataforma (QR Code ou copia-e-cola) utilizam a
          chave PIX da própria Locadora, e os valores são recebidos diretamente por ela. A
          conferência do recebimento é de responsabilidade da Locadora.
        </p>

        <H2>9. Propriedade intelectual</H2>
        <p>
          O software, a marca Meu Jet, o layout e todos os componentes da Plataforma pertencem ao
          Meu Jet ou a seus licenciantes. A contratação concede à Locadora uma licença de uso
          limitada, não exclusiva e intransferível, vigente enquanto durar a relação contratual. Os
          dados inseridos pela Locadora pertencem a ela.
        </p>

        <H2>10. Privacidade e proteção de dados</H2>
        <p>
          O tratamento de dados pessoais na Plataforma é descrito na{' '}
          <Link href="/privacidade" className="text-sky-700 underline">
            Política de Privacidade
          </Link>
          , que integra estes Termos. Em resumo: o Meu Jet atua como operador dos dados dos
          Clientes Finais tratados por conta e ordem da Locadora, e como controlador dos dados
          cadastrais das Locadoras, dos Usuários e das contas de acesso do Portal do Cliente.
        </p>

        <H2>11. Suspensão e encerramento</H2>
        <p>
          O Meu Jet pode suspender o acesso em caso de inadimplência, violação destes Termos, risco
          à segurança da Plataforma ou determinação legal. A Locadora pode encerrar a conta a
          qualquer momento. Após o encerramento ou suspensão definitiva, os dados da Locadora
          permanecem disponíveis para exportação por 30 (trinta) dias mediante solicitação ao
          suporte, ressalvados os registros cuja manutenção seja exigida por lei.
        </p>

        <H2>12. Limitação de responsabilidade</H2>
        <p>
          Na máxima extensão permitida pela lei, a responsabilidade total do Meu Jet por danos
          decorrentes do uso da Plataforma fica limitada ao valor efetivamente pago pela Locadora
          nos 12 (doze) meses anteriores ao evento. O Meu Jet não responde por lucros cessantes,
          danos indiretos, atos dos Usuários ou dos Clientes Finais, decisões da Autoridade
          Marítima, nem por indisponibilidade de serviços governamentais ou de terceiros.
        </p>

        <H2>13. Alterações destes Termos</H2>
        <p>
          Estes Termos podem ser atualizados. Mudanças relevantes serão comunicadas com
          antecedência razoável pelos canais da Plataforma. O uso continuado após a vigência da
          nova versão implica concordância.
        </p>

        <H2>14. Lei aplicável e foro</H2>
        <p>
          Estes Termos são regidos pelas leis da República Federativa do Brasil. Fica eleito o foro
          da comarca de <strong>São Paulo/SP</strong>, com renúncia a qualquer outro, por
          mais privilegiado que seja.
        </p>

        <H2>15. Contato</H2>
        <p>
          Dúvidas sobre estes Termos: <a href="mailto:suporte@meujet.com.br" className="text-sky-700 underline">suporte@meujet.com.br</a>.
        </p>
      </div>
    </article>
  )
}
