// Cenário PLATAFORMA — a operadora olhando o console (fase E5). Só leitura.
//
// Poucos humanos fazem isso na vida real; o valor é outro: as consultas do console são
// CROSS-TENANT (dashboard consolidado, todas as empresas, trilha global, saúde) e crescem com
// o volume de dados de todas as lojas. É aqui que uma query sem índice aparece primeiro.
//
//   ./k6/rodar.sh <ip> plataforma [perfil]
//
// Credencial: a operadora sintética (tipo `plataforma` no tokens.json, client do console).
// Sem X-Tenant-Id — o console não tem empresa corrente.

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, validarAlvo, LIMITES } from '../lib/config.js';
import { carregarCredenciais, credencialDaVU, tokenDe } from '../lib/auth.js';
import { perfil, limitesDoPerfil, PERFIL } from '../perfis.js';

const CREDENCIAIS = carregarCredenciais();
validarAlvo(CREDENCIAIS, 'plataforma'); // no init: `--no-setup` não pula a trava

export const options = {
  scenarios: { plataforma: perfil() },
  thresholds: limitesDoPerfil(LIMITES.leitura, PERFIL),
  userAgent: 'k6-meujet-carga/1.0',
};

export function setup() {
  validarAlvo(CREDENCIAIS, 'plataforma');
  return {};
}

const ROTAS = [
  ['GET /platform/dashboard', '/v1/platform/dashboard?dias=30'],
  ['GET /platform/tenants', '/v1/platform/tenants'],
  ['GET /platform/creditos', '/v1/platform/creditos'],
  ['GET /platform/auditoria', '/v1/platform/auditoria?limite=100'],
  ['GET /platform/saude', '/v1/platform/saude'],
  ['GET /platform/pending-signups', '/v1/platform/pending-signups'],
];

export default function () {
  const credencial = credencialDaVU(CREDENCIAIS, 'plataforma');
  const token = tokenDe(credencial);
  for (const [nome, caminho] of ROTAS) {
    const r = http.get(`${BASE_URL}${caminho}`, { headers: { Authorization: `Bearer ${token}` }, tags: { tipo: 'leitura', name: nome } });
    check(r, { [`${nome} 200`]: (x) => x.status === 200 });
  }
}
