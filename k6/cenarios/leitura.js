// Cenário LEITURA — o grosso do tráfego real do backoffice.
//
// Modela o que um operador faz o dia todo: abre o controle do dia, confere a
// agenda, procura um cliente, olha a frota. É leitura pura — nenhuma escrita,
// nenhum efeito colateral — então é o cenário mais seguro para rodar primeiro,
// inclusive em produção.
//
//   k6 run -e PERFIL=smoke -e BASE_URL=... -e TENANT_ID=... k6/cenarios/leitura.js
//
// O que este cenário procura: saturação de pool de conexões e de threads do
// Tomcat sob leitura concorrente, e queries que degradam com volume.

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { BASE_URL, TENANT_ID, headers, validarAlvo, LIMITES } from '../lib/config.js';
import { carregarCredenciais, credencialDaVU, tokenDe } from '../lib/auth.js';
import { perfil, limitesDoPerfil, PERFIL } from '../perfis.js';

// Contexto de init: é aqui que `open()` pode ser chamado.
const CREDENCIAIS = carregarCredenciais();

export const options = {
  scenarios: { leitura: perfil() },
  thresholds: limitesDoPerfil(LIMITES.leitura, PERFIL),
  // Um teste de carga não deve fingir ser um navegador diferente a cada request.
  userAgent: 'k6-meujet-carga/1.0',
};

export function setup() {
  validarAlvo();
  return { base: `${BASE_URL}/v1/tenants/${TENANT_ID}` };
}

export default function (dados) {
  const credencial = credencialDaVU(CREDENCIAIS);
  const token = tokenDe(credencial);
  const params = { headers: headers(token), tags: { tipo: 'leitura' } };

  group('abrir o dia', () => {
    // A primeira tela que o operador abre de manhã. É a consulta mais pesada
    // do backoffice: junta locações em curso, reservas do dia e frota.
    const res = http.get(`${dados.base}/locacoes/controle-do-dia`, {
      ...params,
      tags: { ...params.tags, name: 'GET /locacoes/controle-do-dia' },
    });
    check(res, { 'controle-do-dia 200': (r) => r.status === 200 });
  });

  sleep(1);

  group('conferir agenda e frota', () => {
    const respostas = http.batch([
      ['GET', `${dados.base}/reservas/agenda`, null, { ...params, tags: { ...params.tags, name: 'GET /reservas/agenda' } }],
      ['GET', `${dados.base}/jetskis`, null, { ...params, tags: { ...params.tags, name: 'GET /jetskis' } }],
      ['GET', `${dados.base}/locacoes?page=0&size=20`, null, { ...params, tags: { ...params.tags, name: 'GET /locacoes' } }],
    ]);
    respostas.forEach((r) => check(r, { 'lista 200': (x) => x.status === 200 }));
  });

  sleep(2);

  group('procurar cliente', () => {
    // Busca pelo prefixo que o gerador de dados carimba: encontra o que o
    // cenário de balcão criou, e nada de tenant nenhum além deste.
    const res = http.get(`${dados.base}/clientes?busca=CARGA&page=0&size=20`, {
      ...params,
      tags: { ...params.tags, name: 'GET /clientes?busca' },
    });
    check(res, { 'busca de cliente 200': (r) => r.status === 200 });
  });

  // Pausa entre ciclos: gente de verdade não dispara requests em loop fechado.
  // Sem isto o teste mede o limite do gerador, não o do servidor.
  sleep(Math.random() * 3 + 2);
}
