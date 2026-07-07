"use client";

import { useRef, useState } from "react";
import { Camera, CheckCircle2, Loader2, UploadCloud } from "lucide-react";

/**
 * Tile de upload de documento com preview e captura pela câmera.
 * Usado no wizard EMA (por reserva) e no Perfil (por loja).
 */
export function UploadTile({ rotulo, presente, previewUrl, camera = "environment", onFile }: {
  rotulo: string; presente: boolean; previewUrl?: string;
  /** "user" = câmera frontal (selfie); "environment" = traseira (documentos). */
  camera?: "user" | "environment";
  onFile: (dataUrl: string) => void;
}) {
  const [lendo, setLendo] = useState(false);
  const arquivoRef = useRef<HTMLInputElement>(null);
  const cameraRef = useRef<HTMLInputElement>(null);

  function lerArquivo(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0];
    e.target.value = ""; // permite reescolher o mesmo arquivo
    if (!f || f.size > 5 * 1024 * 1024) return;
    setLendo(true);
    const r = new FileReader();
    r.onload = () => { onFile(r.result as string); setLendo(false); };
    r.readAsDataURL(f);
  }

  return (
    <div className={`flex flex-col items-center gap-1.5 rounded-xl border-2 border-dashed p-4 text-center text-sm ${
      presente ? "border-emerald-300 bg-emerald-50 text-emerald-700" : "border-slate-300 bg-slate-50 text-slate-600"
    }`}>
      {previewUrl ? (
        <img src={previewUrl} alt={rotulo}
          className="h-28 w-full rounded-lg object-cover" />
      ) : presente ? (
        <CheckCircle2 size={22} />
      ) : lendo ? (
        <Loader2 size={22} className="animate-spin" />
      ) : (
        <UploadCloud size={22} className="text-slate-400" />
      )}
      {rotulo}
      <span className="text-xs opacity-70">
        {presente ? "Enviado — tire ou envie outra para substituir" : "JPEG/PNG até 5 MB"}
      </span>
      <div className="mt-1 flex gap-2">
        <button type="button" onClick={() => cameraRef.current?.click()}
          className="flex items-center gap-1 rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700 hover:border-brand-400">
          <Camera size={13} /> Tirar foto
        </button>
        <button type="button" onClick={() => arquivoRef.current?.click()}
          className="flex items-center gap-1 rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700 hover:border-brand-400">
          <UploadCloud size={13} /> Enviar arquivo
        </button>
      </div>
      {/* capture abre a câmera direto no celular; no desktop cai no seletor de arquivo */}
      <input ref={cameraRef} type="file" accept="image/*" capture={camera}
        className="hidden" onChange={lerArquivo} />
      <input ref={arquivoRef} type="file" accept="image/jpeg,image/png,image/webp"
        className="hidden" onChange={lerArquivo} />
    </div>
  );
}
