// Motor de comportamento — fase E4 do ECOSSISTEMA_SINTETICO_SPEC.md: "um sábado sintético".
//
// Lê o cenário (catalogo/e4-sabado.json) e o estado das personas e faz o dia acontecer pelas
// APIs reais, com o relógio comprimido: clientes reservam pelo portal e chegam no balcão, a
// loja confirma sinal, emite documentos à Marinha (sintética), opera o pier, faz manutenção
// e fecha o dia. Roda DE FORA da VM (use ../motor.sh, que abre os túneis e traz o estado).
//
//   node src/motor.ts dia      roda o cenário inteiro e grava o relatório
//
// Ambiente: DOMINIO (obrigatório), ESTADO (cópia local do personas.json), MAILPIT_URL,
// FAKES_URL, CENARIO, SEMENTE, CHEGADAS e FATOR (sobrepõem o cenário), RELATORIO (arquivo),
// METRICAS_PORTA (padrão 9464; 0 desliga), PRAIA_URL (opcional: manda cada passo à Praia
// Sintética, a visualização em forma de jogo — ver src/lib/praia.ts).

import { createServer } from 'node:http';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { lerCatalogoEmissao } from './emissao.ts';
import { ArquivoDeEstado, type EstadoPersona } from './lib/estado.ts';
import { dataUrl, fotoSintetica } from './lib/imagem.ts';
import { Plataforma } from './lib/plataforma.ts';
import { Acaso, Medidas, Relogio, minutosDoDia, sortearChegadas } from './motor/base.ts';
import { JornadaBalcao, JornadaPortal, Loja, RotinaDaLoja, type Cenario, type Dia } from './motor/jornadas.ts';
import { Sessoes } from './motor/sessoes.ts';
import { Praia } from './lib/praia.ts';

const DOMINIO = process.env.DOMINIO ?? '';
if (!DOMINIO) throw new Error('Defina DOMINIO (ex.: jetsave.com.br).');
// A mesma trava do semeador, do k6 e do preflight: personas sintéticas não agem em produção.
if (/(^|\.)meujet\.com\.br$/.test(DOMINIO)) throw new Error(`${DOMINIO} é PRODUÇÃO. O motor só roda no espelho.`);

const log = (msg: string) => console.log(`[motor ${relogio?.hora() ?? '--:--'}] ${msg}`);
let relogio: Relogio | undefined;

async function prepararLoja(d: Dia, def: Cenario['lojas'][number]): Promise<Loja> {
  const { api, arquivo, cenario, sessoes } = d;
  const persona = (chave: string): EstadoPersona => {
    const p = arquivo.estado.personas[chave];
    if (!p?.senha) throw new Error(`persona ${chave} não está pronta no estado — rode "semeador semear" no espelho.`);
    return p;
  };
  const papeis = Object.fromEntries(Object.entries(def.papeis).map(([papel, chave]) => [papel, persona(chave)]));
  const loja = new Loja(def.chave, persona(def.chave), papeis);
  for (const [papel, p] of Object.entries(papeis)) {
    const nome = d.nomes.get(p.email);
    if (nome) loja.nomes[papel] = nome;
  }
  const gerente = await sessoes.staff(papeis.gerente.email, papeis.gerente.senha as string);
  const t = loja.tenantId;

  const emissao = lerCatalogoEmissao();
  const modelos = await api.listarModelos(gerente, t);
  loja.modeloId = (modelos.find((m) => m.nome === emissao.modelo.nome) ?? modelos[0]).id;

  // Vendedor é cadastro próprio da loja (sem vínculo com usuário). Quem cadastra é o ADMIN: o
  // @PreAuthorize aceita GERENTE, mas o OPA nega `vendedor:create` a ele (403) — o OPA decide.
  const admin = await sessoes.staff(papeis.admin.email, papeis.admin.senha as string);
  const vendedores = await api.naEmpresa<{ id: string; nome: string }[] | { content: { id: string; nome: string }[] }>('GET', admin, t, '/vendedores');
  const lista = Array.isArray(vendedores) ? vendedores : vendedores.content;
  for (const nome of def.vendedores) {
    loja.vendedores.push(lista.find((v) => v.nome === nome)?.id ?? (await api.naEmpresa<{ id: string }>('POST', admin, t, '/vendedores', { nome, tipo: 'INTERNO', comissaoPercentual: 10 })).id);
  }

  // Instrutor da emissão: o próprio (EAMA) ou um designado pela EAMA (delegada).
  const proprios = Object.values(persona(def.chave).instrutores ?? {});
  if (def.chave === emissao.eama.chave) loja.instrutorId = proprios[0].id as string;
  else {
    const elegiveis = await api.naEmpresa<{ id: string; origem: string }[]>('GET', gerente, t, '/vinculos-emissao/instrutores-parceiro');
    loja.instrutorId = (elegiveis.find((i) => i.origem === 'EAMA') ?? elegiveis[0]).id;
  }

  for (const c of emissao.clientesBalcao.filter((x) => x.empresa === def.chave)) {
    const p = arquivo.estado.personas[c.chave];
    if (p?.clienteId && p.cpf) {
      loja.recorrentes.push(p);
      loja.nomes[p.clienteId] = c.nome;
    }
  }

  // Créditos: cada EMA consome um. Saldo baixo → a empresa compra e a operadora de plataforma aprova.
  const { saldo } = await api.naEmpresa<{ saldo: number }>('GET', admin, t, '/creditos/saldo');
  if (saldo < cenario.creditos.minimo) {
    const op = persona(cenario.operadorPlataforma);
    const compra = await api.naEmpresa<{ id: string }>('POST', admin, t, '/creditos/compras', { quantidade: cenario.creditos.compra, comprovanteBase64: dataUrl(fotoSintetica(`compra-${d.rodada}-${def.chave}`, 240, 320)) });
    const adminDaLoja = { id: papeis.admin.email, nome: loja.nomes.admin ?? papeis.admin.email, papel: 'admin', empresa: loja.chave };
    const operadora = { id: op.email, nome: d.nomes.get(op.email) ?? op.email, papel: 'plataforma' };
    d.praia.emitir({ tipo: 'passo', rodada: d.rodada, jornada: `preparo:${loja.slug}`, passo: 'comprar créditos', quem: adminDaLoja, loja: loja.slug, resultado: 'OK', detalhes: { saldo, quantidade: cenario.creditos.compra } });
    const tokenOp = await sessoes.console(op.email, op.senha as string, op.totpSegredo as string);
    d.praia.emitir({ tipo: 'passo', rodada: d.rodada, jornada: `preparo:${loja.slug}`, passo: 'login no console (TOTP)', quem: operadora, resultado: 'OK' });
    await api.plataforma('POST', tokenOp, `/creditos/compras/${t}/${compra.id}/aprovar`);
    d.praia.emitir({ tipo: 'passo', rodada: d.rodada, jornada: `preparo:${loja.slug}`, passo: 'aprovar compra de créditos', quem: operadora, loja: loja.slug, resultado: 'OK', detalhes: { empresa: loja.slug, quantidade: cenario.creditos.compra } });
    log(`${loja.slug}: saldo ${saldo} → comprou ${cenario.creditos.compra} créditos (aprovados pela operadora de plataforma)`);
  }
  // A praia desenha a frota REAL da loja (séries e estado), em vez de inventar jetskis.
  if (d.praia.ligada) {
    const frota = await api.naEmpresa<{ serie: string; status: string; modeloId: string }[]>('GET', gerente, t, '/jetskis');
    d.praia.emitir({ tipo: 'nota', rodada: d.rodada, jornada: `preparo:${loja.slug}`, passo: 'frota da loja', quem: { id: papeis.gerente.email, nome: loja.nomes.gerente ?? papeis.gerente.email, papel: 'gerente', empresa: loja.chave }, loja: loja.slug, resultado: 'OK', detalhes: { frota: frota.map((j) => ({ serie: j.serie, status: j.status })) } });
  }
  log(`${loja.slug}: pronta — ${loja.vendedores.length} vendedores, ${loja.recorrentes.length} fregueses, créditos ${saldo}`);
  return loja;
}

/** e-mail → nome de exibição, dos catálogos E3a/E3b (a praia mostra nomes, não e-mails). */
function nomesDosCatalogos(): Map<string, string> {
  const nomes = new Map<string, string>();
  const e3a = JSON.parse(readFileSync(new URL('../catalogo/e3a.json', import.meta.url), 'utf8')) as { operadorPlataforma: { email: string; nome: string }; empresasDeCarga: { adminEmail: string; adminNome: string }[] };
  nomes.set(e3a.operadorPlataforma.email, e3a.operadorPlataforma.nome);
  for (const e of e3a.empresasDeCarga) nomes.set(e.adminEmail, e.adminNome);
  const e3b = lerCatalogoEmissao();
  nomes.set(e3b.eama.adminEmail, e3b.eama.adminNome);
  for (const del of e3b.delegadas) {
    nomes.set(del.adminEmail, del.adminNome);
    for (const m of (del as { equipe?: { email: string; nome: string }[] }).equipe ?? []) nomes.set(m.email, m.nome);
  }
  for (const c of e3b.clientesPortal) nomes.set(c.email, c.nome);
  return nomes;
}

async function rodarDia(): Promise<void> {
  const cenario = JSON.parse(readFileSync(process.env.CENARIO ?? new URL('../catalogo/e4-sabado.json', import.meta.url), 'utf8')) as Cenario;
  if (process.env.CHEGADAS) cenario.chegadas = Number(process.env.CHEGADAS);
  if (process.env.FATOR) cenario.dia.fator = Number(process.env.FATOR);
  const semente = Number(process.env.SEMENTE ?? Date.now() % 1_000_000);
  const rodada = new Date().toISOString().slice(5, 16).replace(/[-:T]/g, '') + `s${semente}`;
  const mailpit = process.env.MAILPIT_URL ?? 'http://127.0.0.1:8025';

  const arquivo = new ArquivoDeEstado(process.env.ESTADO ?? './personas.json', DOMINIO);
  const medidas = new Medidas();
  const acaso = new Acaso(semente);
  const praia = new Praia(process.env.PRAIA_URL, { token: process.env.PRAIA_TOKEN });
  if (praia.ligada) console.log(`[motor] praia sintética em ${praia.url}`);
  const d: Dia = {
    cenario, arquivo, medidas, acaso, rodada, mailpit, log, praia, nomes: nomesDosCatalogos(),
    api: new Plataforma(`https://www.${DOMINIO}/api`),
    sessoes: new Sessoes({ dominio: DOMINIO, mailpit }),
    relogio: undefined as unknown as Relogio,
    fakes: process.env.FAKES_URL ?? 'http://127.0.0.1:8089',
    lojas: [],
    clientesDoPortal: [],
  };

  const abre = minutosDoDia(cenario.dia.abre);
  const fecha = minutosDoDia(cenario.dia.fecha);
  const duracaoReal = Math.round((fecha - abre) / cenario.dia.fator);
  console.log(`[motor] rodada ${rodada}: ${cenario.chegadas} chegadas, ${cenario.dia.abre}–${cenario.dia.fecha} em ~${duracaoReal} min reais (${cenario.dia.fator}×), semente ${semente}, alvo ${DOMINIO}`);

  const porta = Number(process.env.METRICAS_PORTA ?? 9464);
  const servidor = porta ? createServer((_req, res) => res.writeHead(200, { 'Content-Type': 'text/plain; version=0.0.4' }).end(medidas.prometheus())).listen(porta, '0.0.0.0') : undefined;

  // Antes de abrir: cada loja confere equipe, vendedores, instrutor e créditos.
  for (const def of cenario.lojas) d.lojas.push(await prepararLoja(d, def));
  const emissao = lerCatalogoEmissao();
  // Clientes recorrentes do portal: os do catálogo E3b que já têm CPF no perfil. Cada um reserva no máximo uma vez no dia.
  d.clientesDoPortal = emissao.clientesPortal.map((c) => arquivo.estado.personas[c.chave]).filter((p): p is EstadoPersona => Boolean(p?.perfilCompleto));

  relogio = d.relogio = new Relogio(abre, cenario.dia.fator);
  praia.horaSim = () => d.relogio.hora();
  log(`lojas abertas: ${d.lojas.map((l) => l.slug).join(', ')}`);

  const pesosDeLoja = Object.fromEntries(cenario.lojas.map((l, i) => [String(i), l.peso]));
  const emCurso: Promise<void>[] = [];

  d.lojas.forEach((loja, i) => {
    const r = new RotinaDaLoja(d, 'telas', i);
    emCurso.push(r.rodar(() => r.telas(loja, fecha)));
    if (acaso.chance(cenario.manutencao.chancePorLoja)) {
      const m = new RotinaDaLoja(d, 'manutencao', i);
      emCurso.push(m.rodar(() => m.manutencao(loja)));
    }
  });

  sortearChegadas(acaso, cenario.chegadas, cenario.curvaPorHora).forEach((quando, n) => {
    const loja = d.lojas[Number(acaso.ponderado(pesosDeLoja))];
    const canal = acaso.ponderado(cenario.canal);
    const jornada = canal === 'portal' ? new JornadaPortal(d, 'portal', n + 1) : new JornadaBalcao(d, 'balcao', n + 1);
    emCurso.push(d.relogio.ate(quando).then(() => jornada.rodar(() => jornada.executar(loja))));
  });

  await Promise.all(emCurso);
  log('última jornada terminou — fechando o dia.');
  await Promise.all(
    d.lojas.map((loja, i) => {
      const f = new RotinaDaLoja(d, 'fechamento', i);
      return f.rodar(() => f.fechamento(loja));
    }),
  );

  d.praia.emitir({ tipo: 'nota', rodada, jornada: 'motor', passo: `rodada ${rodada} terminou: ${medidas.falhas.length} falhas`, quem: { id: 'motor', nome: 'Motor sintético', papel: 'motor' } });
  await d.praia.encerrar();
  const relatorio = { rodada, dominio: DOMINIO, semente, cenario: { chegadas: cenario.chegadas, fator: cenario.dia.fator }, terminouEm: new Date().toISOString(), loginsNoKeycloak: d.sessoes.logins, ...medidas.relatorio() };
  const saida = process.env.RELATORIO ?? `./relatorios/motor-${rodada}.json`;
  mkdirSync(dirname(saida), { recursive: true });
  writeFileSync(saida, `${JSON.stringify(relatorio, null, 2)}\n`);
  servidor?.close();

  console.log('\n=== desfechos ===');
  for (const [jornada, r] of Object.entries(relatorio.jornadas)) console.log(`${jornada.padEnd(12)} ${Object.entries(r).map(([k, v]) => `${k}=${v}`).join('  ')}`);
  console.log(`contadores   ${Object.entries(relatorio.contadores).map(([k, v]) => `${k}=${v}`).join('  ')}`);
  console.log(`falhas       ${medidas.falhas.length}   logins no Keycloak ${d.sessoes.logins}   relatório: ${saida}`);
  if (medidas.falhas.length > 0) process.exitCode = 1;
}

const comando = process.argv[2];
if (comando === 'dia') await rodarDia();
else {
  console.error('uso: node src/motor.ts dia');
  process.exit(2);
}
