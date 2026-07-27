import type { NextConfig } from "next";

/**
 * O console vive na RAIZ do próprio subdomínio (admin.meujet.com.br) — sem
 * basePath, diferente do portal do cliente, que já morou sob /portal.
 */
const nextConfig: NextConfig = {
  output: "standalone",
};

export default nextConfig;
