// Login das personas no Keycloak, pelo MESMO fluxo que o navegador faz
// (authorization_code + PKCE), sem navegador.
//
// O tema de login é Keycloakify (React): o HTML NÃO traz <form>. A página entrega
// um objeto JavaScript `kcContext` com a URL de ação e os dados da tela, e é o
// React que monta o formulário. Aqui lemos esses campos e enviamos os mesmos
// POSTs que o formulário enviaria — o autenticador do lado do servidor é o padrão
// do Keycloak e não sabe a diferença.
//
// `kcContext` não é JSON (tem comentários, vírgulas sobrando e funções), então
// extraímos campo a campo em vez de tentar interpretar o objeto inteiro — e
// nunca executamos o script da página.

import { createHash, randomBytes } from 'node:crypto';
import { ErroHttp, PoteDeCookies, pedir, type Resposta } from './http.ts';
import { POLITICA_PADRAO, totp, type PoliticaTotp } from './totp.ts';

export interface Tokens {
  accessToken: string;
  refreshToken: string;
  expiraEmSegundos: number;
}

export interface PedidoDeLogin {
  issuer: string; // https://sso.<dominio>/realms/jetski-saas
  clientId: string;
  redirectUri: string;
  usuario: string;
  senha: string;
  /** Usada se o Keycloak exigir troca de senha (conta recém-ativada tem senha temporária). */
  novaSenha?: string;
  /** Segredo TOTP já configurado (texto cru, como o Keycloak o entrega). */
  totpSegredo?: string;
  /**
   * Entrar SEM senha, pelo código enviado por e-mail (o caminho do cliente do portal).
   * Chamada depois de o código ser pedido; devolve os 6 dígitos (lidos no Mailpit).
   */
  obterCodigoPorEmail?: (pedidoEm: Date) => Promise<string>;
  /**
   * Entrar por um provedor externo ("Entrar com Google"): o walker segue o broker até o realm
   * do provedor (no espelho, o realm sintético do próprio Keycloak), entra lá com usuário/senha
   * e volta. `obterLinkDeVinculo` lê no Mailpit o e-mail "confirme o acesso com Google" que o
   * Keycloak manda quando o e-mail já pertence a uma conta (vínculo explícito, nunca JIT).
   */
  idp?: { alias: string; usuario: string; senha: string; nome?: string; sobrenome?: string; obterLinkDeVinculo?: (desde: Date) => Promise<string>; confiarDispositivo?: boolean };
}

export interface ResultadoDeLogin {
  tokens: Tokens;
  /** Presente quando a senha foi trocada neste login. */
  senhaDefinida?: string;
  /** Presente quando o TOTP foi configurado neste login — GUARDE: não dá para ler de novo. */
  totpConfigurado?: { segredo: string; politica: PoliticaTotp };
  /** Telas atravessadas, em ordem — para log e asserção. */
  telas: string[];
}

// ---------------------------------------------------------------- kcContext

const ENTIDADES: Record<string, string> = { '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&#39;': "'" };

/** Lê um campo string do kcContext. `undefined` se não existir. */
export function campoTexto(html: string, nome: string): string | undefined {
  const m = new RegExp(`"${nome}":\\s*("(?:[^"\\\\]|\\\\.)*")`).exec(html);
  if (!m) return undefined;
  const cru = JSON.parse(m[1]) as string;
  // O Keycloakify aplica decodeHtmlEntities nas strings ao montar a tela.
  return cru.replace(/&(amp|lt|gt|quot|#39);/g, (e) => ENTIDADES[e]);
}

export function campoNumero(html: string, nome: string): number | undefined {
  const m = new RegExp(`"${nome}":\\s*(\\d+)`).exec(html);
  return m ? Number(m[1]) : undefined;
}

/** Identificador da tela, sem o sufixo .ftl (o tema entrega "login"; o Keycloakify puro, "login.ftl"). */
export function telaDe(html: string): string | undefined {
  return campoTexto(html, 'pageId')?.replace(/\.ftl$/, '');
}

/**
 * Action do <form id="kc-form-login"> do tema PADRÃO do Keycloak (o realm do provedor sintético
 * usa esse tema, sem kcContext). Devolve undefined se a página não é esse formulário.
 */
export function acaoDoFormPadrao(html: string): string | undefined {
  if (!/id="kc-form-login"/.test(html)) return undefined;
  const m = /id="kc-form-login"[^>]*action="([^"]+)"/.exec(html) ?? /action="([^"]+)"[^>]*id="kc-form-login"/.exec(html);
  return m?.[1].replace(/&amp;/g, '&');
}

function politicaDe(html: string): PoliticaTotp {
  const bloco = html.slice(html.indexOf('"totp"'));
  const alg = (campoTexto(bloco, 'algorithm') ?? 'HmacSHA1').toLowerCase().replace('hmac', '');
  return {
    algoritmo: alg === 'sha256' || alg === 'sha512' ? alg : 'sha1',
    digitos: campoNumero(bloco, 'digits') ?? POLITICA_PADRAO.digitos,
    periodoSegundos: campoNumero(bloco, 'period') ?? POLITICA_PADRAO.periodoSegundos,
  };
}

// ---------------------------------------------------------------- fluxo

const MAX_TELAS = 24; // iterações (telas 200 + redirects); o login social com vínculo por e-mail, 2FA e reinício chega perto de 20

// O Keycloak (≥ 21) recusa REUSAR um código TOTP no mesmo período: dois logins seguidos da mesma
// persona (o de referência e o pelo Google, por exemplo) esperariam o próximo período. Por segredo.
const otpUsadoNoPeriodo = new Map<string, number>();
export async function codigoTotpInedito(segredo: string, politica: PoliticaTotp): Promise<string> {
  const periodo = politica.periodoSegundos * 1000;
  let agora = Date.now();
  if (otpUsadoNoPeriodo.get(segredo) === Math.floor(agora / periodo)) {
    await new Promise((ok) => setTimeout(ok, periodo - (agora % periodo) + 500));
    agora = Date.now();
  }
  otpUsadoNoPeriodo.set(segredo, Math.floor(agora / periodo));
  return totp(Buffer.from(segredo, 'utf8'), agora, politica);
}

/**
 * Faz o login e devolve os tokens. Atravessa, conforme o Keycloak pedir:
 * senha → troca de senha temporária → configuração de TOTP → código TOTP.
 */
export async function login(p: PedidoDeLogin): Promise<ResultadoDeLogin> {
  const pote = new PoteDeCookies();
  const verifier = randomBytes(48).toString('base64url');
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  const auth = new URL(`${p.issuer}/protocol/openid-connect/auth`);
  auth.search = new URLSearchParams({
    client_id: p.clientId,
    redirect_uri: p.redirectUri,
    response_type: 'code',
    scope: 'openid',
    code_challenge: challenge,
    code_challenge_method: 'S256',
    ...(p.idp ? { kc_idp_hint: p.idp.alias } : {}), // o botão "Entrar com Google" faz exatamente isto
  }).toString();

  const saida: Omit<ResultadoDeLogin, 'tokens'> = { telas: [] };
  let segredo = p.totpSegredo;
  let politica = POLITICA_PADRAO;
  let codigoPedidoEm: Date | undefined;
  let reenviou = false;
  let desdeVinculo = new Date();
  let continuarAposVinculo: string | undefined; // loginAction da tela "confira seu e-mail" (= "clique aqui para continuar")
  const hostSso = new URL(p.issuer).host;
  let r = await pedir(auth.toString(), { pote });

  for (let i = 0; i < MAX_TELAS; i++) {
    // Redirect de volta ao app = login concluído. Outros redirects (dentro do
    // Keycloak) são seguidos à mão, para o pote de cookies acompanhar.
    if (r.status >= 300 && r.status < 400 && r.location) {
      if (r.location.startsWith(p.redirectUri)) {
        const u = new URL(r.location);
        const code = u.searchParams.get('code');
        if (!code) throw new Error(`Keycloak recusou o login de ${p.usuario}: ${u.searchParams.get('error_description') ?? r.location}`);
        return { ...saida, tokens: await trocarCodigo(p, code, verifier) };
      }
      // Trava: o broker só pode levar a persona para dentro do SSO do espelho. Um alias `google`
      // apontando para accounts.google.com (produção) pararia AQUI, sem mandar nada para fora.
      if (new URL(r.location).host !== hostSso) throw new Error(`login de ${p.usuario} saiu do SSO do espelho: ${r.location}`);
      r = await pedir(r.location, { pote });
      continue;
    }
    if (r.status !== 200) throw new ErroHttp(`login de ${p.usuario}`, r);

    // O realm do provedor sintético usa o tema PADRÃO do Keycloak: sem kcContext, com <form>.
    const realmExterno = /\/realms\/([^/]+)\//.exec(new URL(r.url).pathname)?.[1];
    const acaoForm = p.idp && realmExterno && realmExterno !== new URL(p.issuer).pathname.split('/').pop() ? acaoDoFormPadrao(r.corpo) : undefined;
    if (p.idp && realmExterno && acaoForm) {
      const telaExterna = `${realmExterno}:login`;
      saida.telas.push(telaExterna);
      if (/id="input-error"/.test(r.corpo) && saida.telas.filter((t) => t === telaExterna).length > 1) {
        throw new Error(`o provedor ${realmExterno} recusou ${p.idp.usuario} (senha errada?)`);
      }
      r = await pedir(acaoForm, { pote, formulario: { username: p.idp.usuario, password: p.idp.senha, credentialId: '' } });
      continue;
    }

    const tela = telaDe(r.corpo);
    const acao = campoTexto(r.corpo, 'loginAction');
    saida.telas.push(tela ?? '?');
    if (tela === 'info' && continuarAposVinculo) {
      // O link de vínculo foi confirmado e o Keycloak parou num aviso. Dois desfechos legítimos:
      //  1. "volte ao navegador original": a sessão de autenticação segue viva → "clique aqui
      //     para continuar" na aba de origem (GET no loginAction daquela tela);
      //  2. "sua conta foi atualizada": o vínculo já foi feito e a sessão ENCERRADA → a pessoa
      //     entra de novo; agora a identidade está vinculada e nenhum e-mail é pedido.
      const volta = await pedir(continuarAposVinculo, { pote });
      continuarAposVinculo = undefined;
      const seguiu = (volta.status >= 300 && volta.status < 400 && !!volta.location && !(volta.location.startsWith(p.redirectUri) && new URL(volta.location).searchParams.has('error')))
        || (volta.status === 200 && !!telaDe(volta.corpo) && telaDe(volta.corpo) !== 'error' && !/"type":\s*"error"/.test(volta.corpo));
      if (seguiu) { r = volta; continue; }
      saida.telas.push('reinicio');
      r = await pedir(auth.toString(), { pote });
      continue;
    }
    if (!tela || !acao) throw new ErroHttp(`página sem kcContext reconhecível (login de ${p.usuario})`, r);
    if (tela === 'info') throw new Error(`Keycloak parou numa página de aviso no login de ${p.usuario}: ${campoTexto(r.corpo, 'summary') ?? '?'}`);

    // Mensagem de erro da tela anterior (senha errada, código inválido…).
    const erro = /"type":\s*"error"/.test(r.corpo) ? campoTexto(r.corpo, 'summary') : undefined;
    const repeticoesEsperadas = tela === 'email-code-verify' && p.obterCodigoPorEmail ? 3 : 1; // pedir, (reenviar), verificar
    if (erro && saida.telas.filter((t) => t === tela).length > repeticoesEsperadas) {
      throw new Error(`Keycloak recusou "${tela}" para ${p.usuario}: ${erro}`);
    }

    switch (tela) {
      case 'login':
        // A tela MOSTRA dois passos (e-mail → senha), mas é um formulário só:
        // o UsernamePasswordForm aceita usuário e senha no mesmo POST.
        r = await pedir(acao, { pote, formulario: { username: p.usuario, password: p.senha, credentialId: '' } });
        break;

      // Backoffice e portal usam o SPI meujet-email-code — identifier-first DE VERDADE,
      // em dois POSTs (infra/keycloak-extensions/email-code): a tela 1 identifica
      // (mjAction=request) e a tela 2 recebe a senha (mjAction=password) ou o código
      // enviado por e-mail (sendcode/verify — o caminho do cliente do portal, fase E3b).
      case 'email-code-id':
        r = await pedir(acao, { pote, formulario: { mjAction: 'request', identifier: p.usuario } });
        break;

      case 'email-code-verify':
        if (!p.obterCodigoPorEmail) {
          r = await pedir(acao, { pote, formulario: { mjAction: 'password', password: p.senha } });
        } else if (!codigoPedidoEm) {
          // "Entrar sem senha": pede o código; a mesma tela volta, agora esperando os dígitos.
          // Folga de relógio pequena: uma janela larga pega o código da ÚLTIMA entrada, já usado.
          codigoPedidoEm = new Date(Date.now() - 5_000);
          r = await pedir(acao, { pote, formulario: { mjAction: 'sendcode' } });
        } else {
          let codigo: string | undefined;
          try {
            codigo = await p.obterCodigoPorEmail(codigoPedidoEm);
          } catch (e) {
            // O SPI tem cooldown de 60 s por usuário: quem entrou há menos de um minuto não recebe
            // código novo (e o anterior é de uso único). A pessoa espera e clica em "reenviar".
            if (reenviou) throw e;
            reenviou = true;
            await new Promise((ok) => setTimeout(ok, Math.max(0, 62_000 - (Date.now() - (codigoPedidoEm as Date).getTime()))));
            codigoPedidoEm = new Date(Date.now() - 5_000);
            r = await pedir(acao, { pote, formulario: { mjAction: 'resend' } });
            break;
          }
          r = await pedir(acao, { pote, formulario: { mjAction: 'verify', code: codigo } });
        }
        break;

      case 'login-update-password': {
        if (!p.novaSenha) throw new Error(`${p.usuario} precisa trocar a senha e nenhuma novaSenha foi informada.`);
        r = await pedir(acao, { pote, formulario: { 'password-new': p.novaSenha, 'password-confirm': p.novaSenha } });
        saida.senhaDefinida = p.novaSenha;
        break;
      }

      case 'login-config-totp': {
        // O segredo só aparece AQUI, uma vez. A chave do HMAC são os bytes do texto
        // cru (`totpSecret`); `totpSecretEncoded` é o mesmo valor em Base32, para QR.
        const novo = campoTexto(r.corpo, 'totpSecret');
        if (!novo) throw new ErroHttp('tela de TOTP sem totpSecret', r);
        politica = politicaDe(r.corpo);
        segredo = novo;
        r = await pedir(acao, {
          pote,
          formulario: {
            totp: totp(Buffer.from(novo, 'utf8'), Date.now(), politica),
            totpSecret: novo,
            userLabel: 'persona-sintetica',
          },
        });
        saida.totpConfigurado = { segredo: novo, politica };
        break;
      }

      case 'login-otp':
        if (!segredo) throw new Error(`${p.usuario} tem 2FA e nenhum totpSegredo foi informado.`);
        r = await pedir(acao, { pote, formulario: { otp: await codigoTotpInedito(segredo, politica) } });
        break;

      // ---- login social (fase E7): first broker login do Keycloak ----
      case 'login-idp-link-confirm':
        // O e-mail do provedor já pertence a uma conta: a pessoa escolhe "vincular". O Keycloak manda
        // então um e-mail de confirmação — a marca de tempo tem de ser tirada ANTES do POST.
        desdeVinculo = new Date(Date.now() - 5_000);
        r = await pedir(acao, { pote, formulario: { submitAction: 'linkAccount' } });
        break;

      case 'login-idp-link-email': {
        if (!p.idp?.obterLinkDeVinculo) throw new Error(`${p.usuario}: o Keycloak pede confirmação por e-mail do vínculo e nenhum obterLinkDeVinculo foi informado.`);
        if (saida.telas.filter((t) => t === 'login-idp-link-email').length > 1) throw new Error(`${p.usuario}: o link de vínculo não concluiu (a tela voltou)`);
        // O link é um action token: aberto com o MESMO pote (a sessão de autenticação vive no
        // cookie), como a pessoa abrindo o e-mail no mesmo navegador. O que vem depois (2º fator,
        // aviso, reinício) é tratado pelo laço — ver `continuarAposVinculo`.
        continuarAposVinculo = acao;
        const link = await p.idp.obterLinkDeVinculo(desdeVinculo);
        if (new URL(link).host !== hostSso) throw new Error(`o link de vínculo de ${p.usuario} não é do SSO do espelho: ${link}`);
        r = await pedir(link, { pote });
        break;
      }

      case 'idp-review-user-profile':
        // Rede de segurança: o provedor não mandou nome/sobrenome. Preenche como a pessoa faria.
        r = await pedir(acao, { pote, formulario: { username: p.idp?.usuario ?? p.usuario, email: p.idp?.usuario ?? p.usuario, firstName: p.idp?.nome ?? 'Persona', lastName: p.idp?.sobrenome ?? 'Sintética' } });
        break;

      case 'trusted-device-enroll':
        // Depois do 2FA o Keycloak oferece "confiar neste navegador". A prova NÃO confia, senão a
        // rodada seguinte pularia o 2º fator e deixaria de exercitá-lo.
        r = await pedir(acao, { pote, formulario: p.idp?.confiarDispositivo ? { trustDevice: 'on' } : {} });
        break;

      default:
        throw new Error(
          `Tela inesperada no login de ${p.usuario}: "${tela}"` +
            (erro ? ` — ${erro}` : '') +
            `. Telas até aqui: ${saida.telas.join(' → ')}`,
        );
    }
  }
  throw new Error(`Login de ${p.usuario} não terminou em ${MAX_TELAS} telas: ${saida.telas.join(' → ')}`);
}

/**
 * Segue um link de ação enviado por e-mail pelo Keycloak (ex.: verificação de e-mail).
 * O Keycloak costuma mostrar uma tela "clique para prosseguir" (info + actionUri) antes
 * de concluir; seguimos como a pessoa faria. Devolve as telas atravessadas.
 */
export async function seguirLinkDeAcao(link: string): Promise<string[]> {
  const pote = new PoteDeCookies();
  const telas: string[] = [];
  let r = await pedir(link, { pote });
  for (let i = 0; i < MAX_TELAS; i++) {
    if (r.status >= 300 && r.status < 400 && r.location) {
      // Redirect para fora do Keycloak (de volta ao app) = ação concluída.
      if (!r.location.includes('/realms/')) return telas;
      r = await pedir(r.location, { pote });
      continue;
    }
    if (r.status !== 200) throw new ErroHttp('link de ação do Keycloak', r);
    const tela = telaDe(r.corpo) ?? '?';
    telas.push(tela);
    if (/"type":\s*"error"/.test(r.corpo) || tela === 'error') {
      throw new Error(`Keycloak recusou o link de ação: ${campoTexto(r.corpo, 'summary') ?? tela}`);
    }
    const prosseguir = tela === 'info' ? campoTexto(r.corpo, 'actionUri') : undefined;
    if (!prosseguir) return telas;
    r = await pedir(prosseguir, { pote });
  }
  throw new Error(`Link de ação não terminou em ${MAX_TELAS} telas: ${telas.join(' → ')}`);
}

async function trocarCodigo(p: PedidoDeLogin, code: string, verifier: string): Promise<Tokens> {
  const r = await pedir(`${p.issuer}/protocol/openid-connect/token`, {
    formulario: {
      grant_type: 'authorization_code',
      client_id: p.clientId,
      code,
      redirect_uri: p.redirectUri,
      code_verifier: verifier,
    },
  });
  return lerTokens(r, `troca do código de ${p.usuario}`);
}

/** Renova pelo refresh token (client público: sem segredo). */
export async function renovar(issuer: string, clientId: string, refreshToken: string): Promise<Tokens> {
  const r = await pedir(`${issuer}/protocol/openid-connect/token`, {
    formulario: { grant_type: 'refresh_token', client_id: clientId, refresh_token: refreshToken },
  });
  return lerTokens(r, 'renovação de token');
}

function lerTokens(r: Resposta, oQue: string): Tokens {
  if (r.status !== 200) throw new ErroHttp(oQue, r);
  const d = JSON.parse(r.corpo) as { access_token: string; refresh_token: string; expires_in: number };
  return { accessToken: d.access_token, refreshToken: d.refresh_token, expiraEmSegundos: d.expires_in };
}
