/**
 * Logo "Meu Jet" — Crista Dupla (ver BRAND.md na raiz do repo).
 * Preparado para white-label: se `tenantName` vier do branding do tenant,
 * o wordmark exibe o nome da loja e a marca da plataforma vai para o rodapé
 * ("powered by Meu Jet").
 */
export function Logo({
  theme = "light",
  size = 24,
  tenantName,
}: {
  theme?: "light" | "dark";
  size?: number;
  tenantName?: string;
}) {
  const wave2 = theme === "light" ? "#1E4266" : "#F8F4EA";
  const ink = theme === "light" ? "#12263F" : "#F8F4EA";
  return (
    <span className="inline-flex items-center gap-2">
      <svg
        width={size * 1.6}
        height={size}
        viewBox="0 0 64 40"
        fill="none"
        role="img"
        aria-label={tenantName ?? "Meu Jet"}
      >
        <path
          d="M5 15.5 C 15 15.5, 19 6, 30 6 C 39.5 6, 42 12.5, 59 10.5"
          stroke="#C9A24B"
          strokeWidth={4.4}
          strokeLinecap="round"
        />
        <path
          d="M5 29 C 13 29, 18.5 20.5, 28 20.5 C 37 20.5, 42.5 27.5, 59 25"
          stroke={wave2}
          strokeWidth={4.4}
          strokeLinecap="round"
        />
      </svg>
      <span
        className="font-semibold uppercase leading-none"
        style={{
          fontFamily: "var(--font-display)",
          color: ink,
          fontSize: size * 0.62,
          letterSpacing: "0.18em",
        }}
      >
        {tenantName ?? "Meu Jet"}
      </span>
    </span>
  );
}
