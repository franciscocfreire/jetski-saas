import type { MetadataRoute } from "next";
import { BASE_PATH } from "@/lib/base";

/** PWA: instalável no celular (P4). */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Meu Jet — Portal do Cliente",
    short_name: "Meu Jet",
    description: "Reserve jet skis, pague o sinal e resolva sua habilitação náutica.",
    start_url: `${BASE_PATH}/`,
    display: "standalone",
    background_color: "#F8F4EA",
    theme_color: "#1E4266",
    icons: [
      { src: `${BASE_PATH}/icon.svg`, sizes: "any", type: "image/svg+xml" },
    ],
  };
}
