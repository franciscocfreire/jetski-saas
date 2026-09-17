// Imagens sintéticas das personas: documento de identidade, selfie, comprovante e assinatura.
//
// PNGs válidos gerados sem dependências. O conteúdo é ruído + faixas (nada que pareça um
// documento de verdade), com tamanho de foto de celular comprimida — é o peso, e não o
// desenho, que interessa ao espelho: upload em base64, MinIO e PDF com anexos.
// Todo arquivo leva um bloco tEXt "Comment: SINTETICO ..." para ser reconhecível depois.

import { crc32, deflateSync } from 'node:zlib';

function pedaco(tipo: string, dados: Buffer): Buffer {
  const corpo = Buffer.concat([Buffer.from(tipo, 'latin1'), dados]);
  const saida = Buffer.alloc(corpo.length + 8);
  saida.writeUInt32BE(dados.length, 0);
  corpo.copy(saida, 4);
  saida.writeUInt32BE(crc32(corpo) >>> 0, corpo.length + 4);
  return saida;
}

function png(largura: number, altura: number, canais: 1 | 3, pixel: (x: number, y: number, c: number) => number, comentario: string): Buffer {
  const passo = largura * canais + 1;
  const linhas = Buffer.alloc(passo * altura);
  for (let y = 0; y < altura; y++) {
    for (let x = 0; x < largura; x++) for (let c = 0; c < canais; c++) linhas[y * passo + 1 + x * canais + c] = pixel(x, y, c);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(largura, 0);
  ihdr.writeUInt32BE(altura, 4);
  ihdr[8] = 8;
  ihdr[9] = canais === 3 ? 2 : 0; // RGB ou tons de cinza
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pedaco('IHDR', ihdr),
    pedaco('tEXt', Buffer.from(`Comment\0SINTETICO - ${comentario} - ambiente espelho Meu Jet, sem valor`, 'latin1')),
    pedaco('IDAT', deflateSync(linhas)),
    pedaco('IEND', Buffer.alloc(0)),
  ]);
}

/** Gerador determinístico (mulberry32): a mesma semente dá a mesma imagem. */
function aleatorio(semente: string): () => number {
  let a = 0;
  for (const ch of semente) a = (Math.imul(a, 31) + ch.charCodeAt(0)) | 0;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * "Foto" colorida: ruído (não comprime, dá o peso) sob faixas diagonais (marca visual de
 * que não é documento). 320×240 ≈ 200 KB.
 */
export function fotoSintetica(semente: string, largura = 320, altura = 240): Buffer {
  const r = aleatorio(semente);
  const tom = [r() * 255, r() * 255, r() * 255];
  return png(largura, altura, 3, (x, y, c) => (Math.floor((x + y) / 24) % 2 === 0 ? Math.floor(r() * 256) : Math.floor(tom[c] * 0.6 + r() * 100)), `foto ${semente}`);
}

/** Assinatura: traço senoidal escuro sobre branco, como sai de um SignaturePad. ~2 KB. */
export function assinaturaSintetica(semente: string, largura = 360, altura = 120): Buffer {
  const r = aleatorio(semente);
  const [f1, f2, fase] = [2 + r() * 3, 5 + r() * 6, r() * 6];
  const yDe = (x: number) => altura / 2 + Math.sin((x / largura) * Math.PI * f1 + fase) * altura * 0.28 + Math.sin((x / largura) * Math.PI * f2) * altura * 0.1;
  return png(largura, altura, 1, (x, y) => (x > 20 && x < largura - 20 && Math.abs(y - yDe(x)) < 1.6 ? 0x20 : 0xff), `assinatura ${semente}`);
}

export const dataUrl = (imagem: Buffer): string => `data:image/png;base64,${imagem.toString('base64')}`;
