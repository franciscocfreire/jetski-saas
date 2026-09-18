/**
 * fakes-externos — a Marinha (SCAM) e o PagTesouro sintéticos do espelho (fase E2 do
 * ECOSSISTEMA_SINTETICO_SPEC.md). O backend chega aqui só por configuração
 * (`jetski.gru.marinha-base` / `pagtesouro-base`); nenhum código de produção sabe que é fake.
 *
 * Bordas:
 *   /marinha/**      passo a passo do GRU_HTTP_CONTRACT.md (sessão ASP, cascata, 302, ponte, boleto)
 *   /pagtesouro/**   dados-pagamento, PIX, sonda — com máquina de estados do pagamento
 *   /_controle/**    a alavanca dos cenários (pagar, falhas, latência, bloqueio por CPF, eventos)
 *   /metrics         Prometheus          /healthz   vida
 *
 * Uso: node src/fakes/servidor.ts   (env PORTA, padrão 8080)
 */
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import { pathToFileURL } from 'node:url';
import { boletoPdf, brCode, cpfValido, nomeSinteticoDoCpf, qrPng, tokenDoCpf } from './artefatos.ts';
import { ETAPAS, Mundo, dataHoraBr, type Etapa, type Gru } from './mundo.ts';
import { Tsa } from './tsa.ts';

const CD_ORGAO = '89310';
const ITEM_SERVICO = '060;288  ;408'; // os dois espaços são significativos (campo de largura fixa)
const CASCATA = ['objTipoRecolhimentoAjax.asp', 'objTipoServicoAjax.asp', 'objServicosAjax.asp', 'objQtdeServicoAjax.asp', 'objContribuinte.asp', 'objCidadeAjax.asp'];
const AUTH_PROBLEMA = "<html><head><title>PagTesouro</title></head><body>\n<script>alert('Houve um problema de autenticação!');history.back();</script>\n</body></html>";

interface Pedido {
  metodo: string;
  caminho: string;
  consulta: URLSearchParams;
  cookie: string | undefined;
  corpo: string;
  /** O corpo como veio: o TimeStampReq da TSA é DER, e UTF-8 o corromperia. */
  corpoBytes: Buffer;
  form: URLSearchParams;
}

interface Resposta {
  status: number;
  cabecalhos?: Record<string, string | string[]>;
  corpo?: string | Buffer;
}

const html = (corpo: string, extra: Record<string, string | string[]> = {}): Resposta => ({
  status: 200,
  cabecalhos: { 'Content-Type': 'text/html; charset=utf-8', ...extra },
  corpo,
});
const json = (status: number, dado: unknown): Resposta => ({
  status,
  cabecalhos: { 'Content-Type': 'application/json; charset=utf-8' },
  corpo: JSON.stringify(dado),
});
const esc = (s: string) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c] as string);
const erroPagTesouro = (codigo: string, descricao: string) => [{ codigo, descricao }];

function etapaDe(caminho: string): Etapa | undefined {
  if (caminho === '/tsa') return 'tsa';
  if (caminho.startsWith('/pagtesouro/')) return 'pagtesouro';
  if (caminho === '/marinha/pagtesouro/index.php' || caminho.endsWith('/pagtesouro_form.asp')) return 'bridge';
  if (caminho.startsWith('/marinha/')) return 'marinha';
  return undefined;
}

// ---- Marinha ------------------------------------------------------------------------------

function marinha(m: Mundo, p: Pedido): Resposta {
  const base = '/marinha/scam/emitgruscam/';
  if (p.caminho === '/marinha/pagtesouro/index.php' && p.metodo === 'POST') return bridge(m, p);
  if (!p.caminho.startsWith(base)) return { status: 404, corpo: 'nao encontrado' };
  const pagina = p.caminho.slice(base.length);

  if (pagina === 'solicitar_servico.asp' && p.metodo === 'GET') {
    const s = m.abrirSessao();
    // O site real manda também um Set-Cookie malformado ("httpOnly;secure;", sem nome=valor),
    // que derruba cookie jars estritos — foi o gotcha nº 1 do robô. O fake o reproduz.
    return html(
      `<html><body><form name="frm_registro"><select name="cd_orgao"><option value="${CD_ORGAO}">CAPITANIA DOS PORTOS DE SAO PAULO</option></select></form><!-- SINTETICO --></body></html>`,
      { 'Set-Cookie': [`${s.cookie}; path=/`, 'httpOnly;secure;'] },
    );
  }

  const sessao = m.sessaoDe(p.cookie);
  const semSessao = html('<html><body>Sess&atilde;o expirada. Reinicie a solicita&ccedil;&atilde;o.</body></html>');

  if (CASCATA.includes(pagina) && p.metodo === 'POST') {
    if (!sessao) return semSessao;
    sessao.cascata.add(pagina);
    if (pagina === 'objContribuinte.asp') {
      const cpf = (p.form.get('v_nr_contribuinte') ?? '').replace(/\D/g, '');
      sessao.cpf = cpf;
      const nome = m.nomeDoContribuinte(cpf, (c) => (cpfValido(c) ? nomeSinteticoDoCpf(c) : null)) ?? '';
      m.registrar({ tipo: 'CONSULTA_CPF', etapa: 'marinha', cpf, detalhe: nome ? 'encontrado' : 'desconhecido' });
      return html(`<input type="text" name="ds_contribuinte" id="ds_contribuinte" maxlength="45" value="${esc(nome)}">`);
    }
    if (pagina === 'objQtdeServicoAjax.asp' && p.form.get('v_sel_servico_adm') !== ITEM_SERVICO) {
      sessao.cascata.delete(pagina); // item errado (ex.: espaços comidos) não inicializa a sessão
    }
    return html(`<select name="${pagina.replace(/^obj|Ajax\.asp$|\.asp$/g, '')}"><option value="1">SINTETICO</option></select>`);
  }

  if ((pagina === 'pagtesouro.asp' || pagina === 'atualiza_gru.asp') && p.metodo === 'POST') {
    const f = p.form;
    const completo =
      sessao &&
      CASCATA.every((c) => sessao.cascata.has(c)) &&
      f.get('cd_orgao') === CD_ORGAO &&
      f.get('id_tipo_recolhimento') === '1' &&
      f.get('tipo_servico') === '060' &&
      f.get('cd_servico') === ITEM_SERVICO &&
      (f.get('nr_contribuinte') ?? '') !== '' &&
      (f.get('ds_contribuinte') ?? '') !== '';
    // Como o site real: sem sessão inicializada ou sem os campos-chave, devolve o formulário
    // de novo (200, sem redirect) — o robô lê isso como "id_gru não encontrado".
    if (!completo) return html('<html><body><form name="frm_registro"><!-- dados incompletos --></form></body></html>');
    const cpf = (f.get('nr_contribuinte') as string).replace(/\D/g, '');
    const nome = f.get('ds_contribuinte') as string;
    const pix = pagina === 'pagtesouro.asp';
    const g = m.criarGru(pix ? 'PIX' : 'BOLETO', cpf, nome);
    m.registrar({ tipo: 'GRU_CRIADA', etapa: 'marinha', cpf, idGru: g.idGru, detalhe: g.meio });
    const destino = pix
      ? `pagtesouro_form.asp?id_gru=${g.idGru}&cpf_cnpj=${cpf}&svc=${encodeURIComponent(ITEM_SERVICO)}&nome=${encodeURIComponent(nome)}&qtd=`
      : `imprime_gru.asp?v_id_gru=${g.idGru}&v_cd_recolhimento=060`;
    return { status: 302, cabecalhos: { Location: destino }, corpo: '' };
  }

  if (pagina === 'pagtesouro_form.asp' && p.metodo === 'GET') {
    const g = m.grus.get(p.consulta.get('id_gru') ?? '');
    if (!sessao || !g) return semSessao;
    const campo = (nome: string, valor: string) => `  <input type="hidden" name="${nome}" value="${esc(valor)}">`;
    return html(
      [
        '<html><body onload="document.frm_envio.submit()">',
        '<form name="frm_envio" action="/marinha/pagtesouro/index.php" method="post">',
        campo('req', 'dpc1.marinha.mil.br'),
        campo('token', tokenDoCpf(g.cpf)),
        campo('id_gru', g.idGru),
        campo('cpf_cnpj', g.cpf),
        campo('nome', g.nome),
        campo('id_svc', ITEM_SERVICO),
        campo('qtd', ''),
        '</form></body></html>',
      ].join('\n'),
    );
  }

  if (pagina === 'imprime_gru.asp' && p.metodo === 'GET') {
    const g = m.grus.get(p.consulta.get('v_id_gru') ?? '');
    if (!sessao || !g) return semSessao;
    g.arquivoPdf = `gru_${g.idGru}_${g.cpf.slice(-4)}.pdf`;
    sessao.consumida = true;
    m.registrar({ tipo: 'BOLETO_GERADO', etapa: 'marinha', cpf: g.cpf, idGru: g.idGru });
    return { status: 302, cabecalhos: { Location: `${base}gru/tmp/${g.arquivoPdf}` }, corpo: '' };
  }

  if (pagina.startsWith('gru/tmp/') && p.metodo === 'GET') {
    const g = [...m.grus.values()].find((x) => x.arquivoPdf === pagina.slice('gru/tmp/'.length));
    if (!g) return { status: 404, corpo: 'nao encontrado' };
    return { status: 200, cabecalhos: { 'Content-Type': 'application/pdf' }, corpo: boletoPdf({ numero: g.numeroReferencia, nome: g.nome, cpf: g.cpf, valor: g.valor, descricao: g.descricao }) };
  }

  return { status: 404, corpo: 'nao encontrado' };
}

function bridge(m: Mundo, p: Pedido): Resposta {
  const sessao = m.sessaoDe(p.cookie);
  const g = m.grus.get(p.form.get('id_gru') ?? '');
  const cpf = p.form.get('cpf_cnpj') ?? '';
  const recusa = (motivo: string) => {
    m.contar('fakes_bridge_recusas_total', { motivo });
    m.registrar({ tipo: 'BRIDGE_RECUSADO', etapa: 'bridge', cpf, idGru: g?.idGru, detalhe: motivo });
    return html(AUTH_PROBLEMA);
  };
  if (!sessao) return recusa('SEM_SESSAO'); // cookie perdido — o sintoma do cookie jar estrito
  if (!g || g.cpf !== cpf || p.form.get('token') !== tokenDoCpf(cpf)) return recusa('TOKEN_INVALIDO');
  if (m.cpfBloqueado(cpf)) return recusa('CPF_BLOQUEADO_POR_VOLUME');
  const idSessao = m.abrirSessaoPagTesouro(g);
  sessao.consumida = true;
  m.registrar({ tipo: 'SESSAO_PAGTESOURO', etapa: 'bridge', cpf, idGru: g.idGru, idSessao });
  return html(
    `<html><body>Redirecionando para o PagTesouro...\n<iframe src='https://pagtesouro.sintetico.invalid/#/pagamento?idSessao=${idSessao}' width='100%' height='100%'></iframe></body></html>`,
  );
}

// ---- PagTesouro -----------------------------------------------------------------------------

function pagtesouro(m: Mundo, p: Pedido): Resposta {
  const api = '/pagtesouro/api/pagamentos/';
  if (!p.caminho.startsWith(api)) return { status: 404, corpo: 'nao encontrado' };
  const rota = p.caminho.slice(api.length);
  const sessaoInvalida = json(400, erroPagTesouro('C0026', 'Sessão inválida ou expirada.'));

  if (rota === 'dados-pagamento' && p.metodo === 'GET') {
    const g = m.porSessaoPagTesouro.get(p.consulta.get('idSessao') ?? '');
    if (!g) return sessaoInvalida;
    return json(200, {
      contribuinte: contribuinteDe(g),
      descricao: g.descricao,
      valor: g.valor,
      referencia: g.referencia,
      numeroReferencia: g.numeroReferencia,
      pspsMeioTaxa: [{ id: '2', nome: 'Tesouro Nacional (SINTETICO)', tipoPagamento: 'PIX', valorTaxa: 0 }],
    });
  }

  if (rota === 'meios-pagamento/pix' && p.metodo === 'POST') {
    let corpo: { idSessao?: string };
    try {
      corpo = JSON.parse(p.corpo);
    } catch {
      return json(400, erroPagTesouro('C0001', 'Requisição inválida.'));
    }
    const g = m.porSessaoPagTesouro.get(corpo.idSessao ?? '');
    if (!g) return sessaoInvalida;
    if (!g.pix) {
      g.pix = { conteudo: brCode(g.idSessao as string, g.valor), expiraEm: m.agora() + m.config.pixValidadeMin * 60_000 };
      m.registrar({ tipo: 'PIX_GERADO', etapa: 'pagtesouro', cpf: g.cpf, idGru: g.idGru, idSessao: g.idSessao });
    }
    return json(200, {
      conteudo: g.pix.conteudo,
      imagem: qrPng(g.pix.conteudo).toString('base64'),
      dataExpiracao: dataHoraBr(g.pix.expiraEm),
      url: `pix.sintetico.invalid/v2/${(g.idSessao as string).replaceAll('-', '')}`,
    });
  }

  if (rota === 'pix-stn/sonda' && p.metodo === 'GET') {
    const g = m.porSessaoPagTesouro.get(p.consulta.get('idSessao') ?? '');
    if (!g) return sessaoInvalida;
    const situacao = m.situacao(g);
    if (situacao === 'EXPIRADO') return sessaoInvalida;
    if (situacao === 'PENDENTE') return json(400, erroPagTesouro('C0008', 'Erro desconhecido. Tente novamente mais tarde.'));
    return json(200, {
      idPagamento: g.idPagamento,
      numeroReferencia: g.numeroReferencia,
      refTran: `E${(g.idPagamento as string).toUpperCase().slice(0, 14)}`,
      descricao: g.descricao,
      valor: g.valor,
      contribuinte: contribuinteDe(g),
      tipoPagamentoEscolhido: 'PIX',
      situacao: { codigo: 'CONCLUIDO', data: new Date(g.pagaEm as number).toISOString() },
    });
  }

  return { status: 404, corpo: 'nao encontrado' };
}

const contribuinteDe = (g: Gru) => ({ tipoIdentificador: 'CPF', codigoIdentificador: g.cpf, nome: g.nome });

// ---- TSA (RFC 3161) -----------------------------------------------------------------------------

const TIMESTAMP_REPLY = { 'Content-Type': 'application/timestamp-reply' };

function tsa(m: Mundo, p: Pedido): Resposta {
  if (p.caminho === '/tsa/cert.pem' && p.metodo === 'GET') return { status: 200, cabecalhos: { 'Content-Type': 'application/x-pem-file' }, corpo: m.tsa.certPem };
  if (p.caminho === '/tsa/cert.der' && p.metodo === 'GET') return { status: 200, cabecalhos: { 'Content-Type': 'application/pkix-cert' }, corpo: m.tsa.cert };
  if (p.caminho === '/tsa' && p.metodo === 'GET') {
    return html('<html><body><h1>TSA sintética (RFC 3161)</h1><p>POST um TimeStampReq (application/timestamp-query) aqui. Certificado: <a href="/tsa/cert.pem">/tsa/cert.pem</a>. Tudo SINTÉTICO — ambiente espelho do Meu Jet.</p></body></html>');
  }
  if (p.caminho !== '/tsa' || p.metodo !== 'POST') return { status: 404, corpo: 'nao encontrado' };
  let pedido;
  try {
    pedido = Tsa.lerPedido(p.corpoBytes);
  } catch (e) {
    // Como uma TSA real: pedido malformado é resposta 200 com status=2 e failInfo badDataFormat (bit 5).
    m.contar('fakes_tsa_recusas_total', { motivo: 'badDataFormat' });
    m.registrar({ tipo: 'TSA_RECUSADO', etapa: 'tsa', detalhe: e instanceof Error ? e.message : String(e) });
    return { status: 200, cabecalhos: TIMESTAMP_REPLY, corpo: Tsa.recusa(e instanceof Error ? e.message : 'pedido inválido', 5) };
  }
  const c = m.tsa.carimbar(pedido, new Date(m.agora()));
  const alg = pedido.hashOid === '1.3.14.3.2.26' ? 'sha1' : pedido.hashOid === '2.16.840.1.101.3.4.2.1' ? 'sha256' : pedido.hashOid;
  m.contar('fakes_carimbos_total', { hash: alg, cert: String(pedido.certReq) });
  // A referência é o elo com o PDF: a página de auditoria imprime SHA-256(token)[0:16].
  m.registrar({ tipo: 'CARIMBO', etapa: 'tsa', detalhe: `ref=${c.referencia} hash=${alg} serial=${c.serial.toString(16)} nonce=${pedido.nonce ? 'sim' : 'nao'} cert=${pedido.certReq} bytes=${c.tamanho}` });
  return { status: 200, cabecalhos: TIMESTAMP_REPLY, corpo: c.resposta };
}

// ---- /_controle -------------------------------------------------------------------------------

function resumoGru(m: Mundo, g: Gru) {
  return {
    idGru: g.idGru,
    idSessao: g.idSessao ?? null,
    numeroReferencia: g.numeroReferencia,
    meio: g.meio,
    cpf: g.cpf,
    nome: g.nome,
    valor: g.valor,
    situacao: g.meio === 'PIX' ? m.situacao(g) : g.pagaEm ? 'CONCLUIDO' : 'PENDENTE',
    criadaEm: new Date(g.criadaEm).toISOString(),
    pagaEm: g.pagaEm ? new Date(g.pagaEm).toISOString() : null,
    pixExpiraEm: g.pix ? new Date(g.pix.expiraEm).toISOString() : null,
  };
}

function aplicarConfig(m: Mundo, novo: Record<string, unknown>): string | undefined {
  const c = structuredClone(m.config);
  const num = (v: unknown, min: number, max: number) => (typeof v === 'number' && v >= min && v <= max ? v : undefined);
  for (const [chave, valor] of Object.entries(novo)) {
    if (chave === 'falhas' || chave === 'latenciaMs') {
      for (const [etapa, v] of Object.entries(valor as Record<string, unknown>)) {
        if (!ETAPAS.includes(etapa as Etapa)) return `etapa desconhecida: ${etapa} (use ${ETAPAS.join(', ')})`;
        if (chave === 'latenciaMs') {
          const ms = num(v, 0, 120_000);
          if (ms === undefined) return `latenciaMs.${etapa} deve ser um número entre 0 e 120000`;
          c.latenciaMs[etapa as Etapa] = ms;
        } else {
          const f = v as { taxa?: unknown; modo?: unknown };
          const taxa = num(f.taxa, 0, 1);
          if (taxa === undefined) return `falhas.${etapa}.taxa deve estar entre 0 e 1`;
          if (f.modo !== undefined && f.modo !== 'erro' && f.modo !== 'pendurar') return `falhas.${etapa}.modo deve ser "erro" ou "pendurar"`;
          c.falhas[etapa as Etapa] = { taxa, modo: (f.modo as 'erro' | 'pendurar' | undefined) ?? 'erro' };
        }
      }
    } else if (chave === 'bloqueioCpf') {
      const b = valor as { ligado?: unknown; limite?: unknown };
      if (typeof b.ligado === 'boolean') c.bloqueioCpf.ligado = b.ligado;
      if (b.limite !== undefined) {
        const limite = num(b.limite, 1, 1_000_000);
        if (limite === undefined) return 'bloqueioCpf.limite deve ser um número ≥ 1';
        c.bloqueioCpf.limite = limite;
      }
    } else if (chave === 'autoPagarAposSeg') {
      if (valor !== null && num(valor, 0, 86_400) === undefined) return 'autoPagarAposSeg deve ser null ou segundos (0–86400)';
      c.autoPagarAposSeg = valor as number | null;
    } else if (chave === 'pixValidadeMin') {
      const min = num(valor, 0.01, 1_440);
      if (min === undefined) return 'pixValidadeMin deve estar entre 0.01 e 1440';
      c.pixValidadeMin = min;
    } else {
      return `chave desconhecida: ${chave}`;
    }
  }
  m.config = c;
  return undefined;
}

function controle(m: Mundo, p: Pedido): Resposta {
  const rota = p.caminho.slice('/_controle'.length) || '/';
  const corpo = (): Record<string, unknown> | undefined => {
    try {
      const j = p.corpo ? JSON.parse(p.corpo) : {};
      return j && typeof j === 'object' && !Array.isArray(j) ? j : undefined;
    } catch {
      return undefined;
    }
  };

  if (rota === '/estado' && p.metodo === 'GET') {
    return json(200, { config: m.config, grus: m.grus.size, sessoesMarinha: m.sessoes.size, contribuintesRegistrados: m.contribuintes.size, eventos: m.eventos.length });
  }
  if (rota === '/config' && p.metodo === 'POST') {
    const c = corpo();
    const erro = c ? aplicarConfig(m, c) : 'corpo JSON inválido';
    if (erro) return json(400, { erro });
    m.registrar({ tipo: 'CONFIG', detalhe: JSON.stringify(c) });
    return json(200, { config: m.config });
  }
  if (rota === '/grus' && p.metodo === 'GET') {
    const cpf = p.consulta.get('cpf');
    const situacao = p.consulta.get('situacao');
    const lista = [...m.grus.values()].map((g) => resumoGru(m, g)).filter((g) => (!cpf || g.cpf === cpf) && (!situacao || g.situacao === situacao));
    return json(200, lista);
  }
  const pagar = /^\/gru\/([^/]+)\/pagar$/.exec(rota);
  if (pagar && p.metodo === 'POST') {
    const g = m.localizar(decodeURIComponent(pagar[1]));
    if (!g) return json(404, { erro: 'GRU não encontrada (use idSessao, id_gru ou numeroReferencia)' });
    if (g.meio === 'PIX' && !g.pagaEm && m.situacao(g) === 'EXPIRADO') return json(409, { erro: 'PIX expirado — gere outra GRU', gru: resumoGru(m, g) });
    m.pagar(g, 'CONTROLE');
    return json(200, resumoGru(m, g));
  }
  if (rota === '/contribuintes' && p.metodo === 'POST') {
    const c = corpo();
    const cpf = typeof c?.cpf === 'string' ? c.cpf.replace(/\D/g, '') : '';
    if (!cpf || !(typeof c?.nome === 'string' || c?.nome === null)) return json(400, { erro: 'esperado {cpf, nome} — nome null = CPF desconhecido na Marinha' });
    m.contribuintes.set(cpf, c.nome as string | null);
    return json(200, { cpf, nome: c.nome });
  }
  if (rota === '/eventos' && p.metodo === 'GET') {
    const desde = Number(p.consulta.get('desde') ?? 0);
    const tipo = p.consulta.get('tipo');
    return json(200, m.eventos.filter((e) => e.seq > desde && (!tipo || e.tipo === tipo)));
  }
  if (rota === '/reset' && p.metodo === 'POST') {
    m.reset();
    return json(200, { ok: true });
  }
  return json(404, { erro: 'rota de controle desconhecida' });
}

// ---- servidor -----------------------------------------------------------------------------------

async function lerCorpo(req: IncomingMessage): Promise<Buffer> {
  const partes: Buffer[] = [];
  let total = 0;
  for await (const parte of req) {
    total += (parte as Buffer).length;
    if (total > 1_000_000) throw new Error('corpo grande demais');
    partes.push(parte as Buffer);
  }
  return Buffer.concat(partes);
}

const dormir = (ms: number) => new Promise((ok) => setTimeout(ok, ms));

export function criarServidor(m: Mundo = new Mundo()): Server {
  return createServer(async (req: IncomingMessage, res: ServerResponse) => {
    const url = new URL(req.url ?? '/', 'http://fake');
    const caminho = url.pathname;
    const etapa = etapaDe(caminho);
    let resposta: Resposta;
    try {
      const corpoBytes = await lerCorpo(req);
      const corpo = corpoBytes.toString('utf8');
      const tipo = req.headers['content-type'] ?? '';
      const p: Pedido = {
        metodo: req.method ?? 'GET',
        caminho,
        consulta: url.searchParams,
        cookie: req.headers.cookie,
        corpo,
        corpoBytes,
        form: new URLSearchParams(tipo.includes('x-www-form-urlencoded') ? corpo : ''),
      };

      if (etapa) {
        const atraso = m.config.latenciaMs[etapa];
        if (atraso > 0) {
          m.contar('fakes_latencia_injetada_seconds_sum', { etapa }, atraso / 1000);
          m.contar('fakes_latencia_injetada_seconds_count', { etapa });
          await dormir(atraso);
        }
        const falha = m.config.falhas[etapa];
        if (falha.taxa > 0 && m.sorteio() < falha.taxa) {
          m.contar('fakes_falhas_injetadas_total', { etapa, modo: falha.modo });
          m.registrar({ tipo: 'FALHA_INJETADA', etapa, detalhe: `${falha.modo} ${caminho}` });
          if (falha.modo === 'pendurar') {
            await dormir(120_000);
            res.destroy();
            return;
          }
          // A falha típica de cada etapa, na taxonomia do GruClient.
          resposta =
            etapa === 'marinha' ? { status: 503, corpo: 'Service Unavailable' }
            : etapa === 'bridge' ? html(AUTH_PROBLEMA)
            : etapa === 'tsa' ? { status: 200, cabecalhos: TIMESTAMP_REPLY, corpo: Tsa.recusa('falha injetada pelo /_controle') } // RFC 3161: status=2 + failInfo
            : json(500, erroPagTesouro('C0000', 'Erro interno.'));
          m.contar('fakes_requisicoes_total', { etapa, status: String(resposta.status) });
          res.writeHead(resposta.status, resposta.cabecalhos);
          res.end(resposta.corpo);
          return;
        }
      }

      if (caminho === '/healthz') resposta = { status: 200, corpo: 'ok' };
      else if (caminho === '/metrics') resposta = { status: 200, cabecalhos: { 'Content-Type': 'text/plain; version=0.0.4' }, corpo: m.metricas() };
      else if (caminho === '/_controle' || caminho.startsWith('/_controle/')) resposta = controle(m, p);
      else if (caminho.startsWith('/marinha/')) resposta = marinha(m, p);
      else if (caminho.startsWith('/pagtesouro/')) resposta = pagtesouro(m, p);
      else if (caminho === '/tsa' || caminho.startsWith('/tsa/')) resposta = tsa(m, p);
      else resposta = { status: 404, corpo: 'nao encontrado' };
    } catch (e) {
      resposta = json(500, { erro: e instanceof Error ? e.message : String(e) });
    }
    if (etapa) m.contar('fakes_requisicoes_total', { etapa, status: String(resposta.status) });
    res.writeHead(resposta.status, resposta.cabecalhos);
    res.end(resposta.corpo ?? '');
  });
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  const porta = Number(process.env.PORTA ?? 8080);
  const servidor = criarServidor();
  servidor.listen(porta, '0.0.0.0', () => console.log(`fakes-externos (SINTETICO) ouvindo em :${porta} — /marinha /pagtesouro /tsa /_controle /metrics`));
  for (const sinal of ['SIGTERM', 'SIGINT'] as const) {
    process.on(sinal, () => {
      servidor.closeAllConnections();
      servidor.close(() => process.exit(0));
    });
  }
}
