import { redirect } from "next/navigation";
import { auth } from "./auth";
import { platform } from "./platform";
import type { OperadorAtual } from "./types";

const SEM_ACESSO: OperadorAtual = { usuarioId: null, papeis: [], admin: false };

/**
 * Sessão + papéis de plataforma do operador, para toda página do console.
 *
 * O menu precisa disso em TODAS as rotas: se só algumas passassem os papéis ao
 * {@link Shell}, os itens exclusivos de admin apareceriam e sumiriam conforme a navegação.
 *
 * Falha na consulta de papéis não derruba a página — ela cai para "sem acesso" e a própria
 * página mostra o 403 do backend, que é a mensagem útil.
 */
export async function operadorAtual() {
  const session = await auth();
  if (!session?.accessToken) {
    redirect("/login");
  }

  let me = SEM_ACESSO;
  try {
    me = await platform.me();
  } catch {
    // sem papel de plataforma (ou backend fora) — a página renderiza o erro
  }

  return { session, me };
}
