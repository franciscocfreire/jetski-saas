import type { Metadata } from 'next'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Política de Privacidade | Meu Jet',
  description:
    'Política de Privacidade da plataforma Meu Jet — como tratamos dados pessoais conforme a LGPD.',
  robots: { index: true, follow: true },
}

/** Título de seção dos documentos legais. */
function H2({ children, id }: { children: React.ReactNode; id?: string }) {
  return (
    <h2 id={id} className="mt-10 mb-3 text-xl font-semibold text-slate-900">
      {children}
    </h2>
  )
}

export default function PrivacidadePage() {
  return (
    <article className="bg-white">
      <div className="mx-auto max-w-3xl px-6 py-16 text-[15px] leading-relaxed text-slate-700">
        <h1 className="text-3xl font-bold text-slate-900">Política de Privacidade</h1>
        <p className="mt-2 text-sm text-slate-500">Última atualização: 10 de julho de 2026</p>

        <p className="mt-6">
          Esta Política descreve como a plataforma <strong>Meu Jet</strong>, operada por{' '}
          <strong>[RAZÃO SOCIAL DA EMPRESA]</strong> (CNPJ <strong>[CNPJ]</strong>), trata dados
          pessoais, em conformidade com a Lei Geral de Proteção de Dados (Lei nº 13.709/2018 —
          &ldquo;LGPD&rdquo;). Ela vale para o site meujet.com.br, o painel das locadoras
          (backoffice) e o Portal do Cliente. Leia-a junto com os{' '}
          <Link href="/termos" className="text-sky-700 underline">
            Termos de Uso
          </Link>
          .
        </p>

        <H2>1. Papéis no tratamento (quem responde pelo quê)</H2>
        <ul className="list-disc space-y-1 pl-6">
          <li>
            <strong>Meu Jet como controlador</strong>: dados cadastrais das locadoras e de seus
            usuários (equipe), dados da conta de acesso do Portal do Cliente (e-mail/CPF de login) e
            dados de navegação na Plataforma.
          </li>
          <li>
            <strong>Meu Jet como operador</strong>: dados pessoais dos clientes finais registrados
            pelas locadoras (ou pelo próprio cliente no Portal) para executar locações e emitir a
            documentação náutica. Nesses casos, a <strong>locadora é a controladora</strong> e
            define as finalidades; o Meu Jet trata os dados por conta e ordem dela.
          </li>
        </ul>

        <H2>2. Dados que tratamos</H2>
        <p className="font-medium text-slate-900">Das locadoras e suas equipes:</p>
        <ul className="list-disc space-y-1 pl-6">
          <li>Razão social, CNPJ, endereço, cidade, contatos e chave PIX de recebimento;</li>
          <li>Nome, e-mail e papéis de acesso dos usuários da equipe;</li>
          <li>Registros de uso e auditoria (ações relevantes, com data/hora e autor).</li>
        </ul>
        <p className="mt-3 font-medium text-slate-900">
          Dos clientes finais (em nome da locadora):
        </p>
        <ul className="list-disc space-y-1 pl-6">
          <li>
            Identificação: nome, CPF, RG/órgão emissor, data de nascimento, gênero, nacionalidade,
            naturalidade e endereço;
          </li>
          <li>Contato: e-mail, telefone e WhatsApp;</li>
          <li>
            Documentos e imagens exigidos pela NORMAM-212: foto do documento de identidade (RG/CNH),
            foto do rosto (selfie), comprovante de residência e{' '}
            <strong>autodeclaração de saúde</strong> (dado sensível, tratado exclusivamente para a
            habilitação náutica);
          </li>
          <li>
            Evidências de assinatura eletrônica dos termos de locação: data/hora, endereço IP,
            identificação do dispositivo, código de verificação (OTP) e resumo criptográfico (hash)
            do documento assinado;
          </li>
          <li>Fotos da embarcação no check-in e no check-out;</li>
          <li>Reservas, pagamentos registrados pela locadora e avaliações do passeio.</li>
        </ul>
        <p className="mt-3 font-medium text-slate-900">De navegação:</p>
        <ul className="list-disc space-y-1 pl-6">
          <li>
            Cookies estritamente necessários (sessão e autenticação) e registros técnicos (logs) de
            acesso. <strong>Não usamos cookies de publicidade nem rastreadores de terceiros.</strong>
          </li>
        </ul>

        <H2>3. Finalidades e bases legais</H2>
        <ul className="list-disc space-y-1 pl-6">
          <li>
            <strong>Execução de contrato</strong> (art. 7º, V): operar reservas, locações,
            pagamentos, Portal do Cliente e a conta da locadora;
          </li>
          <li>
            <strong>Cumprimento de obrigação legal/regulatória</strong> (art. 7º, II): emissão da
            documentação de habilitação náutica e envio à Marinha do Brasil conforme a NORMAM-212;
            guarda de registros fiscais e de auditoria;
          </li>
          <li>
            <strong>Tutela de direitos</strong> (art. 7º, VI): evidências de assinatura eletrônica
            e trilhas de auditoria, para comprovar a manifestação de vontade nas locações;
          </li>
          <li>
            <strong>Legítimo interesse</strong> (art. 7º, IX): segurança da Plataforma, prevenção a
            fraudes e melhoria do serviço;
          </li>
          <li>
            <strong>Saúde — autodeclaração</strong>: tratada como dado sensível para cumprimento da
            regulamentação da Autoridade Marítima aplicável à habilitação (art. 11, II).
          </li>
        </ul>

        <H2>4. Compartilhamento</H2>
        <p>Não vendemos dados pessoais. Compartilhamos dados apenas com:</p>
        <ul className="list-disc space-y-1 pl-6">
          <li>
            <strong>Marinha do Brasil</strong>: documentação de habilitação náutica do cliente
            final, quando a emissão é solicitada (obrigação regulatória);
          </li>
          <li>
            <strong>Provedores de infraestrutura</strong>, na medida necessária à operação:
            hospedagem em nuvem com dados armazenados no Brasil (Oracle Cloud, região São Paulo),
            proteção de tráfego (Cloudflare), envio de e-mails transacionais e armazenamento de
            cópias de segurança (Google);
          </li>
          <li>
            <strong>A própria locadora</strong> da qual o cliente final é cliente — que é a
            controladora desses dados;
          </li>
          <li>
            <strong>Autoridades</strong>, mediante obrigação legal ou ordem de autoridade
            competente.
          </li>
        </ul>

        <H2>5. Segurança</H2>
        <p>
          Adotamos medidas técnicas e organizacionais proporcionais ao risco: criptografia em
          trânsito (TLS), isolamento de dados por locadora em nível de banco de dados, controle de
          acesso por papéis, credenciais sensíveis cifradas em repouso, registro de auditoria e
          cópias de segurança diárias com verificação de integridade. Nenhum sistema é infalível;
          incidentes de segurança relevantes serão comunicados aos afetados e à ANPD na forma da
          lei.
        </p>

        <H2>6. Retenção</H2>
        <p>
          Mantemos os dados enquanto a conta estiver ativa e pelos prazos exigidos por lei após o
          encerramento — em especial registros de locação, documentos emitidos à Marinha, registros
          fiscais e evidências de assinatura eletrônica, que precisam ser preservados para
          comprovação legal. Dados sem exigência de guarda são excluídos ou anonimizados após o
          encerramento da relação.
        </p>

        <H2>7. Direitos do titular</H2>
        <p>
          Nos termos do art. 18 da LGPD, você pode solicitar: confirmação de tratamento, acesso,
          correção, anonimização, portabilidade, eliminação (quando cabível), informação sobre
          compartilhamentos e revisão de decisões automatizadas. <strong>Cliente final</strong>:
          como a locadora é a controladora dos seus dados de locação, direcione o pedido à locadora
          — o Meu Jet dará o suporte técnico necessário. Pedidos sobre a conta do Portal ou sobre
          dados controlados pelo Meu Jet podem ser feitos diretamente pelo canal abaixo.
        </p>

        <H2 id="cookies">8. Cookies</H2>
        <p>
          Utilizamos apenas cookies estritamente necessários: sessão autenticada (login do painel e
          do Portal) e proteção contra falsificação de requisições. Eles não são usados para
          publicidade e não podem ser desativados sem impedir o funcionamento da Plataforma.
        </p>

        <H2>9. Crianças e adolescentes</H2>
        <p>
          A Plataforma não é dirigida a menores de 18 anos. A condução de motos aquáticas exige
          habilitação de maiores; cadastros de menores como passageiros, quando necessários, são de
          responsabilidade da locadora e do responsável legal.
        </p>

        <H2>10. Encarregado e contato</H2>
        <p>
          Encarregado pelo tratamento de dados (DPO): <strong>[NOME DO ENCARREGADO]</strong> —{' '}
          <a href="mailto:suporte@meujet.com.br" className="text-sky-700 underline">
            suporte@meujet.com.br
          </a>
          . Responderemos às solicitações nos prazos da LGPD.
        </p>

        <H2>11. Alterações</H2>
        <p>
          Esta Política pode ser atualizada para refletir mudanças na Plataforma ou na legislação.
          A versão vigente estará sempre nesta página, com a data de atualização no topo.
        </p>
      </div>
    </article>
  )
}
