// Admin API do realm "Google" sintético do espelho (fase E7) — só o que o semeador precisa:
// criar/repor contas que farão o papel de identidades Google. Entra com o client de serviço
// `semeador-contas` (client_credentials), que só tem manage-users/view-users DESSE realm:
// o semeador nunca segura a senha master e não alcança o realm da aplicação.

import { ErroHttp, pedir } from './http.ts';

export class GoogleSintetico {
  private readonly base: string;
  private readonly realm: string;
  private readonly segredo: string;
  private token?: { valor: string; expiraEm: number };

  constructor(keycloakUrl: string, segredo: string, realm = 'google-sintetico') {
    this.base = keycloakUrl.replace(/\/$/, '');
    this.realm = realm;
    this.segredo = segredo;
  }

  private async bearer(): Promise<string> {
    if (this.token && Date.now() < this.token.expiraEm) return this.token.valor;
    const r = await pedir(`${this.base}/realms/${this.realm}/protocol/openid-connect/token`, {
      formulario: { grant_type: 'client_credentials', client_id: 'semeador-contas', client_secret: this.segredo },
    });
    if (r.status !== 200) throw new ErroHttp('token do semeador-contas no realm sintético', r);
    const d = JSON.parse(r.corpo) as { access_token: string; expires_in: number };
    this.token = { valor: d.access_token, expiraEm: Date.now() + (d.expires_in - 15) * 1000 };
    return d.access_token;
  }

  private async admin(metodo: 'GET' | 'POST' | 'PUT', caminho: string, json?: unknown) {
    return pedir(`${this.base}/admin/realms/${this.realm}${caminho}`, { metodo, json, cabecalhos: { Authorization: `Bearer ${await this.bearer()}`, Accept: 'application/json' } });
  }

  /** O realm existe e o client de serviço entra — a pré-condição de tudo o mais. */
  async issuer(): Promise<string> {
    const r = await pedir(`${this.base}/realms/${this.realm}/.well-known/openid-configuration`);
    if (r.status !== 200) throw new ErroHttp(`realm ${this.realm} não responde — rode infra/espelho/configure-keycloak-google-fake.sh`, r);
    await this.bearer();
    return (JSON.parse(r.corpo) as { issuer: string }).issuer;
  }

  /** Cria a conta "Google" (e-mail verificado, senha definitiva); se já existe, só repõe a senha. */
  async criarConta(c: { email: string; nome: string; sobrenome: string; senha: string }): Promise<'criada' | 'existia'> {
    const r = await this.admin('POST', '/users', {
      username: c.email, email: c.email, emailVerified: true, enabled: true, firstName: c.nome, lastName: c.sobrenome,
      credentials: [{ type: 'password', value: c.senha, temporary: false }],
    });
    if (r.status === 201) return 'criada';
    if (r.status !== 409) throw new ErroHttp(`criar conta Google sintética ${c.email}`, r);
    const lista = await this.admin('GET', `/users?email=${encodeURIComponent(c.email)}&exact=true`);
    const id = (JSON.parse(lista.corpo) as { id: string }[])[0]?.id;
    if (!id) throw new ErroHttp(`conta ${c.email} deu 409 mas não aparece na busca`, lista);
    const reset = await this.admin('PUT', `/users/${id}/reset-password`, { type: 'password', value: c.senha, temporary: false });
    if (reset.status >= 300) throw new ErroHttp(`repor senha de ${c.email}`, reset);
    return 'existia';
  }
}

/** Payload do JWT (sem verificar — é só para ler `sub` e papéis de um token que o próprio SSO acabou de emitir). */
export function payloadDoToken(jwt: string): { sub: string; email?: string; realm_access?: { roles?: string[] } } {
  const parte = jwt.split('.')[1] ?? '';
  return JSON.parse(Buffer.from(parte.replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8'));
}
