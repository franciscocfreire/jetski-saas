import {
  exigirAmbienteDeTeste,
  OPERADOR_PLATAFORMA_EMAIL,
  OPERADOR_PLATAFORMA_SENHA,
  PLANO_SO_DELEGADA,
} from './helpers/ambiente';
import { garantirOperadorPlataforma, garantirPlanoSoDelegada } from './helpers/banco';
import { garantirUsuario, tokenRopc } from './helpers/keycloak';
import { plataforma } from './helpers/backend';

/**
 * Pré-requisitos globais e idempotentes do e2e da emissão delegada:
 *  1. guarda de ambiente (aborta fora do dev);
 *  2. operador de plataforma do e2e (Keycloak + vínculo explícito + PLATFORM_ADMIN);
 *  3. plano "só delegada" (sem EMISSAO_PROPRIA).
 * As empresas NÃO são criadas aqui: cada spec cria as suas, com sufixo único.
 */
export default async function globalSetup(): Promise<void> {
  exigirAmbienteDeTeste();

  const keycloakId = await garantirUsuario(OPERADOR_PLATAFORMA_EMAIL, OPERADOR_PLATAFORMA_SENHA, 'Plataforma');
  garantirOperadorPlataforma(OPERADOR_PLATAFORMA_EMAIL, keycloakId);

  // Falha cedo e com mensagem clara se o token não passar pelas rotas de plataforma.
  const token = await tokenRopc(OPERADOR_PLATAFORMA_EMAIL, OPERADOR_PLATAFORMA_SENHA);
  const api = await plataforma(token);
  try {
    await api.listarPlanos();
  } finally {
    await api.dispose();
  }

  const planoId = garantirPlanoSoDelegada();
  process.env.E2E_PLANO_SO_DELEGADA_ID = String(planoId);
  console.log(`[delegada] operador de plataforma ok · plano "${PLANO_SO_DELEGADA}" id=${planoId}`);
}
