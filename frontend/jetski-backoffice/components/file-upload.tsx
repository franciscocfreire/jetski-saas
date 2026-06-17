'use client'

import { useRef, useState } from 'react'
import { Upload, X, FileCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export type UploadedFile = {
  file: File
  /** dataURL (base64) — pronto p/ enviar no corpo ou converter p/ presigned. */
  dataUrl: string
}

/**
 * Seletor de arquivo reutilizável com preview (imagem) e estado.
 * Retorna o File + dataURL (base64) via `onChange`. A persistência (base64 no
 * corpo ou PUT em URL presigned) fica a cargo do chamador.
 */
export function FileUpload({
  label,
  accept = 'image/*,application/pdf',
  onChange,
}: {
  label: string
  accept?: string
  onChange?: (f: UploadedFile | null) => void
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [picked, setPicked] = useState<UploadedFile | null>(null)
  const [loading, setLoading] = useState(false)

  function pick(file: File) {
    setLoading(true)
    const reader = new FileReader()
    reader.onload = () => {
      const uf = { file, dataUrl: String(reader.result) }
      setPicked(uf)
      setLoading(false)
      onChange?.(uf)
    }
    reader.onerror = () => setLoading(false)
    reader.readAsDataURL(file)
  }

  function clear() {
    setPicked(null)
    if (inputRef.current) inputRef.current.value = ''
    onChange?.(null)
  }

  const isImage = picked?.file.type.startsWith('image/')

  return (
    <div>
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        className="hidden"
        onChange={(e) => {
          const f = e.target.files?.[0]
          if (f) pick(f)
        }}
      />
      {!picked ? (
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          className={cn(
            'flex w-full flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed',
            'border-muted-foreground/30 bg-muted/30 px-4 py-6 text-sm text-muted-foreground',
            'transition-colors hover:border-primary/50 hover:text-foreground'
          )}
        >
          <Upload size={20} />
          <span>{loading ? 'Carregando…' : label}</span>
        </button>
      ) : (
        <div className="flex items-center gap-3 rounded-xl border bg-card p-3">
          {isImage ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={picked.dataUrl}
              alt={picked.file.name}
              className="h-12 w-12 rounded-md object-cover"
            />
          ) : (
            <FileCheck className="h-10 w-10 text-emerald-500" />
          )}
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{picked.file.name}</p>
            <p className="text-xs text-muted-foreground">
              {(picked.file.size / 1024).toFixed(0)} KB
            </p>
          </div>
          <Button type="button" variant="ghost" size="icon" onClick={clear}>
            <X size={16} />
          </Button>
        </div>
      )}
    </div>
  )
}
