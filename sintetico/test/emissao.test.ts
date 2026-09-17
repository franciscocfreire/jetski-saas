// Fase E3b: o que dá para conferir sem o espelho — imagens sintéticas, CPF e o catálogo.
// O teste de integração é o `semeador provar-emissao`, contra o espelho vivo.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { inflateSync } from 'node:zlib';
import { cpfAleatorio, lerCatalogoEmissao } from '../src/emissao.ts';
import { cpfValido } from '../src/fakes/artefatos.ts';
import { assinaturaSintetica, dataUrl, fotoSintetica } from '../src/lib/imagem.ts';

/** Percorre os blocos do PNG e devolve tipo → dados (o backend confere os magic bytes e decodifica). */
function blocos(png: Buffer): Map<string, Buffer> {
  assert.deepEqual([...png.subarray(0, 8)], [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const saida = new Map<string, Buffer>();
  for (let i = 8; i < png.length; ) {
    const n = png.readUInt32BE(i);
    saida.set(png.subarray(i + 4, i + 8).toString('latin1'), png.subarray(i + 8, i + 8 + n));
    i += n + 12;
  }
  return saida;
}

test('foto sintética: PNG RGB válido, determinístico, com peso de foto e marcado SINTETICO', () => {
  const foto = fotoSintetica('balcao-breno-SELFIE');
  const b = blocos(foto);
  const ihdr = b.get('IHDR') as Buffer;
  assert.deepEqual([ihdr.readUInt32BE(0), ihdr.readUInt32BE(4), ihdr[8], ihdr[9]], [320, 240, 8, 2]);
  assert.equal(inflateSync(b.get('IDAT') as Buffer).length, (320 * 3 + 1) * 240, 'uma linha de filtro + RGB por pixel');
  assert.match((b.get('tEXt') as Buffer).toString('latin1'), /SINTETICO/);
  assert.ok(foto.length > 100_000 && foto.length < 400_000, `${foto.length} bytes`);
  assert.ok(foto.equals(fotoSintetica('balcao-breno-SELFIE')) && !foto.equals(fotoSintetica('outra')));
});

test('assinatura sintética: pequena (limite do link do instrutor é 1 MB), com traço, em data URL', () => {
  const png = assinaturaSintetica('instrutor-ivo');
  const pixels = inflateSync(blocos(png).get('IDAT') as Buffer);
  assert.ok(png.length < 50_000);
  assert.ok(pixels.includes(0x20) && pixels.includes(0xff), 'tem traço escuro e fundo branco');
  assert.ok(dataUrl(png).startsWith('data:image/png;base64,iVBORw0KGgo'));
});

test('CPF sorteado tem DV válido', () => {
  for (let i = 0; i < 200; i++) assert.ok(cpfValido(cpfAleatorio()));
});

test('catálogo E3b: coerente e sem o que já quebrou', () => {
  const cat = lerCatalogoEmissao();
  const empresas = [cat.eama, ...cat.delegadas];
  const chavesDeEmpresa = new Set(empresas.map((e) => e.chave));
  for (const c of cat.clientesBalcao) assert.ok(chavesDeEmpresa.has(c.empresa), `${c.chave} aponta para empresa inexistente`);
  for (const e of empresas) assert.match(e.slug, /^sintetico-[a-z0-9-]+$/);

  const nomes = [...empresas.map((e) => e.adminNome), ...cat.eama.instrutores.map((i) => i.nome), ...cat.delegadas.flatMap((d) => d.equipe.map((m) => m.nome)), ...cat.clientesPortal.map((c) => c.nome), ...cat.clientesBalcao.map((c) => c.nome)];
  for (const n of nomes) assert.doesNotMatch(n, /[()]/, `"${n}": parênteses quebram a ativação (bug conhecido)`);

  const emails = [...empresas.map((e) => e.adminEmail), ...cat.delegadas.flatMap((d) => d.equipe.map((m) => m.email)), ...cat.clientesPortal.map((c) => c.email), String(cat.eama.geral.marinhaEmail), String(cat.eama.geral.smtpFrom)];
  for (const e of emails) assert.match(e, /@exemplo\.invalid$/, `${e} tem de ser .invalid — nunca resolve`);
  assert.equal(new Set(emails).size, emails.length, 'e-mails únicos');

  // O ofício só sai pelo SMTP da EAMA; no espelho ele tem de ser o Mailpit, sem TLS.
  assert.deepEqual([cat.eama.geral.smtpHost, cat.eama.geral.smtpPort, cat.eama.geral.smtpStarttls], ['mailpit', 1025, false]);
  assert.ok(cat.delegadas.some((d) => d.equipe.some((m) => m.papeis.includes('OPERADOR'))), 'alguém de balcão que não é admin');
});
