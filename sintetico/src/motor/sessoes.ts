// Sessões das personas durante a rodada: cada uma entra pelo Keycloak como uma pessoa
// (staff por senha no backoffice; cliente pelo código de 6 dígitos no portal) e o token é
// renovado sozinho — um "sábado" dura mais que a validade do access token.

import { login, renovar, type Tokens } from '../lib/keycloak.ts';
import { esperarNoEmail, lerCodigo } from '../lib/mailpit.ts';

export interface Alvo {
  dominio: string;
  mailpit: string;
}

const issuerDe = (dominio: string) => `https://sso.${dominio}/realms/jetski-saas`;

class Sessao {
  private tokens?: Tokens;
  private expiraEm = 0;
  private emAndamento?: Promise<string>;
  private readonly issuer: string;
  private readonly clientId: string;
  private readonly entrar: () => Promise<Tokens>;

  constructor(issuer: string, clientId: string, entrar: () => Promise<Tokens>) {
    this.issuer = issuer;
    this.clientId = clientId;
    this.entrar = entrar;
  }

  private guardar(t: Tokens): string {
    this.tokens = t;
    this.expiraEm = Date.now() + (t.expiraEmSegundos - 30) * 1000;
    return t.accessToken;
  }

  /** Access token válido. Chamadas simultâneas compartilham o mesmo login/renovação. */
  token(): Promise<string> {
    if (this.tokens && Date.now() < this.expiraEm) return Promise.resolve(this.tokens.accessToken);
    this.emAndamento ??= (async () => {
      try {
        if (this.tokens) {
          try {
            return this.guardar(await renovar(this.issuer, this.clientId, this.tokens.refreshToken));
          } catch {
            // sessão SSO caiu (restart do Keycloak, ociosidade): entra de novo, como a pessoa faria
          }
        }
        return this.guardar(await this.entrar());
      } finally {
        this.emAndamento = undefined;
      }
    })();
    return this.emAndamento;
  }
}

export class Sessoes {
  private readonly alvo: Alvo;
  private readonly porEmail = new Map<string, Sessao>();
  logins = 0;

  constructor(alvo: Alvo) {
    this.alvo = alvo;
  }

  staff(email: string, senha: string): Promise<string> {
    return this.sessao(`staff:${email}`, 'jetski-backoffice', async () => {
      this.logins++;
      return (
        await login({
          issuer: issuerDe(this.alvo.dominio),
          clientId: 'jetski-backoffice',
          redirectUri: `https://app.${this.alvo.dominio}/api/auth/callback/keycloak`,
          usuario: email,
          senha,
        })
      ).tokens;
    }).token();
  }

  /** Operadora de plataforma no console: senha + TOTP gerado do segredo guardado no estado. */
  console(email: string, senha: string, totpSegredo: string): Promise<string> {
    return this.sessao(`console:${email}`, 'jetski-platform-console', async () => {
      this.logins++;
      return (
        await login({
          issuer: issuerDe(this.alvo.dominio),
          clientId: 'jetski-platform-console',
          redirectUri: `https://admin.${this.alvo.dominio}/api/auth/callback/keycloak`,
          usuario: email,
          senha,
          totpSegredo,
        })
      ).tokens;
    }).token();
  }

  /** O cliente entra SEM senha: pede o código, lê o e-mail (Mailpit) e digita os 6 dígitos. */
  cliente(email: string): Promise<string> {
    return this.sessao(`cliente:${email}`, 'jetski-customer-portal', async () => {
      this.logins++;
      return (
        await login({
          issuer: issuerDe(this.alvo.dominio),
          clientId: 'jetski-customer-portal',
          redirectUri: `https://cliente.${this.alvo.dominio}/api/auth/callback/keycloak`,
          usuario: email,
          senha: '',
          obterCodigoPorEmail: (pedidoEm) => esperarNoEmail(this.alvo.mailpit, email, pedidoEm, (texto) => lerCodigo(texto), 'código de login'),
        })
      ).tokens;
    }).token();
  }

  private sessao(chave: string, clientId: string, entrar: () => Promise<Tokens>): Sessao {
    let s = this.porEmail.get(chave);
    if (!s) {
      s = new Sessao(issuerDe(this.alvo.dominio), clientId, entrar);
      this.porEmail.set(chave, s);
    }
    return s;
  }
}
