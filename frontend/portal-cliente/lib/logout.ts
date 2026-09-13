import { signOut } from "next-auth/react";
import { withBase } from "@/lib/base";

/**
 * Saída em três tempos (mesma ordem do backoffice — trocar a ordem quebra uma
 * das pontas):
 *
 * 1. pega a URL de logout federado ENQUANTO a sessão existe — o
 *    `id_token_hint` some junto com ela. Sem esse passo, encerrar só o cookie
 *    do portal deixa a sessão SSO viva e o próximo "Entrar" loga sem senha;
 * 2. `signOut()` do Auth.js: apaga o cookie pelo caminho oficial e deixa o
 *    SessionProvider em "unauthenticated" — sem isso, cada
 *    `GET /api/auth/session` RE-EMITE o cookie (expiração deslizante) e uma
 *    resposta atrasada ressuscita a sessão depois da deleção do servidor;
 * 3. só então navega para o Keycloak, que devolve em /api/logout/finish.
 */
export async function sairDaConta() {
  let destino = withBase("/api/logout");

  try {
    const r = await fetch(withBase("/api/logout/url"), { cache: "no-store" });
    if (r.ok) {
      const dados = (await r.json()) as { url?: string };
      if (dados.url) destino = dados.url;
    }
  } catch {
    // sem a URL, o /api/logout (fallback) refaz o trabalho no servidor
  }

  try {
    await signOut({ redirect: false });
  } catch {
    // mesmo falhando, seguimos: o servidor ainda apaga os cookies
  }

  window.location.href = destino;
}
