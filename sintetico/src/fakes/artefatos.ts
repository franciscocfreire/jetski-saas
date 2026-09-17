/**
 * O que os sistemas reais entregam em binário ou em formato próprio, refeito aqui sem
 * dependências: PNG do QR, PDF do boleto, PIX copia-e-cola (BR Code EMV).
 *
 * Tudo é marcado como SINTÉTICO por dentro: um QR escaneado num banco aponta para um
 * recebedor que não existe (`*.invalid`), então nenhum pagamento de verdade se conclui.
 */
import { crc32, deflateSync } from 'node:zlib';
import { createHash } from 'node:crypto';
import { qrCode } from './qr.ts';

// ---- PNG ------------------------------------------------------------------------------

function pedaco(tipo: string, dados: Buffer): Buffer {
  const corpo = Buffer.concat([Buffer.from(tipo, 'latin1'), dados]);
  const saida = Buffer.alloc(corpo.length + 8);
  saida.writeUInt32BE(dados.length, 0);
  corpo.copy(saida, 4);
  saida.writeUInt32BE(crc32(corpo) >>> 0, corpo.length + 4);
  return saida;
}

/** PNG em tons de cinza do QR de `texto`, com a margem silenciosa de 4 módulos da norma. */
export function qrPng(texto: string, escala = 6): Buffer {
  const m = qrCode(texto);
  const margem = 4;
  const lado = (m.length + margem * 2) * escala;
  const linhas = Buffer.alloc((lado + 1) * lado, 0xff);
  for (let y = 0; y < lado; y++) {
    linhas[y * (lado + 1)] = 0; // filtro "none"
    const my = Math.floor(y / escala) - margem;
    for (let x = 0; x < lado; x++) {
      const mx = Math.floor(x / escala) - margem;
      if (m[my]?.[mx]) linhas[y * (lado + 1) + 1 + x] = 0x00;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(lado, 0);
  ihdr.writeUInt32BE(lado, 4);
  ihdr[8] = 8; // 8 bits por amostra, cor 0 = cinza
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pedaco('IHDR', ihdr),
    pedaco('IDAT', deflateSync(linhas)),
    pedaco('IEND', Buffer.alloc(0)),
  ]);
}

// ---- BR Code (PIX copia-e-cola) ---------------------------------------------------------

function tlv(id: string, valor: string): string {
  return id + String(valor.length).padStart(2, '0') + valor;
}

/** CRC16/CCITT-FALSE, o do campo 63 do BR Code. */
export function crc16(texto: string): string {
  let crc = 0xffff;
  for (const b of Buffer.from(texto, 'utf8')) {
    crc ^= b << 8;
    for (let i = 0; i < 8; i++) crc = crc & 0x8000 ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff;
  }
  return crc.toString(16).toUpperCase().padStart(4, '0');
}

/** PIX dinâmico com a estrutura do real; o recebedor é um host `.invalid`. */
export function brCode(idSessao: string, valor: number): string {
  const url = `pix.sintetico.invalid/v2/${idSessao.replaceAll('-', '')}`;
  const semCrc =
    tlv('00', '01') +
    tlv('01', '12') +
    tlv('26', tlv('00', 'br.gov.bcb.pix') + tlv('25', url)) +
    tlv('52', '0000') +
    tlv('53', '986') +
    tlv('54', valor.toFixed(2)) +
    tlv('58', 'BR') +
    tlv('59', 'TESOURO SINTETICO') +
    tlv('60', 'BRASILIA') +
    tlv('62', tlv('05', '***')) +
    '6304';
  return semCrc + crc16(semCrc);
}

// ---- PDF do boleto ------------------------------------------------------------------------

/**
 * PDF mínimo e válido (uma página, Helvetica). O backend exige ≥ 1000 bytes e extrai o
 * número da GRU do TEXTO com pdfbox — por isso o número vai numa linha própria.
 */
export function boletoPdf(d: { numero: string; nome: string; cpf: string; valor: number; descricao: string }): Buffer {
  const ascii = (s: string) => s.normalize('NFD').replace(/[^\x20-\x7e]/g, '').replace(/([()\\])/g, '\\$1');
  const linhas = [
    'GRU - GUIA DE RECOLHIMENTO DA UNIAO  (DOCUMENTO SINTETICO)',
    '',
    'ESTE BOLETO NAO TEM VALOR. Foi gerado pelo ambiente ESPELHO de testes do Meu Jet,',
    'por um servico que imita o site da Marinha. Nao pague, nao apresente a ninguem.',
    '',
    'Numero de referencia:',
    d.numero,
    '',
    `Contribuinte: ${d.nome}`,
    `CPF: ${d.cpf}`,
    `Servico: ${d.descricao}`,
    `Valor: R$ ${d.valor.toFixed(2).replace('.', ',')}`,
    'Unidade gestora: 673001 / Gestao 00001 (SINTETICO)',
    '',
    'Linha digitavel: 00000.00000 00000.000000 00000.000000 0 00000000000000 (SINTETICA)',
    '',
    'Instrucoes: documento ficticio para teste de integracao. O codigo de barras foi omitido',
    'de proposito para que nenhum banco o aceite.',
  ];
  const texto = ['BT', '/F1 11 Tf', '50 780 Td', '16 TL', ...linhas.map((l) => `(${ascii(l)}) Tj T*`), 'ET'].join('\n');
  const objetos = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${Buffer.byteLength(texto, 'latin1')} >>\nstream\n${texto}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>',
  ];
  let pdf = '%PDF-1.4\n';
  const posicoes: number[] = [];
  objetos.forEach((o, i) => {
    posicoes.push(Buffer.byteLength(pdf, 'latin1'));
    pdf += `${i + 1} 0 obj\n${o}\nendobj\n`;
  });
  const xref = Buffer.byteLength(pdf, 'latin1');
  pdf += `xref\n0 ${objetos.length + 1}\n0000000000 65535 f \n`;
  for (const p of posicoes) pdf += `${String(p).padStart(10, '0')} 00000 n \n`;
  pdf += `trailer\n<< /Size ${objetos.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(pdf, 'latin1');
}

// ---- identidade determinística ---------------------------------------------------------------

function digitosDe(semente: string, n: number): string {
  let saida = '';
  for (let i = 0; saida.length < n; i++) {
    saida += BigInt('0x' + createHash('sha256').update(`${semente}:${i}`).digest('hex')).toString(10);
  }
  return saida.slice(0, n);
}

/** O token da página-ponte é por CPF (mesmo CPF → mesmo token), como observado no site real. */
export function tokenDoCpf(cpf: string): string {
  return '14' + digitosDe(`token:${cpf}`, 13);
}

export function cpfValido(cpf: string): boolean {
  if (!/^\d{11}$/.test(cpf) || /^(\d)\1{10}$/.test(cpf)) return false;
  const dv = (n: number) => {
    let soma = 0;
    for (let i = 0; i < n; i++) soma += Number(cpf[i]) * (n + 1 - i);
    const r = (soma * 10) % 11;
    return r === 10 ? 0 : r;
  };
  return dv(9) === Number(cpf[9]) && dv(10) === Number(cpf[10]);
}

const NOMES = ['ALICE', 'BRUNO', 'CAIO', 'DANIELA', 'EDUARDO', 'FERNANDA', 'GABRIEL', 'HELENA', 'IGOR', 'JULIA', 'LUCAS', 'MARINA', 'NICOLAS', 'OLIVIA', 'PEDRO', 'RAFAELA'];
const SOBRENOMES = ['ALMEIDA', 'BARROS', 'CARDOSO', 'DUARTE', 'ESTEVES', 'FARIAS', 'GUIMARAES', 'LACERDA', 'MOURA', 'NOGUEIRA', 'PACHECO', 'QUEIROZ', 'RAMOS', 'SIQUEIRA', 'TAVARES', 'VIANA'];

/**
 * Nome que o cadastro da "Marinha" tem para um CPF não registrado via /_controle:
 * determinístico, em maiúsculas sem acento (como o site devolve) e marcado SINTETICO.
 */
export function nomeSinteticoDoCpf(cpf: string): string {
  const h = createHash('sha256').update(`nome:${cpf}`).digest();
  return `${NOMES[h[0] % NOMES.length]} ${SOBRENOMES[h[1] % SOBRENOMES.length]} SINTETICO`;
}
