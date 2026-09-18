// Fase E3b — a população de EMISSÃO do espelho, pelas APIs reais:
//
//   EAMA emissora      configura capitania/registro/SMTP → a operadora de plataforma a HABILITA
//                      → cadastra instrutores, que assinam pelo link único (rota pública)
//   Operadoras         pedem o vínculo → a EAMA aceita o termo e designa instrutores;
//   delegadas          instrutor próprio só vale depois de a EAMA aprovar
//   Equipe             convidada pelo admin, ativa pelo convite e faz o primeiro login
//   Créditos           a empresa COMPRA (PIX + comprovante) → a operadora de plataforma aprova
//   Clientes           do portal (cadastro, e-mail verificado, CPF no perfil) e de balcão
//                      (ficha completa + identidade, selfie e residência)
//
// `provarEmissao` fecha o ciclo: uma emissão PRÓPRIA na EAMA e uma DELEGADA na operadora,
// do cliente ao ofício à Capitania conferido no Mailpit.

import { readFileSync } from 'node:fs';
import type { ArquivoDeEstado, EstadoPersona } from './lib/estado.ts';
import { ErroHttp, pedir } from './lib/http.ts';
import { assinaturaSintetica, dataUrl, fotoSintetica } from './lib/imagem.ts';
import type { ResultadoDeLogin } from './lib/keycloak.ts';
import { esperarConvite, mensagensPara, type Cabecalho } from './lib/mailpit.ts';
import type { Plataforma } from './lib/plataforma.ts';

interface Instrutor { chave: string; nome: string; rg: string; orgaoEmissor: string; cha: string; dataEmissao: string }
interface Membro { chave: string; email: string; nome: string; papeis: string[] }
interface Assinatura { paginaAuditoria: boolean; carimboTempo: { ativo: boolean; tsaUrl: string }; otp: { ativo: boolean; canal: string }; pades: { cliente: boolean; marinha: boolean } }
interface Empresa {
  chave: string; slug: string; razaoSocial: string; adminEmail: string; adminNome: string;
  jetskis: number; creditos: number; geral: Record<string, unknown>; assinatura: Assinatura;
}
interface Eama extends Empresa { eamaRegistro: string; eamaRegistroValidade: string; instrutores: Instrutor[] }
interface Delegada extends Empresa { instrutorProprio?: Instrutor; equipe: Membro[] }
interface Pessoa { chave: string; nome: string; dataNascimento: string; naturalidade: string }
export interface CatalogoEmissao {
  capitaniaContem: string;
  plano: string;
  modelo: Record<string, unknown> & { nome: string };
  eama: Eama;
  delegadas: Delegada[];
  clientesPortal: (Pessoa & { email: string })[];
  clientesBalcao: (Pessoa & { empresa: string; genero: string })[];
}

export interface Contexto {
  api: Plataforma;
  arquivo: ArquivoDeEstado;
  mailpit: string;
  fakes: string;
  log: (msg: string) => void;
  entrar: (p: EstadoPersona, cliente: 'console' | 'backoffice') => Promise<ResultadoDeLogin>;
  /** Cadastro → ativação → aprovação → plano → frota (o mesmo caminho das empresas de carga). */
  garantirEmpresa: (e: { chave: string; slug: string; razaoSocial: string; adminEmail: string; adminNome: string; jetskis: number; plano: string }, tokenOperador: string, modelo: Record<string, unknown> & { nome: string }) => Promise<EstadoPersona>;
  /** Cadastro no portal → e-mail verificado → login por código; devolve o token do portal. */
  garantirClienteDoPortal: (c: { chave: string; email: string; nome: string }) => Promise<string>;
}

export function lerCatalogoEmissao(): CatalogoEmissao {
  return JSON.parse(readFileSync(process.env.CATALOGO_EMISSAO ?? new URL('../catalogo/e3b.json', import.meta.url), 'utf8')) as CatalogoEmissao;
}

export function cpfAleatorio(): string {
  const d = Array.from({ length: 9 }, () => Math.floor(Math.random() * 10));
  for (const n of [9, 10]) {
    const soma = d.slice(0, n).reduce((a, x, i) => a + x * (n + 1 - i), 0);
    d.push(((soma * 10) % 11) % 10);
  }
  return d.join('');
}

/** Como a Marinha guarda nomes: maiúsculas, sem acento. */
const nomeNaMarinha = (nome: string) => nome.normalize('NFD').replace(/\p{M}/gu, '').toUpperCase();

type Vinculo = { id: string; status: string; emissorSlug?: string; operadoraSlug?: string } & Record<string, unknown>;

class Semeadura {
  private readonly c: Contexto;
  private readonly cat: CatalogoEmissao;
  private readonly tokens = new Map<string, string>();

  constructor(c: Contexto, cat: CatalogoEmissao) {
    this.c = c;
    this.cat = cat;
  }

  /** Token de backoffice da persona, uma vez por execução (cada login é um fluxo real no Keycloak). */
  async token(p: EstadoPersona): Promise<string> {
    let t = this.tokens.get(p.email);
    if (!t) {
      t = (await this.c.entrar(p, 'backoffice')).tokens.accessToken;
      this.tokens.set(p.email, t);
    }
    return t;
  }

  async controle<T>(caminho: string, json?: unknown): Promise<T> {
    const r = await pedir(`${this.c.fakes}/_controle${caminho}`, json === undefined ? {} : { metodo: 'POST', json });
    if (r.status >= 300) throw new ErroHttp(`/_controle${caminho}`, r);
    return JSON.parse(r.corpo) as T;
  }

  persona(chave: string): EstadoPersona {
    const p = this.c.arquivo.estado.personas[chave];
    if (!p) throw new Error(`persona ${chave} não está no estado — rode "semear".`);
    return p;
  }

  salvar(): void {
    this.c.arquivo.salvar();
  }

  // ---- EAMA ---------------------------------------------------------------------------------

  async eama(tokenOperador: string): Promise<EstadoPersona> {
    const { api, log } = this.c;
    const e = this.cat.eama;
    const p = await this.c.garantirEmpresa({ ...e, plano: this.cat.plano }, tokenOperador, this.cat.modelo);
    const t = p.tenantId!;

    if (!p.emissoraConfigurada) {
      const token = await this.token(p);
      const capitanias = await api.global<{ id: string; nome: string }[]>('GET', token, '/v1/capitanias', undefined, t); // catálogo global, mas o filtro de tenant exige o header
      const capitania = capitanias.find((x) => nomeNaMarinha(x.nome).includes(this.cat.capitaniaContem));
      if (!capitania) throw new Error(`nenhuma capitania com "${this.cat.capitaniaContem}" (há: ${capitanias.map((x) => x.nome).join(', ')})`);
      await api.naEmpresa('PUT', token, t, '/config/geral', { razaoSocial: e.razaoSocial, emailRemetente: e.adminEmail, ...e.geral });
      await api.naEmpresa('PUT', token, t, '/config/emissora', { capitaniaId: capitania.id, eamaRegistro: e.eamaRegistro, eamaRegistroValidade: e.eamaRegistroValidade });
      p.emissoraConfigurada = true;
      this.salvar();
      log(`${e.slug}: perfil de emissão declarado (${capitania.nome}, ${e.eamaRegistro}) e SMTP próprio → Mailpit`);
    }
    if (!p.emissoraHabilitada) {
      // O portão cadastral é da PLATAFORMA: só a operadora sintética liga, como faria o superadmin.
      await api.plataforma('POST', tokenOperador, `/tenants/${t}/habilitar-emissora`);
      p.emissoraHabilitada = true;
      this.salvar();
      log(`${e.slug}: habilitada como EAMA emissora pela operadora de plataforma`);
    }
    await this.assinatura(p, e.assinatura);
    for (const i of e.instrutores) await this.instrutor(p, i);
    return p;
  }

  /**
   * Carimbo de tempo na TSA SINTÉTICA. Sem isto o padrão do produto é a freetsa.org — que o
   * sumidouro afunda, e a emissão degrada em silêncio para "âncora interna". Sempre confere o
   * valor vivo (o config pode ter sido mexido pela tela) e só grava se divergir.
   */
  async assinatura(empresa: EstadoPersona, cfg: Assinatura): Promise<void> {
    const { api, log } = this.c;
    const token = await this.token(empresa);
    const atual = await api.naEmpresa<Partial<Assinatura>>('GET', token, empresa.tenantId!, '/config/assinatura');
    const igual = atual.carimboTempo?.ativo === cfg.carimboTempo.ativo && atual.carimboTempo?.tsaUrl === cfg.carimboTempo.tsaUrl
      && atual.pades?.cliente === cfg.pades.cliente && atual.pades?.marinha === cfg.pades.marinha && atual.paginaAuditoria === cfg.paginaAuditoria;
    if (igual) return;
    const { _, ...corpo } = cfg as Assinatura & { _?: string };
    await api.naEmpresa('PUT', token, empresa.tenantId!, '/config/assinatura', corpo);
    log(`${empresa.slug}: carimbo de tempo → ${cfg.carimboTempo.tsaUrl}${cfg.pades.cliente ? ' + PAdES no PDF do cliente' : ''}`);
  }

  /** Cadastra o instrutor e o faz assinar pelo link único — a página pública, sem login. */
  async instrutor(empresa: EstadoPersona, i: Instrutor): Promise<string> {
    const { api, log } = this.c;
    const t = empresa.tenantId!;
    const estado = ((empresa.instrutores ??= {})[i.chave] ??= {});
    if (!estado.id) {
      const token = await this.token(empresa);
      const existentes = await api.naEmpresa<{ id: string; nome: string }[]>('GET', token, t, '/instrutores?includeInactive=true');
      estado.cpf ??= cpfAleatorio();
      estado.id =
        existentes.find((x) => x.nome === i.nome)?.id ??
        (await api.naEmpresa<{ id: string }>('POST', token, t, '/instrutores', { nome: i.nome, rg: i.rg, orgaoEmissor: i.orgaoEmissor, cpf: estado.cpf, cha: i.cha, dataEmissao: i.dataEmissao })).id;
      this.salvar();
      log(`${empresa.slug}: instrutor ${i.nome} cadastrado`);
    }
    if (!estado.assinou) {
      const link = await api.naEmpresa<{ url: string }>('POST', await this.token(empresa), t, `/instrutores/${estado.id}/link-assinatura`);
      const tokenDoLink = new URL(link.url).searchParams.get('token');
      if (!tokenDoLink) throw new Error(`link de assinatura sem token: ${link.url}`);
      // Daqui em diante é o INSTRUTOR, no celular dele: sem conta, só o link.
      const pagina = await api.publica<{ instrutorNome: string }>('GET', `/v1/public/instrutor-assinatura/${tokenDoLink}`);
      await api.publica('POST', `/v1/public/instrutor-assinatura/${tokenDoLink}`, { assinaturaBase64: dataUrl(assinaturaSintetica(i.chave)) });
      estado.assinou = true;
      this.salvar();
      log(`${empresa.slug}: ${pagina.instrutorNome} assinou pelo link único`);
    }
    return estado.id;
  }

  // ---- delegadas ------------------------------------------------------------------------------

  async delegada(d: Delegada, eama: EstadoPersona, tokenOperador: string): Promise<void> {
    const { api, log } = this.c;
    const p = await this.c.garantirEmpresa({ ...d, plano: this.cat.plano }, tokenOperador, this.cat.modelo);
    const t = p.tenantId!;

    if (!p.geralConfigurada) {
      await api.naEmpresa('PUT', await this.token(p), t, '/config/geral', { razaoSocial: d.razaoSocial, emailRemetente: d.adminEmail, ...d.geral });
      p.geralConfigurada = true;
      this.salvar();
    }
    await this.assinatura(p, d.assinatura);

    if (!p.vinculoAtivo) {
      // A operadora PEDE o vínculo; quem aceita o termo é a EAMA. Nunca o mesmo lado.
      const meus = await api.naEmpresa<Vinculo[]>('GET', await this.token(p), t, '/vinculos-emissao');
      let v = meus.find((x) => x.status === 'CONVIDADO' || x.status === 'ATIVO');
      if (!v) {
        v = await api.naEmpresa<Vinculo>('POST', await this.token(p), t, '/vinculos-emissao', { parceiroSlug: eama.slug, papel: 'OPERADORA' });
        log(`${d.slug}: pediu vínculo de emissão à EAMA ${eama.slug}`);
      }
      p.vinculoId = v.id;
      this.salvar();
      if (v.status !== 'ATIVO') {
        await api.naEmpresa('POST', await this.token(eama), eama.tenantId!, `/vinculos-emissao/${v.id}/aceitar`, { termoAceito: true });
        log(`${eama.slug}: aceitou o termo e o vínculo com ${d.slug} está ATIVO`);
      }
      p.vinculoAtivo = true;
      this.salvar();
    }

    if (!p.instrutoresDesignados) {
      const ids = Object.values(eama.instrutores ?? {}).map((i) => i.id);
      await api.naEmpresa('PUT', await this.token(eama), eama.tenantId!, `/vinculos-emissao/${p.vinculoId}/instrutores-designados`, { instrutorIds: ids });
      p.instrutoresDesignados = true;
      this.salvar();
      log(`${eama.slug}: designou ${ids.length} instrutores para ${d.slug}`);
    }

    if (d.instrutorProprio) {
      const id = await this.instrutor(p, d.instrutorProprio);
      const estado = p.instrutores![d.instrutorProprio.chave];
      if (!estado.aprovado) {
        await api.naEmpresa('POST', await this.token(p), t, `/vinculos-emissao/instrutores-proprios/${id}/solicitar-aprovacao`);
        await api.naEmpresa('POST', await this.token(eama), eama.tenantId!, `/vinculos-emissao/${p.vinculoId}/instrutores-operadora/${id}/decisao`, { decisao: 'APROVAR' });
        estado.aprovado = true;
        this.salvar();
        log(`${eama.slug}: aprovou o instrutor próprio de ${d.slug} (${d.instrutorProprio.nome})`);
      }
    }

    for (const m of d.equipe) await this.membro(p, m);
  }

  /** Convite pelo admin → ativação pelo e-mail → primeiro login (troca a senha temporária). */
  async membro(empresa: EstadoPersona, m: Membro): Promise<void> {
    const { api, arquivo, log, mailpit } = this.c;
    const p = arquivo.persona(m.chave, m.email);
    p.tenantId = empresa.tenantId;
    p.slug = empresa.slug;
    p.papeis = m.papeis;
    if (!p.cadastradaEm) {
      p.cadastradaEm = new Date(Date.now() - 30_000).toISOString();
      this.salvar();
      await api.naEmpresa('POST', await this.token(empresa), empresa.tenantId!, '/users/invite', { email: m.email, nome: m.nome, papeis: m.papeis });
      log(`${empresa.slug}: convidou ${m.nome} (${m.papeis.join(', ')})`);
    }
    if (!p.ativada) {
      const convite = await esperarConvite(mailpit, m.email, new Date(p.cadastradaEm));
      p.senhaTemporaria = convite.senhaTemporaria;
      this.salvar();
      await api.ativarConvite(convite.magicToken);
      p.ativada = true;
      this.salvar();
    }
    if (!p.senha) {
      // Relê o convite: se uma execução anterior morreu entre ativar e entrar, a senha
      // temporária guardada pode estar velha — o e-mail é a fonte da verdade.
      p.senhaTemporaria = (await esperarConvite(mailpit, m.email, new Date(p.cadastradaEm))).senhaTemporaria;
      this.salvar();
      await this.token(p); // o primeiro login define a senha definitiva
      log(`${empresa.slug}: ${m.nome} ativou a conta e fez o primeiro login`);
    }
  }

  // ---- créditos -----------------------------------------------------------------------------------

  async creditos(empresa: EstadoPersona, quantidade: number, tokenOperador: string): Promise<void> {
    const { api, log } = this.c;
    if (empresa.creditosComprados) return;
    const t = empresa.tenantId!;
    const token = await this.token(empresa);
    const pendentes = await api.plataforma<{ id: string; tenantId: string }[]>('GET', tokenOperador, '/creditos/compras');
    let compraId = pendentes.find((x) => x.tenantId === t)?.id;
    if (!compraId) {
      // A empresa vê o PIX da plataforma, "paga" e anexa o comprovante — como no backoffice.
      await api.naEmpresa('GET', token, t, `/creditos/pix?quantidade=${quantidade}`);
      const compra = await api.naEmpresa<{ id: string }>('POST', token, t, '/creditos/compras', { quantidade, comprovanteBase64: dataUrl(fotoSintetica(`comprovante-${empresa.slug}`, 240, 320)) });
      compraId = compra.id;
    }
    await api.plataforma('POST', tokenOperador, `/creditos/compras/${t}/${compraId}/aprovar`);
    const { saldo } = await api.naEmpresa<{ saldo: number }>('GET', token, t, '/creditos/saldo');
    empresa.creditosComprados = quantidade;
    this.salvar();
    log(`${empresa.slug}: comprou ${quantidade} créditos (comprovante sintético), a operadora de plataforma aprovou — saldo ${saldo}`);
  }

  // ---- clientes --------------------------------------------------------------------------------------

  async clienteDoPortal(c: CatalogoEmissao['clientesPortal'][number]): Promise<void> {
    const { api, arquivo, log } = this.c;
    const p = arquivo.persona(c.chave, c.email);
    // O fake guarda nomes em memória: re-registra a cada execução (um restart dele os perde).
    if (p.cpf) await this.controle('/contribuintes', { cpf: p.cpf, nome: nomeNaMarinha(c.nome) });
    if (p.perfilCompleto) return;
    const token = await this.c.garantirClienteDoPortal(c);
    p.cpf ??= cpfAleatorio();
    this.salvar();
    await this.controle('/contribuintes', { cpf: p.cpf, nome: nomeNaMarinha(c.nome) });
    await api.global('PUT', token, '/v1/customers/self', { nome: c.nome, cpf: p.cpf, nacionalidade: 'Brasileira', naturalidade: c.naturalidade, estrangeiro: false, dataNascimento: c.dataNascimento });
    p.perfilCompleto = true;
    this.salvar();
    log(`${c.chave}: perfil do portal completo (CPF definido)`);
  }

  /** Ficha de balcão completa, criada por quem atende: o OPERADOR da equipe, se a empresa tiver um. */
  async clienteDeBalcao(c: CatalogoEmissao['clientesBalcao'][number]): Promise<void> {
    const { api, arquivo, log } = this.c;
    const empresa = this.persona(c.empresa);
    const t = empresa.tenantId!;
    const p = arquivo.persona(c.chave, `${c.chave}@exemplo.invalid`);
    p.tenantId = t;
    p.slug = empresa.slug;
    if (p.cpf) await this.controle('/contribuintes', { cpf: p.cpf, nome: nomeNaMarinha(c.nome) });
    if (p.clienteId && p.anexos) return;
    const atendente = this.atendenteDe(c.empresa);
    const token = await this.token(atendente);
    if (!p.clienteId) {
      p.cpf ??= cpfAleatorio();
      this.salvar();
      await this.controle('/contribuintes', { cpf: p.cpf, nome: nomeNaMarinha(c.nome) });
      const existente = await api.naEmpresa<{ id: string }[] | { content?: { id: string }[] }>('GET', token, t, `/clientes?cpf=${p.cpf}`);
      const lista = Array.isArray(existente) ? existente : (existente.content ?? []);
      p.clienteId =
        lista[0]?.id ??
        (
          await api.naEmpresa<{ id: string }>('POST', token, t, '/clientes', {
            nome: c.nome, documento: p.cpf, documentoTipo: 'CPF', rg: String(100000000 + Math.floor(Math.random() * 899999999)), orgaoEmissor: 'SSP/SP',
            nacionalidade: 'Brasileira', naturalidade: c.naturalidade, estrangeiro: false, dataNascimento: c.dataNascimento, genero: c.genero,
            email: p.email, telefone: '+5513988880000', whatsapp: '+5513988880000', termoAceite: true,
            enderecoJson: JSON.stringify({ cep: '11095460', logradouro: 'Rua Sintética', numero: '100', bairro: 'Centro', cidade: 'Santos', uf: 'SP' }),
            observacoes: 'SINTETICO — cliente de balcão da fase E3b',
          })
        ).id;
      this.salvar();
    }
    if (!p.anexos) {
      for (const tipo of ['IDENTIDADE', 'SELFIE', 'COMPROVANTE_RESIDENCIA']) {
        await api.naEmpresa('PUT', token, t, `/clientes/${p.clienteId}/anexos/${tipo}`, { conteudoBase64: dataUrl(fotoSintetica(`${c.chave}-${tipo}`)) });
      }
      p.anexos = true;
      this.salvar();
      log(`${empresa.slug}: ${c.nome} com ficha completa e 3 documentos (atendido por ${atendente.email})`);
    }
  }

  atendenteDe(chaveEmpresa: string): EstadoPersona {
    const d = this.cat.delegadas.find((x) => x.chave === chaveEmpresa);
    const operador = d?.equipe.find((m) => m.papeis.includes('OPERADOR'));
    return this.persona(operador?.chave ?? chaveEmpresa);
  }
}

export async function semearEmissao(c: Contexto, tokenOperador: string): Promise<void> {
  const cat = lerCatalogoEmissao();
  const s = new Semeadura(c, cat);
  const eama = await s.eama(tokenOperador);
  await s.creditos(eama, cat.eama.creditos, tokenOperador);
  for (const d of cat.delegadas) {
    await s.delegada(d, eama, tokenOperador);
    // Depois do vínculo: o aceite ESTORNA o bônus de adesão da operadora; o saldo dela é só o comprado.
    await s.creditos(s.persona(d.chave), d.creditos, tokenOperador);
  }
  for (const cli of cat.clientesPortal) await s.clienteDoPortal(cli);
  for (const cli of cat.clientesBalcao) await s.clienteDeBalcao(cli);
  c.log('população de emissão pronta (EAMA, delegadas, instrutores, equipe, créditos, clientes).');
}

// ---- prova de vida ---------------------------------------------------------------------------------------

type Documento = { id: string; destino?: string } & Record<string, unknown>;

/**
 * Uma emissão de ponta a ponta, do jeito do balcão: reserva → habilitação EMA (videoaula,
 * anexos, instrutor) → termo assinado → GRU (a cliente paga no PagTesouro sintético) →
 * emitir-documentos → crédito debitado → ofício à Capitania no Mailpit, remetido pela EAMA.
 */
async function emitir(s: Semeadura, c: Contexto, cat: CatalogoEmissao, chaveEmpresa: string, chaveCliente: string, delegada: boolean): Promise<void> {
  const { api, log } = c;
  const exigir = (ok: unknown, msg: string) => {
    if (!ok) throw new Error(`prova da emissão falhou (${chaveEmpresa}): ${msg}`);
    log(`  ✓ ${msg}`);
  };
  const empresa = s.persona(chaveEmpresa);
  const eama = s.persona(cat.eama.chave);
  const cliente = s.persona(chaveCliente);
  const t = empresa.tenantId!;
  const token = await s.token(s.atendenteDe(chaveEmpresa));
  const tokenAdmin = await s.token(empresa);
  log(`emissão ${delegada ? 'DELEGADA' : 'PRÓPRIA'} em ${empresa.slug}, cliente ${chaveCliente}:`);

  const modo = await api.naEmpresa<{ modo: string }>('GET', token, t, '/vinculos-emissao/modo');
  exigir(modo.modo === (delegada ? 'DELEGADA' : 'PROPRIA'), `a empresa opera no modo ${modo.modo}`);

  let instrutorId: string;
  if (delegada) {
    const elegiveis = await api.naEmpresa<{ id: string; nome: string; origem: string }[]>('GET', token, t, '/vinculos-emissao/instrutores-parceiro');
    exigir(elegiveis.some((i) => i.origem === 'EAMA') && elegiveis.some((i) => i.origem === 'OPERADORA'), `instrutores elegíveis: ${elegiveis.map((i) => `${i.nome} [${i.origem}]`).join(', ')}`);
    instrutorId = (elegiveis.find((i) => i.origem === 'EAMA') as { id: string }).id;
  } else {
    instrutorId = Object.values(empresa.instrutores ?? {})[0].id as string;
  }

  const modelo = (await api.listarModelos(token, t))[0];
  const inicio = new Date(Date.now() + 24 * 3600_000 + Math.floor(Math.random() * 6) * 3600_000);
  const local = (d: Date) => new Date(d.getTime() - 3 * 3600_000).toISOString().slice(0, 19); // horário de Brasília
  const reserva = await api.naEmpresa<{ id: string }>('POST', token, t, '/reservas', { modeloId: modelo.id, clienteId: cliente.clienteId, dataInicio: local(inicio), dataFimPrevista: local(new Date(inicio.getTime() + 3600_000)) });

  await api.naEmpresa('PUT', token, t, `/reservas/${reserva.id}/habilitacao`, {
    via: 'EMA', videoaulaAssistida: true, videoaulaModo: 'DECLARACAO', videoaulaIdioma: 'pt',
    anexoSaude: true, anexoRegras: true, anexoResidencia: true, usaLentes: false, usaAparelho: false, instrutorId,
  });
  await api.naEmpresa('POST', token, t, `/reservas/${reserva.id}/aceite`, { metodo: 'SIGNATURE_PAD', assinaturaBase64: dataUrl(assinaturaSintetica(chaveCliente)) });
  exigir(true, 'habilitação EMA registrada e termo assinado pela cliente');

  const gru = await api.naEmpresa<{ sucesso: boolean; erroCodigo?: string; gruNumero?: string }>('POST', token, t, `/reservas/${reserva.id}/habilitacao/gru`);
  exigir(gru.sucesso, `GRU ${gru.gruNumero} gerada na Marinha sintética (${gru.erroCodigo ?? 'sem erro'})`);
  const pendentes = await s.controle<{ idSessao: string; numeroReferencia: string }[]>(`/grus?cpf=${cliente.cpf}&situacao=PENDENTE`);
  const minha = pendentes.find((g) => g.numeroReferencia === gru.gruNumero);
  if (!minha) throw new Error(`GRU ${gru.gruNumero} não está pendente no PagTesouro sintético`);
  await s.controle(`/gru/${minha.idSessao}/pagar`, {});
  const pagamento = await api.naEmpresa<{ pago: boolean }>('POST', token, t, `/reservas/${reserva.id}/habilitacao/gru/verificar-pagamento`);
  exigir(pagamento.pago, 'a cliente pagou o PIX e o backend confirmou');

  const desde = new Date(Date.now() - 30_000);
  const antes = (await api.naEmpresa<{ saldo: number }>('GET', tokenAdmin, t, '/creditos/saldo')).saldo;
  const carimbosAntes = await s.controle<{ seq: number }[]>('/eventos?tipo=CARIMBO');
  const seqAntes = carimbosAntes.at(-1)?.seq ?? 0;
  const emissao = await api.naEmpresa<Record<string, unknown>>('POST', token, t, `/reservas/${reserva.id}/emitir-documentos`);
  log(`    resposta: ${JSON.stringify(emissao).slice(0, 500)}`);
  const depois = (await api.naEmpresa<{ saldo: number }>('GET', tokenAdmin, t, '/creditos/saldo')).saldo;
  exigir(depois === antes - 1, `1 crédito debitado de ${empresa.slug} (${antes} → ${depois})`);
  // A TSA sintética carimba a página de auditoria dos dois PDFs (cliente e Marinha) e, com PAdES
  // ligado, o PDF do cliente de novo (OpenPDF, SHA-1). Sem esses eventos, o backend degradou para
  // "âncora interna" em silêncio — a prova tem de exigir o carimbo, não só a emissão.
  const cfg = (chaveEmpresa === cat.eama.chave ? cat.eama : cat.delegadas.find((d) => d.chave === chaveEmpresa))!.assinatura;
  const esperados = 2 + (cfg.pades.cliente ? 1 : 0) + (cfg.pades.marinha ? 1 : 0);
  // Os eventos do fake são globais: a prova exige o espelho QUIETO (sem motor/k6) e conta exato.
  const janela = await s.controle<{ seq: number; tipo: string; detalhe?: string }[]>(`/eventos?desde=${seqAntes}`);
  const estranhos = janela.filter((e) => !['CARIMBO', 'CONSULTA_CPF', 'GRU_CRIADA', 'SESSAO_PAGTESOURO', 'PIX_GERADO', 'PAGAMENTO'].includes(e.tipo));
  exigir(estranhos.length === 0, `nenhum evento estranho na janela da prova (${estranhos.map((e) => e.tipo).join(', ') || 'ok'}) — se houver, há outro cliente usando o fake`);
  const carimbos = janela.filter((e) => e.tipo === 'CARIMBO');
  const sha1 = carimbos.filter((c) => c.detalhe?.includes('hash=sha1')).length;
  const sha256 = carimbos.filter((c) => c.detalhe?.includes('hash=sha256')).length;
  exigir(carimbos.length === esperados && sha256 === 2 && sha1 === esperados - 2, `exatamente ${esperados} carimbo(s) na TSA sintética — ${sha256} sha256 + ${sha1} sha1: ${carimbos.map((c) => c.detalhe?.split(' ')[0]).join(', ')}`);

  const documentos = await api.naEmpresa<Documento[]>('GET', token, t, `/documentos?clienteId=${cliente.clienteId}`);
  exigir(documentos.length > 0, `${documentos.length} documento(s) emitido(s) na ficha da cliente`);

  // O ofício sai pelo SMTP PRÓPRIO da EAMA — também na emissão delegada — e é assíncrono.
  const destino = String(cat.eama.geral.marinhaEmail);
  const remetente = String(cat.eama.geral.smtpFrom);
  let oficio: Cabecalho | undefined;
  for (let i = 0; i < 45 && !oficio; i++) {
    // O assunto traz "reserva #<8 primeiros do id>": só vale o ofício DESTA reserva.
    oficio = (await mensagensPara(c.mailpit, destino, desde)).find((m) => m.Subject.includes(`#${reserva.id.slice(0, 8)}`));
    if (!oficio) await new Promise((ok) => setTimeout(ok, 2_000));
  }
  exigir(oficio, `ofício à Capitania chegou ao Mailpit (${destino}): "${oficio?.Subject}"`);
  exigir(oficio?.From.Address === remetente, `remetente do ofício é a EAMA (${oficio?.From.Address})`);
  exigir((oficio?.Attachments ?? 0) > 0, `o ofício leva ${oficio?.Attachments} anexo(s)`);

  if (delegada) {
    const painel = await api.naEmpresa<unknown[] | { content?: unknown[] }>('GET', await s.token(eama), eama.tenantId!, '/emissoes-delegadas');
    const n = Array.isArray(painel) ? painel.length : (painel.content?.length ?? 0);
    exigir(n > 0, `a emissão aparece no painel de emissões delegadas da EAMA (${n})`);
  }
}

export async function provarEmissao(c: Contexto): Promise<void> {
  const cat = lerCatalogoEmissao();
  const s = new Semeadura(c, cat);
  const daEmpresa = (chave: string) => (cat.clientesBalcao.find((x) => x.empresa === chave) as { chave: string }).chave;
  await emitir(s, c, cat, cat.eama.chave, daEmpresa(cat.eama.chave), false);
  await emitir(s, c, cat, cat.delegadas[0].chave, daEmpresa(cat.delegadas[0].chave), true);
  c.log('emissão PRÓPRIA e DELEGADA provadas de ponta a ponta.');
}
