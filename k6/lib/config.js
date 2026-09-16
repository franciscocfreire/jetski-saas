// Configuração compartilhada dos cenários de carga.
//
// Tudo vem de variável de ambiente (`k6 run -e CHAVE=valor`) para que o mesmo
// script rode contra dev, contra o espelho e contra produção sem edição.

/** Base da API. Em produção: https://www.meujet.com.br/api */
export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8090/api').replace(/\/$/, '');

/** Issuer do Keycloak. Em produção: https://sso.meujet.com.br/realms/jetski-saas */
export const ISSUER = (__ENV.ISSUER || 'http://localhost:8080/realms/jetski-saas').replace(/\/$/, '');

/**
 * Client usado para autenticar. `jetski-backoffice` é público (PKCE) e está na
 * lista `jetski.security.jwt.allowed-clients` do backend.
 *
 * NÃO troque por `jetski-test`: em produção ele não está em
 * JETSKI_JWT_ADDITIONAL_ALLOWED_CLIENTS (vazio) e todo request volta 401 —
 * foi desativado de propósito como correção de segurança.
 */
export const CLIENT_ID = __ENV.CLIENT_ID || 'jetski-backoffice';

/** Tenant alvo. Sem ele o teste nem começa (ver a trava abaixo). */
export const TENANT_ID = __ENV.TENANT_ID || '';

/**
 * Trava de segurança. Um teste de carga cria clientes, reservas e locações de
 * mentira; rodar contra o tenant de uma locadora real suja a operação dela e a
 * cobrança. O slug do tenant provisionado por `provisionar-tenant.sh` começa
 * com `carga-`, e exigimos que quem roda confirme isso explicitamente.
 *
 * Para rodar contra outro tenant (ex.: o de dev), passe -e PERMITIR_TENANT=<id>.
 */
export const TENANT_SLUG = __ENV.TENANT_SLUG || '';
export function validarAlvo() {
  if (!TENANT_ID) {
    throw new Error('TENANT_ID é obrigatório. Rode ./k6/provisionar-tenant.sh primeiro.');
  }
  const liberado = __ENV.PERMITIR_TENANT === TENANT_ID;
  const ehTenantDeCarga = TENANT_SLUG.startsWith('carga-');
  if (!ehTenantDeCarga && !liberado) {
    throw new Error(
      `Recusando rodar contra o tenant ${TENANT_ID} (slug "${TENANT_SLUG}"): ` +
      'não parece ser um tenant de carga. Se for intencional, passe ' +
      `-e PERMITIR_TENANT=${TENANT_ID}.`
    );
  }
}

/**
 * Metas de latência. São os SLIs da F1 traduzidos em portão de teste: se o
 * cenário estourar, o k6 sai com código != 0 e o CI reprova.
 *
 * Os valores saem da linha de base (P95 global de 40 ms com a plataforma
 * ociosa) com folga generosa para carga — a ideia é pegar degradação de ordem
 * de grandeza, não milissegundo.
 */
export const LIMITES = {
  leitura: {
    'http_req_failed{tipo:leitura}': ['rate<0.01'],
    'http_req_duration{tipo:leitura}': ['p(95)<800', 'p(99)<2000'],
  },
  escrita: {
    'http_req_failed{tipo:escrita}': ['rate<0.01'],
    'http_req_duration{tipo:escrita}': ['p(95)<2000', 'p(99)<5000'],
  },
  jornada: {
    'jornada_balcao_completa': ['p(95)<8000'],
  },
};

/** Cabeçalhos padrão de uma chamada autenticada ao backoffice. */
export function headers(token) {
  return {
    Authorization: `Bearer ${token}`,
    'X-Tenant-Id': TENANT_ID,
    'Content-Type': 'application/json',
  };
}

/**
 * Endpoints que os cenários NUNCA devem chamar, e por quê. Está aqui como
 * documentação executável: se alguém adicionar um cenário que toque nestes
 * caminhos, o code review tem onde se apoiar.
 *
 * - /emissoes, /gru            → emite documento REAL na Marinha. Há bloqueio
 *                                por volume (~8–10 GRUs/dia/CPF) e a conta é
 *                                de crédito de verdade.
 * - /enviar-pix-email, convites → dispara e-mail real (cota do Gmail: 500/dia).
 * - /v1/platform/**             → console da plataforma, fora do escopo do tenant.
 */
export const PROIBIDOS = ['/emissoes', '/gru', '/enviar-pix-email', '/convites', '/v1/platform/'];
