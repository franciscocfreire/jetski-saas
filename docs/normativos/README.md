# Normativos de referência

Documentos oficiais que o sistema implementa. Versionados aqui para que o código (PDFs NORMAM em
`DocumentoPdfService`, ofício à Capitania em `MarinhaEmailTemplate`) possa ser conferido contra a
fonte sem depender do site da Marinha.

## NORMAM-212/DPC — Normas da Autoridade Marítima para Motos Aquáticas e Motonautas

| | |
|---|---|
| Arquivo | `NORMAM-212-DPC-2026-Rev1-Mod1.pdf` (71 páginas, SHA-256 `a10cedab96556b97…`) |
| Fonte oficial | https://assets.marinha.mil.br/sites/default/files/atos-normativos/dpc/normam/normam-212.pdf (página: https://www.marinha.mil.br/dpc/normam-212) |
| Versão | Edição 2026 — **Rev. 1** (Portaria DPC/DGN/MB nº 198, de 23/01/2026, vigente desde 26/01/2026) + **Mod 1** (Portaria nº 201, de 27/02/2026, vigente desde 04/03/2026; páginas 3-9) |
| Baixado em | 06/09/2026 |

Histórico de modificações registrado na folha de rosto: Mod 1 (01/04/2024), Mod 2 (01/07/2024),
Mod 3 (01/01/2025), Mod 4 (28/03/2025 — págs. 1-2, 5-7, 5-8, 3-C-2/3), Mod 5 (06/05/2025, An. 3-B),
Rev. 1 (26/01/2026 — inclui 5-1, 5-3, 5-6, 5-9 e 5-10), Mod 1 da Rev. 1 (04/03/2026).

### O que o sistema implementa — Capítulo 5, Seção III (CHA-MTA-E), item 5.4

Excerto literal (págs. 5-9/5-10):

> **5.4. PROCEDIMENTOS PARA EMISSÃO**
>
> **5.4.1. Para o locatário habilitar-se como MTA-E:**
> a) Assinar a Autodeclaração de Atestado de Saúde para Emissão de CHA-MTA-E (anexo 5-C) […];
> b) Assistir à videoaula e participar da demonstração prática por instrutor cadastrado do EAMA credenciado, por ocasião de sua ambientação;
> c) Assinar o Atestado de Demonstração para Condução de Moto Aquática Alugada (anexo 5-B);
> d) Assinar a Declaração de Residência, conforme prescrito na Lei nº 7.115, de 29 de agosto de 1983 […] modelo no anexo 1-C;
> e) Disponibilizar o seu documento oficial de identificação, com fotografia; e
> f) Pagar a GRU, relativa ao serviço de emissão de CHA-MTA-E […].
> Após a emissão da CHA-MTA-E pela CP, o locatário receberá do EAMA a sua CHA-MTA-E, física ou eletronicamente, com validade de trinta dias a contar da data da sua emissão, podendo ser utilizada em outros EAMA devidamente credenciados […]. Não caberá renovação à CHA-MTA-E.
>
> **5.4.2. Para o EAMA**
> Os documentos necessários à emissão da CHA-MTA-E deverão ser escaneados em formato de arquivo ".pdf" e encaminhados para as Capitanias dos Portos da área de jurisdição onde o EAMA foi credenciado, **via e-mail do EAMA constante na declaração para credenciamento de EAMA (anexo 5-A)**, no seguinte formato:
> – para brasileiro: **nome do arquivo PDF será o nome completo do locatário e o número do seu CPF**; e
> – para estrangeiro: nome do arquivo PDF será o nome completo do locatário e o número do seu passaporte.
> Ainda que o credenciamento tenha sido realizado junto a uma Delegacia ou Agência, o encaminhamento deverá ser direcionado à Capitania dos Portos da jurisdição.
> a) Encaminhar para a CP:
> I) o número da GRU paga, efetuado pelo locatário, relativo ao serviço de emissão de CHA-MTA-E;
> II) a Autodeclaração de Atestado de Saúde para Emissão de CHA-MTA-E (anexo 5-C);
> III) o Atestado de Demonstração para Condução de Moto Aquática Alugada (anexo 5-B), assinado pelo instrutor cadastrado, que realizou a demonstração prática, e pelo locatário não habilitado;
> IV) a Declaração de Residência (anexo 1-C), assinada pelo próprio […]; e
> V) a cópia do documento oficial de identificação, com fotografia.
> b) Receber o arquivo eletrônico, contendo a CHA-MTA-E emitida pela CP; e
> c) Fornecer ao locatário a sua CHA-MTA-E.
>
> **5.4.3. Para a Capitania dos Portos**
> a) Receber do EAMA credenciado, no e-mail funcional da CP (a ser informado na Portaria de Credenciamento do EAMA), os documentos previstos no inciso 5.4.2;
> b) Cadastrar o locatário no SISAMA […]; c) Emitir a CHA-MTA-E; e d) Enviar a CHA-MTA-E para o EAMA.

### Anexos citados pelo sistema

| Anexo | Título | Onde no código |
|---|---|---|
| 1-C | Declaração de Residência | `DocumentoPdfService.writeAnexo1C` |
| 5-A | Declaração para Credenciamento de Estabelecimento de Aluguel de Moto Aquática (dados do EAMA: nome, CNPJ, endereço, instrutores, e-mail, site, telefones, responsável) | Configurações › Empresa (`responsavel_nome`, `telefone`, `email_oficial`, V064) |
| 5-B | Atestado de Demonstração para Condução de Moto Aquática Alugada (5-B-1 atestado do instrutor, 5-B-2 declaração do locatário; 5-B-3/5-B-4 versões em inglês) | `DocumentoPdfService` (seção INSTRUTOR / `REGRAS_5B`) |
| 5-C | Autodeclaração de Atestado de Saúde para Emissão de CHA-MTA-E | `DocumentoPdfService` (seção SAUDE) |
| 5-D | Assuntos a serem abordados durante as instruções do EAMA | videoaula (passo Orientações do balcão) |

### Como o sistema cumpre o 5.4.2

- **PDF único** com o recorte da Marinha (`DocumentoConfig.Destino marinha`), nomeado
  `"{Nome completo} {CPF}.pdf"` (passaporte se `cliente.estrangeiro`) — `MarinhaEmailTemplate.nomeArquivo`.
- **E-mail** no formato de ofício (`MarinhaEmailTemplate`): assunto
  `Solicitação de Emissão de CHA-MTA-E – NOME – CPF 000.000.000-00 – reserva #xxxxxxxx`, corpo com a
  lista 5.4.2-a realmente incluída, assinatura do responsável/EAMA/CNPJ/telefone/e-mail oficial.
- **Remetente**: SMTP próprio da empresa (Configurações › Empresa) para sair do e-mail do Anexo 5-A;
  sem SMTP próprio, sai da plataforma com `Reply-To` = e-mail oficial.
- **Destinatário**: `tenant.marinha_email` (e-mail funcional da CP informado na Portaria de
  Credenciamento); na emissão delegada, o da EAMA emissora.
