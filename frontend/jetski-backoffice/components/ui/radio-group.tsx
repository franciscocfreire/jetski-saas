"use client"

import * as React from "react"
import { cn } from "@/lib/utils"

interface RadioGroupProps extends React.HTMLAttributes<HTMLDivElement> {
  value?: string
  onValueChange?: (value: string) => void
  children: React.ReactNode
}

interface RadioGroupItemProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> {
  value: string
}

const RadioGroupContext = React.createContext<{
  value?: string
  name: string
  onValueChange?: (value: string) => void
} | null>(null)

const RadioGroup = React.forwardRef<HTMLDivElement, RadioGroupProps>(
  ({ className, value, onValueChange, children, ...props }, ref) => {
    const name = React.useId()
    return (
      <RadioGroupContext.Provider value={{ value, name, onValueChange }}>
        <div
          ref={ref}
          role="radiogroup"
          className={cn("grid gap-2", className)}
          {...props}
        >
          {children}
        </div>
      </RadioGroupContext.Provider>
    )
  }
)
RadioGroup.displayName = "RadioGroup"

const RadioGroupItem = React.forwardRef<HTMLInputElement, RadioGroupItemProps>(
  ({ className, value, id, ...props }, ref) => {
    const context = React.useContext(RadioGroupContext)
    if (!context) {
      throw new Error("RadioGroupItem must be used within a RadioGroup")
    }

    return (
      <input
        type="radio"
        ref={ref}
        id={id}
        name={context.name}
        value={value}
        checked={context.value === value}
        onChange={() => context.onValueChange?.(value)}
        className={cn(
          "aspect-square h-4 w-4 rounded-full border border-primary text-primary accent-primary ring-offset-background focus:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50",
          className
        )}
        {...props}
      />
    )
  }
)
RadioGroupItem.displayName = "RadioGroupItem"

export { RadioGroup, RadioGroupItem }
