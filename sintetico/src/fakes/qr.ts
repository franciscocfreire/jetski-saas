/**
 * Codificador de QR Code (modo byte, correção L, versões 1–40) — sem dependências.
 *
 * Existe para o PagTesouro fake devolver, como o real, um PNG **escaneável** do PIX
 * copia-e-cola. O algoritmo segue a ISO/IEC 18004; a estrutura (tabelas por versão,
 * contagem de módulos por fórmula) é a da implementação de referência do Project Nayuki.
 */

// Correção L: codewords de correção por bloco e nº de blocos, indexados pela versão (1–40).
const ECC_POR_BLOCO = [0, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30];
const BLOCOS = [0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25];
const FORMATO_L = 1; // bits do nível de correção L na informação de formato

function modulosDeDados(ver: number): number {
  let r = (16 * ver + 128) * ver + 64;
  if (ver >= 2) {
    const n = Math.floor(ver / 7) + 2;
    r -= (25 * n - 10) * n - 55;
    if (ver >= 7) r -= 36;
  }
  return r;
}

function codewordsDeDados(ver: number): number {
  return Math.floor(modulosDeDados(ver) / 8) - ECC_POR_BLOCO[ver] * BLOCOS[ver];
}

// ---- Reed-Solomon sobre GF(2^8), polinômio 0x11D -------------------------------------

function gfMul(x: number, y: number): number {
  let z = 0;
  for (let i = 7; i >= 0; i--) {
    z = (z << 1) ^ ((z >>> 7) * 0x11d);
    z ^= ((y >>> i) & 1) * x;
  }
  return z;
}

function divisorRS(grau: number): number[] {
  const r = new Array<number>(grau).fill(0);
  r[grau - 1] = 1;
  let raiz = 1;
  for (let i = 0; i < grau; i++) {
    for (let j = 0; j < grau; j++) {
      r[j] = gfMul(r[j], raiz);
      if (j + 1 < grau) r[j] ^= r[j + 1];
    }
    raiz = gfMul(raiz, 0x02);
  }
  return r;
}

function restoRS(dados: number[], divisor: number[]): number[] {
  const r = new Array<number>(divisor.length).fill(0);
  for (const b of dados) {
    const fator = b ^ (r.shift() as number);
    r.push(0);
    divisor.forEach((coef, i) => (r[i] ^= gfMul(coef, fator)));
  }
  return r;
}

// ---- montagem ------------------------------------------------------------------------

function escolherVersao(nBytes: number): number {
  for (let ver = 1; ver <= 40; ver++) {
    const bitsContagem = ver <= 9 ? 8 : 16;
    if (4 + bitsContagem + nBytes * 8 <= codewordsDeDados(ver) * 8) return ver;
  }
  throw new Error(`texto grande demais para um QR Code (${nBytes} bytes)`);
}

function codewords(bytes: Uint8Array, ver: number): number[] {
  const bits: number[] = [];
  const por = (valor: number, n: number) => {
    for (let i = n - 1; i >= 0; i--) bits.push((valor >>> i) & 1);
  };
  por(0b0100, 4); // modo byte
  por(bytes.length, ver <= 9 ? 8 : 16);
  for (const b of bytes) por(b, 8);
  const capacidade = codewordsDeDados(ver) * 8;
  por(0, Math.min(4, capacidade - bits.length));
  por(0, (8 - (bits.length % 8)) % 8);
  for (let pad = 0xec; bits.length < capacidade; pad ^= 0xec ^ 0x11) por(pad, 8);

  const dados: number[] = [];
  for (let i = 0; i < bits.length; i += 8) dados.push(bits.slice(i, i + 8).reduce((a, b) => (a << 1) | b, 0));

  // Divide em blocos, calcula a correção de cada um e intercala.
  const nBlocos = BLOCOS[ver];
  const eccLen = ECC_POR_BLOCO[ver];
  const bruto = Math.floor(modulosDeDados(ver) / 8);
  const curtos = nBlocos - (bruto % nBlocos);
  const lenCurto = Math.floor(bruto / nBlocos);
  const divisor = divisorRS(eccLen);
  const blocos: number[][] = [];
  for (let i = 0, k = 0; i < nBlocos; i++) {
    const d = dados.slice(k, k + lenCurto - eccLen + (i < curtos ? 0 : 1));
    k += d.length;
    const ecc = restoRS(d, divisor);
    if (i < curtos) d.push(0); // marcador para alinhar a intercalação
    blocos.push(d.concat(ecc));
  }
  const saida: number[] = [];
  for (let i = 0; i < blocos[0].length; i++) {
    blocos.forEach((b, j) => {
      if (i !== lenCurto - eccLen || j >= curtos) saida.push(b[i]);
    });
  }
  return saida;
}

function posicoesDeAlinhamento(ver: number): number[] {
  if (ver === 1) return [];
  const n = Math.floor(ver / 7) + 2;
  const passo = Math.floor((ver * 8 + n * 3 + 5) / (n * 4 - 4)) * 2;
  const r = [6];
  for (let pos = ver * 4 + 10; r.length < n; pos -= passo) r.splice(1, 0, pos);
  return r;
}

const MASCARAS: ((x: number, y: number) => boolean)[] = [
  (x, y) => (x + y) % 2 === 0,
  (_x, y) => y % 2 === 0,
  (x) => x % 3 === 0,
  (x, y) => (x + y) % 3 === 0,
  (x, y) => (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0,
  (x, y) => ((x * y) % 2) + ((x * y) % 3) === 0,
  (x, y) => (((x * y) % 2) + ((x * y) % 3)) % 2 === 0,
  (x, y) => (((x + y) % 2) + ((x * y) % 3)) % 2 === 0,
];

/** Penalidade da máscara (regras N1, N2 e N4 da norma — bastam para escolher uma boa). */
function penalidade(m: boolean[][]): number {
  const n = m.length;
  let p = 0;
  for (let eixo = 0; eixo < 2; eixo++) {
    for (let a = 0; a < n; a++) {
      let seq = 1;
      for (let b = 1; b < n; b++) {
        const atual = eixo ? m[b][a] : m[a][b];
        const anterior = eixo ? m[b - 1][a] : m[a][b - 1];
        if (atual === anterior) {
          seq++;
          if (seq === 5) p += 3;
          else if (seq > 5) p++;
        } else seq = 1;
      }
    }
  }
  let escuros = 0;
  for (let y = 0; y < n; y++) {
    for (let x = 0; x < n; x++) {
      if (m[y][x]) escuros++;
      if (x + 1 < n && y + 1 < n && m[y][x] === m[y][x + 1] && m[y][x] === m[y + 1][x] && m[y][x] === m[y + 1][x + 1]) p += 3;
    }
  }
  return p + Math.floor(Math.abs((escuros * 20) / (n * n) - 10)) * 10;
}

/** Matriz de módulos (true = escuro) do QR Code de `texto`, em UTF-8. */
export function qrCode(texto: string): boolean[][] {
  const bytes = new TextEncoder().encode(texto);
  const ver = escolherVersao(bytes.length);
  const n = ver * 4 + 17;
  const mod: boolean[][] = Array.from({ length: n }, () => new Array<boolean>(n).fill(false));
  const fixo: boolean[][] = Array.from({ length: n }, () => new Array<boolean>(n).fill(false));
  const fixar = (x: number, y: number, escuro: boolean) => {
    if (x < 0 || y < 0 || x >= n || y >= n) return;
    mod[y][x] = escuro;
    fixo[y][x] = true;
  };

  // Temporização, localizadores, alinhamento.
  for (let i = 0; i < n; i++) {
    fixar(6, i, i % 2 === 0);
    fixar(i, 6, i % 2 === 0);
  }
  for (const [cx, cy] of [[3, 3], [n - 4, 3], [3, n - 4]]) {
    for (let dy = -4; dy <= 4; dy++) {
      for (let dx = -4; dx <= 4; dx++) {
        const d = Math.max(Math.abs(dx), Math.abs(dy));
        fixar(cx + dx, cy + dy, d !== 2 && d !== 4);
      }
    }
  }
  const al = posicoesDeAlinhamento(ver);
  al.forEach((cx, i) =>
    al.forEach((cy, j) => {
      const canto = (i === 0 && j === 0) || (i === 0 && j === al.length - 1) || (i === al.length - 1 && j === 0);
      if (canto) return;
      for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) fixar(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
    }),
  );

  const desenharFormato = (mascara: number) => {
    const dados = (FORMATO_L << 3) | mascara;
    let resto = dados;
    for (let i = 0; i < 10; i++) resto = (resto << 1) ^ ((resto >>> 9) * 0x537);
    const bits = ((dados << 10) | resto) ^ 0x5412;
    const bit = (i: number) => ((bits >>> i) & 1) !== 0;
    for (let i = 0; i <= 5; i++) fixar(8, i, bit(i));
    fixar(8, 7, bit(6));
    fixar(8, 8, bit(7));
    fixar(7, 8, bit(8));
    for (let i = 9; i < 15; i++) fixar(14 - i, 8, bit(i));
    for (let i = 0; i < 8; i++) fixar(n - 1 - i, 8, bit(i));
    for (let i = 8; i < 15; i++) fixar(8, n - 15 + i, bit(i));
    fixar(8, n - 8, true);
  };
  desenharFormato(0); // reserva a área antes de distribuir os dados

  if (ver >= 7) {
    let resto = ver;
    for (let i = 0; i < 12; i++) resto = (resto << 1) ^ ((resto >>> 11) * 0x1f25);
    const bits = (ver << 12) | resto;
    for (let i = 0; i < 18; i++) {
      const escuro = ((bits >>> i) & 1) !== 0;
      const a = n - 11 + (i % 3);
      const b = Math.floor(i / 3);
      fixar(a, b, escuro);
      fixar(b, a, escuro);
    }
  }

  // Dados em zigue-zague, de baixo para cima, pulando a coluna de temporização.
  const cw = codewords(bytes, ver);
  let i = 0;
  for (let direita = n - 1; direita >= 1; direita -= 2) {
    if (direita === 6) direita = 5;
    for (let v = 0; v < n; v++) {
      for (let j = 0; j < 2; j++) {
        const x = direita - j;
        const sobe = ((direita + 1) & 2) === 0;
        const y = sobe ? n - 1 - v : v;
        if (!fixo[y][x] && i < cw.length * 8) {
          mod[y][x] = ((cw[i >>> 3] >>> (7 - (i & 7))) & 1) !== 0;
          i++;
        }
      }
    }
  }

  const aplicar = (k: number) => {
    for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) if (!fixo[y][x] && MASCARAS[k](x, y)) mod[y][x] = !mod[y][x];
  };
  let melhor = 0;
  let menor = Infinity;
  for (let k = 0; k < 8; k++) {
    aplicar(k);
    desenharFormato(k);
    const p = penalidade(mod);
    if (p < menor) {
      menor = p;
      melhor = k;
    }
    aplicar(k); // XOR de novo desfaz
  }
  aplicar(melhor);
  desenharFormato(melhor);
  return mod;
}
