/**
 * TSA sintética (RFC 3161) do espelho — fase E6.
 *
 * Recebe um TimeStampReq (DER, `application/timestamp-query`) e devolve um TimeStampResp com
 * um token CMS SignedData de verdade: TSTInfo, um SignerInfo com `signingCertificateV2`,
 * certificado X.509 auto-assinado com EKU timeStamping e assinatura RSA-2048 real
 * (node:crypto). Chave e certificado nascem a cada boot e nunca saem daqui; o certificado é
 * publicado em /tsa/cert.pem para quem quiser conferir o carimbo por fora (openssl ts -verify).
 *
 * O backend (BouncyCastle 1.76 na página de auditoria; OpenPDF no PAdES-T) só exige o mínimo —
 * imprint igual, nonce ecoado, um SignerInfo com signingCertificateV2 —; a assinatura real é
 * o princípio 3 da spec: o token continua verificável fora do espelho.
 */
import { createHash, generateKeyPairSync, randomBytes, sign, type KeyObject } from 'node:crypto';
import { bitString, booleano, decodificar, explicito, generalizedTime, implicito, inteiro, inteiroDeBytes, nulo, octetos, oid, oidParaTexto, seq, set, TAG, tlv, utcTime, utf8, imprimivel, type No } from './der.ts';

const OID = {
  sha1: '1.3.14.3.2.26',
  sha256: '2.16.840.1.101.3.4.2.1',
  sha384: '2.16.840.1.101.3.4.2.2',
  sha512: '2.16.840.1.101.3.4.2.3',
  rsaEncryption: '1.2.840.113549.1.1.1',
  sha256WithRSA: '1.2.840.113549.1.1.11',
  signedData: '1.2.840.113549.1.7.2',
  tstInfo: '1.2.840.113549.1.9.16.1.4',
  contentType: '1.2.840.113549.1.9.3',
  messageDigest: '1.2.840.113549.1.9.4',
  signingTime: '1.2.840.113549.1.9.5',
  signingCertificateV2: '1.2.840.113549.1.9.16.2.47',
  cn: '2.5.4.3',
  o: '2.5.4.10',
  c: '2.5.4.6',
  keyUsage: '2.5.29.15',
  extKeyUsage: '2.5.29.37',
  basicConstraints: '2.5.29.19',
  kpTimeStamping: '1.3.6.1.5.5.7.3.8',
  /** Política da TSA sintética: arco privado, marcado como "sintético". */
  politica: '1.3.6.1.4.1.99999.1.1',
} as const;

const HASHES: Record<string, { nome: string; bytes: number }> = {
  [OID.sha1]: { nome: 'sha1', bytes: 20 },
  [OID.sha256]: { nome: 'sha256', bytes: 32 },
  [OID.sha384]: { nome: 'sha384', bytes: 48 },
  [OID.sha512]: { nome: 'sha512', bytes: 64 },
};

const algId = (o: string) => seq(oid(o), nulo());
const nome = (cn: string) =>
  seq(
    set(seq(oid(OID.c), imprimivel('BR'))),
    set(seq(oid(OID.o), utf8('Meu Jet - espelho de testes'))),
    set(seq(oid(OID.cn), utf8(cn))),
  );

export interface Pedido {
  hashOid: string;
  hash: Buffer;
  nonce?: Buffer;
  certReq: boolean;
  politica?: string;
}

export interface Carimbo {
  /** TimeStampResp completo (DER). */
  resposta: Buffer;
  /** SHA-256(token DER)[0:16] em maiúsculas — a "Referência" que o backend imprime na página de auditoria. */
  referencia: string;
  serial: bigint;
  genTime: Date;
  hashOid: string;
  tamanho: number;
}

export class Tsa {
  readonly cert: Buffer;
  private readonly chave: KeyObject;
  private readonly nomeTsa = 'TSA Sintetica Meu Jet (espelho)';
  private serial = BigInt('0x' + randomBytes(6).toString('hex'));

  constructor() {
    const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
    this.chave = privateKey;
    this.cert = this.certificado(publicKey.export({ type: 'spki', format: 'der' }) as Buffer);
  }

  get certPem(): string {
    return `-----BEGIN CERTIFICATE-----\n${this.cert.toString('base64').replace(/(.{64})/g, '$1\n').trim()}\n-----END CERTIFICATE-----\n`;
  }

  /** Certificado X.509 v3 auto-assinado: keyUsage digitalSignature, EKU timeStamping (crítico). */
  private certificado(spki: Buffer): Buffer {
    const agora = new Date();
    const extensao = (o: string, critico: boolean, valor: Buffer) => seq(oid(o), ...(critico ? [booleano(true)] : []), octetos(valor));
    const tbs = seq(
      explicito(0, inteiro(2)),
      inteiroDeBytes(randomBytes(16)),
      algId(OID.sha256WithRSA),
      nome(this.nomeTsa),
      seq(utcTime(new Date(agora.getTime() - 86_400_000)), utcTime(new Date(agora.getTime() + 10 * 365 * 86_400_000))),
      nome(this.nomeTsa),
      spki,
      explicito(3, seq(
        extensao(OID.basicConstraints, true, seq()),
        extensao(OID.keyUsage, true, bitString(Buffer.from([0x80]), 7)), // digitalSignature
        extensao(OID.extKeyUsage, true, seq(oid(OID.kpTimeStamping))),
      )),
    );
    return seq(tbs, algId(OID.sha256WithRSA), bitString(sign('sha256', tbs, this.chave)));
  }

  /** Lê o TimeStampReq. Lança com mensagem legível em pedido malformado. */
  static lerPedido(der: Buffer): Pedido {
    const [req] = decodificar(der);
    if (!req || req.tag !== TAG.SEQUENCE) throw new Error('TimeStampReq não é uma SEQUENCE');
    const [versao, imprint, ...resto] = req.filhos;
    if (versao?.tag !== TAG.INTEGER || versao.conteudo[versao.conteudo.length - 1] !== 1) throw new Error('version deve ser 1');
    if (imprint?.tag !== TAG.SEQUENCE) throw new Error('messageImprint ausente');
    const [alg, hash] = imprint.filhos;
    const hashOid = oidParaTexto(alg?.filhos[0]?.conteudo ?? Buffer.alloc(0));
    const h = HASHES[hashOid];
    if (!h) throw new Error(`algoritmo de hash não suportado: ${hashOid}`);
    if (hash?.tag !== TAG.OCTET_STRING || hash.conteudo.length !== h.bytes) throw new Error(`hashedMessage deve ter ${h.bytes} bytes para ${h.nome}`);
    const p: Pedido = { hashOid, hash: Buffer.from(hash.conteudo), certReq: false };
    for (const n of resto) {
      if (n.tag === TAG.OID) p.politica = oidParaTexto(n.conteudo);
      else if (n.tag === TAG.INTEGER) p.nonce = Buffer.from(n.conteudo);
      else if (n.tag === TAG.BOOLEAN) p.certReq = n.conteudo[0] !== 0;
      // [0] extensions: ignoradas
    }
    return p;
  }

  /** TimeStampResp de rejeição (status 2 + failInfo systemFailure) — o modo "erro" da etapa tsa. */
  static recusa(motivo: string, failInfoBit = 25): Buffer {
    const bits = Buffer.alloc(Math.floor(failInfoBit / 8) + 1);
    bits[Math.floor(failInfoBit / 8)] |= 0x80 >> failInfoBit % 8;
    return seq(seq(inteiro(2), seq(utf8(motivo)), bitString(bits, 7 - (failInfoBit % 8))));
  }

  carimbar(pedido: Pedido, agora = new Date()): Carimbo {
    const serial = ++this.serial;
    const tstInfo = seq(
      inteiro(1),
      oid(pedido.politica ?? OID.politica),
      seq(algId(pedido.hashOid), octetos(pedido.hash)),
      inteiro(serial),
      generalizedTime(agora),
      seq(inteiro(1)), // accuracy: 1 s
      ...(pedido.nonce ? [inteiroDeBytes(pedido.nonce)] : []),
    );
    // SET OF em DER: elementos ordenados pelos bytes. A assinatura cobre o SET (tag 0x31); no
    // SignerInfo o mesmo conteúdo vai com a tag [0] IMPLICIT (0xa0).
    const conteudoAttrs = Buffer.concat(
      [
        seq(oid(OID.contentType), set(oid(OID.tstInfo))),
        seq(oid(OID.signingTime), set(utcTime(agora))),
        seq(oid(OID.messageDigest), set(octetos(createHash('sha256').update(tstInfo).digest()))),
        seq(oid(OID.signingCertificateV2), set(seq(seq(seq(octetos(createHash('sha256').update(this.cert).digest())))))),
      ].sort(Buffer.compare),
    );
    const atributos = tlv(TAG.SET, conteudoAttrs);
    const [certificado] = decodificar(this.cert);
    const [tbs] = certificado.filhos;
    const serialDoCert = tbs.filhos[1].bruto;
    const signerInfo = seq(
      inteiro(1),
      seq(nome(this.nomeTsa), serialDoCert),
      algId(OID.sha256),
      tlv(0xa0, conteudoAttrs),
      algId(OID.rsaEncryption),
      octetos(sign('sha256', atributos, this.chave)),
    );
    const signedData = seq(
      inteiro(3),
      set(algId(OID.sha256)),
      seq(oid(OID.tstInfo), explicito(0, octetos(tstInfo))),
      ...(pedido.certReq ? [implicito(0, this.cert)] : []),
      set(signerInfo),
    );
    const token = seq(oid(OID.signedData), explicito(0, signedData));
    const resposta = seq(seq(inteiro(0)), token);
    return {
      resposta,
      referencia: createHash('sha256').update(token).digest('hex').slice(0, 16).toUpperCase(),
      serial,
      genTime: agora,
      hashOid: pedido.hashOid,
      tamanho: token.length,
    };
  }
}

/** Auxiliar de testes: monta um TimeStampReq como o BouncyCastle/OpenPDF montam. */
export function montarPedido(hash: Buffer, hashOid: string, nonce?: Buffer, certReq = true): Buffer {
  return seq(inteiro(1), seq(algId(hashOid), octetos(hash)), ...(nonce ? [inteiroDeBytes(nonce)] : []), ...(certReq ? [booleano(true)] : []));
}

export { OID as OID_TSA };
export type { No };
