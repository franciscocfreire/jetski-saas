// Arquivo de estado do semeador: o que torna a semeadura RETOMÁVEL e idempotente,
// e onde ficam as credenciais das personas (senha e segredo TOTP).
//
// Nunca vai para o repositório: mora na VM do espelho, com permissão 0600.

import { chmodSync, existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import type { PoliticaTotp } from './totp.ts';

export interface EstadoPersona {
  email: string;
  tenantId?: string;
  slug?: string;
  /** Quando o cadastro foi feito — convites anteriores a isto são de outro cadastro. */
  cadastradaEm?: string;
  ativada?: boolean;
  /** Senha temporária do convite, até ser trocada no primeiro login. */
  senhaTemporaria?: string;
  senha?: string;
  totpSegredo?: string;
  totpPolitica?: PoliticaTotp;
  /** Link otpauth:// para o humano pôr a persona no próprio app autenticador. */
  otpauth?: string;
  aprovada?: boolean;
  plano?: string;
  jetskis?: number;
  /** Cliente do portal: e-mail confirmado pelo link do Keycloak. */
  emailVerificado?: boolean;
  /** Cliente do portal: já entrou pelo código enviado por e-mail. */
  entrouPorCodigo?: boolean;
  // ---- fase E3b: emissão ----
  /** CPF sorteado (DV válido) da pessoa; registrado na Marinha sintética com o nome dela. */
  cpf?: string;
  papeis?: string[];
  geralConfigurada?: boolean;
  emissoraConfigurada?: boolean;
  emissoraHabilitada?: boolean;
  instrutores?: Record<string, { id?: string; cpf?: string; assinou?: boolean; aprovado?: boolean }>;
  vinculoId?: string;
  vinculoAtivo?: boolean;
  instrutoresDesignados?: boolean;
  creditosComprados?: number;
  perfilCompleto?: boolean;
  /** Cliente de balcão: id da ficha na empresa e se os documentos já foram enviados. */
  clienteId?: string;
  anexos?: boolean;
}

export interface Estado {
  dominio: string;
  atualizadoEm: string;
  personas: Record<string, EstadoPersona>;
}

export class ArquivoDeEstado {
  readonly estado: Estado;
  private readonly caminho: string;

  constructor(caminho: string, dominio: string) {
    this.caminho = caminho;
    this.estado = existsSync(caminho)
      ? (JSON.parse(readFileSync(caminho, 'utf8')) as Estado)
      : { dominio, atualizadoEm: '', personas: {} };
    if (this.estado.dominio !== dominio) {
      throw new Error(`O estado em ${caminho} é de ${this.estado.dominio}, não de ${dominio}. Espelho recriado? Apague o arquivo.`);
    }
  }

  persona(chave: string, email: string): EstadoPersona {
    return (this.estado.personas[chave] ??= { email });
  }

  /** Grava a cada passo concluído: se o processo morrer no meio, a próxima execução retoma dali. */
  salvar(): void {
    this.estado.atualizadoEm = new Date().toISOString();
    mkdirSync(dirname(this.caminho), { recursive: true });
    const tmp = `${this.caminho}.tmp`;
    writeFileSync(tmp, `${JSON.stringify(this.estado, null, 2)}\n`, { mode: 0o600 });
    renameSync(tmp, this.caminho);
    chmodSync(this.caminho, 0o600);
  }
}
