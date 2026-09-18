/**
 * DER (ASN.1) mínimo, sem dependências: o que a TSA sintética precisa para ler um
 * TimeStampReq e escrever TimeStampResp, CMS SignedData e um certificado X.509.
 * Codificador por construção de TLVs; decodificador é um caminhante de TLVs.
 */

export const TAG = {
  BOOLEAN: 0x01, INTEGER: 0x02, BIT_STRING: 0x03, OCTET_STRING: 0x04, NULL: 0x05, OID: 0x06,
  UTF8: 0x0c, SEQUENCE: 0x30, SET: 0x31, PRINTABLE: 0x13, UTC_TIME: 0x17, GENERALIZED_TIME: 0x18,
} as const;

function comprimento(n: number): Buffer {
  if (n < 0x80) return Buffer.from([n]);
  const bytes: number[] = [];
  for (let v = n; v > 0; v = Math.floor(v / 256)) bytes.unshift(v & 0xff);
  return Buffer.from([0x80 | bytes.length, ...bytes]);
}

export function tlv(tag: number, conteudo: Buffer | Buffer[]): Buffer {
  const c = Array.isArray(conteudo) ? Buffer.concat(conteudo) : conteudo;
  return Buffer.concat([Buffer.from([tag]), comprimento(c.length), c]);
}

export const seq = (...itens: Buffer[]) => tlv(TAG.SEQUENCE, itens);
export const set = (...itens: Buffer[]) => tlv(TAG.SET, itens);
export const nulo = () => tlv(TAG.NULL, Buffer.alloc(0));
export const octetos = (b: Buffer) => tlv(TAG.OCTET_STRING, b);
export const booleano = (v: boolean) => tlv(TAG.BOOLEAN, Buffer.from([v ? 0xff : 0x00]));
export const utf8 = (s: string) => tlv(TAG.UTF8, Buffer.from(s, 'utf8'));
export const imprimivel = (s: string) => tlv(TAG.PRINTABLE, Buffer.from(s, 'latin1'));
/** [n] EXPLICIT (construído) */
export const explicito = (n: number, ...itens: Buffer[]) => tlv(0xa0 | n, itens);
/** [n] IMPLICIT construído (SET/SEQUENCE com a tag trocada) */
export const implicito = (n: number, ...itens: Buffer[]) => tlv(0xa0 | n, itens);

/** INTEGER a partir de bytes big-endian sem sinal (acrescenta 0x00 se o bit alto estiver ligado). */
export function inteiroDeBytes(b: Buffer): Buffer {
  let i = 0;
  while (i < b.length - 1 && b[i] === 0 && (b[i + 1] & 0x80) === 0) i++;
  const corpo = b.subarray(i);
  return tlv(TAG.INTEGER, corpo[0] & 0x80 ? Buffer.concat([Buffer.from([0]), corpo]) : corpo);
}

export function inteiro(n: number | bigint): Buffer {
  let v = BigInt(n);
  if (v < 0n) throw new Error('só inteiros não negativos');
  const bytes: number[] = [];
  do {
    bytes.unshift(Number(v & 0xffn));
    v >>= 8n;
  } while (v > 0n);
  return inteiroDeBytes(Buffer.from(bytes));
}

export function bitString(b: Buffer, bitsNaoUsados = 0): Buffer {
  return tlv(TAG.BIT_STRING, Buffer.concat([Buffer.from([bitsNaoUsados]), b]));
}

export function oid(pontuado: string): Buffer {
  const partes = pontuado.split('.').map(Number);
  const bytes: number[] = [partes[0] * 40 + partes[1]];
  for (const p of partes.slice(2)) {
    const grupo: number[] = [];
    let v = p;
    do {
      grupo.unshift(v & 0x7f);
      v = Math.floor(v / 128);
    } while (v > 0);
    for (let i = 0; i < grupo.length - 1; i++) grupo[i] |= 0x80;
    bytes.push(...grupo);
  }
  return tlv(TAG.OID, Buffer.from(bytes));
}

const dois = (n: number) => String(n).padStart(2, '0');

/** GeneralizedTime YYYYMMDDHHMMSSZ (RFC 3161 pede UTC, sem fração para o nosso uso). */
export function generalizedTime(d: Date): Buffer {
  const s = `${d.getUTCFullYear()}${dois(d.getUTCMonth() + 1)}${dois(d.getUTCDate())}${dois(d.getUTCHours())}${dois(d.getUTCMinutes())}${dois(d.getUTCSeconds())}Z`;
  return tlv(TAG.GENERALIZED_TIME, Buffer.from(s, 'latin1'));
}

/** UTCTime YYMMDDHHMMSSZ (X.509 exige UTCTime até 2049). */
export function utcTime(d: Date): Buffer {
  const s = `${dois(d.getUTCFullYear() % 100)}${dois(d.getUTCMonth() + 1)}${dois(d.getUTCDate())}${dois(d.getUTCHours())}${dois(d.getUTCMinutes())}${dois(d.getUTCSeconds())}Z`;
  return tlv(TAG.UTC_TIME, Buffer.from(s, 'latin1'));
}

// ---- decodificação -----------------------------------------------------------------------------

export interface No {
  tag: number;
  /** conteúdo bruto (sem tag e comprimento) */
  conteudo: Buffer;
  /** TLV inteiro, como veio */
  bruto: Buffer;
  filhos: No[];
}

function ehConstruido(tag: number): boolean {
  return (tag & 0x20) !== 0;
}

/** Lê uma sequência de TLVs a partir de `b`. Lança em DER malformado. */
export function decodificar(b: Buffer): No[] {
  const nos: No[] = [];
  let i = 0;
  while (i < b.length) {
    const inicio = i;
    const tag = b[i++];
    if ((tag & 0x1f) === 0x1f) throw new Error('tag longa não suportada');
    let len = b[i++];
    if (len === undefined) throw new Error('DER truncado (comprimento)');
    if (len & 0x80) {
      const n = len & 0x7f;
      if (n === 0 || n > 4) throw new Error('comprimento indefinido ou grande demais');
      len = 0;
      for (let k = 0; k < n; k++) len = len * 256 + b[i++];
    }
    if (i + len > b.length) throw new Error('DER truncado (conteúdo)');
    const conteudo = b.subarray(i, i + len);
    nos.push({ tag, conteudo, bruto: b.subarray(inicio, i + len), filhos: ehConstruido(tag) ? decodificar(conteudo) : [] });
    i += len;
  }
  return nos;
}

export function oidParaTexto(conteudo: Buffer): string {
  const partes: number[] = [Math.floor(conteudo[0] / 40), conteudo[0] % 40];
  let v = 0;
  for (let i = 1; i < conteudo.length; i++) {
    v = v * 128 + (conteudo[i] & 0x7f);
    if ((conteudo[i] & 0x80) === 0) {
      partes.push(v);
      v = 0;
    }
  }
  return partes.join('.');
}
