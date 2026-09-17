// TOTP (RFC 6238) sobre HOTP (RFC 4226) — o "app autenticador" das personas.

import { createHmac } from 'node:crypto';

export interface PoliticaTotp {
  algoritmo: 'sha1' | 'sha256' | 'sha512';
  digitos: number;
  periodoSegundos: number;
}

export const POLITICA_PADRAO: PoliticaTotp = { algoritmo: 'sha1', digitos: 6, periodoSegundos: 30 };

export function hotp(chave: Buffer, contador: bigint, politica: PoliticaTotp = POLITICA_PADRAO): string {
  const msg = Buffer.alloc(8);
  msg.writeBigUInt64BE(contador);
  const mac = createHmac(politica.algoritmo, chave).update(msg).digest();
  const desloc = mac[mac.length - 1] & 0x0f;
  const bin =
    ((mac[desloc] & 0x7f) << 24) | (mac[desloc + 1] << 16) | (mac[desloc + 2] << 8) | mac[desloc + 3];
  return String(bin % 10 ** politica.digitos).padStart(politica.digitos, '0');
}

export function totp(chave: Buffer, agoraMs: number = Date.now(), politica: PoliticaTotp = POLITICA_PADRAO): string {
  return hotp(chave, BigInt(Math.floor(agoraMs / 1000 / politica.periodoSegundos)), politica);
}

const ALFABETO_B32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

/** Base32 (RFC 4648) sem padding — o formato que os apps autenticadores e o otpauth:// usam. */
export function base32(dados: Buffer): string {
  let bits = 0;
  let valor = 0;
  let saida = '';
  for (const byte of dados) {
    valor = (valor << 8) | byte;
    bits += 8;
    while (bits >= 5) {
      saida += ALFABETO_B32[(valor >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) saida += ALFABETO_B32[(valor << (5 - bits)) & 31];
  return saida;
}

/**
 * Link otpauth:// — é o que o QR code do Keycloak carrega. Serve para o HUMANO
 * adicionar a persona ao próprio app autenticador e entrar no console do espelho.
 */
export function otpauth(emissor: string, conta: string, chave: Buffer, p: PoliticaTotp = POLITICA_PADRAO): string {
  const rotulo = encodeURIComponent(`${emissor}:${conta}`);
  return (
    `otpauth://totp/${rotulo}?secret=${base32(chave)}&issuer=${encodeURIComponent(emissor)}` +
    `&algorithm=${p.algoritmo.toUpperCase()}&digits=${p.digitos}&period=${p.periodoSegundos}`
  );
}
