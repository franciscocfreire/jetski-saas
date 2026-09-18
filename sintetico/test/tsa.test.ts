// Fase E6: a TSA sintética (RFC 3161). Contrato pelo servidor dos fakes — o que o BouncyCastle
// do backend e o OpenPDF (PAdES) mandam e exigem — e, quando há `openssl` na máquina, a prova
// independente de que o token é um carimbo de verdade (assinatura, cadeia, imprint, nonce).
import { test, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, randomBytes } from 'node:crypto';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import type { AddressInfo } from 'node:net';
import { criarServidor } from '../src/fakes/servidor.ts';
import { Mundo } from '../src/fakes/mundo.ts';
import { Tsa, montarPedido, OID_TSA, FALHA } from '../src/fakes/tsa.ts';
import { decodificar, inteiroDeBytes, oidParaTexto, oid, octetos, seq, inteiro, booleano, tlv, TAG } from '../src/fakes/der.ts';

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
beforeEach(() => mundo.reset());

const carimbar = async (der: Buffer) => {
  const r = await fetch(`${raiz}/tsa`, { method: 'POST', headers: { 'Content-Type': 'application/timestamp-query' }, body: new Uint8Array(der) });
  return { r, corpo: Buffer.from(await r.arrayBuffer()) };
};
/** Anda pela árvore DER e junta todos os nós, para achar OIDs, inteiros e octetos onde estiverem. */
const todos = (b: Buffer): ReturnType<typeof decodificar> => {
  const saida: ReturnType<typeof decodificar> = [];
  const visitar = (nos: ReturnType<typeof decodificar>) => nos.forEach((n) => { saida.push(n); visitar(n.filhos); });
  visitar(decodificar(b));
  return saida;
};
const statusDe = (resp: Buffer) => decodificar(resp)[0].filhos[0].filhos[0].conteudo[0];
/** TimeStampResp → ContentInfo → [0] SignedData → encapContentInfo → [0] OCTET STRING → TSTInfo (nós). */
const tstInfoDe = (resp: Buffer) => {
  const signedData = decodificar(resp)[0].filhos[1].filhos[1].filhos[0];
  const eContent = signedData.filhos[2].filhos[1].filhos[0];
  return todos(eContent.conteudo);
};

test('TimeStampReq como o BouncyCastle manda (sha256, nonce, certReq) → token com imprint e nonce ecoados', async () => {
  const hash = createHash('sha256').update('documento sintetico').digest();
  const nonce = randomBytes(8);
  const { r, corpo } = await carimbar(montarPedido(hash, OID_TSA.sha256, nonce, true));
  assert.equal(r.status, 200);
  assert.equal(r.headers.get('content-type'), 'application/timestamp-reply');
  assert.equal(statusDe(corpo), 0, 'PKIStatus granted');
  const nos = todos(corpo);
  const oids = nos.filter((n) => n.tag === TAG.OID).map((n) => oidParaTexto(n.conteudo));
  for (const o of [OID_TSA.signedData, OID_TSA.tstInfo, OID_TSA.signingCertificateV2, OID_TSA.messageDigest, OID_TSA.contentType]) assert.ok(oids.includes(o), `falta ${o}`);
  const tst = tstInfoDe(corpo);
  assert.ok(tst.some((n) => n.tag === TAG.OCTET_STRING && n.conteudo.equals(hash)), 'messageImprint ecoado');
  const nonceDer = inteiroDeBytes(nonce);
  assert.ok(tst.some((n) => n.tag === TAG.INTEGER && n.bruto.equals(nonceDer)), 'nonce ecoado');
  assert.equal(tst.filter((n) => n.tag === TAG.GENERALIZED_TIME).length, 1, 'um genTime');
  // certificados no token (certReq=true) e tamanho abaixo do limite do PAdES (4096)
  assert.ok(nos.some((n) => n.tag === TAG.BIT_STRING && n.conteudo.length > 200), 'certificado com assinatura no token');
  assert.ok(corpo.length < 4096, `token de ${corpo.length} bytes`);
  const eventos = await (await fetch(`${raiz}/_controle/eventos?tipo=CARIMBO`)).json();
  assert.equal(eventos.length, 1);
  assert.match(eventos[0].detalhe, /^ref=[0-9A-F]{16} hash=sha256 serial=[0-9a-f]+ nonce=sim cert=true/);
  assert.match(await (await fetch(`${raiz}/metrics`)).text(), /fakes_carimbos_total\{hash="sha256",cert="true"\} 1/);
});

test('o pedido do PAdES (OpenPDF): sha1 + nonce = currentTimeMillis + certReq → imprint (com NULL) e nonce ecoados', async () => {
  const hash = createHash('sha1').update('pdf').digest();
  const nonce = Buffer.from(Date.now().toString(16).padStart(12, '0'), 'hex');
  const { corpo } = await carimbar(montarPedido(hash, OID_TSA.sha1, nonce, true));
  assert.equal(statusDe(corpo), 0);
  const tst = tstInfoDe(corpo);
  assert.ok(tst.some((n) => n.bruto.equals(inteiroDeBytes(nonce))), 'nonce ecoado');
  assert.ok(tst.some((n) => n.tag === TAG.NULL), 'sha1 vem com parâmetros NULL e o TSTInfo devolve o mesmo AlgorithmIdentifier');
});

test('sha1 sem nonce (como `openssl ts -query -no_nonce`) → aceito; o TSTInfo só tem version, serial e accuracy como inteiros', async () => {
  const hash = createHash('sha1').update('pdf').digest();
  const { corpo } = await carimbar(montarPedido(hash, OID_TSA.sha1, undefined, true));
  assert.equal(statusDe(corpo), 0);
  const tst = tstInfoDe(corpo);
  assert.ok(tst.some((n) => n.tag === TAG.OCTET_STRING && n.conteudo.equals(hash)));
  assert.ok(tst.filter((n) => n.tag === TAG.OID).map((n) => oidParaTexto(n.conteudo)).includes(OID_TSA.sha1));
  assert.equal(tst.filter((n) => n.tag === TAG.INTEGER).length, 3, 'sem nonce, o TSTInfo só tem os inteiros version, serial e accuracy');
});

test('o eco é byte a byte: imprint sha256 SEM parâmetros (como o BouncyCastle manda) e nonce NEGATIVO voltam iguais', async () => {
  const hash = createHash('sha256').update('bc').digest();
  const imprint = seq(seq(oid(OID_TSA.sha256)), octetos(hash)); // sem NULL
  const nonceNegativo = tlv(TAG.INTEGER, Buffer.from([0xff, 0x12, 0x34])); // INTEGER negativo
  const { corpo } = await carimbar(seq(inteiro(1), imprint, nonceNegativo, booleano(true)));
  assert.equal(statusDe(corpo), 0);
  const tst = tstInfoDe(corpo);
  assert.ok(tst.some((n) => n.bruto.equals(imprint)), 'messageImprint idêntico ao pedido (sem NULL inventado)');
  assert.ok(tst.some((n) => n.bruto.equals(nonceNegativo)), 'nonce negativo idêntico ao pedido');
});

test('rejeições com o failInfo certo: badAlg para hash desconhecido, badRequest para version ≠ 1, badDataFormat para corpo grande', async () => {
  const bit = (corpo: Buffer) => {
    const bits = todos(corpo).find((n) => n.tag === TAG.BIT_STRING) as { conteudo: Buffer };
    const b = bits.conteudo.subarray(1);
    for (let i = 0; i < b.length * 8; i++) if (b[i >> 3] & (0x80 >> (i & 7))) return i;
    return -1;
  };
  const md5 = seq(inteiro(1), seq(seq(oid('1.2.840.113549.2.5'), tlv(TAG.NULL, Buffer.alloc(0))), octetos(Buffer.alloc(16))));
  assert.equal(bit((await carimbar(md5)).corpo), FALHA.badAlg);
  const v2 = seq(inteiro(2), seq(seq(oid(OID_TSA.sha256)), octetos(Buffer.alloc(32))));
  assert.equal(bit((await carimbar(v2)).corpo), FALHA.badRequest);
  const { r, corpo } = await carimbar(Buffer.concat([montarPedido(Buffer.alloc(32), OID_TSA.sha256), Buffer.alloc(9_000)]));
  assert.equal(r.status, 200);
  assert.equal(bit(corpo), FALHA.badDataFormat);
  // DER aninhado fundo (1 KB de SEQUENCEs vazias uma dentro da outra) é recusado, não estoura a pilha
  let fundo = Buffer.alloc(0);
  for (let i = 0; i < 400; i++) fundo = seq(fundo);
  assert.equal(statusDe((await carimbar(fundo)).corpo), 2);
});

test('/_controle: escrita sem Content-Type application/json é recusada (o backend pode alcançar o fake pela rede)', async () => {
  const semJson = await fetch(`${raiz}/_controle/reset`, { method: 'POST', headers: { 'Content-Type': 'application/timestamp-query' }, body: new Uint8Array([0x30, 0x00]) });
  assert.equal(semJson.status, 415);
  const comJson = await fetch(`${raiz}/_controle/reset`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
  assert.equal(comJson.status, 200);
  assert.equal((await fetch(`${raiz}/_controle/estado`)).status, 200, 'leitura continua livre');
});

test('DER malformado e hash de tamanho errado → status 2 com failInfo badDataFormat, nunca 5xx', async () => {
  for (const ruim of [Buffer.from('não é DER, e tem bytes altos: ção'), Buffer.from([0x30, 0x03, 0x02, 0x01, 0x01]), montarPedido(Buffer.alloc(20), OID_TSA.sha256)]) {
    const { r, corpo } = await carimbar(ruim);
    assert.equal(r.status, 200);
    assert.equal(statusDe(corpo), 2, 'rejection');
    assert.ok(todos(corpo).some((n) => n.tag === TAG.BIT_STRING), 'failInfo presente');
  }
});

test('falha injetada na etapa tsa: 200 com status=2 (o backend degrada para âncora interna); latência e métricas', async () => {
  assert.equal((await fetch(`${raiz}/_controle/config`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ falhas: { tsa: { taxa: 1 } }, latenciaMs: { tsa: 10 } }) })).status, 200);
  const { r, corpo } = await carimbar(montarPedido(createHash('sha256').update('x').digest(), OID_TSA.sha256));
  assert.equal(r.status, 200);
  assert.equal(statusDe(corpo), 2);
  const m = await (await fetch(`${raiz}/metrics`)).text();
  assert.match(m, /fakes_falhas_injetadas_total\{etapa="tsa",modo="erro"\} 1/);
  assert.match(m, /fakes_latencia_injetada_seconds_count\{etapa="tsa"\} 1/);
});

test('o certificado é publicado em PEM e DER; a chave sobrevive ao reset (o mesmo cert antes e depois)', async () => {
  const pem = await (await fetch(`${raiz}/tsa/cert.pem`)).text();
  assert.match(pem, /^-----BEGIN CERTIFICATE-----\n[A-Za-z0-9+/=\n]+-----END CERTIFICATE-----\n$/);
  const der = Buffer.from(await (await fetch(`${raiz}/tsa/cert.der`)).arrayBuffer());
  assert.equal(Buffer.from(pem.replace(/-----[A-Z ]+-----|\n/g, ''), 'base64').equals(der), true);
  mundo.reset();
  assert.equal(await (await fetch(`${raiz}/tsa/cert.pem`)).text(), pem);
});

test('prova independente com o OpenSSL (pulado se não houver openssl): verify OK, adulterado FAILED, CMS OK', { skip: spawnSync('openssl', ['version']).status !== 0 }, async () => {
  const dir = mkdtempSync(join(tmpdir(), 'tsa-'));
  try {
    const doc = join(dir, 'doc.txt');
    writeFileSync(doc, 'documento sintetico');
    execFileSync('openssl', ['ts', '-query', '-data', doc, '-sha256', '-cert', '-out', join(dir, 'q.tsq')]);
    const tsa = new Tsa();
    writeFileSync(join(dir, 'cert.pem'), tsa.certPem);
    const pedido = Tsa.lerPedido(readFileSync(join(dir, 'q.tsq')));
    writeFileSync(join(dir, 'r.tsr'), tsa.carimbar(pedido).resposta);
    const verify = (args: string[]) => spawnSync('openssl', ['ts', '-verify', ...args, '-in', join(dir, 'r.tsr'), '-CAfile', join(dir, 'cert.pem')], { encoding: 'utf8' });
    assert.match(verify(['-queryfile', join(dir, 'q.tsq')]).stdout + '', /Verification: OK/);
    assert.match(verify(['-data', doc]).stdout + '', /Verification: OK/);
    writeFileSync(join(dir, 'outro.txt'), 'outro');
    const adulterado = verify(['-data', join(dir, 'outro.txt')]);
    assert.doesNotMatch(adulterado.stdout + adulterado.stderr, /Verification: OK/);
    execFileSync('openssl', ['ts', '-reply', '-in', join(dir, 'r.tsr'), '-token_out', '-out', join(dir, 'tok.der')]);
    const cms = spawnSync('openssl', ['cms', '-verify', '-inform', 'DER', '-in', join(dir, 'tok.der'), '-CAfile', join(dir, 'cert.pem'), '-purpose', 'timestampsign', '-out', '/dev/null'], { encoding: 'utf8' });
    assert.match(cms.stderr + cms.stdout, /Verification successful/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
