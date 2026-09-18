// Fase E7 — login social no espelho, com o "Google" sintético (o próprio Keycloak como IdP de
// si mesmo: realm google-sintetico, alias `google` com providerId oidc). Quatro provas, cada
// uma pelas mesmas telas e APIs que uma pessoa usaria:
//
//   A  cliente NOVO entra no portal pelo Google → conta criada pelo broker (papel CLIENTE),
//      gate de CPF do portal (perfil sem CPF), CPF definido, mesma identidade no 2º login;
//   B  identidade Google (e-mail novo) informa o CPF de uma cliente que já existe → 409
//      CPF_EM_USO → código de unificação no e-mail da dona (Mailpit) → as duas viram uma:
//      o próximo login pelo Google já entra como a dona (sub igual);
//   C1 membro de equipe existente entra no BACKOFFICE pelo Google com o mesmo e-mail →
//      first broker login pede confirmação por e-mail (vínculo explícito, nunca JIT) →
//      mesma identidade (sub) de antes;
//   C2 idem com a operadora de plataforma, que tem TOTP → post-broker 2FA + "confiar neste
//      navegador" (a prova NÃO confia, para exercitar o fator toda vez).
//
// As contas "Google" nascem pela Admin API do realm sintético (client de serviço). A e B usam
// contas novas por rodada (a unificação é irreversível e o cooldown do merge é por conta).

import { randomBytes } from 'node:crypto';
import { cpfAleatorio, type Contexto } from './emissao.ts';
import { ErroHttp } from './lib/http.ts';
import { login, type ResultadoDeLogin } from './lib/keycloak.ts';
import { GoogleSintetico, payloadDoToken } from './lib/keycloakAdmin.ts';
import { esperarNoEmail, lerCodigo, lerCodigoNoHtml, lerLinkDeVerificacao } from './lib/mailpit.ts';

export interface ContextoSocial extends Contexto {
  dominio: string;
  keycloakUrl: string;
  segredoSemeadorContas: string;
}

const ALIAS = 'google';

function pessoa(chave: string, email: string, senha: string): { email: string; nome: string; sobrenome: string; senha: string } {
  return { email, nome: chave.split('-')[0].replace(/^\w/, (c) => c.toUpperCase()), sobrenome: 'Google Sintética', senha };
}

export async function provarGoogle(c: ContextoSocial): Promise<void> {
  const { api, arquivo, log, mailpit, dominio } = c;
  const issuer = `https://sso.${dominio}/realms/jetski-saas`;
  const google = new GoogleSintetico(c.keycloakUrl, c.segredoSemeadorContas);
  const rodada = new Date().toISOString().slice(5, 19).replace(/[-:T]/g, ''); // MMDDHHmmss: contas novas por rodada
  const exigir = (ok: unknown, msg: string) => {
    if (!ok) throw new Error(`prova do Google sintético falhou: ${msg}`);
    log(`  ✓ ${msg}`);
  };
  const mostrar = (r: ResultadoDeLogin) => r.telas.join(' → ');
  type Perfil = { email?: string; identidade?: { cpf?: string | null } };
  const perfil = (token: string) => api.global<Perfil>('GET', token, '/v1/customers/self');
  const cpfDe = (p: Perfil): string | undefined => p.identidade?.cpf || undefined;
  const entrarNoPortalPeloGoogle = (conta: { email: string; senha: string; nome: string; sobrenome: string }) =>
    login({ issuer, clientId: 'jetski-customer-portal', redirectUri: `https://cliente.${dominio}/api/auth/callback/keycloak`, usuario: conta.email, senha: '', idp: { alias: ALIAS, ...conta, usuario: conta.email } });

  // Pré-condição: o realm sintético existe, o client de serviço entra e o issuer é o público.
  const iss = await google.issuer();
  exigir(iss === `https://sso.${dominio}/realms/google-sintetico`, `realm google-sintetico no ar com issuer público (${iss})`);

  // ---- A: cliente novo pelo Google ---------------------------------------------------------
  {
    const conta = pessoa('ana', `ana.google.${rodada}@gmail.exemplo.invalid`, `G${randomBytes(9).toString('base64url')}!1`);
    await google.criarConta(conta);
    const e = arquivo.persona(`e7-google-ana-${rodada}`, conta.email);
    e.senha = conta.senha;
    arquivo.salvar();
    const r1 = await entrarNoPortalPeloGoogle(conta);
    exigir(r1.telas.includes('google-sintetico:login') && !r1.telas.some((t) => t.startsWith('login-idp-link')), `A: entrou no portal pelo Google como conta nova (${mostrar(r1)})`);
    const p1 = payloadDoToken(r1.tokens.accessToken);
    exigir(p1.realm_access?.roles?.includes('CLIENTE'), 'A: a conta criada pelo broker recebeu o papel CLIENTE (mapper do IdP)');
    const antes = await perfil(r1.tokens.accessToken);
    exigir(!cpfDe(antes), 'A: o perfil nasce sem CPF — é o gate de CPF do portal');
    const cpf = cpfAleatorio();
    await api.global('PUT', r1.tokens.accessToken, '/v1/customers/self', { nome: `${conta.nome} ${conta.sobrenome}`, cpf, nacionalidade: 'Brasileira', naturalidade: 'Santos/SP', estrangeiro: false, dataNascimento: '1994-04-04' });
    exigir(cpfDe(await perfil(r1.tokens.accessToken)) === cpf, 'A: CPF definido pelo gate');
    const r2 = await entrarNoPortalPeloGoogle(conta);
    exigir(payloadDoToken(r2.tokens.accessToken).sub === p1.sub, `A: o 2º login pelo Google é a mesma identidade (${mostrar(r2)})`);
    e.cpf = cpf;
    arquivo.salvar();
  }

  // ---- B: colisão de CPF → unificação por OTP -----------------------------------------------
  {
    const dona = { chave: `e7-dona-${rodada}`, email: `beatriz.dona.${rodada}@exemplo.invalid`, nome: 'Beatriz Dona Sintética' };
    const tokenDona = await c.garantirClienteDoPortal(dona);
    const cpf = cpfAleatorio();
    await api.global('PUT', tokenDona, '/v1/customers/self', { nome: dona.nome, cpf, nacionalidade: 'Brasileira', naturalidade: 'Santos/SP', estrangeiro: false, dataNascimento: '1990-09-09' });
    const subDona = payloadDoToken(tokenDona).sub;
    log(`B: dona ${dona.email} criada no portal com CPF ${cpf}`);

    const conta = pessoa('beatriz', `beatriz.google.${rodada}@gmail.exemplo.invalid`, `G${randomBytes(9).toString('base64url')}!1`);
    await google.criarConta(conta);
    arquivo.persona(`e7-google-beatriz-${rodada}`, conta.email).senha = conta.senha;
    arquivo.salvar();
    const r1 = await entrarNoPortalPeloGoogle(conta);
    const subDup = payloadDoToken(r1.tokens.accessToken).sub;
    exigir(subDup !== subDona && !cpfDe(await perfil(r1.tokens.accessToken)), `B: a identidade Google entrou como conta nova, sem CPF (${mostrar(r1)})`);

    let colisao: ErroHttp | undefined;
    try {
      await api.global('PUT', r1.tokens.accessToken, '/v1/customers/self', { nome: `${conta.nome} ${conta.sobrenome}`, cpf, estrangeiro: false });
    } catch (e) {
      if (e instanceof ErroHttp) colisao = e;
      else throw e;
    }
    exigir(colisao?.resposta.status === 409 && colisao.resposta.corpo.includes('CPF_EM_USO'), 'B: informar o CPF da dona responde 409 CPF_EM_USO');
    const desde = new Date(Date.now() - 5_000);
    const envio = await api.global<{ disponivel?: boolean; emailMascarado?: string }>('POST', r1.tokens.accessToken, '/v1/customers/self/cpf-merge/enviar', { cpf });
    exigir(envio.disponivel !== false, `B: pedido de unificação aceito (código vai para ${envio.emailMascarado ?? 'a dona'})`);
    const codigo = await esperarNoEmail(mailpit, dona.email, desde, (texto, html) => lerCodigo(texto) ?? lerCodigoNoHtml(html), 'código de unificação de CPF');
    const verif = await api.global<{ verificado?: boolean; mergeConcluido?: boolean }>('POST', r1.tokens.accessToken, '/v1/customers/self/cpf-merge/verificar', { cpf, codigo });
    exigir(verif.verificado !== false && verif.mergeConcluido !== false, 'B: a dona confirmou o código e as contas foram unificadas');
    const r2 = await entrarNoPortalPeloGoogle(conta);
    exigir(payloadDoToken(r2.tokens.accessToken).sub === subDona, `B: o Google agora entra como a dona (sub igual; ${mostrar(r2)})`);
    exigir(cpfDe(await perfil(r2.tokens.accessToken)) === cpf, 'B: o perfil unificado tem o CPF da dona');
  }

  // ---- C: staff existente entra pelo Google (backoffice e console) -----------------------------
  const staffPeloGoogle = async (chave: string, rotulo: string, cliente: 'backoffice' | 'console') => {
    const p = arquivo.estado.personas[chave];
    if (!p?.senha) throw new Error(`${chave} não está no estado — rode "semear" antes.`);
    const [clientId, host] = cliente === 'console' ? ['jetski-platform-console', 'admin'] : ['jetski-backoffice', 'app'];
    const ref = await c.entrar(p, cliente); // com senha (e TOTP): a identidade de referência
    const subRef = payloadDoToken(ref.tokens.accessToken).sub;
    const conta = pessoa(chave, p.email, `G${randomBytes(9).toString('base64url')}!1`);
    await google.criarConta(conta); // MESMO e-mail do membro: é o que dispara o vínculo explícito
    arquivo.persona(`e7-google-${chave}`, p.email).senha = conta.senha;
    arquivo.salvar();
    const r = await login({
      issuer, clientId, redirectUri: `https://${host}.${dominio}/api/auth/callback/keycloak`,
      usuario: p.email, senha: p.senha, totpSegredo: p.totpSegredo,
      idp: { alias: ALIAS, usuario: p.email, senha: conta.senha, nome: conta.nome, sobrenome: conta.sobrenome,
        obterLinkDeVinculo: (desde) => esperarNoEmail(mailpit, p.email, desde, lerLinkDeVerificacao, 'e-mail de confirmação do vínculo com o Google') },
    });
    const vinculouAgora = r.telas.includes('login-idp-link-email');
    exigir(payloadDoToken(r.tokens.accessToken).sub === subRef, `${rotulo}: entrou pelo Google como a MESMA identidade (${vinculouAgora ? 'vínculo confirmado por e-mail nesta rodada' : 'vínculo já existia'}; ${mostrar(r)})`);
    if (p.totpSegredo) exigir(r.telas.includes('login-otp'), `${rotulo}: o post-broker exigiu o 2º fator (TOTP)`);
    else exigir(!r.telas.includes('login-otp'), `${rotulo}: sem fator cadastrado, nenhum 2º fator foi pedido`);
    if (cliente === 'backoffice') {
      const eu = await api.global<{ email?: string; idpFederado?: boolean }>('GET', r.tokens.accessToken, '/v1/user/me');
      exigir(eu.email === p.email && eu.idpFederado === true, `${rotulo}: /v1/user/me é o membro de sempre (${eu.email}) e já o vê como federado (idpFederado=${eu.idpFederado})`);
    } else {
      const eu = await api.global<{ usuarioId?: string | null; papeis?: string[] }>('GET', r.tokens.accessToken, '/v1/platform/me');
      exigir(!!eu.usuarioId && (eu.papeis?.length ?? 0) > 0, `${rotulo}: /v1/platform/me reconhece a operadora (papéis ${eu.papeis?.join(',')})`);
    }
    return r;
  };
  await staffPeloGoogle('atol-vendedor', 'C1 (vendedor da Atol, backoffice, sem fator)', 'backoffice');
  await staffPeloGoogle('operador-plataforma', 'C2 (operadora de plataforma, console, com TOTP)', 'console');

  log('login social provado: cliente novo (gate de CPF), unificação por OTP, staff sem e com 2º fator.');
}
