/**
 * Teste de contrato dos fakes: refaz, passo a passo, o que o `GruClient` do backend faz
 * (GRU_HTTP_CONTRACT.md) — cookie jar manual, redirects não seguidos, mesmos corpos, mesmas
 * expressões regulares de extração. Se o GruClient mudar um passo, este arquivo muda junto.
 */
import { test, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import type { AddressInfo } from 'node:net';
import { criarServidor } from '../src/fakes/servidor.ts';
import { Mundo } from '../src/fakes/mundo.ts';
import { crc16, cpfValido, qrPng } from '../src/fakes/artefatos.ts';
import { qrCode } from '../src/fakes/qr.ts';

// As mesmas expressões do GruClient.java.
const ID_GRU = /id_gru=(\d+)/;
const TOKEN = /name="token"[^>]*value="([^"]+)"/;
const ID_SESSAO = /idSessao=([0-9a-fA-F-]{36})/;
const DS_CONTRIBUINTE = /name="ds_contribuinte"[^>]*value="([^"]*)"/;
const NUMERO_NO_PDF = /(?<!\d)(6089310\d{10})(?!\d)/;
const ITEM = '060;288  ;408';

const mundo = new Mundo();
const servidor = criarServidor(mundo);
let raiz = '';
before(async () => {
  await new Promise<void>((ok) => servidor.listen(0, '127.0.0.1', ok));
  raiz = `http://127.0.0.1:${(servidor.address() as AddressInfo).port}`;
});
after(() => {
  servidor.closeAllConnections();
  servidor.close();
});
beforeEach(() => {
  mundo.reset();
  mundo.agora = () => Date.now();
  mundo.sorteio = () => Math.random();
});

/** Cliente mínimo com o comportamento do GruClient: jar manual, sem seguir redirect. */
class Robo {
  cookies = new Map<string, string>();
  base = `${raiz}/marinha/scam/emitgruscam/`;

  async pedir(url: string, init: RequestInit = {}): Promise<Response> {
    const cabecalhos = new Headers(init.headers);
    if (this.cookies.size) cabecalhos.set('Cookie', [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; '));
    const r = await fetch(url, { ...init, headers: cabecalhos, redirect: 'manual' });
    for (const sc of r.headers.getSetCookie()) {
      const primeiro = sc.split(';', 2)[0].trim();
      const eq = primeiro.indexOf('=');
      if (eq > 0) this.cookies.set(primeiro.slice(0, eq), primeiro.slice(eq + 1)); // ignora o malformado
    }
    return r;
  }

  form(url: string, campos: Record<string, string>): Promise<Response> {
    return this.pedir(url, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(campos).toString() });
  }

  async abrir(): Promise<void> {
    await this.pedir(this.base + 'solicitar_servico.asp');
  }

  async cascata(cpf: string, item = ITEM): Promise<void> {
    await this.form(this.base + 'objTipoRecolhimentoAjax.asp', { v_cd_orgao: '89310' });
    await this.form(this.base + 'objTipoServicoAjax.asp', { v_cd_orgao: '89310', v_id_tipo_recolhimento: '1' });
    await this.form(this.base + 'objServicosAjax.asp', { v_cd_orgao: '89310', v_id_tipo_recolhimento: '1', v_tipo_servico: '060' });
    await this.form(this.base + 'objQtdeServicoAjax.asp', { v_sel_servico_adm: item });
    await this.form(this.base + 'objContribuinte.asp', { v_nr_contribuinte: cpf, v_tipo_documento: 'CPF' });
    await this.form(this.base + 'objCidadeAjax.asp', { v_cep: '11095460', v_nr_endereco: '10', v_complemento_contribuinte: '' });
  }

  formGerar(cpf: string, nome: string): Record<string, string> {
    return {
      cd_orgao: '89310', id_tipo_recolhimento: '1', tipo_servico: '060', cd_servico: ITEM, tipo_documento: 'CPF',
      nr_contribuinte: cpf, ds_contribuinte: nome, fg_incluir_contribuinte: '0', cep_contribuinte: '11095460',
      ender_contribuinte: 'Rua Sintética', nr_endereco: '10', complemento_contribuinte: '', bairro_contribuinte: 'Centro',
      municipio_contribuinte: 'Santos', uf_contribuinte: 'SP',
    };
  }

  /** Passos 1–5: devolve o idSessao, ou o corpo do bridge se ele recusar. */
  async ateOBridge(cpf: string, nome: string): Promise<{ idSessao?: string; idGru: string; corpo: string }> {
    await this.abrir();
    await this.cascata(cpf);
    const r3 = await this.form(this.base + 'pagtesouro.asp', this.formGerar(cpf, nome));
    assert.equal(r3.status, 302);
    const local = r3.headers.get('location') as string;
    const idGru = (ID_GRU.exec(local) as RegExpExecArray)[1];
    const ponte = await (await this.pedir(new URL(local, this.base).toString())).text();
    const token = (TOKEN.exec(ponte) as RegExpExecArray)[1];
    const r5 = await this.form(`${raiz}/marinha/pagtesouro/index.php`, { req: '127.0.0.1', token, id_gru: idGru, cpf_cnpj: cpf, nome, id_svc: ITEM, qtd: '' });
    const corpo = await r5.text();
    return { idSessao: ID_SESSAO.exec(corpo)?.[1], idGru, corpo };
  }
}

const CPF = '52998224725';
const controle = (rota: string, corpo?: unknown) =>
  fetch(`${raiz}/_controle${rota}`, corpo === undefined ? {} : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corpo) });

test('PIX de ponta a ponta: gerar, pendente, persona paga, concluído', async () => {
  const nome = 'JOSÉ DA CONCEIÇÃO'; // acento atravessa form → ponte → PagTesouro
  const robo = new Robo();
  const { idSessao, idGru, corpo } = await robo.ateOBridge(CPF, nome);
  assert.ok(idSessao, corpo);

  const dados = await (await fetch(`${raiz}/pagtesouro/api/pagamentos/dados-pagamento?idSessao=${idSessao}`)).json();
  assert.match(dados.numeroReferencia, /^6089310\d{10}$/);
  assert.equal(dados.valor, 60.32);
  assert.equal(dados.contribuinte.nome, nome);

  const pix = await (
    await fetch(`${raiz}/pagtesouro/api/pagamentos/meios-pagamento/pix`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idSessao, informacoesAdicionais: [{ nome: 'Origem', valor: 'PagTesouro' }] }),
    })
  ).json();
  assert.match(pix.dataExpiracao, /^\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}$/);
  assert.ok(pix.conteudo.startsWith('000201'));
  assert.equal(crc16(pix.conteudo.slice(0, -4)), pix.conteudo.slice(-4), 'CRC do BR Code');
  assert.deepEqual([...Buffer.from(pix.imagem, 'base64').subarray(0, 8)], [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

  const sonda = () => fetch(`${raiz}/pagtesouro/api/pagamentos/pix-stn/sonda?idSessao=${idSessao}`).then((r) => r.json());
  const pendente = await sonda();
  assert.ok(Array.isArray(pendente) && pendente[0].codigo === 'C0008', 'ninguém paga sozinho');

  const pago = await controle(`/gru/${idSessao}/pagar`, {});
  assert.equal(pago.status, 200);
  const concluido = await sonda();
  assert.equal(concluido.situacao.codigo, 'CONCLUIDO');
  assert.equal(concluido.numeroReferencia, dados.numeroReferencia);
  assert.equal(concluido.contribuinte.codigoIdentificador, CPF);
  assert.ok(!Number.isNaN(Date.parse(concluido.situacao.data)));

  const eventos = await (await controle('/eventos')).json();
  assert.deepEqual(
    eventos.map((e: { tipo: string }) => e.tipo).filter((t: string) => t !== 'RESET'),
    ['CONSULTA_CPF', 'GRU_CRIADA', 'SESSAO_PAGTESOURO', 'PIX_GERADO', 'PAGAMENTO'],
  );
  assert.equal(eventos.at(-1).idGru, idGru);
});

test('boleto: 302 → 302 → PDF com o número extraível', async () => {
  const robo = new Robo();
  await robo.abrir();
  await robo.cascata(CPF);
  const r1 = await robo.form(robo.base + 'atualiza_gru.asp', robo.formGerar(CPF, 'MARIA SINTETICA'));
  assert.equal(r1.status, 302);
  assert.match(r1.headers.get('location') as string, ID_GRU); // "v_id_gru=" casa com o mesmo regex
  const r2 = await robo.pedir(new URL(r1.headers.get('location') as string, robo.base).toString());
  assert.equal(r2.status, 302);
  const r3 = await robo.pedir(new URL(r2.headers.get('location') as string, robo.base).toString());
  const pdf = Buffer.from(await r3.arrayBuffer());
  assert.ok(pdf.length >= 1000, `PDF com ${pdf.length} bytes`);
  assert.equal(pdf.subarray(0, 4).toString('latin1'), '%PDF');
  assert.match(pdf.toString('latin1'), NUMERO_NO_PDF);
  // A tabela xref aponta para o início real de cada objeto (o pdfbox é estrito nisso).
  const texto = pdf.toString('latin1');
  const xref = Number(/startxref\n(\d+)/.exec(texto)?.[1]);
  assert.ok(texto.slice(xref).startsWith('xref'));
  [...texto.slice(xref).matchAll(/(\d{10}) 00000 n /g)].forEach((m, i) => assert.ok(texto.slice(Number(m[1])).startsWith(`${i + 1} 0 obj`)));
});

test('sem a cascata, ou com o item sem os espaços, a Marinha não gera id_gru', async () => {
  const semCascata = new Robo();
  await semCascata.abrir();
  assert.equal((await semCascata.form(semCascata.base + 'pagtesouro.asp', semCascata.formGerar(CPF, 'X'))).status, 200);

  const itemErrado = new Robo();
  await itemErrado.abrir();
  await itemErrado.cascata(CPF, '060;288;408');
  assert.equal((await itemErrado.form(itemErrado.base + 'pagtesouro.asp', itemErrado.formGerar(CPF, 'X'))).status, 200);
});

test('bridge sem o cookie da sessão responde "problema de autenticação" (gotcha do cookie jar)', async () => {
  const robo = new Robo();
  await robo.abrir();
  await robo.cascata(CPF);
  const r3 = await robo.form(robo.base + 'pagtesouro.asp', robo.formGerar(CPF, 'X'));
  const idGru = (ID_GRU.exec(r3.headers.get('location') as string) as RegExpExecArray)[1];
  const ponte = await (await robo.pedir(new URL(r3.headers.get('location') as string, robo.base).toString())).text();
  const token = (TOKEN.exec(ponte) as RegExpExecArray)[1];
  const semCookie = await fetch(`${raiz}/marinha/pagtesouro/index.php`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ token, id_gru: idGru, cpf_cnpj: CPF, nome: 'X' }).toString(),
  });
  const corpo = await semCookie.text();
  assert.equal(semCookie.status, 200);
  assert.ok(corpo.includes('autentica') && !ID_SESSAO.test(corpo));
});

test('a sessão ASP é de uso único e o token é por CPF', async () => {
  const robo = new Robo();
  const a = await robo.ateOBridge(CPF, 'X');
  assert.ok(a.idSessao);
  const reuso = await robo.form(robo.base + 'pagtesouro.asp', robo.formGerar(CPF, 'X'));
  assert.equal(reuso.status, 200, 'sessão consumida não gera outra GRU');
});

test('bloqueio por volume: desligado por padrão; ligado, recusa só aquele CPF', async () => {
  for (let i = 0; i < 3; i++) assert.ok((await new Robo().ateOBridge(CPF, 'X')).idSessao, 'sem bloqueio por padrão');
  assert.equal((await controle('/config', { bloqueioCpf: { ligado: true, limite: 3 } })).status, 200);
  const bloqueado = await new Robo().ateOBridge(CPF, 'X');
  assert.equal(bloqueado.idSessao, undefined);
  assert.ok(bloqueado.corpo.includes('autentica'));
  assert.ok((await new Robo().ateOBridge('11144477735', 'Y')).idSessao, 'outro CPF segue normal');
});

test('falha e latência injetadas aparecem no /metrics; config inválida é recusada', async () => {
  assert.equal((await controle('/config', { falhas: { marinha: { taxa: 2 } } })).status, 400);
  assert.equal((await controle('/config', { falhas: { correios: { taxa: 1 } } })).status, 400);
  assert.equal((await controle('/config', { falhas: { marinha: { taxa: 1 } }, latenciaMs: { pagtesouro: 20 } })).status, 200);
  assert.equal((await fetch(`${raiz}/marinha/scam/emitgruscam/solicitar_servico.asp`)).status, 503);
  assert.equal((await fetch(`${raiz}/pagtesouro/api/pagamentos/pix-stn/sonda?idSessao=x`)).status, 400);
  const metricas = await (await fetch(`${raiz}/metrics`)).text();
  assert.match(metricas, /fakes_falhas_injetadas_total\{etapa="marinha",modo="erro"\} 1/);
  assert.match(metricas, /fakes_latencia_injetada_seconds_sum\{etapa="pagtesouro"\} 0\.02/);
  assert.match(metricas, /fakes_requisicoes_total\{etapa="marinha",status="503"\} 1/);
});

test('PIX expira (C0026), não pode mais ser pago, e o pagamento automático é opcional', async () => {
  const { idSessao } = await new Robo().ateOBridge(CPF, 'X');
  const gerarPix = (id: string) =>
    fetch(`${raiz}/pagtesouro/api/pagamentos/meios-pagamento/pix`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ idSessao: id }) });
  await gerarPix(idSessao as string);
  const t0 = Date.now();
  mundo.agora = () => t0 + 31 * 60_000;
  const sonda = await (await fetch(`${raiz}/pagtesouro/api/pagamentos/pix-stn/sonda?idSessao=${idSessao}`)).json();
  assert.equal(sonda[0].codigo, 'C0026');
  assert.equal((await controle(`/gru/${idSessao}/pagar`, {})).status, 409);

  mundo.agora = () => Date.now();
  await controle('/config', { autoPagarAposSeg: 0 });
  const outro = await new Robo().ateOBridge('11144477735', 'Y');
  await gerarPix(outro.idSessao as string);
  const auto = await (await fetch(`${raiz}/pagtesouro/api/pagamentos/pix-stn/sonda?idSessao=${outro.idSessao}`)).json();
  assert.equal(auto.situacao.codigo, 'CONCLUIDO');
});

test('consulta de nome por CPF: determinística, registrável e vazia para CPF inválido', async () => {
  const consultar = async (cpf: string) => {
    const robo = new Robo();
    await robo.abrir();
    const r = await robo.form(robo.base + 'objContribuinte.asp', { v_nr_contribuinte: cpf, v_tipo_documento: 'CPF' });
    return (DS_CONTRIBUINTE.exec(await r.text()) as RegExpExecArray)[1];
  };
  const nome = await consultar(CPF);
  assert.match(nome, / SINTETICO$/);
  assert.equal(await consultar(CPF), nome);
  assert.equal(await consultar('12345678900'), '');
  await controle('/contribuintes', { cpf: CPF, nome: 'CLARA CLIENTE SINTETICA' });
  assert.equal(await consultar(CPF), 'CLARA CLIENTE SINTETICA');
  await controle('/contribuintes', { cpf: CPF, nome: null });
  assert.equal(await consultar(CPF), '');
  assert.ok(cpfValido(CPF) && !cpfValido('11111111111'));
});

test('QR Code: tamanho por versão, localizadores e PNG bem formado', () => {
  const pequeno = qrCode('SINTETICO');
  assert.equal(pequeno.length, 21); // versão 1
  const grande = qrCode('x'.repeat(200));
  assert.equal(grande.length, 4 * 9 + 17); // 200 bytes cabem na versão 9-L (230)
  for (const m of [pequeno, grande]) {
    const n = m.length;
    for (const [x, y] of [[0, 0], [n - 7, 0], [0, n - 7]]) {
      assert.ok(m[y][x] && m[y + 6][x + 6] && m[y + 3][x + 3] && !m[y + 1][x + 1], 'localizador');
    }
    assert.ok(m[n - 8][8], 'módulo escuro fixo');
  }
  const png = qrPng('SINTETICO', 2);
  assert.equal(png.readUInt32BE(16), (21 + 8) * 2); // largura no IHDR
  assert.equal(png.subarray(-8, -4).toString('latin1'), 'IEND');
});
