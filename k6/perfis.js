// Perfis de carga compartilhados.
//
// A jornada (o que o cenário FAZ) e o perfil (com que intensidade) são coisas
// separadas de propósito: o mesmo `balcao.js` roda como smoke, como load, como
// stress e como soak, só mudando -e PERFIL=. Sem isto viram oito arquivos quase
// iguais que divergem no primeiro ajuste.
//
// Uso:  k6 run -e PERFIL=load k6/cenarios/balcao.js

const PERFIS = {
  /**
   * Portão de sanidade. Roda antes de tudo e no CI depois do deploy: se isto
   * falha, nenhum outro número importa.
   */
  smoke: {
    executor: 'constant-vus',
    vus: 1,
    duration: '2m',
  },

  /**
   * Carga esperada. O alvo padrão modela um sábado de manhã com algumas
   * locadoras operando ao mesmo tempo.
   *
   * ATENÇÃO ao calibrar: a linha de base de produção mostrou 0,30 req/s de
   * média e 3,59 req/s de PICO. Qualquer número aqui já é ordens de grandeza
   * acima do que a plataforma vê hoje — o objetivo é dimensionar o futuro, não
   * reproduzir o presente.
   */
  load: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '2m', target: Number(__ENV.VUS || 20) },   // subida suave
      { duration: '25m', target: Number(__ENV.VUS || 20) },  // patamar
      { duration: '3m', target: 0 },                          // descida
    ],
    gracefulRampDown: '30s',
  },

  /**
   * Rampa até quebrar. Procura o joelho da curva — o ponto onde a latência
   * dispara sem que a vazão suba junto.
   *
   * Aqui os thresholds são propositalmente DESLIGADOS (ver cenários): num
   * stress, estourar a meta é o resultado esperado, não uma falha de teste.
   */
  stress: {
    executor: 'ramping-arrival-rate',
    startRate: 1,
    timeUnit: '1s',
    preAllocatedVUs: 50,
    maxVUs: Number(__ENV.MAX_VUS || 300),
    stages: [
      { duration: '2m', target: 10 },
      { duration: '3m', target: 30 },
      { duration: '3m', target: 60 },
      { duration: '3m', target: 100 },
      { duration: '3m', target: 150 },
      { duration: '2m', target: 0 },
    ],
  },

  /**
   * Resistência. É o perfil mais valioso deste sistema: o histórico inclui um
   * vazamento de ThreadLocal do TenantContext que contaminava jobs agendados —
   * o tipo de defeito que só aparece depois de horas, nunca num teste de 5 min.
   *
   * O que olhar no fim: heap subindo sem voltar, conexões Hikari que não são
   * devolvidas, threads do Tomcat acumulando.
   */
  soak: {
    executor: 'constant-vus',
    vus: Number(__ENV.VUS || 10),
    duration: __ENV.DURACAO || '4h',
  },

  /**
   * Pico súbito: 8h da manhã de sábado, quando a praia abre e todo mundo chega
   * junto. Testa a subida a frio (pool vazio, cache frio, JIT ainda morno).
   */
  spike: {
    executor: 'ramping-arrival-rate',
    startRate: 1,
    timeUnit: '1s',
    preAllocatedVUs: 20,
    maxVUs: Number(__ENV.MAX_VUS || 200),
    stages: [
      { duration: '30s', target: 2 },
      { duration: '1m', target: Number(__ENV.PICO || 80) },  // o soco
      { duration: '3m', target: Number(__ENV.PICO || 80) },
      { duration: '1m', target: 2 },
    ],
  },
};

export const PERFIL = __ENV.PERFIL || 'smoke';

export function perfil(nome) {
  const escolhido = PERFIS[nome || PERFIL];
  if (!escolhido) {
    throw new Error(`Perfil desconhecido: "${nome || PERFIL}". Use: ${Object.keys(PERFIS).join(', ')}`);
  }
  return escolhido;
}

/** No stress a meta é achar o limite, então o threshold não deve reprovar. */
export function limitesDoPerfil(limites, nome) {
  return (nome || PERFIL) === 'stress' ? {} : limites;
}
