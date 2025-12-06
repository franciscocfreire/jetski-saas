/**
 * Helper para verificar se credenciais de teste estão configuradas.
 *
 * Testes que requerem autenticação devem usar este helper para
 * pular automaticamente quando credenciais não estão disponíveis.
 */
export function hasCredentials(): boolean {
  return !!process.env.TEST_USER_EMAIL && !!process.env.TEST_USER_PASSWORD;
}

/**
 * Mensagem padrão para testes pulados por falta de credenciais
 */
export const SKIP_MESSAGE =
  'Credenciais não configuradas - rode testes de signup primeiro ou configure TEST_USER_EMAIL e TEST_USER_PASSWORD';
