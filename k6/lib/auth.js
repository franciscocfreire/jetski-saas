// Autenticação dos cenários de carga.
//
// DESENHO — por que refresh token e não login direto:
//
// O Keycloak deste realm não expõe ROPC (`directAccessGrants`) em nenhum client
// que a API aceite. Os dois que têm ROPC são `jetski-password-check`
// (confidencial, existe só para validar senha atual) e `jetski-test` — e nenhum
// dos dois está em `jetski.security.jwt.allowed-clients`, então o token deles
// volta 401. Em produção `JETSKI_JWT_ADDITIONAL_ALLOWED_CLIENTS` está vazio de
// propósito (correção de segurança); reabrir isso só para o teste seria desfazer
// a correção.
//
// A saída é separar o que é frágil do que é quente:
//
//   1. `gerar-tokens.sh` (via sintetico/) faz o login de verdade UMA vez por usuário — o fluxo
//      authorization_code + PKCE com o formulário de dois passos do tema meujet
//      (identifier → senha) — e guarda o REFRESH TOKEN em tokens.json.
//   2. Este módulo só troca refresh por access token, que é um POST simples,
//      sem formulário e sem HTML para parsear.
//
// Prazos deste realm: access token 300 s (5 min), sessão SSO ociosa 12 h. Ou
// seja: o tokens.json vale por um turno inteiro de testes, e a renovação de 5 em
// 5 minutos é responsabilidade daqui.

import http from 'k6/http';
import { fail } from 'k6';
import { ISSUER, CLIENT_ID } from './config.js';

const TOKEN_URL = `${ISSUER}/protocol/openid-connect/token`;

/** Renova com folga: troca aos 4 min, antes dos 5 min de validade. */
const MARGEM_MS = 60 * 1000;

/**
 * Cache por VU. Cada VU do k6 tem seu próprio escopo de módulo, então este mapa
 * não é compartilhado entre VUs — que é exatamente o que queremos: cada VU se
 * comporta como um usuário com sua própria sessão.
 */
const cache = {};

/**
 * Devolve um access token válido para o usuário indicado, renovando quando
 * perto de expirar.
 *
 * @param {{usuario: string, refreshToken: string}} credencial entrada do tokens.json
 */
export function tokenDe(credencial) {
  const agora = Date.now();
  const emCache = cache[credencial.usuario];
  if (emCache && emCache.expiraEm - MARGEM_MS > agora) {
    return emCache.accessToken;
  }

  const corpo = {
    grant_type: 'refresh_token',
    client_id: CLIENT_ID,
    refresh_token: emCache ? emCache.refreshToken : credencial.refreshToken,
  };

  const res = http.post(TOKEN_URL, corpo, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    // Fora das métricas do cenário: renovar token é andaime do teste, não a
    // carga que queremos medir. Sem isto o P95 do cenário vira média com o SSO.
    tags: { tipo: 'auth', name: 'POST /token (refresh)' },
  });

  if (res.status !== 200) {
    fail(
      `Falha ao renovar token de ${credencial.usuario}: ${res.status} ${res.body}\n` +
      'Se for "invalid_grant", o tokens.json expirou (sessão SSO de 12 h) — ' +
      'rode ./k6/gerar-tokens.sh de novo.'
    );
  }

  const dados = res.json();
  cache[credencial.usuario] = {
    accessToken: dados.access_token,
    // O Keycloak roda rotação de refresh token: guardamos SEMPRE o novo, senão
    // a segunda renovação falha com invalid_grant.
    refreshToken: dados.refresh_token || corpo.refresh_token,
    expiraEm: agora + dados.expires_in * 1000,
  };
  return dados.access_token;
}

/**
 * Carrega o tokens.json produzido pelo gerar-tokens.sh.
 *
 * ATENÇÃO: chame no escopo de MÓDULO do cenário (contexto de init), nunca de
 * dentro de `setup()` nem do loop. O `open()` do k6 só existe no init — em
 * qualquer outro lugar ele lança "open() can only be used in the init context".
 */
export function carregarCredenciais() {
  // Relativo ao ARQUIVO, não ao diretório de onde se roda o k6: hoje o open()
  // resolve a partir da pasta do cenário (k6/cenarios/) e, nas versões novas,
  // passa a resolver a partir deste módulo (k6/lib/) — como as duas pastas são
  // irmãs, `../.auth/` acerta nos dois casos.
  const caminho = __ENV.TOKENS || '../.auth/tokens.json';
  let bruto;
  try {
    bruto = open(caminho);
  } catch (e) {
    fail(`Não achei ${caminho}. Rode ./k6/gerar-tokens.sh <ip-do-espelho> antes do teste. (${e})`);
  }
  const dados = JSON.parse(bruto);
  if (!dados.usuarios || dados.usuarios.length === 0) {
    fail(`${caminho} não tem usuários.`);
  }
  return dados;
}

/** Distribui os usuários entre as VUs, para não ser sempre o mesmo. */
export function credencialDaVU(credenciais) {
  return credenciais.usuarios[(__VU - 1) % credenciais.usuarios.length];
}
