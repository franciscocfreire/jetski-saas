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
//
// Ambiente: DOMINIO (obrigatório), MAILPIT_URL, ESTADO, CATALOGO, SAIDA (tokens).

import { randomBytes } from 'node:crypto';
import { chmodSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { ArquivoDeEstado, type EstadoPersona } from './lib/estado.ts';
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

  for (const e of catalogo.empresasDeCarga) {
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
      const modeloId = modelos.find((m) => m.nome === catalogo.modelo.nome)?.id ?? (await api.criarModelo(t, p.tenantId!, catalogo.modelo));
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
  }
  await semearClientes();
  log('semeadura concluída.');
}

/**
 * Clientes do portal (fase E1): auto-cadastro público → verificação de e-mail pelo link
 * que o KEYCLOAK envia → login SEM senha, pelo código de 6 dígitos enviado por e-mail.
 * Os dois e-mails saem do Keycloak, não do backend — é o que a fase E1 destravou.
 */
async function semearClientes(): Promise<void> {
  for (const c of catalogo.clientes ?? []) {
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
    if (!p.entrouPorCodigo) {
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
    }
  }
}

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
  const usuarios = [];
  for (const e of catalogo.empresasDeCarga) {
    const p = arquivo.estado.personas[e.chave];
    if (!p?.senha || !p.tenantId) throw new Error(`${e.chave} ainda não foi semeada — rode "semear" no espelho.`);
    const { tokens } = await entrar(p, 'backoffice');
    usuarios.push({ usuario: p.email, refreshToken: tokens.refreshToken, tenantId: p.tenantId, tenantSlug: p.slug });
  }
  mkdirSync(dirname(saida), { recursive: true });
  writeFileSync(saida, `${JSON.stringify({ geradoEm: new Date().toISOString(), issuer: ISSUER, clientId: 'jetski-backoffice', usuarios }, null, 2)}\n`, { mode: 0o600 });
  chmodSync(saida, 0o600);
  log(`${usuarios.length} tokens gravados em ${saida} (valem ~12 h).`);
}

const comando = process.argv[2];
if (comando === 'bootstrap') await bootstrap();
else if (comando === 'semear') await semear();
else if (comando === 'resumo') resumo();
else if (comando === 'tokens') await gerarTokens();
else {
  console.error('uso: node src/semeador.ts <bootstrap|semear|resumo|tokens>');
  process.exit(2);
}
