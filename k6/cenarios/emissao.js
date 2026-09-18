// Cenário EMISSÃO — o caminho mais pesado do produto (fase E5).
//
// Ficha do cliente com 3 documentos em base64 (~230 KB cada), reserva, habilitação EMA,
// termo assinado, GRU na Marinha SINTÉTICA, PIX pago pelo PagTesouro sintético, documentos
// emitidos (PDF, carimbo de tempo, MinIO, e-mail ao Mailpit). Cada iteração gasta 1 crédito.
//
//   ./k6/rodar.sh <ip> emissao [perfil]        (o runner liga o pagamento automático no fake)
//
// O que procura: pressão de memória (base64 → bytes → PDF), MinIO, threads do Tomcat presas
// nas chamadas à "Marinha" (20 s de timeout), e o custo do e-mail assíncrono.
//
// SÓ RODA COM `fakesVerificados` no tokens.json: o semeador, dentro da VM, provou que o backend
// fala com a Marinha sintética. Sem isso, validarAlvo recusa — GRU real nunca.

import { check, fail } from 'k6';
import http from 'k6/http';
import { Trend, Counter } from 'k6/metrics';
import { BASE_URL, validarAlvo, LIMITES } from '../lib/config.js';
import { carregarCredenciais, credencialDaVU, credenciaisDoTipo, tokenDe } from '../lib/auth.js';
import { emissaoCompleta, modeloDe } from '../lib/jornadas.js';
import { perfil, limitesDoPerfil, PERFIL } from '../perfis.js';

const CREDENCIAIS = carregarCredenciais();
const jornada = new Trend('jornada_emissao_completa', true);
const emitidas = new Counter('emissoes_concluidas');
const fallbackManual = new Counter('emissoes_fallback_manual');
const abortadas = new Counter('emissoes_abortadas');

export const options = {
  scenarios: { emissao: perfil() },
  thresholds: limitesDoPerfil(LIMITES.emissao, PERFIL),
  userAgent: 'k6-meujet-carga/1.0',
};

/** Créditos: cada emissão debita 1. Antes de começar, a operadora de plataforma recarrega quem está baixo. */
export function setup() {
  validarAlvo(CREDENCIAIS, 'emissao');
  if (CREDENCIAIS.fakesVerificados !== true) fail('tokens.json sem fakesVerificados — o cenário de emissão só roda com a Marinha sintética provada.');
  const operadora = credenciaisDoTipo(CREDENCIAIS, 'plataforma')[0];
  const tokenOp = tokenDe(operadora);
  const minimo = Number(__ENV.CREDITOS_MINIMO || 300);
  for (const c of credenciaisDoTipo(CREDENCIAIS, 'emissao')) {
    const saldos = http.get(`${BASE_URL}/v1/platform/creditos`, { headers: { Authorization: `Bearer ${tokenOp}` }, tags: { tipo: 'setup' } }).json();
    const atual = (Array.isArray(saldos) ? saldos : saldos.content || []).find((s) => s.tenantId === c.tenantId);
    const saldo = atual ? Number(atual.saldo) : 0;
    if (saldo < minimo) {
      const r = http.post(
        `${BASE_URL}/v1/platform/creditos/${c.tenantId}`,
        JSON.stringify({ quantidade: minimo, motivo: 'CARGA — recarga para o cenário k6 de emissão (espelho)', tipo: 'CORTESIA' }),
        { headers: { Authorization: `Bearer ${tokenOp}`, 'Content-Type': 'application/json' }, tags: { tipo: 'setup' } },
      );
      check(r, { 'recarga de créditos 2xx': (x) => x.status < 300 }) || fail(`recarga de créditos de ${c.tenantSlug} falhou: ${r.status} ${r.body}`);
      console.log(`${c.tenantSlug}: saldo ${saldo} → +${minimo} créditos (cortesia da operadora de plataforma)`);
    }
  }
  return {};
}

let modeloId = null;

export default function () {
  const credencial = credencialDaVU(CREDENCIAIS, 'emissao');
  const token = tokenDe(credencial);
  if (!modeloId) modeloId = modeloDe(credencial, token) || fail(`${credencial.tenantSlug} sem modelo`);

  const comecou = Date.now();
  const r = emissaoCompleta(credencial, { token, modeloId, anexos: Number(__ENV.ANEXOS || 3) });
  if (r.ok) {
    emitidas.add(1);
    jornada.add(Date.now() - comecou);
  } else if (r.gruFalhou) {
    fallbackManual.add(1); // Marinha/PagTesouro sintéticos recusaram: fluxo manual, não erro
  } else {
    abortadas.add(1);
  }
}
