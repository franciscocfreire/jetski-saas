// As jornadas do "sábado sintético": o que cada pessoa faz ao longo do dia, SEMPRE pelas
// APIs reais e com o papel que de fato tem aquele poder na loja (a matriz do OPA manda:
// OPERADOR atende e opera o pier; FINANCEIRO confirma sinal e fecha o dia; GERENTE abre OS
// e cancela reserva; MECANICO inicia e conclui a OS).
//
// Cada jornada é uma função assíncrona que espera no relógio comprimido entre um ato e o
// próximo. Um desfecho por jornada vai para as medidas; erro HTTP inesperado vira "falha".

import { cpfAleatorio } from '../emissao.ts';
import type { ArquivoDeEstado, EstadoPersona } from '../lib/estado.ts';
import { ErroHttp, pedir } from '../lib/http.ts';
import { assinaturaSintetica, dataUrl, fotoSintetica } from '../lib/imagem.ts';
import { seguirLinkDeAcao } from '../lib/keycloak.ts';
import { esperarNoEmail, lerLinkDeVerificacao } from '../lib/mailpit.ts';
import type { Plataforma } from '../lib/plataforma.ts';
import { Acaso, Medidas, Relogio, minutosDoDia } from './base.ts';
import type { Sessoes } from './sessoes.ts';
import { nomePeloEmail, type Praia, type Quem } from '../lib/praia.ts';

type Faixa = [number, number];
export interface Cenario {
  dia: { abre: string; fecha: string; fator: number };
  chegadas: number;
  curvaPorHora: Record<string, number>;
  canal: Record<string, number>;
  portal: { clienteNovo: number; antecedenciaMin: Faixa; pagaSinal: number; demoraParaPagarMin: Faixa; lojaConfirmaEmMin: Faixa; naoComparece: number; avalia: number; notas: Record<string, number> };
  balcao: { clienteNovo: number; temCha: number; pagaGru: number; demoraParaPagarGruMin: Faixa; comVendedor: number; esperaAteSairMin: Faixa };
  passeio: { duracaoMin: Record<string, number>; atrasaDevolucao: number; atrasoMin: Faixa; formaDePagamento: Record<string, number> };
  semJetski: { tentativas: number; esperaMin: number };
  manutencao: { chancePorLoja: number; janela: [string, string]; duracaoMin: Faixa };
  telas: { aCadaMin: number };
  creditos: { minimo: number; compra: number };
  operadorPlataforma: string;
  lojas: { chave: string; peso: number; papeis: Record<'admin' | 'gerente' | 'atendente' | 'financeiro' | 'mecanico', string>; vendedores: string[] }[];
  nomes: string[];
  sobrenomes: string[];
}

interface Jetski { id: string; serie: string; status: string; modeloId: string; horimetroAtual: number }

/** Uma loja durante o dia: quem trabalha nela e o que o motor sabe do seu estado. */
export class Loja {
  readonly chave: string;
  readonly tenantId: string;
  readonly slug: string;
  readonly papeis: Record<string, EstadoPersona>;
  modeloId = '';
  vendedores: string[] = [];
  instrutorId = '';
  /** Jetskis que uma jornada já pegou para si — evita duas alocarem o mesmo no mesmo instante. */
  readonly emUso = new Set<string>();
  readonly recorrentes: EstadoPersona[] = [];
  /** Nomes de exibição (catálogo) por papel da equipe e por id de cliente recorrente — só para a praia. */
  readonly nomes: Record<string, string> = {};

  constructor(chave: string, empresa: EstadoPersona, papeis: Record<string, EstadoPersona>) {
    this.chave = chave;
    this.tenantId = empresa.tenantId as string;
    this.slug = empresa.slug as string;
    this.papeis = papeis;
  }
}

export interface Dia {
  cenario: Cenario;
  api: Plataforma;
  arquivo: ArquivoDeEstado;
  sessoes: Sessoes;
  relogio: Relogio;
  medidas: Medidas;
  acaso: Acaso;
  lojas: Loja[];
  clientesDoPortal: EstadoPersona[];
  mailpit: string;
  fakes: string;
  rodada: string;
  log: (msg: string) => void;
  /** Gancho da Praia Sintética (opcional, PRAIA_URL). Sem URL, `emitir` é um no-op. */
  praia: Praia;
  /** e-mail → nome de exibição, dos catálogos. */
  nomes: Map<string, string>;
}

const nomeNaMarinha = (nome: string) => nome.normalize('NFD').replace(/\p{M}/gu, '').toUpperCase();
/** LocalDateTime no fuso da loja (America/Sao_Paulo, UTC-3 fixo desde 2019). */
const horaLocal = (d: Date) => new Date(d.getTime() - 3 * 3600_000).toISOString().slice(0, 19);
const hojeLocal = () => horaLocal(new Date()).slice(0, 10);

class Desistiu extends Error {}

/** Base de toda jornada: mede cada passo, fala como a persona certa e registra o desfecho. */
class Jornada {
  protected readonly d: Dia;
  protected readonly a: Acaso;
  protected readonly tipo: string;
  protected readonly id: string;
  private passoAtual = 'início';
  /** A pessoa "dona" da jornada (o cliente); quem executa o passo corrente pode ser outra (o staff do `naLoja`). */
  protected quem: Quem;
  private quemDoPasso?: Quem;
  private ultimoStaff?: Quem;
  protected lojaAtual?: Loja;
  /** O que a praia precisa saber em cada ato da jornada: série do jetski, ids da reserva/locação/OS. */
  protected readonly contexto: Record<string, unknown> = {};

  constructor(d: Dia, tipo: string, n: number) {
    this.d = d;
    this.a = d.acaso.filho();
    this.tipo = tipo;
    this.id = `${tipo}#${n}`;
    this.quem = { id: this.id, nome: this.id, papel: 'sistema' };
  }

  /** A jornada passa a ser de um cliente com nome — é ele quem aparece na praia. */
  protected comoCliente(id: string, nome: string | undefined, loja?: Loja): void {
    this.quem = { id, nome: nome ?? this.d.nomes.get(id) ?? nomePeloEmail(id), papel: 'cliente', empresa: loja?.chave };
  }

  protected async passo<T>(nome: string, fn: () => Promise<T>): Promise<T> {
    this.passoAtual = nome;
    const t0 = performance.now();
    let erro: string | undefined;
    try {
      return await fn();
    } catch (e) {
      erro = e instanceof Error ? e.message.split('\n')[0].slice(0, 300) : String(e);
      throw e;
    } finally {
      const ms = performance.now() - t0;
      this.d.medidas.latencia(nome, ms);
      const quem = this.quemDoPasso ?? this.quem;
      // Quando a equipe age numa jornada de cliente, a praia precisa saber de QUEM é o ato (o piloto no check-in).
      const cliente = this.quem.papel === 'cliente' && quem !== this.quem ? this.quem.nome : undefined;
      this.d.praia.emitir({ tipo: 'passo', rodada: this.d.rodada, jornada: this.id, passo: nome, quem, loja: this.lojaAtual?.slug, resultado: erro ? 'FALHOU' : 'OK', ms: Math.round(ms), erro, detalhes: { ...this.contexto, cliente, clienteId: cliente ? this.quem.id : undefined } });
      this.quemDoPasso = undefined;
    }
  }

  /** Quem da equipe faz um passo (para a praia). */
  protected staff(loja: Loja, papel: string): Quem {
    const p = loja.papeis[papel];
    return { id: p.email, nome: loja.nomes[papel] ?? this.d.nomes.get(p.email) ?? nomePeloEmail(p.email), papel, empresa: loja.chave };
  }

  protected token(loja: Loja, papel: string): Promise<string> {
    const p = loja.papeis[papel];
    return this.d.sessoes.staff(p.email, p.senha as string);
  }

  /** Chamada de staff: `papel` decide QUEM da loja faz. */
  protected async naLoja<T>(loja: Loja, papel: string, passo: string, metodo: 'GET' | 'POST' | 'PUT' | 'DELETE', caminho: string, json?: unknown): Promise<T> {
    const token = await this.token(loja, papel);
    this.lojaAtual = loja;
    this.quemDoPasso = this.ultimoStaff = this.staff(loja, papel);
    return this.passo(passo, () => this.d.api.naEmpresa<T>(metodo, token, loja.tenantId, caminho, json));
  }

  protected esperar(faixa: Faixa): Promise<void> {
    return this.d.relogio.esperar(this.a.entre(faixa[0], faixa[1]));
  }

  protected async controle<T>(caminho: string, json?: unknown): Promise<T> {
    const r = await pedir(`${this.d.fakes}/_controle${caminho}`, json === undefined ? {} : { metodo: 'POST', json });
    if (r.status >= 300) throw new ErroHttp(`/_controle${caminho}`, r);
    return JSON.parse(r.corpo) as T;
  }

  protected nomeNovo(): string {
    return `${this.a.escolher(this.d.cenario.nomes)} ${this.a.escolher(this.d.cenario.sobrenomes)} Sintético`;
  }

  async rodar(corpo: () => Promise<string>): Promise<void> {
    let desfecho: string;
    try {
      desfecho = await corpo();
    } catch (e) {
      if (e instanceof Desistiu) desfecho = e.message;
      else {
        desfecho = 'erro';
        // "fetch failed" sozinho não diz nada: a causa (ECONNREFUSED, timeout…) vem em `cause`.
        const causa = e instanceof Error && e.cause instanceof Error ? ` (${e.cause.message})` : '';
        const erro = e instanceof Error ? e.message.split('\n').slice(0, 2).join(' ').slice(0, 400) + causa : String(e);
        this.d.medidas.falhas.push({ quando: this.d.relogio.hora(), jornada: this.id, passo: this.passoAtual, erro });
        this.d.log(`✗ ${this.id} falhou em "${this.passoAtual}": ${erro}`);
      }
    }
    this.d.medidas.desfecho(this.tipo, desfecho);
    this.d.log(`${this.id}: ${desfecho}`);
    // Rotinas da loja não têm cliente: o desfecho é de quem agiu por último.
    const quem = this.quem.papel === 'sistema' && this.ultimoStaff ? this.ultimoStaff : this.quem;
    this.d.praia.emitir({ tipo: 'desfecho', rodada: this.d.rodada, jornada: this.id, desfecho, quem, loja: this.lojaAtual?.slug });
  }

  // ---- atos comuns no pier -------------------------------------------------------------------

  /** Aloca um jetski livre do modelo; sem nenhum, o cliente espera e tenta de novo. */
  protected async alocar(loja: Loja, reservaId: string): Promise<Jetski> {
    const { tentativas, esperaMin } = this.d.cenario.semJetski;
    for (let i = 0; i < tentativas; i++) {
      const frota = await this.naLoja<Jetski[]>(loja, 'atendente', 'listar jetskis', 'GET', '/jetskis');
      const livre = frota.find((j) => j.status === 'DISPONIVEL' && j.modeloId === loja.modeloId && !loja.emUso.has(j.id));
      if (livre) {
        loja.emUso.add(livre.id);
        Object.assign(this.contexto, { jetski: livre.serie, reservaId });
        try {
          await this.naLoja(loja, 'atendente', 'alocar jetski', 'POST', `/reservas/${reservaId}/alocar-jetski`, { jetskiId: livre.id });
          return livre;
        } catch (e) {
          loja.emUso.delete(livre.id);
          delete this.contexto.jetski;
          throw e;
        }
      }
      this.d.medidas.contar('espera_por_jetski');
      await this.d.relogio.esperar(esperaMin);
    }
    throw new Desistiu('sem-jetski');
  }

  /** Check-in → passeio → check-out → pagamento. Devolve o id da locação. O jetski SEMPRE volta. */
  protected async passear(loja: Loja, reservaId: string, jetski: Jetski, jaPago: number): Promise<string> {
    const c = this.d.cenario.passeio;
    let locacaoId: string | undefined;
    try {
      const locacao = await this.naLoja<{ id: string }>(loja, 'atendente', 'check-in', 'POST', '/locacoes/check-in/reserva', { reservaId, horimetroInicio: jetski.horimetroAtual ?? 0 });
      locacaoId = locacao.id;
      this.contexto.locacaoId = locacao.id;
      const duracao = Number(this.a.ponderado(c.duracaoMin)) + (this.a.chance(c.atrasaDevolucao) ? this.a.entre(c.atrasoMin[0], c.atrasoMin[1]) : 0);
      // Não é um passo medido (é uma espera), mas é o momento mais visível do dia: o cliente no mar.
      this.d.praia.emitir({ tipo: 'passo', rodada: this.d.rodada, jornada: this.id, passo: 'passeio', quem: this.quem, loja: loja.slug, resultado: 'OK', detalhes: { ...this.contexto, jetski: jetski.serie, duracaoMin: Math.round(duracao), horimetro: jetski.horimetroAtual ?? 0 } });
      await this.d.relogio.esperar(duracao);
      const saida = await this.naLoja<{ valorTotal?: number }>(loja, 'atendente', 'check-out', 'POST', `/locacoes/${locacaoId}/check-out`, {
        horimetroFim: Number(((jetski.horimetroAtual ?? 0) + duracao / 60).toFixed(1)),
        checklistEntradaJson: JSON.stringify({ casco: 'ok', motor: 'ok', combustivel: 'ok', observacao: 'SINTETICO' }),
        skipPhotos: true,
        observacoes: `SINTETICO — motor ${this.d.rodada}`,
      });
      locacaoId = undefined; // devolvido
      const resta = Number(((saida.valorTotal ?? 0) - jaPago).toFixed(2));
      if (resta > 0.01) {
        await this.naLoja(loja, 'atendente', 'registrar pagamento', 'POST', `/locacoes/${locacao.id}/registrar-pagamento`, { forma: this.a.ponderado(c.formaDePagamento), valor: resta, observacao: 'SINTETICO' });
      }
      this.d.medidas.contar('locacoes');
      return locacao.id;
    } finally {
      loja.emUso.delete(jetski.id);
      // Se algo quebrou com o jetski na água, o pier o recolhe mesmo assim — senão a frota some.
      if (locacaoId) {
        await this.naLoja(loja, 'atendente', 'check-out de resgate', 'POST', `/locacoes/${locacaoId}/check-out`, { horimetroFim: (jetski.horimetroAtual ?? 0) + 1, checklistEntradaJson: '{"resgate":true}', skipPhotos: true }).catch(() => undefined);
      }
    }
  }
}

// ---- cliente que reserva pelo portal -------------------------------------------------------------------

export class JornadaPortal extends Jornada {
  async executar(loja: Loja): Promise<string> {
    const c = this.d.cenario.portal;
    const { api } = this.d;

    // 1. Quem é: alguém que já tem conta, ou gente nova que se cadastra agora.
    const recorrente = this.a.chance(c.clienteNovo) ? undefined : this.d.clientesDoPortal.shift();
    this.lojaAtual = loja;
    if (recorrente) this.comoCliente(recorrente.email, undefined, loja);
    const email = recorrente?.email ?? (await this.cadastrarNoPortal(loja));
    // Token pedido a cada uso, nunca guardado: entre um ato e o próximo há esperas de até 25 min
    // (em tempo real), mais que os 5 min do access token — o soak achou 401 aqui.
    const token = () => this.d.sessoes.cliente(email);
    await this.passo('login do cliente (código por e-mail)', token);

    // 2. Navega como o portal: a loja, os modelos, a disponibilidade.
    await this.passo('vitrine da loja', () => api.publica('GET', `/v1/public/lojas/${loja.slug}`));
    await this.passo('modelos da loja', () => api.publica('GET', `/v1/public/lojas/${loja.slug}/modelos`));
    const inicioSim = this.d.relogio.agora() + this.a.entre(c.antecedenciaMin[0], c.antecedenciaMin[1]);
    const inicio = this.d.relogio.real(inicioSim);
    const fim = new Date(inicio.getTime() + 60 * 60_000); // o modelo exige duração mínima em minutos REAIS
    const janela = `dataInicio=${horaLocal(inicio)}&dataFimPrevista=${horaLocal(fim)}`;
    await this.passo('disponibilidade', () => api.publica('GET', `/v1/public/lojas/${loja.slug}/disponibilidade?modeloId=${loja.modeloId}&${janela}`));

    // 3. Reserva com sinal.
    const reserva = await this.passo('reservar no portal', async () =>
      api.global<{ id: string }>('POST', await token(), '/v1/customers/reservas', {
        lojaSlug: loja.slug, modeloId: loja.modeloId, dataInicio: horaLocal(inicio), dataFimPrevista: horaLocal(fim),
        pagamentoTipo: 'SINAL', telefone: '+5513977770000', possuiCha: true, observacoes: `SINTETICO — motor ${this.d.rodada}`,
      }),
    );
    this.d.medidas.contar('reservas_portal');

    // 4. Paga o sinal? Quem não paga some — a pré-reserva fica para o job de expiração.
    if (!this.a.chance(c.pagaSinal)) return 'abandonou-sem-pagar-sinal';
    await this.esperar(c.demoraParaPagarMin);
    const detalhe = await this.passo('ver PIX do sinal', async () => api.global<Record<string, unknown>>('GET', await token(), `/v1/customers/reservas/${reserva.id}`));
    const sinal = Number(detalhe.valorSinal ?? detalhe.sinalValor ?? (detalhe.pix as { valor?: number } | undefined)?.valor ?? 0) || 100;
    await this.passo('enviar comprovante do sinal', async () =>
      api.global('POST', await token(), `/v1/customers/reservas/${reserva.id}/comprovante`, { tipo: 'SINAL', valorInformado: sinal, contentType: 'image/png', dataBase64: fotoSintetica(`comprovante-${reserva.id}`, 240, 320).toString('base64') }),
    );

    // 5. A loja confere: o FINANCEIRO confirma o sinal, o atendente confirma a reserva.
    await this.esperar(c.lojaConfirmaEmMin);
    await this.naLoja(loja, 'financeiro', 'confirmar sinal', 'POST', `/reservas/${reserva.id}/confirmar-sinal`, { valorSinal: sinal, tipo: 'SINAL' });
    const ficha = await this.naLoja<{ status?: string; reserva?: { status?: string } }>(loja, 'atendente', 'abrir ficha da reserva', 'GET', `/reservas/${reserva.id}`);
    if ((ficha.status ?? ficha.reserva?.status) === 'PENDENTE') await this.naLoja(loja, 'atendente', 'confirmar reserva', 'POST', `/reservas/${reserva.id}/confirmar`);

    // 6. Chega a hora. Aparece?
    await this.d.relogio.ate(inicioSim);
    if (this.a.chance(c.naoComparece)) {
      await this.d.relogio.esperar(15); // a loja espera um pouco antes de marcar
      await this.naLoja(loja, 'atendente', 'marcar no-show', 'POST', `/reservas/${reserva.id}/no-show`);
      return 'no-show';
    }
    const jetski = await this.alocar(loja, reserva.id);
    const locacaoId = await this.passear(loja, reserva.id, jetski, sinal);

    // 7. Avalia?
    if (this.a.chance(c.avalia)) {
      await this.d.relogio.esperar(this.a.entre(5, 30));
      await this.passo('avaliar passeio', async () => api.global('POST', await this.d.sessoes.cliente(email), `/v1/customers/locacoes/${locacaoId}/avaliacao`, { nota: Number(this.a.ponderado(c.notas)), comentario: 'SINTETICO — avaliação do motor de personas' }));
      return 'passeou-e-avaliou';
    }
    return 'passeou';
  }

  /** Cadastro no portal, do zero: conta → link de verificação do Keycloak → CPF no perfil. */
  private async cadastrarNoPortal(loja: Loja): Promise<string> {
    const { api, mailpit } = this.d;
    const nome = this.nomeNovo();
    const email = `motor.${this.d.rodada}.${this.id.replace('#', '-')}@exemplo.invalid`;
    this.comoCliente(email, nome, loja);
    const desde = new Date(Date.now() - 30_000);
    await this.passo('cadastro no portal', () => api.cadastrarCliente({ nome, email, senha: `S1nt3tico!${this.a.inteiro(100000, 999999)}aZ` }));
    const link = await this.passo('e-mail de verificação chega', () => esperarNoEmail(mailpit, email, desde, lerLinkDeVerificacao, 'link de verificação de e-mail'));
    await this.passo('verificar e-mail', () => seguirLinkDeAcao(link));
    const token = await this.passo('login do cliente (código por e-mail)', () => this.d.sessoes.cliente(email));
    const cpf = cpfAleatorio();
    await this.controle('/contribuintes', { cpf, nome: nomeNaMarinha(nome) });
    await this.passo('completar perfil (CPF)', () => api.global('PUT', token, '/v1/customers/self', { nome, cpf, nacionalidade: 'Brasileira', naturalidade: 'Santos/SP', estrangeiro: false, dataNascimento: `19${this.a.inteiro(70, 99)}-0${this.a.inteiro(1, 9)}-1${this.a.inteiro(0, 8)}` }));
    this.d.medidas.contar('clientes_novos_portal');
    return email;
  }
}

// ---- cliente que chega no balcão ------------------------------------------------------------------------

export class JornadaBalcao extends Jornada {
  async executar(loja: Loja): Promise<string> {
    const c = this.d.cenario.balcao;

    // 1. Quem é: freguês da loja ou alguém que nunca veio (consulta do CPF na Marinha + ficha).
    const recorrente = this.a.chance(c.clienteNovo) ? undefined : loja.recorrentes.shift();
    const temCha = this.a.chance(c.temCha);
    this.lojaAtual = loja;
    if (recorrente) this.comoCliente(recorrente.clienteId as string, loja.nomes[recorrente.clienteId as string], loja);
    const cliente = recorrente ? { id: recorrente.clienteId as string, cpf: recorrente.cpf as string } : await this.cadastrarNoBalcao(loja, !temCha);

    // 2. Reserva de balcão para daqui a pouco, às vezes com vendedor.
    const inicioSim = this.d.relogio.agora() + this.a.entre(c.esperaAteSairMin[0], c.esperaAteSairMin[1]);
    const inicio = this.d.relogio.real(inicioSim);
    const vendedorId = loja.vendedores.length && this.a.chance(c.comVendedor) ? this.a.escolher(loja.vendedores) : undefined;
    const reserva = await this.naLoja<{ id: string }>(loja, 'atendente', 'reservar no balcão', 'POST', '/reservas', {
      modeloId: loja.modeloId, clienteId: cliente.id, vendedorId, dataInicio: horaLocal(new Date(inicio.getTime() + 60_000)),
      dataFimPrevista: horaLocal(new Date(inicio.getTime() + 61 * 60_000)), observacoes: `SINTETICO — motor ${this.d.rodada}`,
    });
    this.d.medidas.contar('reservas_balcao');

    // 3. Habilitação: já tem CHA, ou tira a EMA agora (GRU + documentos à Marinha).
    if (temCha) {
      await this.naLoja(loja, 'atendente', 'registrar CHA', 'PUT', `/reservas/${reserva.id}/habilitacao`, { via: 'CHA', chaCategoria: 'MTA', chaNumero: `SINT${this.a.inteiro(100000, 999999)}`, chaValidade: '2031-12-31' });
      await this.naLoja(loja, 'atendente', 'assinar termo', 'POST', `/reservas/${reserva.id}/aceite`, { metodo: 'SIGNATURE_PAD', assinaturaBase64: dataUrl(assinaturaSintetica(reserva.id)) });
      await this.naLoja(loja, 'atendente', 'confirmar reserva', 'POST', `/reservas/${reserva.id}/confirmar`);
    } else {
      await this.naLoja(loja, 'atendente', 'registrar habilitação EMA', 'PUT', `/reservas/${reserva.id}/habilitacao`, {
        via: 'EMA', videoaulaAssistida: true, videoaulaModo: 'DECLARACAO', videoaulaIdioma: 'pt', anexoSaude: true, anexoRegras: true, anexoResidencia: true, usaLentes: false, usaAparelho: false, instrutorId: loja.instrutorId,
      });
      await this.naLoja(loja, 'atendente', 'assinar termo', 'POST', `/reservas/${reserva.id}/aceite`, { metodo: 'SIGNATURE_PAD', assinaturaBase64: dataUrl(assinaturaSintetica(reserva.id)) });
      const gru = await this.naLoja<{ sucesso: boolean; gruNumero?: string; erroCodigo?: string }>(loja, 'atendente', 'gerar GRU', 'POST', `/reservas/${reserva.id}/habilitacao/gru`);
      if (!gru.sucesso) {
        this.d.medidas.contar('gru_indisponivel');
        await this.cancelar(loja, reserva.id);
        return `gru-falhou-${gru.erroCodigo ?? 'desconhecido'}`;
      }
      if (!this.a.chance(c.pagaGru)) {
        await this.d.relogio.esperar(20);
        await this.cancelar(loja, reserva.id);
        return 'desistiu-sem-pagar-gru';
      }
      await this.esperar(c.demoraParaPagarGruMin);
      const pendentes = await this.controle<{ idSessao: string; numeroReferencia: string }[]>(`/grus?cpf=${cliente.cpf}&situacao=PENDENTE`);
      const minha = pendentes.find((g) => g.numeroReferencia === gru.gruNumero);
      if (!minha) throw new Error(`GRU ${gru.gruNumero} não está pendente no PagTesouro sintético`);
      await this.passo('cliente paga o PIX da GRU', () => this.controle(`/gru/${minha.idSessao}/pagar`, {}));
      const pg = await this.naLoja<{ pago: boolean }>(loja, 'atendente', 'verificar pagamento da GRU', 'POST', `/reservas/${reserva.id}/habilitacao/gru/verificar-pagamento`);
      if (!pg.pago) throw new Error('GRU paga no fake, mas o backend não confirmou');
      await this.naLoja(loja, 'atendente', 'emitir documentos à Marinha', 'POST', `/reservas/${reserva.id}/emitir-documentos`);
      this.d.medidas.contar('emissoes');
      // A emissão com documentação completa já confirma a reserva; se não confirmou, o atendente confirma.
      const ficha = await this.naLoja<{ status?: string }>(loja, 'atendente', 'abrir ficha da reserva', 'GET', `/reservas/${reserva.id}`);
      if (ficha.status === 'PENDENTE') await this.naLoja(loja, 'atendente', 'confirmar reserva', 'POST', `/reservas/${reserva.id}/confirmar`);
    }

    // 4. Sai para o passeio e paga tudo na volta.
    await this.d.relogio.ate(inicioSim);
    const jetski = await this.alocar(loja, reserva.id);
    await this.passear(loja, reserva.id, jetski, 0);
    return temCha ? 'passeou-com-cha' : 'passeou-com-ema';
  }

  /** Cancelar é poder do GERENTE — o OPA nega ao OPERADOR, apesar do @PreAuthorize. */
  private cancelar(loja: Loja, reservaId: string): Promise<unknown> {
    return this.naLoja(loja, 'gerente', 'cancelar reserva', 'DELETE', `/reservas/${reservaId}`);
  }

  private async cadastrarNoBalcao(loja: Loja, comDocumentos: boolean): Promise<{ id: string; cpf: string }> {
    const nome = this.nomeNovo();
    const cpf = cpfAleatorio();
    this.comoCliente(`motor.${this.d.rodada}.${this.id.replace('#', '-')}@exemplo.invalid`, nome, loja);
    await this.controle('/contribuintes', { cpf, nome: nomeNaMarinha(nome) });
    await this.naLoja(loja, 'atendente', 'consultar CPF na Marinha', 'GET', `/clientes/consulta-marinha?cpf=${cpf}`);
    const cliente = await this.naLoja<{ id: string }>(loja, 'atendente', 'cadastrar cliente', 'POST', '/clientes', {
      nome, documento: cpf, documentoTipo: 'CPF', rg: String(this.a.inteiro(100000000, 999999999)), orgaoEmissor: 'SSP/SP', nacionalidade: 'Brasileira', naturalidade: 'Santos/SP',
      estrangeiro: false, dataNascimento: `19${this.a.inteiro(70, 99)}-0${this.a.inteiro(1, 9)}-1${this.a.inteiro(0, 8)}`, genero: this.a.escolher(['M', 'F']),
      email: `motor.${this.d.rodada}.${this.id.replace('#', '-')}@exemplo.invalid`, telefone: '+5513966660000', termoAceite: true,
      enderecoJson: JSON.stringify({ cep: '11095460', logradouro: 'Rua Sintética', numero: String(this.a.inteiro(1, 999)), bairro: 'Centro', cidade: 'Santos', uf: 'SP' }),
      observacoes: `SINTETICO — motor ${this.d.rodada}`,
    });
    if (comDocumentos) {
      for (const tipo of ['IDENTIDADE', 'SELFIE', 'COMPROVANTE_RESIDENCIA']) {
        await this.naLoja(loja, 'atendente', 'enviar documento do cliente', 'PUT', `/clientes/${cliente.id}/anexos/${tipo}`, { conteudoBase64: dataUrl(fotoSintetica(`${cliente.id}-${tipo}`)) });
      }
    }
    this.d.medidas.contar('clientes_novos_balcao');
    return { id: cliente.id, cpf };
  }
}

// ---- a loja por trás do balcão ---------------------------------------------------------------------------

export class RotinaDaLoja extends Jornada {
  /** O gerente abre a OS; o MECÂNICO inicia e conclui. O jetski some da frota enquanto isso (RN06). */
  async manutencao(loja: Loja): Promise<string> {
    const m = this.d.cenario.manutencao;
    await this.d.relogio.ate(this.a.entre(minutosDoDia(m.janela[0]), minutosDoDia(m.janela[1])));
    const frota = await this.naLoja<Jetski[]>(loja, 'gerente', 'listar jetskis', 'GET', '/jetskis');
    const alvo = frota.find((j) => j.status === 'DISPONIVEL' && !loja.emUso.has(j.id));
    if (!alvo) return 'sem-jetski-parado';
    loja.emUso.add(alvo.id);
    Object.assign(this.contexto, { jetski: alvo.serie, tipo: 'CORRETIVA', problema: 'ruído na turbina relatado por cliente' });
    try {
      const os = await this.naLoja<{ id: string }>(loja, 'gerente', 'abrir OS', 'POST', '/manutencoes', { jetskiId: alvo.id, tipo: 'CORRETIVA', prioridade: 'MEDIA', descricaoProblema: 'SINTETICO — ruído na turbina relatado por cliente', horimetroAbertura: alvo.horimetroAtual ?? 0 });
      this.contexto.osId = os.id;
      await this.d.relogio.esperar(this.a.entre(5, 20));
      await this.naLoja(loja, 'mecanico', 'iniciar OS', 'POST', `/manutencoes/${os.id}/start`);
      await this.esperar(m.duracaoMin);
      await this.naLoja(loja, 'mecanico', 'concluir OS', 'POST', `/manutencoes/${os.id}/finish`, { horimetroFechamento: alvo.horimetroAtual ?? 0, valorPecas: 180.0, valorMaoObra: 120.0, observacoesFinais: 'SINTETICO — limpeza da turbina' });
      return 'os-concluida';
    } finally {
      loja.emUso.delete(alvo.id);
    }
  }

  /** O atendente olha as telas do dia de tempos em tempos, até a loja fechar. */
  async telas(loja: Loja, ate: number): Promise<string> {
    while (this.d.relogio.agora() < ate) {
      await this.naLoja(loja, 'atendente', 'tela: controle do dia', 'GET', `/locacoes/controle-do-dia?data=${hojeLocal()}`);
      await this.naLoja(loja, 'atendente', 'tela: agenda', 'GET', `/reservas/agenda?data=${hojeLocal()}`);
      await this.d.relogio.esperar(this.d.cenario.telas.aCadaMin);
    }
    return 'acompanhou-o-dia';
  }

  /** Fim do expediente: o FINANCEIRO consolida e fecha o dia. Rodada repetida no mesmo dia reabre antes. */
  async fechamento(loja: Loja): Promise<string> {
    const data = hojeLocal();
    const existente = await this.naLoja<{ id: string; bloqueado?: boolean }>(loja, 'financeiro', 'ver fechamento do dia', 'GET', `/fechamentos/dia/data/${data}`).catch((e) => {
      if (e instanceof ErroHttp && e.resposta.status === 404) return undefined;
      throw e;
    });
    if (existente?.bloqueado) await this.naLoja(loja, 'financeiro', 'reabrir fechamento', 'POST', `/fechamentos/dia/${existente.id}/reabrir`);
    const f = await this.naLoja<{ id: string } & Record<string, unknown>>(loja, 'financeiro', 'consolidar o dia', 'POST', '/fechamentos/dia/consolidar', { dtReferencia: data });
    await this.naLoja(loja, 'financeiro', 'fechar o dia', 'POST', `/fechamentos/dia/${f.id}/fechar`);
    this.d.log(`${loja.slug}: dia ${data} fechado — ${JSON.stringify(f).slice(0, 300)}`);
    return 'dia-fechado';
  }
}
