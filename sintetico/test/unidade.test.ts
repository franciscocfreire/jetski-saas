import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { PoteDeCookies } from '../src/lib/http.ts';
import { acaoDoFormPadrao, campoTexto, codigoTotpInedito, telaDe } from '../src/lib/keycloak.ts';
import { payloadDoToken } from '../src/lib/keycloakAdmin.ts';
import { lerCodigo, lerCodigoNoHtml, lerConvite, lerLinkDeVerificacao } from '../src/lib/mailpit.ts';
import { base32, hotp, otpauth, totp } from '../src/lib/totp.ts';

const CHAVE_RFC = Buffer.from('12345678901234567890', 'ascii');

test('HOTP bate com os vetores da RFC 4226', () => {
  const esperado = ['755224', '287082', '359152', '969429', '338314', '254676', '287922', '162583', '399871', '520489'];
  esperado.forEach((codigo, i) => assert.equal(hotp(CHAVE_RFC, BigInt(i)), codigo));
});

test('TOTP bate com os vetores da RFC 6238 (SHA1, 8 dígitos)', () => {
  const p = { algoritmo: 'sha1', digitos: 8, periodoSegundos: 30 } as const;
  assert.equal(totp(CHAVE_RFC, 59_000, p), '94287082');
  assert.equal(totp(CHAVE_RFC, 1111111109_000, p), '07081804');
  assert.equal(totp(CHAVE_RFC, 20000000000_000, p), '65353130');
});

test('Base32 e otpauth', () => {
  assert.equal(base32(Buffer.from('foobar')), 'MZXW6YTBOI');
  assert.equal(base32(CHAVE_RFC), 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ');
  assert.match(
    otpauth('Meu Jet', 'a@exemplo.invalid', CHAVE_RFC),
    /^otpauth:\/\/totp\/Meu%20Jet%3Aa%40exemplo\.invalid\?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&/,
  );
});

test('kcContext: lê tela e ação da página REAL do tema meujet (sem <form> no HTML)', () => {
  const html = readFileSync(new URL('./fixtures/login.html', import.meta.url), 'utf8');
  assert.equal(html.includes('<form'), false, 'a premissa do parser: o tema não entrega <form>');
  assert.equal(telaDe(html), 'login');
  const acao = campoTexto(html, 'loginAction')!;
  assert.match(acao, /^https:\/\/sso\.[^/]+\/realms\/jetski-saas\/login-actions\/authenticate\?session_code=/);
  assert.equal(acao.includes('&amp;'), false, 'entidades HTML têm de ser decodificadas');
  assert.equal(campoTexto(html, 'naoExiste'), undefined);
});

test('kcContext: strings com escapes JS e entidades', () => {
  assert.equal(campoTexto('"x": "a\\/b\\u0026c &amp; \\"d\\""', 'x'), 'a/b&c & "d"');
});

test('convite: link mágico + senha temporária na linha seguinte ao rótulo', () => {
  const texto = ['Olá', '*Senha temporária:*', '', 'Ab3$xY9!kLm2', '', '* Use o link E a senha'].join('\n');
  const html = '<a href="https://app.exemplo/magic-activate?token=eyJh.bGci.OiJI-_z">ativar</a>';
  assert.deepEqual(lerConvite(texto, html), { magicToken: 'eyJh.bGci.OiJI-_z', senhaTemporaria: 'Ab3$xY9!kLm2' });
  assert.equal(lerConvite('sem nada', ''), undefined);
});

test('convite: senha que começa com "*" não é confundida com negrito (caso real da E3b)', () => {
  const link = '<a href="https://app.exemplo/magic-activate?token=eyJh.bGci.OiJI-_z">ativar</a>';
  const html = `${link}<p style="margin: 5px 0;"><strong>Senha temporária:</strong></p>\r\n  <p style="font-family: 'Courier New'">\r\n      *x1Z%Yn35&amp;gG*\r\n  </p>`;
  assert.equal(lerConvite('*Senha temporária:*\n\n*x1Z%Yn35&gG*', html)?.senhaTemporaria, '*x1Z%Yn35&gG*');
  // Sem HTML, o texto puro também preserva os asteriscos da senha.
  assert.equal(lerConvite('*Senha temporária:*\n\n*x1Z%Yn35bgG\n\nhttps://a/magic-activate?token=t.t.t', '')?.senhaTemporaria, '*x1Z%Yn35bgG');
});

test('pote de cookies: caminho, substituição e expiração', () => {
  const pote = new PoteDeCookies();
  pote.guardar('https://sso.x/realms/r/auth', ['A=1; Path=/realms/r/; HttpOnly', 'B=2; Path=/outro/']);
  assert.equal(pote.cabecalho('https://sso.x/realms/r/login'), 'A=1');
  assert.equal(pote.cabecalho('https://outro.x/realms/r/login'), '');
  pote.guardar('https://sso.x/realms/r/auth', ['A=9; Path=/realms/r/']);
  assert.equal(pote.cabecalho('https://sso.x/realms/r/login'), 'A=9');
  pote.guardar('https://sso.x/realms/r/auth', ['A=; Path=/realms/r/; Max-Age=0']);
  assert.equal(pote.cabecalho('https://sso.x/realms/r/login'), '');
});

test('código de login: 6 dígitos isolados, não pedaço de número maior', () => {
  assert.equal(lerCodigo('Seu código de acesso é 048213. Vale por 10 minutos.'), '048213');
  assert.equal(lerCodigo('Pedido 12345678 sem código'), undefined);
  assert.equal(lerCodigo('nada aqui'), undefined);
});

test('link de verificação do Keycloak: action-token, com &amp; decodificado', () => {
  const html = '<a href="https://sso.x/realms/jetski-saas/login-actions/action-token?key=abc.def&amp;client_id=portal&amp;tab_id=9">Verificar</a>';
  assert.equal(lerLinkDeVerificacao('', html), 'https://sso.x/realms/jetski-saas/login-actions/action-token?key=abc.def&client_id=portal&tab_id=9');
  assert.equal(lerLinkDeVerificacao('https://app.x/magic-activate?token=zzz', ''), undefined);
});

// ---------------------------------------------------------------- fase E7: login social

test('E7: form de login do tema padrão do Keycloak (realm do Google sintético): action com &amp; decodificado', () => {
  const html = '<div id="kc-form"><div id="kc-form-wrapper"><form id="kc-form-login" onsubmit="login.disabled = true; return true;" '
    + 'action="https://sso.jetsave.com.br/realms/google-sintetico/login-actions/authenticate?session_code=abc&amp;execution=def&amp;client_id=jetski-broker&amp;tab_id=xyz" method="post">'
    + '<input id="username" name="username"><input id="password" name="password"></form></div></div>';
  assert.equal(acaoDoFormPadrao(html), 'https://sso.jetsave.com.br/realms/google-sintetico/login-actions/authenticate?session_code=abc&execution=def&client_id=jetski-broker&tab_id=xyz');
  // a página do tema meujet (kcContext, sem <form id="kc-form-login">) NÃO é confundida com o provedor
  assert.equal(acaoDoFormPadrao(readFileSync(new URL('./fixtures/login.html', import.meta.url), 'utf8')), undefined);
  assert.equal(acaoDoFormPadrao('<form action="x" id="outro">'), undefined);
});

test('E7: código de unificação de CPF num e-mail SÓ HTML (o do backend), sem parte texto', () => {
  const html = '<p>Olá.</p><p>Alguém entrou no <b>Meu Jet</b> com o Google e informou o seu CPF. Se foi você, use o código abaixo para unificar as contas:</p>'
    + '<p style="font-size:24px;font-weight:bold;letter-spacing:3px">048213</p><p>Válido por 10 minutos. Se não reconhece esta solicitação, ignore este e-mail — nada será alterado.</p>';
  assert.equal(lerCodigoNoHtml(html), '048213');
  assert.equal(lerCodigo(''), undefined); // o Mailpit devolve Text vazio para esse e-mail → cai no HTML
  assert.equal(lerCodigoNoHtml('<p>pedido 1234567 (7 dígitos) não é código</p>'), undefined);
  assert.equal(lerCodigoNoHtml('<p>a&nbsp;123456&nbsp;b</p>'), '123456');
});

test('E7: payload do JWT (base64url, sem verificar) traz sub e papéis do realm', () => {
  const payload = Buffer.from(JSON.stringify({ sub: '49024d26-c249-4af7-b8d9-22712dc0973d', email: 'x@y', realm_access: { roles: ['CLIENTE', 'offline_access'] } })).toString('base64url');
  const jwt = `eyJhbGciOiJSUzI1NiJ9.${payload}.assinatura`;
  const p = payloadDoToken(jwt);
  assert.equal(p.sub, '49024d26-c249-4af7-b8d9-22712dc0973d');
  assert.deepEqual(p.realm_access?.roles, ['CLIENTE', 'offline_access']);
});

test('E7: o guarda de TOTP nunca entrega o mesmo código duas vezes no mesmo período (o Keycloak recusa reuso)', async () => {
  const politica = { algoritmo: 'sha1' as const, digitos: 6, periodoSegundos: 1 };
  const antes = Date.now();
  const a = await codigoTotpInedito('segredo-e7', politica);
  const b = await codigoTotpInedito('segredo-e7', politica);
  assert.notEqual(a, b);
  assert.ok(Date.now() - antes >= 500, 'a 2ª chamada esperou o próximo período');
  assert.ok(Date.now() - antes < 3_000);
});
