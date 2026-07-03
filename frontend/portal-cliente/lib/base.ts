/**
 * basePath do app (/portal atrás do nginx; vazio no dev standalone).
 * Links do próprio Next (Link/router) já recebem o prefixo automaticamente —
 * este helper é para URLs que saem do roteador do Next: callbackUrl do
 * NextAuth, pages do provider, redirects absolutos.
 */
export const BASE_PATH = process.env.NEXT_PUBLIC_BASE_PATH ?? "";

export const withBase = (path: string) => `${BASE_PATH}${path}`;
