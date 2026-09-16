// Cenário BALCÃO — o caminho do dinheiro, de ponta a ponta.
//
// Modela o atendimento presencial: chega um cliente sem reserva, cadastra,
// faz check-in de walk-in num jetski livre, anda, e devolve (check-out).
// É o cenário de ESCRITA: cria cliente, cria locação, fecha locação.
//
//   k6 run -e PERFIL=load -e BASE_URL=... -e TENANT_ID=... k6/cenarios/balcao.js
//
// O que este cenário procura: contenção no pool de conexões (cada passo abre
// transação), lentidão no cálculo de cobrança (RN01) e degradação do check-out,
// que é o passo mais pesado.
//
// FOTOS: o check-out vai com `skipPhotos: true` de propósito. O upload é a
// operação mais cara do sistema e merece cenário próprio, com seu próprio
// perfil — misturar os dois produz um P95 que não explica nada.
//
// NÃO EMITE DOCUMENTO. Nenhum passo aqui chama /emissoes nem /gru: isso geraria
// GRU real na Marinha (que bloqueia por volume) e consumiria crédito de verdade.

import http from 'k6/http';
import { check, group, fail } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { BASE_URL, TENANT_ID, headers, validarAlvo, LIMITES } from '../lib/config.js';
import { carregarCredenciais, credencialDaVU, tokenDe } from '../lib/auth.js';
import { perfil, limitesDoPerfil, PERFIL } from '../perfis.js';
import { cliente, horimetro, duracaoPrevista, checklist, inteiro } from '../lib/dados.js';

const CREDENCIAIS = carregarCredenciais();

/** Tempo da jornada inteira — é o número que interessa ao negócio, não o do request. */
const jornada = new Trend('jornada_balcao_completa', true);
const jornadasOk = new Counter('jornadas_concluidas');
const semJetski = new Counter('jornadas_abortadas_sem_jetski');

export const options = {
  scenarios: { balcao: perfil() },
  thresholds: limitesDoPerfil({ ...LIMITES.escrita, ...LIMITES.jornada }, PERFIL),
  userAgent: 'k6-meujet-carga/1.0',
};

export function setup() {
  validarAlvo();
  return { base: `${BASE_URL}/v1/tenants/${TENANT_ID}` };
}

export default function (dados) {
  const credencial = credencialDaVU(CREDENCIAIS);
  const token = tokenDe(credencial);
  const params = { headers: headers(token), tags: { tipo: 'escrita' } };
  const leitura = { headers: headers(token), tags: { tipo: 'leitura' } };

  const comecou = Date.now();

  // ---- 1. Achar um jetski livre -------------------------------------------
  // Disponibilidade é regra de negócio (RN06): jetski em manutenção ou já em
  // uso não pode ser alocado. Se não houver nenhum livre, a jornada aborta —
  // e isso é um SINAL, não um erro: com muitas VUs a frota do tenant de carga
  // acaba. Aumente a frota no provisionamento em vez de ignorar o contador.
  const resJetskis = http.get(`${dados.base}/jetskis?status=DISPONIVEL`, {
    ...leitura,
    tags: { ...leitura.tags, name: 'GET /jetskis?status' },
  });
  if (!check(resJetskis, { 'lista de jetskis 200': (r) => r.status === 200 })) {
    return;
  }
  const corpo = resJetskis.json();
  const livres = corpo.content || corpo;
  if (!livres || livres.length === 0) {
    semJetski.add(1);
    return;
  }
  const jetski = livres[inteiro(0, livres.length - 1)];

  // ---- 2. Cadastrar o cliente ---------------------------------------------
  let clienteId;
  group('cadastrar cliente', () => {
    const res = http.post(`${dados.base}/clientes`, JSON.stringify(cliente()), {
      ...params,
      tags: { ...params.tags, name: 'POST /clientes' },
    });
    if (!check(res, { 'cliente criado 201': (r) => r.status === 201 || r.status === 200 })) {
      fail(`cadastro de cliente falhou: ${res.status} ${res.body}`);
    }
    clienteId = res.json('id');
  });

  // ---- 3. Check-in walk-in -------------------------------------------------
  let locacaoId;
  const horimetroInicio = horimetro();
  group('check-in', () => {
    const pedido = {
      jetskiId: jetski.id,
      clienteId,
      horimetroInicio,
      duracaoPrevista: duracaoPrevista(),
    };
    const res = http.post(`${dados.base}/locacoes/check-in/walk-in`, JSON.stringify(pedido), {
      ...params,
      tags: { ...params.tags, name: 'POST /locacoes/check-in/walk-in' },
    });
    // 400 aqui costuma ser deny de NEGÓCIO (jetski ocupado por outra VU no
    // meio do caminho), não falha de carga — 403 é que seria autorização.
    if (res.status === 400) {
      semJetski.add(1);
      return;
    }
    if (!check(res, { 'check-in 201': (r) => r.status === 201 || r.status === 200 })) {
      fail(`check-in falhou: ${res.status} ${res.body}`);
    }
    locacaoId = res.json('id');
  });

  if (!locacaoId) return;

  // ---- 4. Check-out --------------------------------------------------------
  group('check-out', () => {
    const pedido = {
      horimetroFim: Number((horimetroInicio + Math.random() * 2 + 0.5).toFixed(1)),
      observacoes: 'CARGA — devolução simulada',
      checklistEntradaJson: checklist(),
      skipPhotos: true, // fotos têm cenário próprio (ver o README)
    };
    const res = http.post(`${dados.base}/locacoes/${locacaoId}/check-out`, JSON.stringify(pedido), {
      ...params,
      tags: { ...params.tags, name: 'POST /locacoes/{id}/check-out' },
    });
    check(res, { 'check-out 200': (r) => r.status === 200 });
  });

  // ---- 5. Conferir o extrato (o operador sempre confere o valor) -----------
  group('extrato', () => {
    const res = http.get(`${dados.base}/locacoes/${locacaoId}/extrato`, {
      ...leitura,
      tags: { ...leitura.tags, name: 'GET /locacoes/{id}/extrato' },
    });
    check(res, { 'extrato 200': (r) => r.status === 200 });
  });

  jornada.add(Date.now() - comecou);
  jornadasOk.add(1);
}
