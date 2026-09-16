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

/**
 * Data de hoje no fuso da operação (America/Sao_Paulo, UTC−3 fixo — o Brasil
 * não tem horário de verão desde 2019). `controle-do-dia` e `agenda` EXIGEM o
 * parâmetro `data`: sem ele a API devolve 400 e o cenário mediria só erro.
 */
function hojeEmSaoPaulo() {
  return new Date(Date.now() - 3 * 3600 * 1000).toISOString().slice(0, 10);
}

export default function (dados) {
  const credencial = credencialDaVU(CREDENCIAIS);
  const token = tokenDe(credencial);
  const params = { headers: headers(token), tags: { tipo: 'leitura' } };
  const hoje = hojeEmSaoPaulo();

  group('abrir o dia', () => {
    // A primeira tela que o operador abre de manhã. É a consulta mais pesada
    // do backoffice: junta locações em curso, reservas do dia e frota.
    const res = http.get(`${dados.base}/locacoes/controle-do-dia?data=${hoje}`, {
      ...params,
      tags: { ...params.tags, name: 'GET /locacoes/controle-do-dia' },
    });
    check(res, { 'controle-do-dia 200': (r) => r.status === 200 });
  });

  sleep(1);

  // ATENÇÃO ao ler os resultados: /jetskis, /clientes e /locacoes devolvem a
  // lista INTEIRA do tenant, sem paginação (List<>, não Page<>). Rodando junto
  // com o cenário de balcão, que cria cliente e locação a cada jornada, essas
  // respostas crescem sem parar — latência subindo ao longo de um soak aqui é
  // primeiro suspeita de lista sem limite, e só depois de vazamento.
  group('conferir agenda e frota', () => {
    const respostas = http.batch([
      ['GET', `${dados.base}/reservas/agenda?data=${hoje}`, null, { ...params, tags: { ...params.tags, name: 'GET /reservas/agenda' } }],
      ['GET', `${dados.base}/jetskis`, null, { ...params, tags: { ...params.tags, name: 'GET /jetskis (lista inteira)' } }],
      ['GET', `${dados.base}/locacoes`, null, { ...params, tags: { ...params.tags, name: 'GET /locacoes (lista inteira)' } }],
    ]);
    respostas.forEach((r) => check(r, { 'lista 200': (x) => x.status === 200 }));
  });

  sleep(2);

  group('abrir clientes', () => {
    // Não há busca textual na API (o único filtro é `cpf`); o operador abre a
    // lista e filtra na tela — então é a lista inteira que o servidor paga.
    const res = http.get(`${dados.base}/clientes`, {
      ...params,
      tags: { ...params.tags, name: 'GET /clientes (lista inteira)' },
    });
    check(res, { 'lista de clientes 200': (r) => r.status === 200 });
  });

  // Pausa entre ciclos: gente de verdade não dispara requests em loop fechado.
  // Sem isto o teste mede o limite do gerador, não o do servidor.
  sleep(Math.random() * 3 + 2);
}
