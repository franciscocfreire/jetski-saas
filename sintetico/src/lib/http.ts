// Cliente HTTP mínimo com pote de cookies — o "navegador" das personas.
//
// Sem dependências de propósito: este código roda dentro da VM do espelho num
// container Node puro (sem `npm install`), e nada de terceiros entra no caminho
// das credenciais das personas.

export interface Resposta {
  status: number;
  url: string;
  headers: Headers;
  corpo: string;
  /** Location absoluto, quando a resposta é um redirect. */
  location: string | null;
}

interface Cookie {
  nome: string;
  valor: string;
  host: string;
  caminho: string;
}

/**
 * Pote de cookies por host + caminho. É o suficiente para o Keycloak
 * (AUTH_SESSION_ID, KC_RESTART… com Path=/realms/<realm>/) — não implementa
 * Domain=, expiração nem SameSite, que nenhum fluxo daqui usa.
 */
export class PoteDeCookies {
  private cookies: Cookie[] = [];

  guardar(url: string, setCookies: string[]): void {
    const { hostname } = new URL(url);
    for (const linha of setCookies) {
      const [par, ...atributos] = linha.split(';').map((s) => s.trim());
      const igual = par.indexOf('=');
      if (igual < 1) continue;
      const nome = par.slice(0, igual);
      const valor = par.slice(igual + 1);
      let caminho = '/';
      let expirou = false;
      for (const a of atributos) {
        const [k, v = ''] = a.split('=');
        if (k.toLowerCase() === 'path' && v) caminho = v;
        if (k.toLowerCase() === 'max-age' && Number(v) <= 0) expirou = true;
      }
      this.cookies = this.cookies.filter(
        (c) => !(c.nome === nome && c.host === hostname && c.caminho === caminho),
      );
      if (!expirou && valor !== '') this.cookies.push({ nome, valor, host: hostname, caminho });
    }
  }

  cabecalho(url: string): string {
    const { hostname, pathname } = new URL(url);
    return this.cookies
      .filter((c) => c.host === hostname && pathname.startsWith(c.caminho))
      .map((c) => `${c.nome}=${c.valor}`)
      .join('; ');
  }
}

export interface OpcoesPedido {
  metodo?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  cabecalhos?: Record<string, string>;
  /** Corpo application/x-www-form-urlencoded. */
  formulario?: Record<string, string>;
  /** Corpo application/json. */
  json?: unknown;
  pote?: PoteDeCookies;
  timeoutMs?: number;
}

export const AGENTE = 'meujet-sintetico/1.0 (persona; +ECOSSISTEMA_SINTETICO_SPEC.md)';

/** Um pedido HTTP. NUNCA segue redirect sozinho: os fluxos de login precisam ler o Location. */
export async function pedir(url: string, opcoes: OpcoesPedido = {}): Promise<Resposta> {
  const cabecalhos: Record<string, string> = {
    'User-Agent': AGENTE,
    Accept: 'text/html,application/json;q=0.9,*/*;q=0.8',
    'Accept-Language': 'pt-BR',
    ...opcoes.cabecalhos,
  };
  let body: string | undefined;
  if (opcoes.formulario) {
    body = new URLSearchParams(opcoes.formulario).toString();
    cabecalhos['Content-Type'] = 'application/x-www-form-urlencoded';
  } else if (opcoes.json !== undefined) {
    body = JSON.stringify(opcoes.json);
    cabecalhos['Content-Type'] = 'application/json';
  }
  const cookie = opcoes.pote?.cabecalho(url);
  if (cookie) cabecalhos.Cookie = cookie;

  const res = await fetch(url, {
    method: opcoes.metodo ?? (body === undefined ? 'GET' : 'POST'),
    headers: cabecalhos,
    body,
    redirect: 'manual',
    signal: AbortSignal.timeout(opcoes.timeoutMs ?? 30_000),
  });
  opcoes.pote?.guardar(url, res.headers.getSetCookie());
  const loc = res.headers.get('location');
  return {
    status: res.status,
    url,
    headers: res.headers,
    corpo: await res.text(),
    location: loc ? new URL(loc, url).toString() : null,
  };
}

/** Erro com contexto suficiente para diagnosticar sem reexecutar. */
export class ErroHttp extends Error {
  constructor(oQue: string, r: Resposta) {
    super(`${oQue}: HTTP ${r.status} em ${r.url}\n${r.corpo.slice(0, 400)}`);
  }
}
