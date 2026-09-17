// Fase E4: o que se confere sem o espelho — o acaso reproduzível, o relógio comprimido, as
// medidas e a coerência do cenário. O teste de integração é a própria rodada (`motor.sh`).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { lerCatalogoEmissao } from '../src/emissao.ts';
import { Acaso, Medidas, Relogio, minutosDoDia, sortearChegadas } from '../src/motor/base.ts';
import type { Cenario } from '../src/motor/jornadas.ts';

const cenario = JSON.parse(readFileSync(new URL('../catalogo/e4-sabado.json', import.meta.url), 'utf8')) as Cenario;

test('acaso: a mesma semente repete as mesmas decisões; filhos são independentes da ordem de uso', () => {
  const serie = (s: number) => {
    const a = new Acaso(s);
    return [a.proximo(), a.chance(0.5), a.inteiro(1, 6), a.ponderado({ x: 1, y: 3 }), a.escolher(['a', 'b', 'c'])];
  };
  assert.deepEqual(serie(42), serie(42));
  assert.notDeepEqual(serie(42), serie(43));

  const pai1 = new Acaso(7);
  const [f1, f2] = [pai1.filho(), pai1.filho()];
  const pai2 = new Acaso(7);
  const [g1, g2] = [pai2.filho(), pai2.filho()];
  g2.proximo(); // outra jornada andou primeiro: não muda o que a primeira vai sortear
  assert.equal(f1.proximo(), g1.proximo());
  assert.notEqual(f1.proximo(), f2.proximo());
});

test('acaso: o sorteio ponderado respeita as proporções', () => {
  const a = new Acaso(1);
  const n = { portal: 0, balcao: 0 };
  for (let i = 0; i < 20_000; i++) n[a.ponderado(cenario.canal) as 'portal' | 'balcao']++;
  assert.ok(Math.abs(n.portal / 20_000 - cenario.canal.portal) < 0.02, `portal=${n.portal / 20_000}`);
});

test('relógio: 12× — 60 min simulados são 5 min reais; hora legível', () => {
  const r = new Relogio(minutosDoDia('09:00'), 12);
  assert.ok(Math.abs(r.msAte(600) - 5 * 60_000) < 50); // 10:00 está a 5 min reais
  assert.equal(r.msAte(500), 0, 'o passado não espera');
  assert.ok(Math.abs(r.real(600).getTime() - Date.now() - 5 * 60_000) < 50);
  assert.equal(Relogio.hhmm(570.4), '09:30');
  assert.equal(minutosDoDia('18:00'), 1080);
});

test('chegadas: dentro do expediente, em ordem, seguindo a curva', () => {
  const chegadas = sortearChegadas(new Acaso(3), 2_000, cenario.curvaPorHora);
  assert.equal(chegadas.length, 2_000);
  assert.ok(chegadas.every((c, i) => i === 0 || c >= chegadas[i - 1]));
  assert.ok(chegadas[0] >= minutosDoDia(cenario.dia.abre) && (chegadas.at(-1) as number) < minutosDoDia(cenario.dia.fecha));
  const das11 = chegadas.filter((c) => Math.floor(c / 60) === 11).length / 2_000;
  assert.ok(Math.abs(das11 - cenario.curvaPorHora['11']) < 0.03, `11h=${das11}`);
});

test('medidas: desfechos, percentis, relatório e exposição Prometheus', () => {
  const m = new Medidas();
  m.desfecho('portal', 'passeou');
  m.desfecho('portal', 'passeou');
  m.desfecho('portal', 'no-show');
  m.contar('locacoes', 2);
  for (const ms of [100, 200, 300, 400, 1000]) m.latencia('check-in', ms);
  m.falhas.push({ quando: '10:00', jornada: 'balcao#3', passo: 'check-out', erro: 'HTTP 500' });
  assert.equal(Medidas.percentil([100, 200, 300, 400, 1000], 50), 300);
  assert.equal(Medidas.percentil([100, 200, 300, 400, 1000], 95), 1000);
  const r = m.relatorio();
  assert.deepEqual(r.jornadas, { portal: { passeou: 2, 'no-show': 1 } });
  assert.deepEqual(r.passos['check-in'], { n: 5, p50ms: 300, p95ms: 1000, maxMs: 1000 });
  const prom = m.prometheus();
  assert.match(prom, /motor_jornadas_total\{jornada="portal",desfecho="passeou"\} 2/);
  assert.match(prom, /motor_passo_seconds\{passo="check-in",quantile="0\.95"\} 1\.000/);
  assert.match(prom, /motor_falhas_total 1/);
  assert.match(prom, /motor_locacoes_total 2/);
});

test('cenário do sábado: probabilidades válidas e lojas que existem no catálogo de emissão', () => {
  const soma = (o: Record<string, number>) => Object.values(o).reduce((a, b) => a + b, 0);
  for (const [nome, dist] of Object.entries({ curva: cenario.curvaPorHora, canal: cenario.canal, notas: cenario.portal.notas, duracao: cenario.passeio.duracaoMin, forma: cenario.passeio.formaDePagamento })) {
    assert.ok(Math.abs(soma(dist) - 1) < 1e-9, `${nome} soma ${soma(dist)}`);
  }
  for (const p of [cenario.portal.clienteNovo, cenario.portal.pagaSinal, cenario.portal.naoComparece, cenario.portal.avalia, cenario.balcao.clienteNovo, cenario.balcao.temCha, cenario.balcao.pagaGru, cenario.balcao.comVendedor, cenario.passeio.atrasaDevolucao, cenario.manutencao.chancePorLoja]) {
    assert.ok(p >= 0 && p <= 1);
  }
  const horas = Object.keys(cenario.curvaPorHora).map(Number);
  assert.ok(Math.min(...horas) * 60 >= minutosDoDia(cenario.dia.abre) && (Math.max(...horas) + 1) * 60 <= minutosDoDia(cenario.dia.fecha));

  const emissao = lerCatalogoEmissao();
  const empresas = new Set([emissao.eama.chave, ...emissao.delegadas.map((d) => d.chave)]);
  const pessoas = new Set([...empresas, ...emissao.delegadas.flatMap((d) => d.equipe.map((m) => m.chave))]);
  for (const loja of cenario.lojas) {
    assert.ok(empresas.has(loja.chave), `${loja.chave} não é empresa do catálogo E3b`);
    for (const [papel, chave] of Object.entries(loja.papeis)) assert.ok(pessoas.has(chave), `${loja.chave}.${papel} → ${chave} não existe`);
  }
  // Quem opera o pier na Atol é o OPERADOR da equipe; quem fecha o dia é o FINANCEIRO — não o admin.
  const atol = cenario.lojas.find((l) => l.chave === 'delegada-atol');
  assert.deepEqual([atol?.papeis.atendente, atol?.papeis.financeiro, atol?.papeis.mecanico], ['atol-operador', 'atol-financeiro', 'atol-mecanico']);
});
