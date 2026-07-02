import { cn } from '@/lib/utils'

type LogoProps = {
  /** full = ondas + wordmark; icon = só as ondas */
  variant?: 'full' | 'icon'
  /** light = para fundo claro (onda navy); dark = para fundo navy (onda espuma) */
  theme?: 'light' | 'dark'
  className?: string
  /** altura do símbolo em px; o wordmark escala junto */
  size?: number
}

const GOLD = '#C9A24B'
const NAVY = '#1E4266'
const FOAM = '#F8F4EA'
const INK_LIGHT = '#12263F'

/**
 * Logo "Meu Jet" — Crista Dupla (ver BRAND.md).
 * Duas ondas: traço dourado (esteira ao sol) sobre traço navy/espuma.
 */
export function Logo({ variant = 'full', theme = 'dark', className, size = 28 }: LogoProps) {
  const wave2 = theme === 'light' ? NAVY : FOAM
  const ink = theme === 'light' ? INK_LIGHT : FOAM

  return (
    <span className={cn('inline-flex items-center gap-2.5', className)}>
      <svg
        width={size * 1.6}
        height={size}
        viewBox="0 0 64 40"
        fill="none"
        role="img"
        aria-label="Meu Jet"
        className="shrink-0"
      >
        <path
          d="M5 15.5 C 15 15.5, 19 6, 30 6 C 39.5 6, 42 12.5, 59 10.5"
          stroke={GOLD}
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
      {variant === 'full' && (
        <span
          className="font-display font-semibold uppercase leading-none"
          style={{ color: ink, fontSize: size * 0.62, letterSpacing: '0.2em' }}
        >
          Meu&nbsp;Jet
        </span>
      )}
    </span>
  )
}
