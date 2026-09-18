// Cenário PORTAL — o cliente final, o único tráfego exposto a anônimos (fase E5).
//
// Metade da jornada é PÚBLICA (vitrine da loja, modelos, disponibilidade): é o que marketing
// traz e o que o nginx limita por IP (`rl_publico`, 120 r/min, burst 60). A outra metade é
// autenticada: reserva online com sinal e envio do comprovante (imagem ~230 KB em base64).
//
//   ./k6/rodar.sh <ip> portal [perfil]
//
// Rodando de UMA máquina, todas as VUs partem do mesmo IP e caem no mesmo balde do nginx:
// com o limite REAL, o cenário vê 429 a partir de ~2 req/s no público — e isso é o resultado
// esperado do cenário `limites` (ESPELHO_LIMITES=reais). Para medir a APLICAÇÃO, o espelho
// roda com os limites afrouxados (infra/espelho/nginx-limites.sh; decisão nº 5 da spec).
//
// Cada iteração deixa uma pré-reserva na loja (PENDENTE; com comprovante fica EM_ANALISE e
// não expira sozinha). É sujeira aceitável num espelho descartável — não rode em produção.

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { BASE_URL, validarAlvo, LIMITES } from '../lib/config.js';
import { carregarCredenciais, credencialDaVU, tokenDe } from '../lib/auth.js';
import { FOTO_DATA_URL, horaLocal } from '../lib/jornadas.js';
import { inteiro } from '../lib/dados.js';
import { perfil, limitesDoPerfil, PERFIL } from '../perfis.js';

const CREDENCIAIS = carregarCredenciais();
const jornada = new Trend('jornada_portal_completa', true);
const reservas = new Counter('reservas_online');
const limitadas = new Counter('respostas_429');

export const options = {
  scenarios: { portal: perfil() },
  thresholds: limitesDoPerfil({ ...LIMITES.publico, ...LIMITES.escrita, jornada_portal_completa: ['p(95)<6000'] }, PERFIL),
  userAgent: 'k6-meujet-carga/1.0',
};

export function setup() {
  validarAlvo(CREDENCIAIS, 'portal');
  return {};
}

const publico = (nome) => ({ tags: { tipo: 'publico', name: nome } });
const conta = (r) => {
  if (r.status === 429) limitadas.add(1);
  return r;
};

export default function () {
  const credencial = credencialDaVU(CREDENCIAIS, 'portal');
  const loja = credencial.tenantSlug;
  const comecou = Date.now();

  // ---- vitrine, sem login ------------------------------------------------------------------
  const rLoja = conta(http.get(`${BASE_URL}/v1/public/lojas/${loja}`, publico('GET /public/lojas/{slug}')));
  if (!check(rLoja, { 'vitrine 200': (r) => r.status === 200 })) return;
  const rModelos = conta(http.get(`${BASE_URL}/v1/public/lojas/${loja}/modelos`, publico('GET /public/lojas/{slug}/modelos')));
  if (!check(rModelos, { 'modelos 200': (r) => r.status === 200 })) return;
  const lista = rModelos.json();
  const modelos = Array.isArray(lista) ? lista : lista.content || [];
  if (modelos.length === 0) return;
  const modeloId = modelos[inteiro(0, modelos.length - 1)].id;

  const inicio = new Date(Date.now() + (120 + inteiro(0, 600)) * 60 * 1000);
  const fim = new Date(inicio.getTime() + 60 * 60 * 1000);
  const rDisp = conta(http.get(`${BASE_URL}/v1/public/lojas/${loja}/disponibilidade?modeloId=${modeloId}&dataInicio=${horaLocal(inicio)}&dataFimPrevista=${horaLocal(fim)}`, publico('GET /public/lojas/{slug}/disponibilidade')));
  if (!check(rDisp, { 'disponibilidade 200': (r) => r.status === 200 })) return;

  // ---- reserva, com login -------------------------------------------------------------------
  const token = tokenDe(credencial);
  const auth = (nome) => ({ headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, tags: { tipo: 'escrita', name: nome } });
  const rReserva = http.post(
    `${BASE_URL}/v1/customers/reservas`,
    JSON.stringify({ lojaSlug: loja, modeloId, dataInicio: horaLocal(inicio), dataFimPrevista: horaLocal(fim), pagamentoTipo: 'SINAL', possuiCha: true, observacoes: 'CARGA — reserva sintética do k6' }),
    auth('POST /customers/reservas'),
  );
  if (!check(rReserva, { 'reserva 2xx': (r) => r.status < 300 })) return;
  const reservaId = rReserva.json('id');
  reservas.add(1);

  const rDetalhe = http.get(`${BASE_URL}/v1/customers/reservas/${reservaId}`, { ...auth('GET /customers/reservas/{id}'), tags: { tipo: 'leitura', name: 'GET /customers/reservas/{id}' } });
  check(rDetalhe, { 'detalhe 200': (r) => r.status === 200 });

  // Metade envia o comprovante na hora; a outra metade "vai pagar depois".
  if (inteiro(0, 1) === 1) {
    const valor = Number(rDetalhe.json('valorSinal') || rDetalhe.json('sinalValor') || 100);
    const rComp = http.post(
      `${BASE_URL}/v1/customers/reservas/${reservaId}/comprovante`,
      JSON.stringify({ tipo: 'SINAL', valorInformado: valor, contentType: 'image/png', dataBase64: FOTO_DATA_URL.split(',')[1] }),
      auth('POST /customers/reservas/{id}/comprovante'),
    );
    check(rComp, { 'comprovante 2xx': (r) => r.status < 300 });
  }
  jornada.add(Date.now() - comecou);
}
