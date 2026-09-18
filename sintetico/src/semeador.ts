// Semeador de personas — fase E3a do ECOSSISTEMA_SINTETICO_SPEC.md.
//
// Cria o operador de plataforma sintético e as empresas de carga, tudo pelas
// APIs REAIS: cadastro público, e-mail de convite (lido no Mailpit), ativação,
// login no Keycloak (troca de senha, configuração de TOTP) e aprovação pelo
// operador. Idempotente: o arquivo de estado registra cada passo concluído.
//
//   node src/semeador.ts bootstrap   cadastra e ativa o operador de plataforma
//   (reiniciar o backend: é o boot que promove quem está em PLATFORM_ADMIN_EMAILS)
//   node src/semeador.ts semear      operador entra (TOTP), aprova; empresas ganham frota
//   node src/semeador.ts resumo      mostra o que existe, sem segredos
//   node src/semeador.ts tokens      gera o tokens.json do k6 (admins das empresas de carga)
//   node src/semeador.ts provar-gru      E2: uma GRU de ponta a ponta contra os fakes
//   node src/semeador.ts provar-emissao  E3b: emissão própria (EAMA) e delegada, até o ofício
//
// Ambiente: DOMINIO (obrigatório), MAILPIT_URL, ESTADO, CATALOGO, SAIDA (tokens).

import { randomBytes } from 'node:crypto';
import { chmodSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { cpfAleatorio, lerCatalogoEmissao, provarEmissao, semearEmissao, type Contexto } from './emissao.ts';
import { ArquivoDeEstado, type EstadoPersona } from './lib/estado.ts';
import { ErroHttp, pedir } from './lib/http.ts';
import { login, seguirLinkDeAcao, type ResultadoDeLogin } from './lib/keycloak.ts';
import { esperarConvite, esperarNoEmail, lerCodigo, lerLinkDeVerificacao } from './lib/mailpit.ts';
import { Plataforma } from './lib/plataforma.ts';
import { otpauth } from './lib/totp.ts';

interface Catalogo {
  operadorPlataforma: { chave: string; email: string; nome: string; empresa: { razaoSocial: string; slug: string } };
  empresasDeCarga: { chave: string; slug: string; razaoSocial: string; adminEmail: string; adminNome: string; jetskis: number; plano: string }[];
  clientes?: { chave: string; email: string; nome: string }[];
  modelo: Record<string, unknown> & { nome: string };
}

const DOMINIO = process.env.DOMINIO ?? '';
if (!DOMINIO) throw new Error('Defina DOMINIO (ex.: jetsave.com.br).');
// Mesma trava do k6 e do preflight: personas sintéticas não existem em produção.
if (/(^|\.)meujet\.com\.br$/.test(DOMINIO)) throw new Error(`${DOMINIO} é PRODUÇÃO. O semeador só roda no espelho.`);

const BASE = `https://www.${DOMINIO}/api`;
const ISSUER = `https://sso.${DOMINIO}/realms/jetski-saas`;
const MAILPIT = process.env.MAILPIT_URL ?? 'http://127.0.0.1:8025';
const FAKES = process.env.FAKES_URL ?? 'http://127.0.0.1:8089';
const catalogo = JSON.parse(readFileSync(process.env.CATALOGO ?? new URL('../catalogo/e3a.json', import.meta.url), 'utf8')) as Catalogo;
const arquivo = new ArquivoDeEstado(process.env.ESTADO ?? '/estado/personas.json', DOMINIO);
const api = new Plataforma(BASE);

const log = (msg: string) => console.log(`[semeador] ${msg}`);

/** Senha forte com as quatro classes de caractere, para qualquer política de senha do realm. */
function novaSenha(): string {
  return `${randomBytes(18).toString('base64url')}aZ9!`;
}

/** Cadastro + ativação pelo convite do Mailpit. Cada passo só roda se ainda não foi feito. */
async function cadastrarEAtivar(p: EstadoPersona, e: { razaoSocial: string; slug: string; adminNome: string }): Promise<void> {
  if (!p.tenantId) {
    // 30 s de folga para diferença de relógio entre esta máquina e o Mailpit.
    p.cadastradaEm = new Date(Date.now() - 30_000).toISOString();
    p.tenantId = await api.cadastrarEmpresa({ ...e, adminEmail: p.email });
    p.slug = e.slug;
    arquivo.salvar();
    log(`${e.slug}: empresa cadastrada (${p.tenantId})`);
  }
  if (!p.ativada) {
    const convite = await esperarConvite(MAILPIT, p.email, new Date(p.cadastradaEm ?? 0));
    p.senhaTemporaria = convite.senhaTemporaria;
    arquivo.salvar();
    await api.ativarConta(convite.magicToken);
    p.ativada = true;
    arquivo.salvar();
    log(`${e.slug}: conta de ${p.email} ativada pelo convite`);
  }
}

/** Login da persona; absorve no estado o que o login produzir (senha trocada, TOTP configurado). */
async function entrar(p: EstadoPersona, cliente: 'console' | 'backoffice'): Promise<ResultadoDeLogin> {
  const [clientId, host] = cliente === 'console' ? ['jetski-platform-console', 'admin'] : ['jetski-backoffice', 'app'];
  const proximaSenha = p.senha ? undefined : novaSenha();
  const r = await login({
    issuer: ISSUER,
    clientId,
    redirectUri: `https://${host}.${DOMINIO}/api/auth/callback/keycloak`,
    usuario: p.email,
    senha: p.senha ?? p.senhaTemporaria ?? '',
    novaSenha: proximaSenha,
    totpSegredo: p.totpSegredo,
  });
  if (r.senhaDefinida) {
    p.senha = r.senhaDefinida;
    delete p.senhaTemporaria;
  }
  if (r.totpConfigurado) {
    p.totpSegredo = r.totpConfigurado.segredo;
    p.totpPolitica = r.totpConfigurado.politica;
    p.otpauth = otpauth(`Meu Jet espelho (${DOMINIO})`, p.email, Buffer.from(p.totpSegredo, 'utf8'), p.totpPolitica);
  }
  arquivo.salvar(); // ANTES de qualquer outra coisa: o segredo TOTP não pode ser lido de novo
  log(`${p.email}: entrou pelo ${cliente} (${r.telas.join(' → ')})`);
  return r;
}

async function bootstrap(): Promise<void> {
  const o = catalogo.operadorPlataforma;
  await cadastrarEAtivar(arquivo.persona(o.chave, o.email), { ...o.empresa, adminNome: o.nome });
  log('bootstrap concluído — reinicie o backend para o boot promover o operador (PLATFORM_ADMIN_EMAILS).');
}

async function semear(): Promise<void> {
  const o = catalogo.operadorPlataforma;
  const operador = arquivo.persona(o.chave, o.email);
  if (!operador.ativada) throw new Error('Operador ainda não ativado — rode "bootstrap" antes.');

  const sessao = await entrar(operador, 'console');
  const tokenOperador = sessao.tokens.accessToken;
  if (!(await api.ehOperador(tokenOperador))) {
    console.error(
      `[semeador] ${operador.email} ainda NÃO é operador de plataforma. O boot do backend promove quem está em ` +
        'PLATFORM_ADMIN_EMAILS: confira o .env do espelho e reinicie o backend.',
    );
    process.exit(3);
  }

  // O próprio operador também é dono de uma empresa pendente — ele a aprova, como faria um humano.
  if (!operador.aprovada && operador.tenantId) {
    await api.aprovarEmpresa(tokenOperador, operador.tenantId);
    operador.aprovada = true;
    arquivo.salvar();
  }

  for (const e of catalogo.empresasDeCarga) await garantirEmpresa(e, tokenOperador, catalogo.modelo);
  await semearClientes();
  await semearEmissao(contexto, tokenOperador);
  log('semeadura concluída.');
}

/** Cadastro → ativação → aprovação pela operadora → plano → frota. Cada passo só roda uma vez. */
async function garantirEmpresa(
  e: { chave: string; slug: string; razaoSocial: string; adminEmail: string; adminNome: string; jetskis: number; plano: string },
  tokenOperador: string,
  modeloDoCatalogo: Record<string, unknown> & { nome: string },
): Promise<EstadoPersona> {
  {
    const p = arquivo.persona(e.chave, e.adminEmail);
    await cadastrarEAtivar(p, e);

    if (!p.aprovada) {
      const aprovou = await api.aprovarEmpresa(tokenOperador, p.tenantId!);
      p.aprovada = true;
      arquivo.salvar();
      log(`${e.slug}: ${aprovou ? 'aprovada pelo operador de plataforma' : 'já estava aprovada'}`);
    }

    // A aprovação põe a empresa no plano Trial (3 jetskis, 50 locações/mês) — limites reais,
    // que travariam a frota e a carga. A operadora muda o plano, como faria para um cliente.
    if (p.plano !== e.plano) {
      const plano = (await api.listarPlanos(tokenOperador)).find((x) => x.nome === e.plano);
      if (!plano) throw new Error(`Plano "${e.plano}" não existe na plataforma.`);
      await api.mudarPlano(tokenOperador, p.tenantId!, plano.id);
      p.plano = e.plano;
      arquivo.salvar();
      log(`${e.slug}: plano ${e.plano} (pela operadora de plataforma)`);
    }

    if ((p.jetskis ?? 0) < e.jetskis) {
      const { tokens } = await entrar(p, 'backoffice');
      const t = tokens.accessToken;
      // Consulta o que JÁ existe antes de criar: o estado pode ter ficado para trás
      // se o processo morreu entre a criação e a gravação.
      const modelos = await api.listarModelos(t, p.tenantId!);
      const modeloId = modelos.find((m) => m.nome === modeloDoCatalogo.nome)?.id ?? (await api.criarModelo(t, p.tenantId!, modeloDoCatalogo));
      const existentes = new Set((await api.listarJetskis(t, p.tenantId!)).map((j) => j.serie));
      for (let i = 1; i <= e.jetskis; i++) {
        const serie = `${e.slug.toUpperCase()}-${String(i).padStart(3, '0')}`;
        if (!existentes.has(serie)) {
          await api.criarJetski(t, p.tenantId!, { modeloId, serie, ano: 2026, horimetroAtual: 100.0, status: 'DISPONIVEL' });
        }
      }
      p.jetskis = e.jetskis;
      arquivo.salvar();
      log(`${e.slug}: frota de ${e.jetskis} jetskis pronta`);
    }
    return p;
  }
}

/**
 * Clientes do portal (fase E1): auto-cadastro público → verificação de e-mail pelo link
 * que o KEYCLOAK envia → login SEM senha, pelo código de 6 dígitos enviado por e-mail.
 * Os dois e-mails saem do Keycloak, não do backend — é o que a fase E1 destravou.
 */
async function semearClientes(): Promise<void> {
  for (const c of catalogo.clientes ?? []) await garantirClienteDoPortal(c);
}

/** Devolve o token do portal; o login por código só se repete quando alguém precisa do token. */
async function garantirClienteDoPortal(c: { chave: string; email: string; nome: string }, exigirToken = false): Promise<string | undefined> {
  {
    const p = arquivo.persona(c.chave, c.email);
    if (!p.ativada) {
      p.senha ??= novaSenha();
      p.cadastradaEm ??= new Date(Date.now() - 30_000).toISOString();
      arquivo.salvar();
      await api.cadastrarCliente({ nome: c.nome, email: c.email, senha: p.senha });
      p.ativada = true;
      arquivo.salvar();
      log(`${c.chave}: conta de cliente criada no portal`);
    }
    if (!p.emailVerificado) {
      const link = await esperarNoEmail(MAILPIT, c.email, new Date(p.cadastradaEm ?? 0), lerLinkDeVerificacao, 'link de verificação de e-mail');
      const telas = await seguirLinkDeAcao(link);
      p.emailVerificado = true;
      arquivo.salvar();
      log(`${c.chave}: e-mail verificado pelo link do Keycloak (${telas.join(' → ') || 'redirect direto'})`);
    }
    if (!p.entrouPorCodigo || exigirToken) {
      const r = await login({
        issuer: ISSUER,
        clientId: 'jetski-customer-portal',
        redirectUri: `https://cliente.${DOMINIO}/api/auth/callback/keycloak`,
        usuario: c.email,
        senha: '',
        obterCodigoPorEmail: (pedidoEm) => esperarNoEmail(MAILPIT, c.email, pedidoEm, (texto) => lerCodigo(texto), 'código de login'),
      });
      const eu = await api.clienteLogado(r.tokens.accessToken);
      if (eu.status !== 200) throw new Error(`${c.chave}: token do portal recusado pela API (HTTP ${eu.status}): ${eu.corpo.slice(0, 200)}`);
      p.entrouPorCodigo = true;
      arquivo.salvar();
      log(`${c.chave}: entrou no portal pelo código do e-mail (${r.telas.join(' → ')}) e a API a reconhece como cliente`);
      return r.tokens.accessToken;
    }
    return undefined;
  }
}

const contexto: Contexto = {
  api,
  arquivo,
  mailpit: MAILPIT,
  fakes: FAKES,
  log,
  entrar,
  garantirEmpresa,
  garantirClienteDoPortal: async (c) => (await garantirClienteDoPortal(c, true)) as string,
};

function resumo(): void {
  for (const [chave, p] of Object.entries(arquivo.estado.personas)) {
    const marcas = [p.ativada && 'ativada', p.aprovada && 'aprovada', p.totpSegredo && '2FA', p.jetskis && `${p.jetskis} jetskis`].filter(Boolean);
    console.log(`${chave.padEnd(22)} ${p.email.padEnd(44)} ${p.slug ?? '-'}  [${marcas.join(', ')}]`);
  }
}

/**
 * Gera o arquivo que os cenários k6 leem: um refresh token por admin de empresa de
 * carga, com o tenant de cada um. Roda de FORA da VM, com uma cópia do estado.
 * Validade: a sessão SSO do realm (12 h ociosa) — serve para um turno de testes.
 */
async function gerarTokens(): Promise<void> {
  const saida = process.env.SAIDA ?? './tokens.json';
  const emissao = lerCatalogoEmissao();
  const persona = (chave: string): EstadoPersona => {
    const p = arquivo.estado.personas[chave];
    if (!p?.senha || !p.tenantId) throw new Error(`${chave} ainda não foi semeada — rode "semear" no espelho.`);
    return p;
  };
  type Credencial = { tipo: 'carga' | 'emissao' | 'portal' | 'plataforma'; usuario: string; clientId: string; refreshToken: string; tenantId?: string; tenantSlug?: string; instrutorId?: string };
  const usuarios: Credencial[] = [];

  // Empresas de carga: os admins, para `leitura` e `balcao`.
  for (const e of catalogo.empresasDeCarga) {
    const p = persona(e.chave);
    const { tokens } = await entrar(p, 'backoffice');
    usuarios.push({ tipo: 'carga', usuario: p.email, clientId: 'jetski-backoffice', refreshToken: tokens.refreshToken, tenantId: p.tenantId, tenantSlug: p.slug });
  }

  // Emissão: quem atende o balcão em cada loja (EAMA = admin; delegadas = o OPERADOR da equipe) + o instrutor que ela usa.
  const eama = persona(emissao.eama.chave);
  const atendentes = [
    { p: eama, instrutorId: Object.values(eama.instrutores ?? {})[0]?.id },
    ...emissao.delegadas.map((d) => ({ p: persona(d.equipe.find((m) => m.papeis.includes('OPERADOR'))?.chave ?? d.chave), instrutorId: undefined as string | undefined })),
  ];
  for (const { p, instrutorId } of atendentes) {
    const { tokens } = await entrar(p, 'backoffice');
    let instrutor = instrutorId;
    if (!instrutor) {
      const elegiveis = await api.naEmpresa<{ id: string; origem: string }[]>('GET', tokens.accessToken, p.tenantId!, '/vinculos-emissao/instrutores-parceiro');
      instrutor = (elegiveis.find((i) => i.origem === 'EAMA') ?? elegiveis[0])?.id;
    }
    usuarios.push({ tipo: 'emissao', usuario: p.email, clientId: 'jetski-backoffice', refreshToken: tokens.refreshToken, tenantId: p.tenantId, tenantSlug: p.slug, instrutorId: instrutor });
  }

  // Portal: os clientes recorrentes entram pelo código do e-mail (client do portal).
  for (const c of emissao.clientesPortal) {
    const p = arquivo.estado.personas[c.chave];
    if (!p?.perfilCompleto) continue;
    const r = await login({
      issuer: ISSUER,
      clientId: 'jetski-customer-portal',
      redirectUri: `https://cliente.${DOMINIO}/api/auth/callback/keycloak`,
      usuario: c.email,
      senha: '',
      obterCodigoPorEmail: (pedidoEm) => esperarNoEmail(MAILPIT, c.email, pedidoEm, (texto) => lerCodigo(texto), 'código de login'),
    });
    usuarios.push({ tipo: 'portal', usuario: c.email, clientId: 'jetski-customer-portal', refreshToken: r.tokens.refreshToken, tenantSlug: emissao.delegadas[0].slug });
  }

  // Plataforma: a operadora sintética, pelo console (TOTP).
  const op = persona(catalogo.operadorPlataforma.chave);
  const console_ = await entrar(op, 'console');
  usuarios.push({ tipo: 'plataforma', usuario: op.email, clientId: 'jetski-platform-console', refreshToken: console_.tokens.refreshToken });

  // Prova de que o backend fala com a MARINHA SINTÉTICA — e não com a real: registra um CPF no fake
  // com um nome que só ele conhece e pergunta ao backend. Sem essa prova, os cenários de emissão
  // (que geram GRU) se recusam a rodar.
  let fakesVerificados = false;
  try {
    const cpfProva = cpfAleatorio();
    const nomeProva = `PROVA FAKES ${Date.now()}`;
    const r = await pedir(`${FAKES}/_controle/contribuintes`, { metodo: 'POST', json: { cpf: cpfProva, nome: nomeProva } });
    if (r.status < 300) {
      const t = (await entrar(persona(catalogo.empresasDeCarga[0].chave), 'backoffice')).tokens.accessToken;
      const c = await api.naEmpresa<{ nome?: string }>('GET', t, persona(catalogo.empresasDeCarga[0].chave).tenantId!, `/clientes/consulta-marinha?cpf=${cpfProva}`);
      fakesVerificados = c.nome === nomeProva;
    }
  } catch (e) {
    log(`fakes NÃO verificados: ${e instanceof Error ? e.message.split('\n')[0] : e}`);
  }
  // Trava da E6 (spec §5.1): nenhuma empresa-persona pode carimbar fora da TSA sintética. O
  // padrão do produto é a freetsa.org; um tsaUrl estranho aqui derruba a prova dos fakes.
  for (const e of [emissao.eama, ...emissao.delegadas]) {
    const p = persona(e.chave);
    const cfg = await api.naEmpresa<{ carimboTempo?: { ativo?: boolean; tsaUrl?: string } }>('GET', (await entrar(p, 'backoffice')).tokens.accessToken, p.tenantId!, '/config/assinatura');
    const url = cfg.carimboTempo?.tsaUrl ?? '';
    if (cfg.carimboTempo?.ativo !== false && !url.startsWith('http://fakes-externos:')) {
      log(`${e.slug}: tsaUrl='${url}' NÃO é a TSA sintética — fakes marcados como não verificados`);
      fakesVerificados = false;
    }
  }

  mkdirSync(dirname(saida), { recursive: true });
  writeFileSync(saida, `${JSON.stringify({ geradoEm: new Date().toISOString(), issuer: ISSUER, ambiente: 'espelho', dominio: DOMINIO, fakesVerificados, clientId: 'jetski-backoffice', usuarios }, null, 2)}\n`, { mode: 0o600 });
  chmodSync(saida, 0o600);
  const porTipo = usuarios.reduce<Record<string, number>>((acc, u) => ({ ...acc, [u.tipo]: (acc[u.tipo] ?? 0) + 1 }), {});
  log(`${usuarios.length} tokens gravados em ${saida} (valem ~12 h): ${JSON.stringify(porTipo)}; fakes verificados: ${fakesVerificados}`);
}

// ---- E2: prova de vida da emissão de GRU ---------------------------------------------------------

/**
 * A emissão de ponta a ponta, pelas rotas que o balcão usa: a persona da empresa consulta o
 * CPF, cadastra o cliente, reserva, gera a GRU (PIX), vê "não pago"; a persona cliente paga
 * (pelo /_controle do PagTesouro sintético — é o "app do banco" dela); a empresa vê "pago".
 * Depois, outra reserva pelo caminho do boleto. Falhou um passo = sai 1.
 */
async function provarGru(): Promise<void> {
  const fakes = FAKES;
  const controle = async <T>(caminho: string, json?: unknown): Promise<T> => {
    const r = await pedir(`${fakes}/_controle${caminho}`, json === undefined ? {} : { metodo: 'POST', json });
    if (r.status >= 300) throw new ErroHttp(`/_controle${caminho}`, r);
    return JSON.parse(r.corpo) as T;
  };
  const exigir = (ok: unknown, msg: string) => {
    if (!ok) throw new Error(`prova da GRU falhou: ${msg}`);
    log(`  ✓ ${msg}`);
  };

  const empresa = catalogo.empresasDeCarga[0];
  const p = arquivo.estado.personas[empresa.chave];
  if (!p?.senha || !p.tenantId) throw new Error(`${empresa.chave} ainda não foi semeada — rode "semear" antes.`);
  const token = (await entrar(p, 'backoffice')).tokens.accessToken;
  const t = p.tenantId;

  const cpf = cpfAleatorio();
  const nome = 'Patrícia Prova da Conceição'; // com acento, de propósito: atravessa Marinha → PagTesouro
  await controle('/contribuintes', { cpf, nome: 'PATRICIA PROVA DA CONCEICAO' });
  const consulta = await api.naEmpresa<{ nome?: string }>('GET', token, t, `/clientes/consulta-marinha?cpf=${cpf}`);
  exigir(consulta.nome === 'PATRICIA PROVA DA CONCEICAO', `consulta de CPF na Marinha sintética devolveu o nome (${consulta.nome})`);

  const cliente = await api.naEmpresa<{ id: string }>('POST', token, t, '/clientes', {
    nome,
    documento: cpf,
    documentoTipo: 'CPF',
    email: `prova.gru.${Date.now()}@exemplo.invalid`,
    telefone: '+5513999990000',
    dataNascimento: '1990-05-17',
    termoAceite: true,
    enderecoJson: JSON.stringify({ cep: '11095460', logradouro: 'Rua Sintética', numero: '10', bairro: 'Centro', cidade: 'Santos', uf: 'SP' }),
    observacoes: 'SINTETICO — prova de vida da emissão de GRU (E2)',
  });
  const modelo = (await api.listarModelos(token, t))[0];
  const amanha = new Date(Date.now() + 24 * 3600_000).toISOString().slice(0, 10);
  const reservar = (hora: string, fim: string) =>
    api.naEmpresa<{ id: string }>('POST', token, t, '/reservas', { modeloId: modelo.id, clienteId: cliente.id, dataInicio: `${amanha}T${hora}:00`, dataFimPrevista: `${amanha}T${fim}:00` });

  // PIX
  const r1 = await reservar('10:00', '11:00');
  type Gru = { sucesso: boolean; erroCodigo?: string; erroMensagem?: string } & Record<string, unknown>;
  const gru = await api.naEmpresa<Gru>('POST', token, t, `/reservas/${r1.id}/habilitacao/gru`);
  exigir(gru.sucesso, `GRU + PIX gerados pelo backend (${gru.erroCodigo ?? 'sem erro'} ${gru.erroMensagem ?? ''})`);
  log(`    resposta: ${JSON.stringify({ ...gru, qrPngBase64: undefined, pixQrPngBase64: undefined }).slice(0, 400)}`);
  type Pagamento = { pago: boolean; situacao: string; comprovanteDisponivel: boolean };
  const antes = await api.naEmpresa<Pagamento>('POST', token, t, `/reservas/${r1.id}/habilitacao/gru/verificar-pagamento`);
  exigir(!antes.pago, `antes de a cliente pagar, o backend vê "${antes.situacao}"`);

  const noFake = (await controle<{ idSessao: string; numeroReferencia: string; nome: string }[]>(`/grus?cpf=${cpf}`))[0];
  exigir(noFake?.nome === nome, `o nome acentuado chegou íntegro à Marinha sintética (${noFake?.nome})`);
  await controle(`/gru/${noFake.idSessao}/pagar`, {});
  const depois = await api.naEmpresa<Pagamento>('POST', token, t, `/reservas/${r1.id}/habilitacao/gru/verificar-pagamento`);
  exigir(depois.pago && depois.comprovanteDisponivel, `depois de a cliente pagar: pago=${depois.pago}, comprovante=${depois.comprovanteDisponivel}`);

  // Boleto
  const r2 = await reservar('14:00', '15:00');
  const boleto = await api.naEmpresa<Gru>('POST', token, t, `/reservas/${r2.id}/habilitacao/gru/boleto`);
  exigir(boleto.sucesso, `boleto gerado e PDF aceito pelo backend (${boleto.erroCodigo ?? 'sem erro'} ${boleto.erroMensagem ?? ''})`);
  log(`    resposta: ${JSON.stringify(boleto).slice(0, 300)}`);
  log(`emissão de GRU provada de ponta a ponta em ${empresa.slug} (GRU ${noFake.numeroReferencia}).`);
}

const comando = process.argv[2];
if (comando === 'bootstrap') await bootstrap();
else if (comando === 'semear') await semear();
else if (comando === 'resumo') resumo();
else if (comando === 'tokens') await gerarTokens();
else if (comando === 'provar-gru') await provarGru();
else if (comando === 'provar-emissao') await provarEmissao(contexto);
else {
  console.error('uso: node src/semeador.ts <bootstrap|semear|resumo|tokens|provar-gru|provar-emissao>');
  process.exit(2);
}
