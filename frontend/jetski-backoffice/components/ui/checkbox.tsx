"use client"

import * as React from "react"
import * as CheckboxPrimitive from "@radix-ui/react-checkbox"
import { CheckIcon } from "lucide-react"

import { cn } from "@/lib/utils"

/**
 * Checkbox — três decisões deliberadas, contra o padrão que veio do shadcn:
 *
 * 1. `border-control` no lugar de `border-input`. O contorno de --input dá
 *    1,31:1 sobre card branco; a WCAG 2.1 (1.4.11) pede 3:1 para componente de
 *    interface. Operadores relataram não enxergar a caixa — e não era impressão.
 * 2. `border-2` e 1,125rem (18px) no lugar de borda de 1px e 16px: num contorno
 *    fino, meio pixel de antialiasing come boa parte da presença visual.
 * 3. `bg-background` no estado não marcado. Caixa transparente sobre fundo
 *    colorido (linha de tabela, card destacado) perde a leitura de "vazia,
 *    esperando clique".
 *
 * O estado marcado continua preenchido com --primary, que já tinha contraste
 * de sobra; o problema sempre foi o não marcado.
 */

function Checkbox({
  className,
  ...props
}: React.ComponentProps<typeof CheckboxPrimitive.Root>) {
  return (
    <CheckboxPrimitive.Root
      data-slot="checkbox"
      className={cn(
        "peer border-control hover:border-control-hover bg-background dark:bg-input/30 data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground dark:data-[state=checked]:bg-primary data-[state=checked]:border-primary focus-visible:border-ring focus-visible:ring-ring/50 aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive size-[1.125rem] shrink-0 rounded-[4px] border-2 shadow-xs transition-colors outline-none focus-visible:ring-[3px] disabled:cursor-not-allowed disabled:opacity-50",
        className
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator
        data-slot="checkbox-indicator"
        className="grid place-content-center text-current transition-none"
      >
        <CheckIcon className="size-3.5" />
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  )
}

export { Checkbox }
