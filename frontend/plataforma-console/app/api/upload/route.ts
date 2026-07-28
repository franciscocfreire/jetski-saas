import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/lib/auth";

const INTERNAL = process.env.API_INTERNAL_URL ?? "http://backend:8090/api";

/**
 * Proxy autenticado para o upload do zip de import de arquivamento.
 *
 * Espelho do /api/download: o access token nunca vai para o browser, então o
 * multipart chega aqui e o servidor anexa o Bearer e repassa por STREAMING ao
 * backend (server action não serve — bodySizeLimit de 1MB; o zip pode ter
 * centenas de MB). O nginx do host admin.* tem location próprio com corpo 1g.
 *
 *   POST /api/upload?tenantId=…   (multipart, campo "arquivo")
 */
export async function POST(request: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) {
    return NextResponse.json({ erro: "Sessão expirada" }, { status: 401 });
  }

  const tenantId = request.nextUrl.searchParams.get("tenantId");
  if (!tenantId) {
    return NextResponse.json({ erro: "Parâmetros inválidos" }, { status: 400 });
  }

  const upstream = await fetch(
    `${INTERNAL}/v1/platform/tenants/${tenantId}/import/upload`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${session.accessToken}`,
        // Preserva o boundary do multipart original
        "content-type": request.headers.get("content-type") ?? "",
      },
      body: request.body,
      // Node/undici exige duplex para body em stream
      // @ts-expect-error duplex não está no tipo RequestInit do TS
      duplex: "half",
      cache: "no-store",
    },
  );

  const corpo = await upstream.text();
  return new NextResponse(corpo, {
    status: upstream.status,
    headers: {
      "content-type": upstream.headers.get("content-type") ?? "application/json",
      "cache-control": "no-store",
    },
  });
}
