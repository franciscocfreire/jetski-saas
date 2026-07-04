"use client";

import { createContext, useCallback, useContext, useRef, useState } from "react";
import { CheckCircle2, AlertTriangle, Info } from "lucide-react";

/**
 * Toast do sistema (D1): feedback pós-ação único em todo o portal.
 * Uso: const { toast } = useToast(); toast("Perfil salvo"); toast("Falhou", "erro")
 */

type Tipo = "sucesso" | "erro" | "info";
interface Item { id: number; texto: string; tipo: Tipo }

const ToastContext = createContext<{ toast: (texto: string, tipo?: Tipo) => void }>({
  toast: () => {},
});

export function useToast() {
  return useContext(ToastContext);
}

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [itens, setItens] = useState<Item[]>([]);
  const seq = useRef(0);

  const toast = useCallback((texto: string, tipo: Tipo = "sucesso") => {
    const id = ++seq.current;
    setItens((v) => [...v, { id, texto, tipo }]);
    setTimeout(() => setItens((v) => v.filter((i) => i.id !== id)), 3500);
  }, []);

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 bottom-20 z-50 flex flex-col items-center gap-2 px-4 md:bottom-6">
        {itens.map((i) => (
          <div
            key={i.id}
            role="status"
            className={`pointer-events-auto flex max-w-sm items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-medium text-white shadow-lg ${
              i.tipo === "erro" ? "bg-red-600" : i.tipo === "info" ? "bg-slate-800" : "bg-emerald-600"
            }`}
          >
            {i.tipo === "erro" ? <AlertTriangle size={16} /> : i.tipo === "info" ? <Info size={16} /> : <CheckCircle2 size={16} />}
            {i.texto}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
