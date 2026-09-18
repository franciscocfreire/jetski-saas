// O gancho da Praia Sintética é acessório: sem URL não faz nada; com URL, envia em lote e
// nunca derruba o motor quando a praia está fora do ar.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Praia, nomePeloEmail, type EventoPraia } from '../src/lib/praia.ts';

const passo = (n: number): Omit<EventoPraia, 'ts'> => ({ tipo: 'passo', rodada: 'r', jornada: `balcao#${n}`, passo: 'gerar GRU', quem: { id: 'x@exemplo.invalid', nome: 'X', papel: 'atendente' }, resultado: 'OK', ms: 12 });

test('sem PRAIA_URL nada é enfileirado nem enviado', async () => {
  let chamadas = 0;
  const p = new Praia(undefined, { enviar: async () => { chamadas++; } });
  assert.equal(p.ligada, false);
  p.emitir(passo(1));
  await p.encerrar();
  assert.equal(chamadas, 0);
  assert.equal(p.enviados, 0);
});

test('com URL, agrupa os eventos num lote e carimba o ts', async () => {
  const lotes: EventoPraia[][] = [];
  const p = new Praia('http://localhost:7331/', { intervaloMs: 5, enviar: async (l) => { lotes.push(l); } });
  assert.equal(p.url, 'http://localhost:7331');
  for (let i = 0; i < 3; i++) p.emitir(passo(i));
  await new Promise((ok) => setTimeout(ok, 30));
  assert.equal(lotes.length, 1);
  assert.equal(lotes[0].length, 3);
  assert.match(lotes[0][0].ts, /^\d{4}-\d{2}-\d{2}T/);
  assert.equal(p.enviados, 3);
});

test('praia fora do ar: avisa uma vez, não lança, e encerrar() desiste depois de 3 falhas', async () => {
  const avisos: string[] = [];
  const original = console.warn;
  console.warn = (m: string) => { avisos.push(String(m)); };
  try {
    const p = new Praia('http://127.0.0.1:1', { intervaloMs: 1, enviar: async () => { throw new Error('ECONNREFUSED'); } });
    p.emitir(passo(1));
    p.emitir(passo(2));
    await p.encerrar();
    assert.equal(p.enviados, 0);
    assert.equal(avisos.length, 1);
    assert.match(avisos[0], /o motor segue sem a praia/);
  } finally {
    console.warn = original;
  }
});

test('hora simulada: carimbada em cada evento quando o relógio do dia está ligado', async () => {
  const lotes: EventoPraia[][] = [];
  const p = new Praia('http://localhost:7331', { intervaloMs: 1, enviar: async (l) => { lotes.push(l); } });
  p.emitir(passo(1)); // antes de o dia abrir não há hora simulada
  p.horaSim = () => '11:42';
  p.emitir(passo(2));
  await p.encerrar();
  const todos = lotes.flat();
  assert.equal(todos[0].horaSim, undefined);
  assert.equal(todos[1].horaSim, '11:42');
});

test('nome pelo e-mail sintético', () => {
  assert.equal(nomePeloEmail('gerente.delegada-atol@exemplo.invalid'), 'gerente delegada-atol');
});
