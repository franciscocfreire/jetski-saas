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

const MAX_TELAS = 8;

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
  }).toString();

  const saida: Omit<ResultadoDeLogin, 'tokens'> = { telas: [] };
  let segredo = p.totpSegredo;
  let politica = POLITICA_PADRAO;
  let codigoPedidoEm: Date | undefined;
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
      r = await pedir(r.location, { pote });
      continue;
    }
    if (r.status !== 200) throw new ErroHttp(`login de ${p.usuario}`, r);

    const tela = telaDe(r.corpo);
    const acao = campoTexto(r.corpo, 'loginAction');
    saida.telas.push(tela ?? '?');
    if (!tela || !acao) throw new ErroHttp(`página sem kcContext reconhecível (login de ${p.usuario})`, r);

    // Mensagem de erro da tela anterior (senha errada, código inválido…).
    const erro = /"type":\s*"error"/.test(r.corpo) ? campoTexto(r.corpo, 'summary') : undefined;
    const repeticoesEsperadas = tela === 'email-code-verify' && p.obterCodigoPorEmail ? 2 : 1;
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
          codigoPedidoEm = new Date(Date.now() - 30_000); // folga de relógio com o Mailpit
          r = await pedir(acao, { pote, formulario: { mjAction: 'sendcode' } });
        } else {
          r = await pedir(acao, { pote, formulario: { mjAction: 'verify', code: await p.obterCodigoPorEmail(codigoPedidoEm) } });
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
        r = await pedir(acao, { pote, formulario: { otp: totp(Buffer.from(segredo, 'utf8'), Date.now(), politica) } });
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
