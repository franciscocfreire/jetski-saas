// Ilustração SVG local (data URI) de uma cena de mar com jet ski.
// Determinística e sem dependência externa — ideal para protótipo/demo.

export function jetImage(hueFrom: string, hueTo: string, seed = 0): string {
  // pequenas variações de onda por seed
  const o1 = 250 + (seed % 3) * 14;
  const svg = `
<svg xmlns="http://www.w3.org/2000/svg" width="900" height="600" viewBox="0 0 900 600">
  <defs>
    <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#dbeafe"/>
      <stop offset="0.55" stop-color="${hueFrom}"/>
      <stop offset="1" stop-color="${hueTo}"/>
    </linearGradient>
    <linearGradient id="hull" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#ffffff"/>
      <stop offset="1" stop-color="#e2e8f0"/>
    </linearGradient>
  </defs>
  <rect width="900" height="600" fill="url(#sky)"/>
  <circle cx="730" cy="120" r="68" fill="#fde68a" opacity="0.85"/>
  <circle cx="730" cy="120" r="92" fill="#fef3c7" opacity="0.25"/>

  <!-- ondas de fundo -->
  <path d="M0 ${o1} Q 225 ${o1 - 40} 450 ${o1} T 900 ${o1} V600 H0 Z" fill="#ffffff" opacity="0.10"/>
  <path d="M0 ${o1 + 60} Q 225 ${o1 + 20} 450 ${o1 + 60} T 900 ${o1 + 60} V600 H0 Z" fill="#ffffff" opacity="0.08"/>

  <!-- esteira / spray -->
  <path d="M120 420 Q 360 470 690 392" stroke="#ffffff" stroke-width="10" fill="none" opacity="0.5" stroke-linecap="round"/>

  <!-- jet ski (perfil estilizado) -->
  <g transform="translate(250 300)">
    <!-- sombra na água -->
    <ellipse cx="190" cy="138" rx="200" ry="20" fill="#0f172a" opacity="0.12"/>
    <!-- casco -->
    <path d="M20 112 C 70 70 180 56 250 58 L 330 60 C 372 62 392 78 384 104 C 376 124 120 142 60 132 C 32 128 14 122 20 112 Z" fill="url(#hull)" stroke="#cbd5e1" stroke-width="2"/>
    <!-- faixa colorida -->
    <path d="M44 110 C 90 86 190 74 250 76 L 322 78 C 348 80 360 88 360 100 C 300 112 120 120 70 116 C 52 114 42 112 44 110 Z" fill="${hueFrom}" opacity="0.9"/>
    <!-- assento -->
    <path d="M196 58 C 210 40 268 40 286 58 Z" fill="#334155"/>
    <!-- console / guidão -->
    <path d="M150 60 C 150 40 150 36 156 32 L 180 30 C 176 40 176 50 178 60 Z" fill="#475569"/>
    <rect x="150" y="26" width="40" height="8" rx="4" fill="#1e293b"/>
  </g>
</svg>`.trim();
  return "data:image/svg+xml;utf8," + encodeURIComponent(svg);
}
