import { execFileSync } from 'child_process';
import { POSTGRES_CONTAINER, POSTGRES_DB, POSTGRES_USER, PLANO_SO_DELEGADA } from './ambiente';

/**
 * Seeds que NÃO têm endpoint, feitos direto no Postgres de dev (docker exec).
 * Só dois, e os dois idempotentes:
 *  - o plano sem EMISSAO_PROPRIA (não existe API para criar plano, e alterar os
 *    módulos de um plano existente mudaria todas as empresas daquele plano);
 *  - o operador de plataforma do e2e (o PlatformAdminSeeder só promove no boot, e a
 *    concessão pela API exige um operador que já exista).
 */

function psql(sql: string): string {
  return execFileSync(
    'docker',
    ['exec', '-i', POSTGRES_CONTAINER, 'psql', '-U', POSTGRES_USER, '-d', POSTGRES_DB,
      '-v', 'ON_ERROR_STOP=1', '-At', '-q'],
    { input: sql, encoding: 'utf-8' },
  ).trim();
}

const literal = (v: string) => `'${v.replace(/'/g, "''")}'`;

/** Plano "só delegada"; devolve o id numérico usado por POST /v1/platform/tenants/{id}/plano. */
export function garantirPlanoSoDelegada(): number {
  const id = psql(`
    INSERT INTO plano (nome, preco_mensal, modulos)
    VALUES (${literal(PLANO_SO_DELEGADA)}, 0, '["EMISSAO_DELEGADA"]'::jsonb)
    ON CONFLICT (nome) DO UPDATE SET modulos = EXCLUDED.modulos
    RETURNING id;
  `);
  const n = Number(id.split('\n').pop());
  if (!Number.isInteger(n)) throw new Error(`Plano ${PLANO_SO_DELEGADA}: id inesperado "${id}"`);
  return n;
}

/**
 * Liga a identidade do Keycloak a um usuario com papel PLATFORM_ADMIN. Vínculo explícito
 * (regra de identidade única): é o mesmo registro que a tela /operadores do console cria.
 */
export function garantirOperadorPlataforma(email: string, keycloakUserId: string): void {
  psql(`
    WITH u AS (
      INSERT INTO usuario (email, nome, ativo, email_verified, email_verified_at)
      VALUES (${literal(email)}, 'E2E Plataforma', true, true, now())
      ON CONFLICT (email) DO UPDATE SET ativo = true
      RETURNING id
    )
    INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id)
    SELECT id, 'keycloak', ${literal(keycloakUserId)} FROM u
    ON CONFLICT (provider, provider_user_id) DO NOTHING;

    INSERT INTO usuario_global_roles (usuario_id, roles, unrestricted_access)
    SELECT id, ARRAY['PLATFORM_ADMIN'], true FROM usuario WHERE email = ${literal(email)}
    ON CONFLICT (usuario_id) DO UPDATE SET roles = EXCLUDED.roles, unrestricted_access = true;
  `);
}

/** Id da capitania pelo código (catálogo de plataforma). */
export function capitaniaId(codigo: string): string {
  const id = psql(`SELECT id FROM capitania WHERE codigo = ${literal(codigo)} AND ativa;`);
  if (!id) throw new Error(`Capitania ${codigo} não encontrada ou inativa no catálogo`);
  return id;
}
