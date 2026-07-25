import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/lib/auth";

const INTERNAL = process.env.API_INTERNAL_URL ?? "http://backend:8090/api";

/**
 * Proxy autenticado para os downloads binários da plataforma (export .zip de
 * empresa, comprovante PIX de compra de crédito).
 *
 * Existe porque o access token nunca vai para o browser: um `<a href>` direto
 * ao backend chegaria sem Authorization. Aqui o servidor anexa o token e faz
 * streaming do corpo.
 *
 *   /api/download?tenantId=…&key=…            → export de arquivamento
 *   /api/download?tenantId=…&compraId=…       → comprovante da compra
 */
export async function GET(request: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) {
    return NextResponse.json({ erro: "Sessão expirada" }, { status: 401 });
  }

  const { searchParams } = request.nextUrl;
  const tenantId = searchParams.get("tenantId");
  const key = searchParams.get("key");
  const compraId = searchParams.get("compraId");

  if (!tenantId || (!key && !compraId)) {
    return NextResponse.json({ erro: "Parâmetros inválidos" }, { status: 400 });
  }

  const alvo = compraId
    ? `/v1/platform/creditos/compras/${tenantId}/${compraId}/comprovante`
    : `/v1/platform/tenants/${tenantId}/exports/download?key=${encodeURIComponent(key!)}`;

  const upstream = await fetch(`${INTERNAL}${alvo}`, {
    headers: { Authorization: `Bearer ${session.accessToken}` },
    cache: "no-store",
  });

  if (!upstream.ok) {
    // 403 aqui = não é operador de plataforma (PlatformScopeInterceptor).
    return NextResponse.json(
      { erro: `Falha no download (${upstream.status})` },
      { status: upstream.status },
    );
  }

  const headers = new Headers();
  const tipo = upstream.headers.get("content-type");
  const disposicao = upstream.headers.get("content-disposition");
  if (tipo) headers.set("content-type", tipo);
  if (disposicao) headers.set("content-disposition", disposicao);
  headers.set("cache-control", "no-store");

  return new NextResponse(upstream.body, { status: 200, headers });
}
