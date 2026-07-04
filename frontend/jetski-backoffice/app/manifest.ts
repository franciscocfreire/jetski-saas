import type { MetadataRoute } from "next";

/** PWA do backoffice: staff instala no celular/tablet (píer, marina). */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Meu Jet — Backoffice",
    short_name: "Meu Jet Staff",
    description: "Operação da loja: agenda, balcão, fila, pagamentos e emissão.",
    start_url: "/dashboard",
    display: "standalone",
    background_color: "#F8F4EA",
    theme_color: "#1E4266",
    icons: [{ src: "/icon.svg", sizes: "any", type: "image/svg+xml" }],
  };
}
