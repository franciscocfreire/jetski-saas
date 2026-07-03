import { NextRequest } from "next/server";
import { handlers } from "@/lib/auth";
import { BASE_PATH } from "@/lib/base";

/**
 * O Next remove o basePath (/portal) antes de entregar a request ao handler,
 * mas o Auth.js foi configurado com basePath completo (/portal/api/auth) para
 * gerar redirect_uri/URLs públicas corretas. Reanexamos o prefixo aqui para o
 * parser de action do Auth.js casar com a configuração.
 */
function comBasePath(req: NextRequest): NextRequest {
  if (!BASE_PATH) return req;
  const url = new URL(req.url);
  if (!url.pathname.startsWith(`${BASE_PATH}/`)) {
    url.pathname = `${BASE_PATH}${url.pathname}`;
    return new NextRequest(url, req);
  }
  return req;
}

export const GET = (req: NextRequest) => handlers.GET(comBasePath(req));
export const POST = (req: NextRequest) => handlers.POST(comBasePath(req));
