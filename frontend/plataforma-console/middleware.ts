import { NextRequest, NextResponse } from "next/server";

/**
 * Guarda de sessão do console: sem cookie de sessão, vai para /login.
 *
 * Isto é UX, não segurança — quem autoriza é o backend
 * (PlatformScopeInterceptor + OPA). Um cookie forjado não abre nada: toda
 * chamada a /v1/platform/** revalida o JWT e o papel de plataforma.
 */
// /api/logout entra aqui de propósito: sair precisa funcionar mesmo com a
// sessão em estado parcial (cookie fragmentado incompleto, token expirado).
const PUBLICO = ["/login", "/api/auth", "/api/logout"];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  if (PUBLICO.some((p) => pathname === p || pathname.startsWith(`${p}/`))) {
    return NextResponse.next();
  }

  // startsWith, não has(): quando o JWT passa do limite de tamanho o NextAuth
  // FRAGMENTA o cookie em `…session-token.0`, `.1`, … e o nome exato deixa de
  // existir. Checar só o nome exato criava loop /login ↔ /empresas — o
  // middleware não via sessão, a página via (auth() remonta os fragmentos).
  const temSessao = request.cookies
    .getAll()
    .some(
      (c) =>
        c.name.startsWith("console.session-token") ||
        c.name.startsWith("__Secure-console.session-token"),
    );

  if (!temSessao) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    url.searchParams.set("callbackUrl", pathname);
    return NextResponse.redirect(url);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
