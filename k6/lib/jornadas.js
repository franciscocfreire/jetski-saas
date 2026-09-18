// Jornadas reutilizadas por mais de um cenário (E5): a emissão EMA completa e as leituras
// do balcão. Ficam aqui para que `emissao.js` (capacidade) e `resiliencia.js` (falha externa)
// exerçam EXATAMENTE os mesmos passos — o que muda é a intensidade e o que se mede.

import http from 'k6/http';
import { check, fail } from 'k6';
import encoding from 'k6/encoding';
import { BASE_URL, headers, tenantDe } from './config.js';
import { credenciaisDoTipo, tokenDe } from './auth.js';
import { cliente as clienteDeCarga, inteiro } from './dados.js';

// Fixtures binárias no init (open() só existe aqui); base64 uma vez por VU. import.meta.resolve
// resolve pelo MÓDULO em qualquer versão do k6 (o open() puro resolvia pela pasta do cenário).
const FOTO = encoding.b64encode(open(import.meta.resolve('./fixtures/foto-sintetica.png'), 'b'));
const ASSINATURA = encoding.b64encode(open(import.meta.resolve('./fixtures/assinatura-sintetica.png'), 'b'));
export const FOTO_DATA_URL = `data:image/png;base64,${FOTO}`;
const ASSINATURA_DATA_URL = `data:image/png;base64,${ASSINATURA}`;

/** LocalDateTime no fuso da loja (UTC-3). */
export function horaLocal(d) {
  return new Date(d.getTime() - 3 * 3600 * 1000).toISOString().slice(0, 19);
}

/**
 * Emissão EMA de ponta a ponta, como o balcão faz: ficha do cliente com documentos → reserva →
 * habilitação EMA → termo assinado → GRU (o PagTesouro sintético paga sozinho: o runner liga
 * `autoPagarAposSeg: 0`) → confirmação do pagamento → documentos à Marinha.
 *
 * Devolve `{ ok, gruFalhou, passo }`. `gruFalhou` NÃO é erro do sistema: é o fallback manual
 * funcionando (Marinha fora do ar) — o cenário de resiliência conta isso como sucesso.
 */
export function emissaoCompleta(credencial, opcoes) {
  const o = Object.assign({ anexos: 3, tentativasPagamento: 3, tag: 'emissao' }, opcoes);
  const tenant = tenantDe(credencial);
  const base = `${BASE_URL}/v1/tenants/${tenant}`;
  const params = (nome) => ({ headers: headers(o.token, tenant), tags: { tipo: o.tag, name: nome } });
  const okStatus = (r, nome) => check(r, { [`${nome} 2xx`]: (x) => x.status >= 200 && x.status < 300 });

  const dados = clienteDeCarga();
  dados.enderecoJson = JSON.stringify({ cep: '11095460', logradouro: 'Rua Sintética', numero: String(inteiro(1, 999)), bairro: 'Centro', cidade: 'Santos', uf: 'SP' });
  dados.nacionalidade = 'Brasileira';
  dados.naturalidade = 'Santos/SP';
  const rCliente = http.post(`${base}/clientes`, JSON.stringify(dados), params('POST /clientes'));
  if (!okStatus(rCliente, 'cliente')) return { ok: false, passo: 'cliente' };
  const clienteId = rCliente.json('id');

  for (const tipo of ['IDENTIDADE', 'SELFIE', 'COMPROVANTE_RESIDENCIA'].slice(0, o.anexos)) {
    const r = http.put(`${base}/clientes/${clienteId}/anexos/${tipo}`, JSON.stringify({ conteudoBase64: FOTO_DATA_URL }), params('PUT /clientes/{id}/anexos/{tipo}'));
    if (!okStatus(r, 'anexo')) return { ok: false, passo: `anexo ${tipo}` };
  }

  const inicio = new Date(Date.now() + (60 + inteiro(0, 240)) * 60 * 1000);
  const rReserva = http.post(
    `${base}/reservas`,
    JSON.stringify({ modeloId: o.modeloId, clienteId, dataInicio: horaLocal(inicio), dataFimPrevista: horaLocal(new Date(inicio.getTime() + 3600 * 1000)), observacoes: 'CARGA — emissão sintética' }),
    params('POST /reservas'),
  );
  if (!okStatus(rReserva, 'reserva')) return { ok: false, passo: 'reserva' };
  const reservaId = rReserva.json('id');

  const rHab = http.put(
    `${base}/reservas/${reservaId}/habilitacao`,
    JSON.stringify({ via: 'EMA', videoaulaAssistida: true, videoaulaModo: 'DECLARACAO', videoaulaIdioma: 'pt', anexoSaude: true, anexoRegras: true, anexoResidencia: true, usaLentes: false, usaAparelho: false, instrutorId: credencial.instrutorId }),
    params('PUT /reservas/{id}/habilitacao'),
  );
  if (!okStatus(rHab, 'habilitação')) return { ok: false, passo: 'habilitação' };

  const rAceite = http.post(`${base}/reservas/${reservaId}/aceite`, JSON.stringify({ metodo: 'SIGNATURE_PAD', assinaturaBase64: ASSINATURA_DATA_URL }), params('POST /reservas/{id}/aceite'));
  if (!okStatus(rAceite, 'aceite')) return { ok: false, passo: 'aceite' };

  // A GRU atravessa a Marinha e o PagTesouro sintéticos. Falha deles NÃO é 5xx: o backend devolve
  // sucesso=false + erroCodigo e o operador segue pelo fluxo manual — é o comportamento certo.
  const rGru = http.post(`${base}/reservas/${reservaId}/habilitacao/gru`, null, params('POST /reservas/{id}/habilitacao/gru'));
  if (!okStatus(rGru, 'gru')) return { ok: false, passo: 'gru' };
  if (rGru.json('sucesso') !== true) return { ok: false, gruFalhou: true, erroCodigo: rGru.json('erroCodigo'), passo: 'gru' };

  let pago = false;
  for (let i = 0; i < o.tentativasPagamento && !pago; i++) {
    const r = http.post(`${base}/reservas/${reservaId}/habilitacao/gru/verificar-pagamento`, null, params('POST .../gru/verificar-pagamento'));
    if (!okStatus(r, 'verificar pagamento')) return { ok: false, passo: 'verificar pagamento' };
    pago = r.json('pago') === true;
  }
  if (!pago) return { ok: false, passo: 'gru não paga (fake sem autoPagarAposSeg?)' };

  const rEmitir = http.post(`${base}/reservas/${reservaId}/emitir-documentos`, null, params('POST /reservas/{id}/emitir-documentos'));
  if (!okStatus(rEmitir, 'emitir')) return { ok: false, passo: 'emitir' };
  return { ok: true, passo: 'fim' };
}

/** Leituras típicas de um operador olhando as telas — o "tráfego inocente" da resiliência. */
export function leiturasDoBalcao(credencial, token, tag) {
  const tenant = tenantDe(credencial);
  const base = `${BASE_URL}/v1/tenants/${tenant}`;
  const hoje = horaLocal(new Date()).slice(0, 10);
  const params = (nome) => ({ headers: headers(token, tenant), tags: { tipo: tag || 'leitura', name: nome } });
  const rotas = [
    ['GET /locacoes/controle-do-dia', `${base}/locacoes/controle-do-dia?data=${hoje}`],
    ['GET /reservas/agenda', `${base}/reservas/agenda?data=${hoje}`],
    ['GET /jetskis', `${base}/jetskis`],
    ['GET /clientes', `${base}/clientes`],
  ];
  let ok = true;
  for (const [nome, url] of rotas) {
    const r = http.get(url, params(nome));
    ok = check(r, { [`${nome} 200`]: (x) => x.status === 200 }) && ok;
  }
  return ok;
}

/**
 * Recarga de créditos pela operadora de plataforma (cortesia), dimensionada pelo perfil: um smoke
 * gasta dezenas; load/stress/soak gastam milhares. Sem isto o crédito vira um limite oculto e o
 * cenário mede 'abortadas por saldo' como se fosse comportamento da aplicação.
 */
export function recarregarCreditos(credenciais, perfil) {
  const minimo = Number(__ENV.CREDITOS_MINIMO || (perfil === 'smoke' ? 300 : 5000));
  const operadora = credenciaisDoTipo(credenciais, 'plataforma')[0];
  const tokenOp = tokenDe(operadora);
  for (const c of credenciaisDoTipo(credenciais, 'emissao')) {
    const rSaldos = http.get(`${BASE_URL}/v1/platform/creditos`, { headers: { Authorization: `Bearer ${tokenOp}` }, tags: { tipo: 'setup' } });
    if (rSaldos.status !== 200) fail(`saldos de créditos: HTTP ${rSaldos.status}`);
    const saldos = rSaldos.json();
    const atual = (Array.isArray(saldos) ? saldos : saldos.content || []).find((s) => s.tenantId === c.tenantId);
    const saldo = atual ? Number(atual.saldo) : 0;
    if (saldo >= minimo) continue;
    const r = http.post(
      `${BASE_URL}/v1/platform/creditos/${c.tenantId}`,
      JSON.stringify({ quantidade: minimo, motivo: `CARGA — recarga para o k6 (${perfil}) no espelho`, tipo: 'CORTESIA' }),
      { headers: { Authorization: `Bearer ${tokenOp}`, 'Content-Type': 'application/json' }, tags: { tipo: 'setup' } },
    );
    if (r.status < 200 || r.status >= 300) fail(`recarga de créditos de ${c.tenantSlug} falhou: ${r.status} ${r.body}`);
    console.log(`${c.tenantSlug}: saldo ${saldo} → +${minimo} créditos (cortesia da operadora de plataforma)`);
  }
}

/** Primeiro modelo da empresa — uma vez por VU. */
export function modeloDe(credencial, token) {
  const tenant = tenantDe(credencial);
  const r = http.get(`${BASE_URL}/v1/tenants/${tenant}/modelos`, { headers: headers(token, tenant), tags: { tipo: 'leitura', name: 'GET /modelos' } });
  const lista = r.json();
  const modelos = Array.isArray(lista) ? lista : lista.content || [];
  return modelos.length ? modelos[0].id : null;
}
