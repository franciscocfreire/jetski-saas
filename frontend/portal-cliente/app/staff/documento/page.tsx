"use client";

import { Printer, ArrowLeft } from "lucide-react";
import Link from "next/link";

/* Dados mock do cliente/atendimento (consistentes com o balcão) */
const D = {
  nome: "Roberto Lima",
  cpf: "987.654.321-00",
  identidade: "12.345.678-9",
  orgao: "DETRAN/RJ",
  emissao: "10/03/2018",
  nacionalidade: "brasileira",
  naturalidade: "Rio de Janeiro/RJ",
  telefone: "(21) 3030-1020",
  celular: "(21) 98888-1234",
  email: "roberto.lima@email.com",
  endereco: "Avenida Paulista, 1500, ap. 902 — Bela Vista",
  cidadeUf: "São Paulo/SP",
  cep: "01310-100",
  local: "Angra dos Reis",
  data: "15 de junho de 2026",
  dataCurta: "15/06/2026",
  eama: "JET SAVE TURISMO NÁUTICO LTDA",
  cnpj: "65.455.888/0001-00",
  instrutor: "Carlos Mendes da Silva",
  instrutorId: "98.765.432-1",
  instrutorOrgao: "SSP/RJ",
  instrutorCpf: "111.222.333-44",
  instrutorCha: "MTA-1234567",
};

function F({ children }: { children: React.ReactNode }) {
  return (
    <span className="border-b border-slate-800 px-1 font-medium text-slate-900">
      {children}
    </span>
  );
}

function Assinatura({ legenda }: { legenda: string }) {
  return (
    <div className="mt-8 text-center">
      <div
        className="mx-auto inline-block min-w-[280px] -rotate-2 border-b border-slate-800 pb-0.5 text-2xl text-slate-800"
        style={{ fontFamily: "'Segoe Script', 'Brush Script MT', cursive" }}
      >
        {D.nome}
      </div>
      <div className="mt-1 text-xs text-slate-600">{legenda}</div>
    </div>
  );
}

function Header({ anexo }: { anexo: string }) {
  return (
    <div className="mb-4 flex items-center justify-between border-b border-slate-300 pb-2 text-[11px] uppercase tracking-wide text-slate-500">
      <span>NORMAM-212/DPC</span>
      <span className="font-semibold">{anexo}</span>
    </div>
  );
}

function Art299() {
  return (
    <p className="mt-2 text-justify text-[11px] italic leading-snug text-slate-600">
      “Art. 299 - Omitir, em documento público ou particular, declaração que nele
      deveria constar, ou nele inserir ou fazer inserir declaração falsa ou diversa
      da que deveria ser escrita, com o fim de prejudicar direito, criar obrigação
      ou alterar a verdade sobre o fato juridicamente relevante.” “Pena: reclusão
      de 1 (um) a 5 (cinco) anos e multa, se o documento é público e reclusão de 1
      (um) a 3 (três) anos, se o documento é particular.”
    </p>
  );
}

function Pagina({
  children,
  quebra,
}: {
  children: React.ReactNode;
  quebra?: boolean;
}) {
  return (
    <section
      className={`doc-page mx-auto mb-6 w-full max-w-[800px] bg-white p-10 text-[13px] leading-relaxed text-slate-800 shadow-sm print:mb-0 print:shadow-none ${
        quebra ? "page-break" : ""
      }`}
      style={{ fontFamily: "'Times New Roman', Georgia, serif" }}
    >
      {children}
    </section>
  );
}

export default function DocumentoPage() {
  return (
    <div>
      {/* Barra de controle (não imprime) */}
      <div className="no-print mb-6 flex items-center justify-between">
        <Link
          href="/staff/embarque"
          className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700"
        >
          <ArrowLeft size={15} /> Voltar
        </Link>
        <div className="text-sm text-slate-500">
          Exemplo de PDF consolidado · {D.nome}
        </div>
        <button
          onClick={() => window.print()}
          className="inline-flex h-10 items-center gap-2 rounded-xl bg-brand-600 px-4 text-sm font-semibold text-white hover:bg-brand-700"
        >
          <Printer size={16} /> Imprimir / Salvar PDF
        </button>
      </div>

      {/* ===================== ANEXO 1-C ===================== */}
      <Pagina>
        <Header anexo="Anexo 1-C" />
        <h1 className="mb-4 text-center text-base font-bold">
          DECLARAÇÃO DE RESIDÊNCIA
        </h1>
        <p className="text-slate-700">
          Sr. Capitão dos Portos/Delegado/Agente <F>Angra dos Reis</F>
        </p>
        <p className="mt-3 text-justify">
          Eu, <F>{D.nome}</F>, CPF <F>{D.cpf}</F>, nacionalidade{" "}
          <F>{D.nacionalidade}</F>, naturalidade <F>{D.naturalidade}</F>,
          Telefone <F>{D.telefone}</F>, celular <F>{D.celular}</F>, e-mail{" "}
          <F>{D.email}</F>. Na falta de documentos para comprovação de residência,
          em conformidade com o disposto na Lei nº 7.115, de 29 de agosto de 1983,
          DECLARO para os devidos fins, sob as penas da Lei, ser residente e
          domiciliado no endereço <F>{D.endereco}</F>, <F>{D.cidadeUf}</F>, CEP{" "}
          <F>{D.cep}</F>.
        </p>
        <p className="mt-3 text-justify">
          Declaro ainda, estar ciente de que a falsidade da presente declaração
          pode implicar na sanção penal prevista no art. 299 do Código Penal,
          conforme transcrição abaixo:
        </p>
        <Art299 />
        <p className="mt-6">
          {D.local}, {D.data}.
        </p>
        <Assinatura legenda="Assinatura do Requerente" />
        <div className="mt-8 text-center text-[11px] text-slate-400">- 1-C-1 -</div>
      </Pagina>

      {/* ===================== ANEXO 5-C ===================== */}
      <Pagina quebra>
        <Header anexo="Anexo 5-C" />
        <h1 className="mb-4 text-center text-base font-bold">
          AUTODECLARAÇÃO DE ATESTADO DE SAÚDE PARA EMISSÃO DE CHA-MTA-E
        </h1>
        <p className="text-justify">
          Eu, <F>{D.nome}</F>, Identidade nº <F>{D.identidade}</F>, CPF nº{" "}
          <F>{D.cpf}</F>, declaro para fins específicos de emissão de Carteira de
          Habilitação de Motonauta Especial (CHA-MTA-E) e condução de Moto
          Aquática alugada, que gozo de boas condições de saúde física e mental,
          estando ciente de que eventual informação falsa poderá ensejar
          responsabilidade nas esferas civil, administrativa e criminal, inclusive
          a caracterização do crime de falsidade ideológica, nos termos do art.
          299 do Decreto-Lei nº 2.848, de 7 de dezembro de 1940 (Código Penal).
        </p>

        <table className="mt-5 w-full border-collapse text-sm">
          <thead>
            <tr>
              <th className="border border-slate-400 p-2 text-left"> </th>
              <th className="w-16 border border-slate-400 p-2">SIM</th>
              <th className="w-16 border border-slate-400 p-2">NÃO</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td className="border border-slate-400 p-2">
                Faço uso de lentes de correção visual
              </td>
              <td className="border border-slate-400 p-2 text-center">☐</td>
              <td className="border border-slate-400 p-2 text-center font-bold">
                ☒
              </td>
            </tr>
            <tr>
              <td className="border border-slate-400 p-2">
                Faço uso de aparelho de correção auditiva
              </td>
              <td className="border border-slate-400 p-2 text-center">☐</td>
              <td className="border border-slate-400 p-2 text-center font-bold">
                ☒
              </td>
            </tr>
          </tbody>
        </table>

        <div className="mt-8 grid grid-cols-2 gap-8 text-center text-xs">
          <div>
            <div className="border-t border-slate-800 pt-1">Local e Data</div>
            <div className="mt-1 text-slate-700">
              {D.local}, {D.dataCurta}
            </div>
          </div>
          <div>
            <div
              className="mb-1 -rotate-2 text-xl text-slate-800"
              style={{ fontFamily: "'Segoe Script', 'Brush Script MT', cursive" }}
            >
              {D.nome}
            </div>
            <div className="border-t border-slate-800 pt-1">
              Nome e assinatura do declarante
            </div>
          </div>
        </div>
        <div className="mt-8 text-center text-[11px] text-slate-400">- 5-C-1 -</div>
      </Pagina>

      {/* ===================== ANEXO 5-B (5-B-1) ===================== */}
      <Pagina quebra>
        <Header anexo="Anexo 5-B" />
        <h1 className="mb-4 text-center text-base font-bold">
          ATESTADO DE DEMONSTRAÇÃO PARA CONDUÇÃO DE MOTO AQUÁTICA ALUGADA
        </h1>
        <p className="text-justify text-[12px] text-slate-600">
          O Atestado de Demonstração para Condução de Moto Aquática Alugada visa a
          atestar que foi ministrada ao locatário uma familiarização mínima
          necessária a esse tipo de embarcação, possibilitando a emissão de uma
          habilitação temporária (CHA-MTA-E), a qual permitirá a sua condução
          dentro de uma área restrita.
        </p>
        <p className="mt-4 text-justify">
          Atesto, para os devidos fins, que o(a) Sr.(a.) <F>{D.nome}</F>, CPF nº{" "}
          <F>{D.cpf}</F> assistiu à videoaula e recebeu a demonstração prática para
          condução de moto aquática alugada junto ao <F>{D.eama}</F> (nome do
          EAMA), tendo o(a) Sr.(a.) <F>{D.instrutor}</F> como instrutor(a).
        </p>
        <p className="mt-3">
          Identidade nº <F>{D.instrutorId}</F> Órgão emissor{" "}
          <F>{D.instrutorOrgao}</F> · CPF <F>{D.instrutorCpf}</F> · Nº da CHA{" "}
          <F>{D.instrutorCha}</F>.
        </p>
        <div className="mt-8 text-center">
          <div
            className="mx-auto inline-block min-w-[260px] -rotate-2 border-b border-slate-800 pb-0.5 text-xl text-slate-800"
            style={{ fontFamily: "'Segoe Script', 'Brush Script MT', cursive" }}
          >
            {D.instrutor}
          </div>
          <div className="mt-1 text-xs text-slate-600">Assinatura do Instrutor</div>
        </div>
        <div className="mt-8 text-center text-[11px] text-slate-400">- 5-B-1 -</div>
      </Pagina>

      {/* ===================== ANEXO 5-B (5-B-2) ===================== */}
      <Pagina quebra>
        <Header anexo="Anexo 5-B" />
        <p className="text-justify">
          Declaro, para os devidos fins, que compreendi os principais
          procedimentos de segurança e orientações básicas, fornecidas pelo EAMA,
          por meio da videoaula produzida pela Marinha do Brasil e a demonstração
          prática para condução de moto aquática alugada. Irei cumprir as regras
          relacionadas abaixo:
        </p>
        <ol className="mt-3 list-[lower-alpha] space-y-1 pl-6 text-justify text-[12px]">
          <li>
            conduzirei a MA somente no interior da área delimitada à condução por
            locatários com CHA-MTA-E;
          </li>
          <li>conduzirei a MA somente no período entre o nascer e o pôr do sol;</li>
          <li>
            não utilizarei a MA para fim outro que não a recreação ou prática de
            esportes;
          </li>
          <li>não transferirei a MA a terceiros, sob qualquer pretexto;</li>
          <li>não transportarei passageiros;</li>
          <li>
            cumprirei as instruções sobre os procedimentos de segurança e
            orientações básicas fornecidas pelo EAMA;
          </li>
          <li>
            não ultrapassarei a velocidade de 37 km/h (vinte milhas náuticas/h ou
            vinte nós);
          </li>
          <li>não abastecerei a MA;</li>
          <li>
            jamais conduzirei a MA alugada após consumir bebidas alcoólicas ou
            qualquer substância entorpecente ou tóxica; e
          </li>
          <li>
            utilizarei, obrigatoriamente, lentes de correção visual e/ou aparelho
            de correção auditiva, na hipótese de restrição física.
          </li>
        </ol>

        <div className="mt-4 space-y-1 text-[12px]">
          <p>
            <span className="mr-1 font-bold">☒</span> Declaro que não tenho
            experiência na condução de MA ou embarcação miúda. (É mandatória a
            demonstração com a MA alugada em deslocamento com o locatário na garupa
            do Instrutor).
          </p>
          <p className="text-slate-500">
            <span className="mr-1">☐</span> Declaro que tenho experiência na
            condução de MA ou embarcação miúda. (É obrigatória a apresentação da
            CHA ARA/MSA/CPA/MTA-E).
          </p>
        </div>

        <p className="mt-3 text-justify text-[12px]">
          Estou ciente: a) das imputações administrativas e penais decorrentes de
          acidentes em que esteja envolvido, caso seja responsabilizado; e b) das
          sanções previstas na LESTA e RLESTA. Declaro também estar ciente da
          sanção penal prevista no art. 299 do Código Penal.
        </p>

        <div className="mt-5 text-[12px]">
          Nome: <F>{D.nome}</F> (locatário)
          <div className="mt-2">
            Identidade nº <F>{D.identidade}</F> · Órgão Emissor <F>{D.orgao}</F> ·
            CPF <F>{D.cpf}</F>
          </div>
        </div>
        <Assinatura legenda="Assinatura do Locatário" />
        <p className="mt-6 text-[11px] text-slate-500">
          Observações: 1) O presente atestado não é válido para emissão de nova
          CHA-MTA-E; e 2) Tem validade de 30 dias, a partir da data em que foi
          emitido (<F>{D.dataCurta}</F>).
        </p>
        <div className="mt-4 text-center text-[11px] text-slate-400">- 5-B-2 -</div>
      </Pagina>

      {/* ===================== TERMO JET SAVE ===================== */}
      <Pagina quebra>
        <h1 className="mb-1 text-center text-base font-bold">
          TERMO DE RESPONSABILIDADE PELO USO DE MOTO AQUÁTICA (JET SKI)
        </h1>
        <p className="text-center font-semibold">{D.eama}</p>
        <p className="mb-4 text-center text-xs text-slate-500">CNPJ: {D.cnpj}</p>

        <p className="text-justify">
          Eu, <F>{D.nome}</F>, portador(a) do CPF nº <F>{D.cpf}</F>, declaro que
          recebi orientações de segurança e instruções de utilização da moto
          aquática disponibilizada pela {D.eama}, assumindo total responsabilidade
          pelo equipamento durante o período de utilização. Declaro estar ciente de
          que:
        </p>
        <ol className="mt-3 list-decimal space-y-1 pl-6 text-justify text-[12px]">
          <li>
            A moto aquática me é entregue em perfeitas condições de funcionamento e
            conservação.
          </li>
          <li>
            Durante o período de utilização, sou responsável pela guarda,
            conservação e correta operação do equipamento.
          </li>
          <li>
            Qualquer dano causado por negligência, imprudência, imperícia,
            desrespeito às orientações recebidas ou descumprimento das normas de
            navegação será de minha inteira responsabilidade.
          </li>
          <li>
            Em caso de colisão, abalroamento, encalhe, choque contra embarcações,
            píeres, boias, pedras, estruturas flutuantes ou qualquer outro objeto,
            comprometo-me a arcar integralmente com os custos de reparo.
          </li>
          <li>
            Estou ciente de que o tombamento (virada) da moto aquática pode
            ocasionar entrada de água no motor e em seus componentes internos.
          </li>
          <li>
            Em caso de virada por erro operacional ou desrespeito às orientações,
            autorizo a cobrança dos custos de inspeção, drenagem, manutenção e
            reparação, os quais poderão variar entre R$ 400,00 e R$ 2.000,00.
          </li>
          <li>
            Caso os danos ultrapassem os valores acima, comprometo-me a ressarcir
            integralmente os prejuízos efetivamente apurados.
          </li>
          <li>
            Declaro que possuo condições físicas e psicológicas adequadas e que não
            estou sob efeito de álcool, drogas ou qualquer substância que possa
            comprometer minha capacidade de condução.
          </li>
          <li>
            Comprometo-me a respeitar todas as orientações do instrutor, as normas
            da Autoridade Marítima Brasileira e as regras de segurança aplicáveis.
          </li>
        </ol>

        <div className="mt-5 text-[12px]">
          Local: <F>{D.local}</F> · Data: <F>{D.dataCurta}</F>
        </div>
        <Assinatura legenda={`Nome do Cliente: ${D.nome} · CPF: ${D.cpf}`} />
        <div className="mt-8 text-center text-xs text-slate-600">
          <div className="mx-auto w-72 border-t border-slate-800 pt-1">
            Responsável pela {D.eama}
          </div>
        </div>
      </Pagina>

      <div className="no-print mb-10 text-center text-xs text-slate-400">
        Exemplo fiel aos anexos da NORMAM-212/DPC + termo da loja · dados
        fictícios · use “Imprimir / Salvar PDF”.
      </div>
    </div>
  );
}
