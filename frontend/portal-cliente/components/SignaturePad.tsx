"use client";

import { useRef, useState } from "react";
import { Eraser, PenLine } from "lucide-react";

export function SignaturePad({
  onChange,
}: {
  onChange?: (hasSignature: boolean) => void;
}) {
  const ref = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);
  const [has, setHas] = useState(false);

  function point(e: React.PointerEvent) {
    const c = ref.current!;
    const r = c.getBoundingClientRect();
    return {
      x: (e.clientX - r.left) * (c.width / r.width),
      y: (e.clientY - r.top) * (c.height / r.height),
    };
  }

  function down(e: React.PointerEvent) {
    e.preventDefault();
    drawing.current = true;
    const ctx = ref.current!.getContext("2d")!;
    const { x, y } = point(e);
    ctx.beginPath();
    ctx.moveTo(x, y);
    ref.current!.setPointerCapture(e.pointerId);
  }

  function move(e: React.PointerEvent) {
    if (!drawing.current) return;
    const ctx = ref.current!.getContext("2d")!;
    const { x, y } = point(e);
    ctx.lineTo(x, y);
    ctx.strokeStyle = "#0f172a";
    ctx.lineWidth = 2.5;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.stroke();
    if (!has) {
      setHas(true);
      onChange?.(true);
    }
  }

  function up() {
    drawing.current = false;
  }

  function clear() {
    const c = ref.current!;
    c.getContext("2d")!.clearRect(0, 0, c.width, c.height);
    setHas(false);
    onChange?.(false);
  }

  return (
    <div>
      <div className="relative overflow-hidden rounded-xl border-2 border-dashed border-slate-300 bg-slate-50">
        <canvas
          ref={ref}
          width={600}
          height={180}
          onPointerDown={down}
          onPointerMove={move}
          onPointerUp={up}
          onPointerLeave={up}
          className="h-[180px] w-full touch-none"
        />
        {!has && (
          <div className="pointer-events-none absolute inset-0 flex items-center justify-center gap-2 text-sm text-slate-400">
            <PenLine size={16} /> Assine aqui (dedo, caneta ou mouse)
          </div>
        )}
        <div className="pointer-events-none absolute bottom-6 left-8 right-8 border-b border-slate-300" />
      </div>
      <div className="mt-2 flex items-center justify-between">
        <span className="text-xs text-slate-400">
          Captura: imagem da assinatura + hash do documento
        </span>
        <button
          onClick={clear}
          className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs text-slate-500 hover:bg-slate-100"
        >
          <Eraser size={13} /> Limpar
        </button>
      </div>
    </div>
  );
}
