// Cenário RESILIÊNCIA — o mundo lá fora quebra; o que acontece aqui dentro? (fase E5)
//
// Dois fluxos ao mesmo tempo, durante uma falha externa injetada pelo /_controle dos fakes
// (o runner liga e desliga: `./k6/rodar.sh <ip> resiliencia --modo marinha-fora|pagtesouro-lento|pagtesouro-pendurado`):
//
//   emissao   — balcão tentando emitir EMA enquanto a Marinha/PagTesouro falham;
//   inocente  — operadores olhando telas que NÃO têm nada a ver com a Marinha.
//
// O que se exige (thresholds):
//   1. a emissão NÃO vira 5xx: o backend responde sucesso=false + erroCodigo e o operador segue
//      pelo fluxo manual (`emissoes_fallback_manual` > 0 no modo marinha-fora);
//   2. o tráfego inocente continua dentro da meta de leitura — numa aplicação monolítica, threads
//      do Tomcat presas 20 s cada numa "Marinha" pendurada são o jeito clássico de um sistema
//      externo derrubar o que não depende dele.
//
// `--modo nenhum` é o controle: os mesmos fluxos sem falha injetada, para comparar.

import { Counter, Trend } from 'k6/metrics';
import { fail } from 'k6';
import { LIMITES, validarAlvo } from '../lib/config.js';
import { carregarCredenciais, credencialDaVU, tokenDe } from '../lib/auth.js';
import { emissaoCompleta, leiturasDoBalcao, modeloDe } from '../lib/jornadas.js';

const CREDENCIAIS = carregarCredenciais();
const MODO = __ENV.MODO || 'nenhum';
const DURACAO = __ENV.DURACAO || '4m';

const emitidas = new Counter('emissoes_concluidas');
const fallbackManual = new Counter('emissoes_fallback_manual');
const abortadas = new Counter('emissoes_abortadas');
const jornada = new Trend('jornada_emissao_completa', true);

export const options = {
  scenarios: {
    emissao: { executor: 'constant-vus', vus: Number(__ENV.VUS_EMISSAO || 4), duration: DURACAO, exec: 'emissao' },
    inocente: { executor: 'constant-vus', vus: Number(__ENV.VUS_INOCENTE || 6), duration: DURACAO, exec: 'inocente' },
  },
  thresholds: {
    // O inocente é a medida que importa: a mesma meta de sempre, com a Marinha caída ao lado.
    'http_req_failed{tipo:inocente}': ['rate<0.01'],
    'http_req_duration{tipo:inocente}': LIMITES.leitura['http_req_duration{tipo:leitura}'],
    // A emissão pode demorar (timeout de 20 s na Marinha pendurada), mas não pode ERRAR.
    'http_req_failed{tipo:emissao}': ['rate<0.01'],
    ...(MODO === 'marinha-fora' ? { emissoes_fallback_manual: ['count>0'], emissoes_concluidas: ['count==0'] } : {}),
    ...(MODO === 'nenhum' ? { emissoes_concluidas: ['count>0'] } : {}),
  },
  userAgent: 'k6-meujet-carga/1.0',
};

export function setup() {
  validarAlvo(CREDENCIAIS, 'emissao');
  if (CREDENCIAIS.fakesVerificados !== true) fail('tokens.json sem fakesVerificados — resiliência só roda com a Marinha sintética provada.');
  console.log(`modo de falha: ${MODO} (injetado pelo runner via /_controle)`);
  return {};
}

let modeloId = null;

export function emissao() {
  const credencial = credencialDaVU(CREDENCIAIS, 'emissao');
  const token = tokenDe(credencial);
  if (!modeloId) modeloId = modeloDe(credencial, token) || fail(`${credencial.tenantSlug} sem modelo`);
  const comecou = Date.now();
  const r = emissaoCompleta(credencial, { token, modeloId, anexos: 1, tag: 'emissao' });
  if (r.ok) {
    emitidas.add(1);
    jornada.add(Date.now() - comecou);
  } else if (r.gruFalhou) fallbackManual.add(1);
  else abortadas.add(1);
}

export function inocente() {
  // Os operadores das empresas de CARGA: outra empresa, outro fluxo, nada a ver com a Marinha.
  const credencial = credencialDaVU(CREDENCIAIS, 'carga');
  leiturasDoBalcao(credencial, tokenDe(credencial), 'inocente');
}
