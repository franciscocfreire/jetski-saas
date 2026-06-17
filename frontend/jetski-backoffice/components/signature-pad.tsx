'use client'

import { useRef, useState } from 'react'
import { Eraser, PenLine } from 'lucide-react'
import { Button } from '@/components/ui/button'

/**
 * Pad de assinatura (pointer events: dedo/caneta/mouse).
 * `onChange` recebe o **dataURL PNG** quando há assinatura, ou `null` ao limpar.
 */
export function SignaturePad({
  onChange,
  height = 180,
}: {
  onChange?: (dataUrl: string | null) => void
  height?: number
}) {
  const ref = useRef<HTMLCanvasElement>(null)
  const drawing = useRef(false)
  const [has, setHas] = useState(false)

  function point(e: React.PointerEvent) {
    const c = ref.current!
    const r = c.getBoundingClientRect()
    return {
      x: (e.clientX - r.left) * (c.width / r.width),
      y: (e.clientY - r.top) * (c.height / r.height),
    }
  }

  function down(e: React.PointerEvent) {
    e.preventDefault()
    drawing.current = true
    const ctx = ref.current!.getContext('2d')!
    const { x, y } = point(e)
    ctx.beginPath()
    ctx.moveTo(x, y)
    ref.current!.setPointerCapture(e.pointerId)
  }

  function move(e: React.PointerEvent) {
    if (!drawing.current) return
    const ctx = ref.current!.getContext('2d')!
    const { x, y } = point(e)
    ctx.lineTo(x, y)
    ctx.strokeStyle = '#0f172a'
    ctx.lineWidth = 2.5
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
    ctx.stroke()
    if (!has) setHas(true)
  }

  function up() {
    if (!drawing.current) return
    drawing.current = false
    if (has) onChange?.(ref.current!.toDataURL('image/png'))
  }

  function clear() {
    const c = ref.current!
    c.getContext('2d')!.clearRect(0, 0, c.width, c.height)
    setHas(false)
    onChange?.(null)
  }

  return (
    <div>
      <div className="relative overflow-hidden rounded-xl border-2 border-dashed border-muted-foreground/30 bg-muted/30">
        <canvas
          ref={ref}
          width={600}
          height={height}
          onPointerDown={down}
          onPointerMove={move}
          onPointerUp={up}
          onPointerLeave={up}
          className="w-full touch-none"
          style={{ height }}
        />
        {!has && (
          <div className="pointer-events-none absolute inset-0 flex items-center justify-center gap-2 text-sm text-muted-foreground">
            <PenLine size={16} /> Assine aqui (dedo, caneta ou mouse)
          </div>
        )}
        <div className="pointer-events-none absolute bottom-6 left-8 right-8 border-b border-muted-foreground/30" />
      </div>
      <div className="mt-2 flex items-center justify-between">
        <span className="text-xs text-muted-foreground">
          Captura: imagem da assinatura + hash do documento
        </span>
        <Button type="button" variant="ghost" size="sm" onClick={clear} className="h-7 gap-1 text-xs">
          <Eraser size={13} /> Limpar
        </Button>
      </div>
    </div>
  )
}
